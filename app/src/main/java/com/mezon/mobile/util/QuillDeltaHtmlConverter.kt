package com.mezon.mobile.util

import org.json.JSONArray
import org.json.JSONObject

internal object QuillDeltaHtmlConverter {

    private val BLOCK_ATTR_KEYS = setOf("header", "list", "blockquote", "code-block", "indent", "align")

    fun emptyDelta(): String = """{"ops":[{"insert":"\n"}]}"""

    fun deltaToHtml(deltaJson: String): String {
        if (deltaJson.isBlank()) return "<p><br></p>"
        return try {
            val ops = parseOps(deltaJson) ?: return "<p><br></p>"
            if (ops.length() == 0) return "<p><br></p>"
            renderLines(collectLines(ops))
        } catch (_: Exception) {
            "<p>${CanvasHtmlTextUtils.escapeHtmlText(deltaJson)}</p>"
        }
    }

    fun htmlToDelta(html: String): String {
        if (html.isBlank()) return emptyDelta()
        val ops = JSONArray()
        val blocks = splitHtmlBlocks(html)
        if (blocks.isEmpty()) {
            ops.put(JSONObject().put("insert", "\n"))
            return JSONObject().put("ops", ops).toString()
        }
        for (block in blocks) {
            when (block.tag) {
                "ul", "ol" -> appendListBlocksAsOps(ops, block)
                else -> appendBlockAsOps(ops, block)
            }
        }
        if (ops.length() == 0) {
            ops.put(JSONObject().put("insert", "\n"))
        }
        return JSONObject().put("ops", ops).toString()
    }

    private fun parseOps(deltaJson: String): JSONArray? {
        val trimmed = unwrapJsonString(deltaJson.trim())
        return try {
            val root = JSONObject(trimmed)
            root.optJSONArray("ops")
        } catch (_: Exception) {
            null
        }
    }

    private fun unwrapJsonString(value: String): String {
        var current = value
        repeat(3) {
            if (current.length < 2 || current.first() != '"' || current.last() != '"') return current
            try {
                val unwrapped = JSONObject("""{"v":$current}""").getString("v")
                if (unwrapped == current) return current
                current = unwrapped.trim()
            } catch (_: Exception) {
                return current
            }
        }
        return current
    }

    private data class InlineSegment(val text: String, val attrs: JSONObject?)
    private data class CanvasLine(
        val segments: MutableList<InlineSegment> = mutableListOf(),
        val embeds: MutableList<JSONObject> = mutableListOf(),
        var blockAttrs: JSONObject? = null
    )

    private fun collectLines(ops: JSONArray): List<CanvasLine> {
        val lines = mutableListOf<CanvasLine>()
        var current = CanvasLine()

        fun finishLine(blockAttrs: JSONObject?) {
            current.blockAttrs = blockAttrs
            lines.add(current)
            current = CanvasLine()
        }

        for (i in 0 until ops.length()) {
            val op = ops.optJSONObject(i) ?: continue
            if (op.has("retain")) continue
            val insert = op.opt("insert") ?: continue
            val attrs = op.optJSONObject("attributes")
            when (insert) {
                is String -> {
                    val (inlineAttrs, blockAttrs) = splitAttrs(attrs)
                    var start = 0
                    while (start <= insert.length) {
                        val newlineIndex = insert.indexOf('\n', start)
                        if (newlineIndex == -1) {
                            if (start < insert.length) {
                                current.segments.add(InlineSegment(insert.substring(start), inlineAttrs))
                            }
                            break
                        }
                        if (newlineIndex > start) {
                            current.segments.add(InlineSegment(insert.substring(start, newlineIndex), inlineAttrs))
                        }
                        finishLine(blockAttrs)
                        start = newlineIndex + 1
                    }
                }
                is JSONObject -> {
                    if (current.segments.isNotEmpty() || current.embeds.isNotEmpty()) {
                        finishLine(null)
                    }
                    current.embeds.add(insert)
                    finishLine(null)
                }
            }
        }

        if (current.segments.isNotEmpty() || current.embeds.isNotEmpty()) {
            lines.add(current)
        }
        return lines
    }

    private fun splitAttrs(attrs: JSONObject?): Pair<JSONObject?, JSONObject?> {
        if (attrs == null) return null to null
        val inline = JSONObject()
        val block = JSONObject()
        val keys = attrs.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (BLOCK_ATTR_KEYS.contains(key)) {
                block.put(key, attrs.get(key))
            } else {
                inline.put(key, attrs.get(key))
            }
        }
        return (if (inline.length() > 0) inline else null) to (if (block.length() > 0) block else null)
    }

    private fun renderLines(lines: List<CanvasLine>): String {
        val sb = StringBuilder()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            if (line.segments.isEmpty() && line.embeds.isNotEmpty()) {
                for (embed in line.embeds) {
                    sb.append(renderEmbed(embed))
                }
                index++
                continue
            }

            val listType = line.blockAttrs?.optString("list", "").orEmpty()
            if (listType.isNotEmpty()) {
                val isCheckList = listType == "checked" || listType == "unchecked"
                val listTag = if (listType == "ordered") "ol" else "ul"
                sb.append("<$listTag")
                if (isCheckList) sb.append(""" data-checked="true"""")
                sb.append(">")
                while (index < lines.size) {
                    val current = lines[index]
                    val currentList = current.blockAttrs?.optString("list", "").orEmpty()
                    val sameList = when {
                        isCheckList -> currentList == "checked" || currentList == "unchecked"
                        listType == "ordered" -> currentList == "ordered"
                        else -> currentList == "bullet"
                    }
                    if (!sameList) break
                    sb.append("<li")
                    when (currentList) {
                        "checked" -> sb.append(""" data-checked="true"""")
                        "unchecked" -> sb.append(""" data-checked="false"""")
                    }
                    sb.append(">")
                    sb.append(renderInline(current))
                    for (embed in current.embeds) {
                        sb.append(renderEmbed(embed))
                    }
                    sb.append("</li>")
                    index++
                }
                sb.append("</$listTag>")
                continue
            }

            sb.append(renderBlock(line))
            index++
        }
        return sb.toString().ifBlank { "<p><br></p>" }
    }

    private fun renderBlock(line: CanvasLine): String {
        val content = buildString {
            append(renderInline(line))
            for (embed in line.embeds) {
                append(renderEmbed(embed))
            }
        }
        val attrs = line.blockAttrs
        return when {
            attrs?.optString("code-block", "") == "plain" -> {
                val code = content.ifBlank { "<br>" }
                "<pre><code>$code</code></pre>"
            }
            attrs?.optBoolean("blockquote", false) == true -> {
                "<blockquote><p>${content.ifBlank { "<br>" }}</p></blockquote>"
            }
            attrs?.headerLevel() == 1 -> "<h1>${content.ifBlank { "<br>" }}</h1>"
            attrs?.headerLevel() == 2 -> "<h2>${content.ifBlank { "<br>" }}</h2>"
            attrs?.headerLevel() == 3 -> "<h3>${content.ifBlank { "<br>" }}</h3>"
            content.isBlank() -> "<p><br></p>"
            else -> "<p>$content</p>"
        }
    }

    private fun renderInline(line: CanvasLine): String {
        if (line.segments.isEmpty()) return ""
        return line.segments.joinToString("") { (text, attrs) ->
            wrapInline(text, attrs)
        }
    }

    private fun renderEmbed(embed: JSONObject): String {
        val imageUrl = embed.optString("image", "")
        if (imageUrl.isNotEmpty()) {
            return """<p><img src="${CanvasHtmlTextUtils.escapeHtmlAttribute(imageUrl)}"></p>"""
        }
        val videoUrl = embed.optString("video", "")
        if (videoUrl.isNotEmpty()) {
            return """<p><a href="${CanvasHtmlTextUtils.escapeHtmlAttribute(videoUrl)}">${CanvasHtmlTextUtils.escapeHtmlText(videoUrl)}</a></p>"""
        }
        return ""
    }

    private fun wrapInline(text: String, attrs: JSONObject?): String {
        if (text.isEmpty()) return ""
        var out = CanvasHtmlTextUtils.escapeHtmlText(text)
        if (attrs == null) return out
        if (attrs.optBoolean("code", false)) out = "<code>$out</code>"
        if (attrs.optBoolean("bold", false)) out = "<strong>$out</strong>"
        if (attrs.optBoolean("italic", false)) out = "<em>$out</em>"
        if (attrs.optBoolean("underline", false)) out = "<u>$out</u>"
        if (attrs.optBoolean("strike", false)) out = "<s>$out</s>"
        val color = attrs.optString("color", "")
        if (color.isNotEmpty()) out = """<span style="color:${CanvasHtmlTextUtils.escapeHtmlAttribute(color)}">$out</span>"""
        val background = attrs.optString("background", "")
        if (background.isNotEmpty()) out = """<span style="background-color:${CanvasHtmlTextUtils.escapeHtmlAttribute(background)}">$out</span>"""
        val link = attrs.optString("link", "")
        if (link.isNotEmpty()) out = """<a href="${CanvasHtmlTextUtils.escapeHtmlAttribute(link)}">$out</a>"""
        return out
    }

    private data class HtmlBlock(
        val tag: String,
        val attrs: Map<String, String> = emptyMap(),
        val innerHtml: String
    )

    private fun splitHtmlBlocks(html: String): List<HtmlBlock> {
        val normalized = html.trim()
        if (normalized.isEmpty()) return emptyList()
        val pattern = Regex(
            """<(p|h1|h2|h3|blockquote|pre|li|ul|ol)(?:\s+([^>]*))?>(.*?)</\1>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val blocks = pattern.findAll(normalized).map { match ->
            val tag = match.groupValues[1].lowercase()
            HtmlBlock(
                tag = tag,
                attrs = parseHtmlAttrs(match.groupValues[2]),
                innerHtml = match.groupValues[3]
            )
        }.toList()
        if (blocks.isNotEmpty()) return blocks
        return listOf(HtmlBlock(tag = "p", innerHtml = normalized))
    }

    private fun parseHtmlAttrs(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        val attrs = mutableMapOf<String, String>()
        Regex("""([\w-]+)="([^"]*)"""").findAll(raw).forEach { match ->
            attrs[match.groupValues[1].lowercase()] = match.groupValues[2]
        }
        return attrs
    }

    private fun appendBlockAsOps(ops: JSONArray, block: HtmlBlock) {
        val blockAttrs = JSONObject()
        when (block.tag) {
            "h1" -> blockAttrs.put("header", 1)
            "h2" -> blockAttrs.put("header", 2)
            "h3" -> blockAttrs.put("header", 3)
            "blockquote" -> blockAttrs.put("blockquote", true)
            "pre" -> blockAttrs.put("code-block", "plain")
            "li" -> {
                when (block.attrs["data-checked"]) {
                    "true" -> blockAttrs.put("list", "checked")
                    "false" -> blockAttrs.put("list", "unchecked")
                    else -> blockAttrs.put("list", "bullet")
                }
            }
        }
        appendInlineHtmlAsOps(ops, block.innerHtml)
        val newline = JSONObject().put("insert", "\n")
        if (blockAttrs.length() > 0) newline.put("attributes", blockAttrs)
        ops.put(newline)
    }

    private fun appendListBlocksAsOps(ops: JSONArray, block: HtmlBlock) {
        val listType = if (block.tag == "ol") "ordered" else "bullet"
        val liPattern = Regex(
            """<li(?:\s+([^>]*))?>(.*?)</li>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val matches = liPattern.findAll(block.innerHtml).toList()
        if (matches.isEmpty()) {
            appendBlockAsOps(ops, HtmlBlock("li", mapOf("data-list" to listType), block.innerHtml))
            return
        }
        for (match in matches) {
            val attrs = parseHtmlAttrs(match.groupValues[1]).toMutableMap()
            if (!attrs.containsKey("data-checked")) {
                attrs["data-list"] = listType
            }
            val liBlock = HtmlBlock("li", attrs, match.groupValues[2])
            val blockAttrs = JSONObject()
            when {
                attrs["data-checked"] == "true" -> blockAttrs.put("list", "checked")
                attrs["data-checked"] == "false" -> blockAttrs.put("list", "unchecked")
                listType == "ordered" -> blockAttrs.put("list", "ordered")
                else -> blockAttrs.put("list", "bullet")
            }
            appendInlineHtmlAsOps(ops, liBlock.innerHtml)
            val newline = JSONObject().put("insert", "\n")
            if (blockAttrs.length() > 0) newline.put("attributes", blockAttrs)
            ops.put(newline)
        }
    }

    private fun appendInlineHtmlAsOps(ops: JSONArray, html: String, attrs: JSONObject? = null) {
        var index = 0
        while (index < html.length) {
            val tagStart = html.indexOf('<', index)
            if (tagStart == -1) {
                appendTextOp(ops, CanvasHtmlTextUtils.decodeHtmlEntitiesFully(html.substring(index)), attrs)
                break
            }
            if (tagStart > index) {
                appendTextOp(ops, CanvasHtmlTextUtils.decodeHtmlEntitiesFully(html.substring(index, tagStart)), attrs)
            }
            val tagEnd = html.indexOf('>', tagStart)
            if (tagEnd == -1) break
            val tagContent = html.substring(tagStart + 1, tagEnd).trim()
            if (tagContent.startsWith("/")) {
                index = tagEnd + 1
                continue
            }
            val tagName = tagContent.split(Regex("\\s+")).firstOrNull()?.lowercase().orEmpty()
            when (tagName) {
                "br" -> appendTextOp(ops, "\n", attrs)
                "strong", "b", "em", "i", "u", "s", "strike", "code", "a", "span" -> {
                    val closeTag = "</$tagName>"
                    val closeIndex = html.indexOf(closeTag, tagEnd + 1, ignoreCase = true)
                    if (closeIndex == -1) {
                        index = tagEnd + 1
                        continue
                    }
                    val inner = html.substring(tagEnd + 1, closeIndex)
                    val merged = mergeInlineAttrs(attrs, inlineAttrsForTag(tagName, tagContent))
                    appendInlineHtmlAsOps(ops, inner, merged)
                    index = closeIndex + closeTag.length
                }
                else -> index = tagEnd + 1
            }
        }
    }

    private fun appendTextOp(ops: JSONArray, text: String, attrs: JSONObject?) {
        if (text.isEmpty()) return
        val op = JSONObject().put("insert", text)
        if (attrs != null && attrs.length() > 0) {
            op.put("attributes", cloneAttrs(attrs))
        }
        ops.put(op)
    }

    private fun inlineAttrsForTag(tagName: String, tagContent: String): JSONObject? {
        val attrs = JSONObject()
        when (tagName) {
            "strong", "b" -> attrs.put("bold", true)
            "em", "i" -> attrs.put("italic", true)
            "u" -> attrs.put("underline", true)
            "s", "strike" -> attrs.put("strike", true)
            "code" -> attrs.put("code", true)
            "a" -> {
                val href = Regex("""href="([^"]*)"""", RegexOption.IGNORE_CASE)
                    .find(tagContent)
                    ?.groupValues
                    ?.getOrNull(1)
                    .orEmpty()
                if (href.isNotEmpty()) attrs.put("link", href)
            }
            "span" -> {
                val style = Regex("""style="([^"]*)"""", RegexOption.IGNORE_CASE)
                    .find(tagContent)
                    ?.groupValues
                    ?.getOrNull(1)
                    .orEmpty()
                parseStyleAttrs(style, attrs)
            }
        }
        return if (attrs.length() > 0) attrs else null
    }

    private fun parseStyleAttrs(style: String, attrs: JSONObject) {
        Regex("""([\w-]+)\s*:\s*([^;]+)""").findAll(style).forEach { match ->
            when (match.groupValues[1].lowercase()) {
                "color" -> attrs.put("color", match.groupValues[2].trim())
                "background-color" -> attrs.put("background", match.groupValues[2].trim())
            }
        }
    }

    private fun mergeInlineAttrs(base: JSONObject?, extra: JSONObject?): JSONObject? {
        if (base == null && extra == null) return null
        val merged = JSONObject()
        base?.keys()?.forEach { key -> merged.put(key, base.get(key)) }
        extra?.keys()?.forEach { key -> merged.put(key, extra.get(key)) }
        return if (merged.length() > 0) merged else null
    }

    private fun cloneAttrs(source: JSONObject): JSONObject {
        val copy = JSONObject()
        source.keys().forEach { key -> copy.put(key, source.get(key)) }
        return copy
    }

    private fun JSONObject.headerLevel(): Int {
        val value = opt("header") ?: return 0
        return when (value) {
            is Int -> value
            is Long -> value.toInt()
            is Double -> value.toInt()
            is String -> value.toIntOrNull() ?: 0
            else -> 0
        }
    }
}
