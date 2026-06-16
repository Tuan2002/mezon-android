package com.mezon.mobile.util

import org.json.JSONArray
import org.json.JSONObject

object CanvasComposeQuillBridge {

    private const val EMPTY_HTML = "<p><br></p>"

    fun composeQuillJsonFromApiContent(apiContent: String): String {
        val html = apiContentToHtml(apiContent)
        return buildComposeQuillJson(html.ifBlank { EMPTY_HTML })
    }

    fun composeQuillJsonFromEditorHtml(html: String): String {
        return buildComposeQuillJson(html.ifBlank { EMPTY_HTML })
    }

    fun htmlFromComposeQuillJson(composeQuillJson: String): String {
        val trimmed = composeQuillJson.trim()
        if (trimmed.isEmpty()) return EMPTY_HTML
        return CanvasHtmlTextUtils.prepareCanvasHtmlForDisplay(
            extractHtmlFromComposeQuillJson(trimmed)
        ).ifBlank { EMPTY_HTML }
    }

    fun canvasHtmlEquals(left: String, right: String): Boolean {
        return normalizeCanvasHtml(left) == normalizeCanvasHtml(right)
    }

    fun apiContentFromComposeQuillJson(
        composeQuillJson: String,
        originalApiContent: String? = null
    ): String {
        val trimmed = composeQuillJson.trim()
        if (trimmed.isEmpty()) {
            return defaultEmptyContent(originalApiContent)
        }
        val html = extractHtmlFromComposeQuillJson(trimmed).ifBlank { EMPTY_HTML }
        val original = originalApiContent?.let { normalizeApiContent(it) }.orEmpty()
        return if (isProseMirrorDoc(original)) {
            CanvasHtmlTextUtils.htmlToDoc(html)
        } else {
            QuillDeltaHtmlConverter.htmlToDelta(html)
        }
    }

    private fun apiContentToHtml(apiContent: String): String {
        val normalized = normalizeApiContent(apiContent)
        if (normalized.isEmpty()) return EMPTY_HTML
        return when (detectContentKind(normalized)) {
            ContentKind.COMPOSE_QUILL -> {
                CanvasHtmlTextUtils.prepareCanvasHtmlForDisplay(
                    extractHtmlFromComposeQuillJson(normalized).ifBlank { EMPTY_HTML }
                )
            }
            ContentKind.PROSEMIRROR_DOC -> CanvasHtmlTextUtils.prepareCanvasHtmlForDisplay(
                CanvasHtmlTextUtils.docToHtml(normalized)
            )
            ContentKind.HTML -> CanvasHtmlTextUtils.prepareCanvasHtmlForDisplay(normalized)
            ContentKind.DELTA -> CanvasHtmlTextUtils.prepareCanvasHtmlForDisplay(
                QuillDeltaHtmlConverter.deltaToHtml(normalized)
            )
            ContentKind.PLAIN_TEXT -> "<p>${CanvasHtmlTextUtils.escapeHtmlText(normalized)}</p>"
        }
    }

    private fun defaultEmptyContent(originalApiContent: String?): String {
        val original = originalApiContent?.let { normalizeApiContent(it) }.orEmpty()
        return if (isProseMirrorDoc(original)) {
            CanvasHtmlTextUtils.htmlToDoc(EMPTY_HTML)
        } else {
            QuillDeltaHtmlConverter.emptyDelta()
        }
    }

    private fun normalizeApiContent(content: String): String {
        var value = content.trim()
        repeat(3) {
            if (value.length < 2 || value.first() != '"' || value.last() != '"') return value
            try {
                val unwrapped = JSONObject("""{"v":$value}""").getString("v").trim()
                if (unwrapped == value) return value
                value = unwrapped
            } catch (_: Exception) {
                return value
            }
        }
        return value
    }

    private enum class ContentKind {
        COMPOSE_QUILL,
        PROSEMIRROR_DOC,
        DELTA,
        HTML,
        PLAIN_TEXT
    }

    private fun detectContentKind(content: String): ContentKind {
        if (isComposeQuillFormat(content)) return ContentKind.COMPOSE_QUILL
        if (isProseMirrorDoc(content)) return ContentKind.PROSEMIRROR_DOC
        if (isQuillDelta(content)) return ContentKind.DELTA
        if (content.startsWith("<")) return ContentKind.HTML
        return ContentKind.PLAIN_TEXT
    }

    private fun isProseMirrorDoc(content: String): Boolean {
        if (!content.startsWith("{")) return false
        return try {
            val root = JSONObject(content)
            root.optString("type") == "doc" && root.optJSONArray("content") != null
        } catch (_: Exception) {
            false
        }
    }

    private fun isComposeQuillFormat(json: String): Boolean {
        if (!json.startsWith("[")) return false
        return try {
            val arr = JSONArray(json)
            arr.length() > 0 && arr.optJSONObject(0)?.has("type") == true
        } catch (_: Exception) {
            false
        }
    }

    private fun isQuillDelta(content: String): Boolean {
        if (!content.startsWith("{")) return false
        return try {
            val root = JSONObject(content)
            root.has("ops") && root.optJSONArray("ops") != null
        } catch (_: Exception) {
            false
        }
    }

    private fun extractHtmlFromComposeQuillJson(json: String): String {
        return try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val type = item.optString("type", "")
                if (type.equals("TEXT", ignoreCase = true)) {
                    return item.optString("value", "")
                }
            }
            ""
        } catch (_: Exception) {
            when {
                isProseMirrorDoc(json) -> CanvasHtmlTextUtils.docToHtml(json)
                isQuillDelta(json) -> QuillDeltaHtmlConverter.deltaToHtml(json)
                else -> ""
            }
        }
    }

    private fun buildComposeQuillJson(html: String): String {
        val arr = JSONArray()
        arr.put(
            JSONObject()
                .put("type", "TEXT")
                .put("value", html)
        )
        arr.put(
            JSONObject()
                .put("type", "IMAGE")
                .put("value", JSONObject.NULL)
        )
        arr.put(
            JSONObject()
                .put("type", "VIDEO")
                .put("value", JSONObject.NULL)
        )
        return arr.toString()
    }

    private fun normalizeCanvasHtml(html: String): String {
        return html.trim()
            .replace("\r\n", "\n")
            .replace(Regex(">\\s+<"), "><")
    }
}
