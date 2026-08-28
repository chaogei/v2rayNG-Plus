package com.v2ray.ang.ui

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.main.MainActivity
import com.v2ray.ang.util.LogRedaction
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder

class UrlSchemeActivity : BaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            when (val request = readRequest()) {
                is UrlSchemeRequest.Import -> {
                    // The main screen is where the imported profile shows up, so open it right
                    // away while this activity is still the foreground task; the import itself
                    // is too slow to wait for and a background start would be blocked.
                    startActivity(Intent(this, MainActivity::class.java))
                    importThenFinish(request)
                    return
                }

                UrlSchemeRequest.MissingUrl -> toastError(R.string.toast_url_scheme_missing_url)

                is UrlSchemeRequest.UnsupportedHost -> {
                    LogUtil.w(AppConfig.TAG, "URL scheme: unsupported host ${request.host}")
                    toastError(R.string.toast_url_scheme_unsupported)
                }

                UrlSchemeRequest.Empty -> Unit
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Error processing URL scheme", e)
            toastError(R.string.toast_failure)
        }
        finish()
    }

    @Composable
    override fun ScreenContent() {
    }

    private fun readRequest(): UrlSchemeRequest = when (intent?.action) {
        Intent.ACTION_SEND ->
            if (intent.type == "text/plain") {
                UrlSchemeRequest.fromSharedText(intent.getStringExtra(Intent.EXTRA_TEXT))
            } else {
                UrlSchemeRequest.Empty
            }

        Intent.ACTION_VIEW -> {
            val uri = intent.data
            UrlSchemeRequest.fromViewLink(uri?.host, uri?.getQueryParameter("url"), uri?.fragment)
        }

        else -> UrlSchemeRequest.Empty
    }

    /**
     * Finishing before the import returns would cancel it with `lifecycleScope`, so this
     * activity stays alive — invisible, behind the main screen it just opened — until there
     * is an outcome to report.
     */
    private fun importThenFinish(request: UrlSchemeRequest.Import) {
        // The incoming link is a share link or a subscription URL, so it carries the node
        // password or the subscription token in its path, query or fragment.
        LogUtil.i(AppConfig.TAG, "URL scheme received: ${LogRedaction.url(request.url)}")

        val url = UrlSchemeRequest.mergeFragment(decodeOrRaw(request.url), request.fragment)

        lifecycleScope.launch(Dispatchers.IO) {
            val imported = runCatching { AngConfigManager.importBatchConfig(url, "", false) }
                .onFailure { LogUtil.e(AppConfig.TAG, "URL scheme import failed", it) }
                .getOrNull()
            val count = imported?.let { it.first + it.second } ?: 0
            withContext(Dispatchers.Main) {
                if (count > 0) {
                    toastSuccess(R.string.import_subscription_success)
                } else {
                    toastError(R.string.import_subscription_failure)
                }
                finish()
            }
        }
    }

    /**
     * Share links are percent-encoded when they travel inside `?url=`, but a link shared as
     * plain text may contain a bare `%` that makes the decoder throw. A link we cannot decode
     * is still worth trying as-is instead of being dropped without a word.
     */
    private fun decodeOrRaw(url: String): String = try {
        URLDecoder.decode(url, "UTF-8")
    } catch (e: IllegalArgumentException) {
        LogUtil.w(AppConfig.TAG, "URL scheme: link is not percent-encoded, importing verbatim", e)
        url
    }
}
