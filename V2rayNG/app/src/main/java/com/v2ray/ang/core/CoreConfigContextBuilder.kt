package com.v2ray.ang.core

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.CoreConfigContext
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.enums.BalancerStrategyType
import com.v2ray.ang.enums.CoreResolvedType
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils

/**
 * Build runtime context from the selected profile.
 *
 * All outbound type analysis is completed here for both the selected profile
 * and routing targets. Custom profiles are returned immediately without
 * entering the normal analysis flow.
 */
object CoreConfigContextBuilder {

    /**
     * Load one profile and produce a fully analyzed context.
     *
     * An empty [guid] means "local proxy only": no profile is resolved and the caller
     * builds a freedom outbound instead. Null is returned only when a requested profile
     * cannot be loaded.
     */
    fun build(context: Context, guid: String): CoreConfigContext? {
        if (guid.isEmpty()) {
            val rulesets = MmkvManager.decodeRoutingRulesets().orEmpty()
            return CoreConfigContext(
                context = context,
                guid = guid,
                isDirectOnly = true,
                routingDomainRules = collectRoutingDomainRulesForDns(rulesets),
                rulesetItems = rulesets,
            )
        }

        val config = MmkvManager.decodeServerConfig(guid) ?: return null

        // CUSTOM: return immediately — CoreConfigManager handles this path on its own.
        if (config.configType == EConfigType.CUSTOM) {
            return CoreConfigContext(context = context, guid = guid, isCustom = true)
        }

        // Profiles and rulesets are decoded from MMKV at most once per build.
        // Every remark lookup below hits an in-memory index instead of
        // re-decoding the whole server list per lookup (previously
        // O(tags x servers) Gson work on connect/switch/reload).
        val profileStore = ProfileStore()
        val rulesetItems = MmkvManager.decodeRoutingRulesets().orEmpty()

        // Step 1: Resolve the main outbound (always tag = TAG_PROXY).
        val primaryResolvedOutbound = resolveOutbound(AppConfig.TAG_PROXY, config, profileStore) ?: run {
            LogUtil.e(AppConfig.TAG, "Failed to resolve main outbound for '${config.remarks}'")
            return null
        }

        // Step 2: Resolve all non-builtin routing outbound tags.
        val routingResolvedOutbounds = resolveRoutingOutbounds(rulesetItems, profileStore)
        val resolvedOutbounds = listOf(primaryResolvedOutbound) + routingResolvedOutbounds
        val fallbackResolvedOutbounds = resolveFallbackOutbounds(resolvedOutbounds, profileStore)
        val routingDomainRules = collectRoutingDomainRulesForDns(rulesetItems)

        return CoreConfigContext(
            context = context,
            guid = guid,
            resolvedOutbounds = resolvedOutbounds + fallbackResolvedOutbounds,
            routingDomainRules = routingDomainRules,
            rulesetItems = rulesetItems,
        )
    }

    /**
     * Per-build profile cache. The full server list is decoded lazily and at
     * most once per config build; remark lookups use a prebuilt index keeping
     * the first profile per remark, which matches the previous firstOrNull
     * linear-scan semantics of [SettingsManager.getServerViaRemarks].
     */
    private class ProfileStore {
        val allProfiles: List<ProfileItem> by lazy {
            MmkvManager.decodeAllServerList()
                .mapNotNull { guid -> MmkvManager.decodeServerConfig(guid) }
        }

        private val profilesByRemarks: Map<String, ProfileItem> by lazy {
            HashMap<String, ProfileItem>(allProfiles.size * 2).apply {
                allProfiles.forEach { putIfAbsent(it.remarks, it) }
            }
        }

        fun findByRemarks(remarks: String?): ProfileItem? {
            if (remarks.isNullOrEmpty()) return null
            return profilesByRemarks[remarks]
        }
    }

    /**
     * Resolve one outbound target into a normalized outbound entry.
     *
     * Custom profiles are ignored at this stage and produce no entry.
     */
    private fun resolveOutbound(tag: String, profile: ProfileItem, profileStore: ProfileStore): CoreConfigContext.ResolvedOutbound? {
        if (profile.configType == EConfigType.CUSTOM) {
            return null
        }

        val (resolvedProfiles, resolvedType) = when (profile.configType) {
            EConfigType.POLICYGROUP -> Pair(
                resolvePolicyGroupProfiles(profile, profileStore),
                CoreResolvedType.POLICYGROUP,
            )

            EConfigType.PROXYCHAIN -> {
                val chainProfiles = resolveProxyChainProfiles(profile, profileStore)
                val type = if (chainProfiles.size <= 1) CoreResolvedType.NORMAL else CoreResolvedType.PROXYCHAIN
                Pair(chainProfiles, type)
            }

            else -> {
                val chainProfiles = resolveProxyChainProfilesFromGroup(profile, profileStore)
                val type = if (chainProfiles.size <= 1) CoreResolvedType.NORMAL else CoreResolvedType.PROXYCHAIN
                Pair(chainProfiles, type)
            }
        }

        return CoreConfigContext.ResolvedOutbound(
            tag = tag,
            profile = profile,
            resolvedProfiles = resolvedProfiles,
            resolvedType = resolvedType,
        )
    }

    /**
     * Collect and resolve non-builtin routing targets from enabled rules.
     *
     * Invalid or empty targets are skipped and handled by fallback logic later.
     */
    private fun resolveRoutingOutbounds(
        rulesetItems: List<RulesetItem>,
        profileStore: ProfileStore,
    ): List<CoreConfigContext.ResolvedOutbound> {
        if (rulesetItems.isEmpty()) return emptyList()
        val resolvedOutbounds = mutableListOf<CoreConfigContext.ResolvedOutbound>()
        val processedTags = mutableSetOf<String>()

        try {
            rulesetItems
                .filter { it.enabled }
                .mapNotNull { it.outboundTag.takeIf { tag -> tag.isNotBlank() } }
                .filter { tag -> tag !in AppConfig.BUILTIN_OUTBOUND_TAGS }
                .distinct()
                .forEach { tag ->
                    if (tag in processedTags) {
                        return@forEach
                    }
                    processedTags.add(tag)

                    try {
                        val profile = profileStore.findByRemarks(tag) ?: run {
                            LogUtil.w(AppConfig.TAG, "Routing tag '$tag' has no matching profile — will fall back to proxy at routing time")
                            return@forEach
                        }
                        val resolvedOutbound = resolveOutbound(tag, profile, profileStore) ?: run {
                            LogUtil.w(AppConfig.TAG, "Cannot use CUSTOM profile as routing outbound for tag '$tag', skipping")
                            return@forEach
                        }
                        if (resolvedOutbound.resolvedProfiles.isEmpty()) {
                            LogUtil.w(AppConfig.TAG, "Routing outbound '$tag' resolved to empty list, skipping")
                            return@forEach
                        }
                        resolvedOutbounds.add(resolvedOutbound)
                        LogUtil.d(AppConfig.TAG, "Resolved routing outbound: tag='$tag', type='${resolvedOutbound.resolvedType}', profiles=${resolvedOutbound.resolvedProfiles.size}")
                    } catch (e: Exception) {
                        LogUtil.e(AppConfig.TAG, "Failed to resolve routing outbound for tag '$tag', skipping", e)
                    }
                }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to resolve routing outbounds from rulesets", e)
        }

        return resolvedOutbounds
    }

    private fun resolvePolicyGroupProfiles(config: ProfileItem, profileStore: ProfileStore): List<ProfileItem> {
        try {
            return profileStore.allProfiles
                .asSequence()
                .filter { profile ->
                    val subscriptionId = config.policyGroupSubscriptionId
                    if (subscriptionId.isNullOrBlank()) {
                        true
                    } else {
                        profile.subscriptionId == subscriptionId
                    }
                }
                .filter { profile ->
                    val filter = config.policyGroupFilter
                    if (filter.isNullOrBlank()) {
                        true
                    } else {
                        try {
                            Regex(filter).containsMatchIn(profile.remarks)
                        } catch (_: Exception) {
                            profile.remarks.contains(filter)
                        }
                    }
                }
                .filter { it.server.isNotNullEmpty() }
                .filter { Utils.isPureIpAddress(it.server!!) || Utils.isValidUrl(it.server!!) }
                .filter { !it.configType.isComplexType() }
                .toList()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to resolve policy group profiles for '${config.remarks}'", e)
            return listOf(config)
        }
    }

    private fun resolveProxyChainProfiles(config: ProfileItem, profileStore: ProfileStore): List<ProfileItem> {
        if (config.proxyChainProfiles.isNullOrBlank()) {
            return listOf(config)
        }

        try {
            return config.proxyChainProfiles.orEmpty().split(",")
                .asSequence()
                .mapNotNull { remark -> profileStore.findByRemarks(remark) }
                .filter { it.server.isNotNullEmpty() }
                .filter { Utils.isPureIpAddress(it.server!!) || Utils.isValidUrl(it.server!!) }
                .filter { !it.configType.isComplexType() }
                .toList()
                .reversed()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to resolve proxy chain profiles for '${config.remarks}'", e)
            return listOf(config)
        }
    }

    /**
     * Resolve chain nodes from subscription neighbors in order: next, current, prev.
     *
     * When no chain is available, return a single-node result.
     */
    private fun resolveProxyChainProfilesFromGroup(config: ProfileItem, profileStore: ProfileStore): List<ProfileItem> {
        if (config.subscriptionId.isEmpty()) {
            return listOf(config)
        }

        try {
            val subItem = MmkvManager.decodeSubscription(config.subscriptionId) ?: return listOf(config)
            // Skip decoding the whole profile list for the common case of a
            // profile without chain neighbors configured.
            if (subItem.nextProfile.isNullOrEmpty() && subItem.prevProfile.isNullOrEmpty()) {
                return listOf(config)
            }
            val resolved = mutableListOf<ProfileItem>()
            profileStore.findByRemarks(subItem.nextProfile)?.let { resolved.add(it) }
            resolved.add(config)
            profileStore.findByRemarks(subItem.prevProfile)?.let { resolved.add(it) }
            return resolved
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to resolve proxy chain from group for '${config.remarks}'", e)
            return listOf(config)
        }
    }

    /**
     * Collect enabled routing domain rules in original order for DNS segmentation.
     *
     * outbounds are normalized into three tags only: proxy / direct / block.
     */
    private fun collectRoutingDomainRulesForDns(rulesetItems: List<RulesetItem>): List<CoreConfigContext.RoutingDomainRule> {
        if (rulesetItems.isEmpty()) return emptyList()
        val result = mutableListOf<CoreConfigContext.RoutingDomainRule>()

        rulesetItems
            .asSequence()
            .filter { it.enabled }
            .filter { !it.domain.isNullOrEmpty() }
            .forEach { rule ->
                val normalizedOutboundTag = when (rule.outboundTag) {
                    AppConfig.TAG_DIRECT -> AppConfig.TAG_DIRECT
                    AppConfig.TAG_BLOCKED -> AppConfig.TAG_BLOCKED
                    else -> AppConfig.TAG_PROXY
                }
                result.add(
                    CoreConfigContext.RoutingDomainRule(
                        domain = rule.domain.orEmpty(),
                        outboundTag = normalizedOutboundTag
                    )
                )
            }

        return result
    }

    /**
     * Resolve and collect fallback outbounds from all POLICYGROUP nodes.
     *
     * Fallback targets must not overlap with already resolved tags or builtin tags.
     */
    private fun resolveFallbackOutbounds(
        resolvedOutbounds: List<CoreConfigContext.ResolvedOutbound>,
        profileStore: ProfileStore,
    ): List<CoreConfigContext.ResolvedOutbound> {
        return resolvedOutbounds
            .asSequence()
            .filter { it.resolvedType == CoreResolvedType.POLICYGROUP }
            .filter { BalancerStrategyType.from(it.profile.policyGroupType).supportsObservatory && it.profile.policyGroupTestOutbounds != false }
            .mapNotNull { it.profile.policyGroupFallbackTag }
            .filter { it !in AppConfig.BUILTIN_OUTBOUND_TAGS && resolvedOutbounds.none { outbound -> outbound.tag == it } }
            .distinct()
            .mapNotNull { tag ->
                profileStore.findByRemarks(tag)
                    ?.takeUnless { it.configType == EConfigType.CUSTOM || it.configType == EConfigType.POLICYGROUP }
                    ?.let { resolveOutbound(tag, it, profileStore) }
            }
            .toList()
    }
}
