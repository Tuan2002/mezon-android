package com.mezon.mobile.util

import android.text.Html
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

data class InputOgpPreview(
    val url: String,
    val title: String,
    val description: String,
    val imageUrl: String
)

object InputOgpFetcher {
    private const val FETCH_MAX_BYTES = 24_576
    private const val CHECK_INTERVAL_BYTES = 1024
    private const val HEAD_CLOSE = "</head>"
    private val META_TAG_REGEX = Regex("""<meta\s+[^>]*>""", RegexOption.IGNORE_CASE)
    private val TITLE_TAG_REGEX = Regex(
        """<title[^>]*>(.*?)</title>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val META_ATTR_REGEX = Regex("""(\w+)\s*=\s*(['"])(.*?)\2""", RegexOption.IGNORE_CASE)

    private val activeCall = AtomicReference<Call?>(null)

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun cancelInFlight() {
        activeCall.getAndSet(null)?.cancel()
    }

    fun fetch(
        url: String,
        onTextReady: ((InputOgpPreview) -> Unit)? = null,
        onSendReady: ((InputOgpPreview) -> Unit)? = null
    ): InputOgpPreview? {
        cancelInFlight()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9,vi;q=0.8")
            .build()
        val call = client.newCall(request)
        activeCall.set(call)
        return try {
            call.execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                val contentType = body.contentType()?.toString().orEmpty()
                if (contentType.isNotEmpty() && !contentType.contains("html", ignoreCase = true)) {
                    return null
                }
                val html = readHtmlHead(body, url, onTextReady, onSendReady)
                parsePreview(url, html)
            }
        } catch (_: Exception) {
            null
        } finally {
            activeCall.compareAndSet(call, null)
        }
    }

    private fun readHtmlHead(
        body: ResponseBody,
        url: String,
        onTextReady: ((InputOgpPreview) -> Unit)?,
        onSendReady: ((InputOgpPreview) -> Unit)?
    ): String {
        return body.byteStream().bufferedReader().use { reader ->
            val sb = StringBuilder(FETCH_MAX_BYTES.coerceAtMost(8192))
            val buffer = CharArray(4096)
            var total = 0
            var lastCheckAt = 0
            var textReadyEmitted = false
            while (total < FETCH_MAX_BYTES) {
                val read = reader.read(buffer)
                if (read <= 0) break
                sb.append(buffer, 0, read)
                total += read
                val searchFrom = (sb.length - read - HEAD_CLOSE.length).coerceAtLeast(0)
                val headClosed = sb.indexOf(HEAD_CLOSE, searchFrom, ignoreCase = true) >= 0
                if (total - lastCheckAt < CHECK_INTERVAL_BYTES && !headClosed) continue
                lastCheckAt = total
                val preview = parsePreview(url, sb) ?: run {
                    if (headClosed) break
                    continue
                }
                if (!textReadyEmitted && isSendable(preview)) {
                    textReadyEmitted = true
                    onTextReady?.invoke(preview)
                }
                if (isSendable(preview)) {
                    onSendReady?.invoke(preview)
                    if (preview.imageUrl.isNotBlank() || headClosed) break
                } else if (headClosed) {
                    break
                }
            }
            sb.toString()
        }
    }

    private fun isSendable(preview: InputOgpPreview): Boolean {
        if (preview.title.isBlank()) return false
        return preview.imageUrl.isNotBlank() || preview.description.isNotBlank()
    }

    private fun parsePreview(url: String, html: CharSequence): InputOgpPreview? {
        if (html.isEmpty()) return null
        val (ogTitle, ogDesc, ogImage) = parseOgpMeta(html)
        val title = ogTitle.ifBlank { parseTitleTag(html) }
        val desc = ogDesc.trim()
        val image = ogImage.trim()
        if (title.isBlank() || (desc.isBlank() && image.isBlank())) return null
        return InputOgpPreview(url = url, title = title, description = desc, imageUrl = image)
    }

    private fun parseOgpMeta(html: CharSequence): Triple<String, String, String> {
        var ogTitle = ""
        var ogDesc = ""
        var ogImage = ""
        for (match in META_TAG_REGEX.findAll(html)) {
            val tag = match.value
            val property = extractMetaAttr(tag, "property")
            val name = extractMetaAttr(tag, "name")
            val content = extractMetaAttr(tag, "content")
            if (content.isBlank()) continue
            when {
                property.equals("og:title", ignoreCase = true) ||
                    name.equals("og:title", ignoreCase = true) ->
                    ogTitle = decodeHtml(content).trim()
                property.equals("og:description", ignoreCase = true) ||
                    name.equals("og:description", ignoreCase = true) ->
                    ogDesc = decodeHtml(content).trim()
                property.equals("og:image", ignoreCase = true) ||
                    name.equals("og:image", ignoreCase = true) ->
                    ogImage = decodeHtml(content).trim()
            }
            if (ogTitle.isNotBlank() && ogDesc.isNotBlank() && ogImage.isNotBlank()) break
        }
        return Triple(ogTitle, ogDesc, ogImage)
    }

    private fun parseTitleTag(html: CharSequence): String {
        return TITLE_TAG_REGEX.find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::decodeHtml)
            .orEmpty()
            .trim()
    }

    private fun extractMetaAttr(tag: String, attr: String): String {
        for (match in META_ATTR_REGEX.findAll(tag)) {
            if (!match.groupValues[1].equals(attr, ignoreCase = true)) continue
            return match.groupValues[3]
        }
        return ""
    }

    private fun decodeHtml(raw: String): String {
        return Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString()
    }
}
