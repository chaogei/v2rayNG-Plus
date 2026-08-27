package com.v2ray.ang.dto

import android.content.Context
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.enums.CoreResolvedType

data class CoreConfigContext(
    val context: Context,
    val guid: String,
    val isCustom: Boolean = false,
    /**
     * No profile is in play: the core only serves the local inbounds and everything
     * leaves through freedom. [resolvedOutbounds] is empty in that case.
     */
    val isDirectOnly: Boolean = false,
    val resolvedOutbounds: List<ResolvedOutbound> = emptyList(),
    val routingDomainRules: List<RoutingDomainRule> = emptyList(),
    /**
     * User routing rulesets decoded from MMKV once per build and shared by
     * outbound resolution, DNS assembly, and routing-rule generation, instead
     * of each consumer re-parsing the ruleset JSON.
     */
    val rulesetItems: List<RulesetItem> = emptyList(),
) {
    data class ResolvedOutbound(
        val tag: String,
        val profile: ProfileItem,
        val resolvedProfiles: List<ProfileItem>,
        val resolvedType: CoreResolvedType,
    )

    data class RoutingDomainRule(
        val domain: List<String>,
        val outboundTag: String,
    )
}
