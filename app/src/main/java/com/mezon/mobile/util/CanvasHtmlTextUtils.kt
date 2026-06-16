package com.mezon.mobile.util

import org.json.JSONArray
import org.json.JSONObject

/**
 * Canvas HTML text helpers.
 *
 * Display path decodes safe entities (e.g. &plus;, &equals;) without turning encoded
 * markup (&lt;, &gt;) back into raw tags. Output path fully normalizes text first,
 * then escapes &, <, > for XSS-safe HTML generation.
 */
internal object CanvasHtmlTextUtils {

    private val ENTITY_PATTERN = Regex("""&(#x[0-9a-fA-F]+|#\d+|[a-zA-Z]+);""")

    private val SAFE_DISPLAY_NAMED_ENTITIES = mapOf(
        "nbsp" to "\u00A0",
        "quot" to "\"",
        "apos" to "'",
        "amp" to "&",
        "plus" to "+",
        "minus" to "-",
        "equals" to "=",
        "times" to "\u00D7",
        "divide" to "\u00F7",
        "hellip" to "\u2026",
        "mdash" to "\u2014",
        "ndash" to "\u2013",
        "copy" to "\u00A9",
        "reg" to "\u00AE",
        "trade" to "\u2122",
        "laquo" to "\u00AB",
        "raquo" to "\u00BB",
    )
    
    
    private val LIST_ITEM_PATTERN = Regex(
        """<li(\s[^>]*)?>(.*?)</li>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val LIST_ITEM_P_CLOSE_OPEN = Regex("""</p>\s*<p(\s[^>]*)?>""", RegexOption.IGNORE_CASE)
    private val LIST_ITEM_P_OPEN = Regex("""<p(\s[^>]*)?>""", RegexOption.IGNORE_CASE)
    private val LIST_ITEM_P_CLOSE = Regex("""</p>""", RegexOption.IGNORE_CASE)
    private val LIST_ITEM_H_CLOSE_OPEN = Regex("""</h[1-6]>\s*<h[1-6](\s[^>]*)?>""", RegexOption.IGNORE_CASE)
    private val LIST_ITEM_H_OPEN = Regex("""<h[1-6](\s[^>]*)?>""", RegexOption.IGNORE_CASE)
    private val LIST_ITEM_H_CLOSE = Regex("""</h[1-6]>""", RegexOption.IGNORE_CASE)

    fun prepareCanvasHtmlForDisplay(html: String): String {
        if (html.isEmpty()) return html
        val out = StringBuilder(html.length)
        var index = 0
        while (index < html.length) {
            val tagStart = html.indexOf('<', index)
            if (tagStart == -1) {
                out.append(decodeSafeDisplayEntities(html.substring(index)))
                break
            }
            if (tagStart > index) {
                out.append(decodeSafeDisplayEntities(html.substring(index, tagStart)))
            }
            val tagEnd = html.indexOf('>', tagStart)
            if (tagEnd == -1) {
                out.append(html.substring(tagStart))
                break
            }
            out.append(html, tagStart, tagEnd + 1)
            index = tagEnd + 1
        }
        return flattenBlockElementsInListItems(out.toString())
    }

    fun escapeHtmlText(text: String): String {
        val normalized = decodeHtmlEntitiesFully(text)
        return normalized
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    fun escapeHtmlAttribute(text: String): String {
        val normalized = decodeHtmlEntitiesFully(text)
        return normalized
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
    }

    fun decodeHtmlEntitiesFully(text: String): String {
        var result = text
        var previous: String
        do {
            previous = result
            result = decodeEntityPass(result, allowMarkupEntities = true)
        } while (result != previous)
        return result
    }

    fun docToHtml(docJson: String): String {
        if (docJson.isBlank()) return "<p><br></p>"
        return try {
            val root = JSONObject(docJson)
            if (root.optString("type") != "doc") return "<p><br></p>"
            val content = root.optJSONArray("content") ?: return "<p><br></p>"
            val html = buildString {
                for (i in 0 until content.length()) {
                    val node = content.optJSONObject(i) ?: continue
                    append(renderProseMirrorBlockNode(node))
                }
            }
            html.ifBlank { "<p><br></p>" }
        } catch (_: Exception) {
            "<p><br></p>"
        }
    }

    fun htmlToDoc(html: String): String {
        val content = JSONArray()
        val blocks = splitProseMirrorHtmlBlocks(html)
        if (blocks.isEmpty()) {
            content.put(emptyProseMirrorParagraph())
        } else {
            for (block in blocks) {
                content.put(proseMirrorHtmlBlockToNode(block))
            }
        }
        return JSONObject()
            .put("type", "doc")
            .put("content", content)
            .toString()
    }

    private fun decodeSafeDisplayEntities(text: String): String {
        var result = text
        var previous: String
        do {
            previous = result
            result = decodeEntityPass(result, allowMarkupEntities = false)
        } while (result != previous)
        return result
    }

    private fun decodeEntityPass(text: String, allowMarkupEntities: Boolean): String {
        return ENTITY_PATTERN.replace(text) { match ->
            resolveEntity(match.groupValues[1], allowMarkupEntities) ?: match.value
        }
    }

    private fun resolveEntity(body: String, allowMarkupEntities: Boolean): String? {
        return when {
            body.equals("lt", ignoreCase = true) -> if (allowMarkupEntities) "<" else null
            body.equals("gt", ignoreCase = true) -> if (allowMarkupEntities) ">" else null
            body.startsWith("#x", ignoreCase = true) -> {
                decodeNumericEntity(body.substring(2), radix = 16, allowMarkupEntities)
            }
            body.startsWith("#") -> {
                decodeNumericEntity(body.substring(1), radix = 10, allowMarkupEntities)
            }
            else -> SAFE_DISPLAY_NAMED_ENTITIES[body]
        }
    }

    private fun decodeNumericEntity(raw: String, radix: Int, allowMarkupEntities: Boolean): String? {
        val codePoint = raw.toIntOrNull(radix) ?: return null
        if (!allowMarkupEntities && (codePoint == 60 || codePoint == 62)) return null
        if (codePoint <= 0 || codePoint > 0x10FFFF) return null
        return String(Character.toChars(codePoint))
    }

    private fun renderProseMirrorBlockNode(node: JSONObject): String {
        return when (node.optString("type")) {
            "paragraph" -> "<p>${renderProseMirrorInlineNodes(node.optJSONArray("content"))}</p>"
            "heading" -> {
                val level = node.optJSONObject("attrs")?.proseMirrorHeaderLevel() ?: 1
                val tag = "h${level.coerceIn(1, 6)}"
                "<$tag>${renderProseMirrorInlineNodes(node.optJSONArray("content"))}</$tag>"
            }
            "blockquote" -> {
                "<blockquote>${renderProseMirrorChildBlocks(node.optJSONArray("content"))}</blockquote>"
            }
            "codeBlock" -> {
                val text = extractProseMirrorPlainText(node)
                "<pre><code>${escapeHtmlText(text)}</code></pre>"
            }
            "bulletList" -> renderProseMirrorList(node, "ul")
            "orderedList" -> renderProseMirrorList(node, "ol")
            "taskList" -> renderProseMirrorTaskList(node)
            "listItem", "taskItem" -> "<li>${renderProseMirrorListItemContent(node.optJSONArray("content"))}</li>"
            "horizontalRule" -> "<hr>"
            "image" -> {
                val attrs = node.optJSONObject("attrs")
                val src = attrs?.optString("src", "").orEmpty()
                val alt = attrs?.optString("alt", "").orEmpty()
                """<p><img src="${escapeHtmlAttribute(src)}" alt="${escapeHtmlAttribute(alt)}"></p>"""
            }
            "hardBreak" -> "<br>"
            "text" -> wrapProseMirrorMarks(node.optString("text", ""), node.optJSONArray("marks"))
            else -> renderProseMirrorChildBlocks(node.optJSONArray("content"))
        }
    }

    private fun renderProseMirrorList(node: JSONObject, tag: String): String {
        val items = node.optJSONArray("content") ?: return ""
        val sb = StringBuilder("<$tag>")
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            sb.append(renderProseMirrorBlockNode(item))
        }
        return sb.append("</$tag>").toString()
    }

    private fun renderProseMirrorTaskList(node: JSONObject): String {
        val items = node.optJSONArray("content") ?: return ""
        val sb = StringBuilder("""<ul data-checked="true">""")
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val checked = item.optJSONObject("attrs")?.optBoolean("checked", false) == true
            sb.append("<li")
            sb.append(if (checked) """ data-checked="true"""" else """ data-checked="false"""")
            sb.append(">")
            sb.append(renderProseMirrorListItemContent(item.optJSONArray("content")))
            sb.append("</li>")
        }
        return sb.append("</ul>").toString()
    }

    private fun renderProseMirrorChildBlocks(content: JSONArray?): String {
        if (content == null || content.length() == 0) return ""
        return buildString {
            for (i in 0 until content.length()) {
                val child = content.optJSONObject(i) ?: continue
                append(renderProseMirrorBlockNode(child))
            }
        }
    }

    private fun renderProseMirrorListItemContent(content: JSONArray?): String {
        if (content == null || content.length() == 0) return "<br>"
        return buildString {
            for (i in 0 until content.length()) {
                if (i > 0) append("<br>")
                val child = content.optJSONObject(i) ?: continue
                append(
                    when (child.optString("type")) {
                        "paragraph" -> renderProseMirrorInlineNodes(child.optJSONArray("content"))
                        "heading" -> {
                            val inline = renderProseMirrorInlineNodes(child.optJSONArray("content"))
                            "<strong>$inline</strong>"
                        }
                        "bulletList", "orderedList", "taskList" -> renderProseMirrorBlockNode(child)
                        else -> renderProseMirrorBlockNode(child)
                    }
                )
            }
        }.ifBlank { "<br>" }
    }

    private fun renderProseMirrorInlineNodes(content: JSONArray?): String {
        if (content == null || content.length() == 0) return "<br>"
        return buildString {
            for (i in 0 until content.length()) {
                val node = content.optJSONObject(i) ?: continue
                when (node.optString("type")) {
                    "text" -> append(wrapProseMirrorMarks(node.optString("text", ""), node.optJSONArray("marks")))
                    "hardBreak" -> append("<br>")
                    else -> append(renderProseMirrorBlockNode(node))
                }
            }
        }.ifBlank { "<br>" }
    }

    private fun wrapProseMirrorMarks(text: String, marks: JSONArray?): String {
        var out = escapeHtmlText(text)
        if (marks == null || marks.length() == 0) return out
        for (i in 0 until marks.length()) {
            val mark = marks.optJSONObject(i) ?: continue
            val attrs = mark.optJSONObject("attrs")
            out = when (mark.optString("type")) {
                "bold" -> "<strong>$out</strong>"
                "italic" -> "<em>$out</em>"
                "underline" -> "<u>$out</u>"
                "strike" -> "<s>$out</s>"
                "code" -> "<code>$out</code>"
                "link" -> {
                    val href = attrs?.optString("href", "").orEmpty()
                    """<a href="${escapeHtmlAttribute(href)}">$out</a>"""
                }
                "textStyle" -> {
                    val color = attrs?.optString("color", "").orEmpty()
                    if (color.isNotEmpty()) """<span style="color:${escapeHtmlAttribute(color)}">$out</span>""" else out
                }
                "highlight" -> {
                    val color = attrs?.optString("color", "").orEmpty()
                    if (color.isNotEmpty()) """<span style="background-color:${escapeHtmlAttribute(color)}">$out</span>""" else out
                }
                else -> out
            }
        }
        return out
    }

    private fun extractProseMirrorPlainText(node: JSONObject): String {
        val content = node.optJSONArray("content") ?: return ""
        return buildString {
            for (i in 0 until content.length()) {
                val child = content.optJSONObject(i) ?: continue
                if (child.optString("type") == "text") {
                    append(child.optString("text", ""))
                } else {
                    append(extractProseMirrorPlainText(child))
                }
            }
        }
    }

    private data class ProseMirrorHtmlBlock(val tag: String, val innerHtml: String)

    private fun splitProseMirrorHtmlBlocks(html: String): List<ProseMirrorHtmlBlock> {
        val normalized = html.trim()
        if (normalized.isEmpty()) return emptyList()
        val pattern = Regex(
            """<(p|h1|h2|h3|h4|h5|h6|blockquote|pre|li)(?:\s+[^>]*)?>(.*?)</\1>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val blocks = pattern.findAll(normalized).map { match ->
            ProseMirrorHtmlBlock(tag = match.groupValues[1].lowercase(), innerHtml = match.groupValues[2])
        }.toList()
        if (blocks.isNotEmpty()) return blocks
        return listOf(ProseMirrorHtmlBlock(tag = "p", innerHtml = stripProseMirrorTags(normalized)))
    }

    private fun proseMirrorHtmlBlockToNode(block: ProseMirrorHtmlBlock): JSONObject {
        return when (block.tag) {
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                val level = block.tag.removePrefix("h").toIntOrNull() ?: 1
                JSONObject()
                    .put("type", "heading")
                    .put("attrs", JSONObject().put("level", level))
                    .put("content", proseMirrorInlineNodesFromHtml(block.innerHtml))
            }
            "blockquote" -> {
                JSONObject()
                    .put("type", "blockquote")
                    .put("content", JSONArray().put(proseMirrorHtmlBlockToNode(ProseMirrorHtmlBlock("p", block.innerHtml))))
            }
            "pre" -> {
                JSONObject()
                    .put("type", "codeBlock")
                    .put("content", JSONArray().put(proseMirrorTextNode(stripProseMirrorTags(block.innerHtml))))
            }
            else -> {
                JSONObject()
                    .put("type", "paragraph")
                    .put("content", proseMirrorInlineNodesFromHtml(block.innerHtml))
            }
        }
    }

    private fun proseMirrorInlineNodesFromHtml(html: String): JSONArray {
        val nodes = JSONArray()
        parseProseMirrorInlineHtml(html, nodes)
        if (nodes.length() == 0) {
            nodes.put(proseMirrorTextNode(""))
        }
        return nodes
    }

    private fun parseProseMirrorInlineHtml(html: String, out: JSONArray) {
        var index = 0
        while (index < html.length) {
            val tagStart = html.indexOf('<', index)
            if (tagStart == -1) {
                appendProseMirrorTextNode(out, html.substring(index), null)
                break
            }
            if (tagStart > index) {
                appendProseMirrorTextNode(out, html.substring(index, tagStart), null)
            }
            val tagEnd = html.indexOf('>', tagStart)
            if (tagEnd == -1) {
                appendProseMirrorTextNode(out, html.substring(tagStart), null)
                break
            }
            val tagContent = html.substring(tagStart + 1, tagEnd).trim()
            val closing = tagContent.startsWith("/")
            if (closing) {
                index = tagEnd + 1
                continue
            }
            val tagName = tagContent.split(Regex("\\s+")).firstOrNull()?.lowercase().orEmpty()
            when (tagName) {
                "br" -> out.put(JSONObject().put("type", "hardBreak"))
                "strong", "b", "em", "i", "u", "s", "code", "a", "span" -> {
                    val closeTag = "</$tagName>"
                    val closeIndex = html.indexOf(closeTag, tagEnd + 1, ignoreCase = true)
                    if (closeIndex == -1) {
                        index = tagEnd + 1
                        continue
                    }
                    val inner = html.substring(tagEnd + 1, closeIndex)
                    val marks = proseMirrorMarksForTag(tagName, tagContent)
                    val innerNodes = JSONArray()
                    parseProseMirrorInlineHtml(inner, innerNodes)
                    for (i in 0 until innerNodes.length()) {
                        val node = innerNodes.optJSONObject(i) ?: continue
                        if (node.optString("type") != "text") {
                            out.put(node)
                            continue
                        }
                        val mergedMarks = mergeProseMirrorMarks(marks, node.optJSONArray("marks"))
                        out.put(proseMirrorTextNode(node.optString("text", ""), mergedMarks))
                    }
                    index = closeIndex + closeTag.length
                }
                else -> index = tagEnd + 1
            }
        }
    }

    private fun proseMirrorMarksForTag(tagName: String, tagContent: String): JSONArray {
        val marks = JSONArray()
        when (tagName) {
            "strong", "b" -> marks.put(JSONObject().put("type", "bold"))
            "em", "i" -> marks.put(JSONObject().put("type", "italic"))
            "u" -> marks.put(JSONObject().put("type", "underline"))
            "s" -> marks.put(JSONObject().put("type", "strike"))
            "code" -> marks.put(JSONObject().put("type", "code"))
            "a" -> {
                val href = Regex("""href="([^"]*)"""", RegexOption.IGNORE_CASE)
                    .find(tagContent)
                    ?.groupValues
                    ?.getOrNull(1)
                    .orEmpty()
                marks.put(
                    JSONObject()
                        .put("type", "link")
                        .put("attrs", JSONObject().put("href", href))
                )
            }
        }
        return marks
    }

    private fun mergeProseMirrorMarks(outer: JSONArray?, inner: JSONArray?): JSONArray? {
        val merged = JSONArray()
        outer?.let { for (i in 0 until it.length()) merged.put(it.get(i)) }
        inner?.let { for (i in 0 until it.length()) merged.put(it.get(i)) }
        return if (merged.length() > 0) merged else null
    }

    private fun appendProseMirrorTextNode(out: JSONArray, text: String, marks: JSONArray?) {
        if (text.isEmpty()) return
        val decoded = text
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
        out.put(proseMirrorTextNode(decoded, marks))
    }

    private fun proseMirrorTextNode(text: String, marks: JSONArray? = null): JSONObject {
        val node = JSONObject().put("type", "text").put("text", text)
        if (marks != null && marks.length() > 0) {
            node.put("marks", marks)
        }
        return node
    }

    private fun emptyProseMirrorParagraph(): JSONObject {
        return JSONObject()
            .put("type", "paragraph")
            .put("content", JSONArray().put(proseMirrorTextNode("")))
    }

    private fun JSONObject.proseMirrorHeaderLevel(): Int {
        val value = opt("level") ?: return 1
        return when (value) {
            is Int -> value
            is Long -> value.toInt()
            is Double -> value.toInt()
            is String -> value.toIntOrNull() ?: 1
            else -> 1
        }
    }

    private fun stripProseMirrorTags(html: String): String {
        return decodeHtmlEntitiesFully(
            html
                .replace(Regex("(?i)<br\\s*/?>"), "\n")
                .replace(Regex("<[^>]+>"), "")
        ).trim()
    }

    private fun flattenBlockElementsInListItems(html: String): String {
        return LIST_ITEM_PATTERN.replace(html) { match ->
            val attrs = match.groupValues[1]
            val inner = match.groupValues[2].trim()
                .replace(LIST_ITEM_P_CLOSE_OPEN, "<br>")
                .replace(LIST_ITEM_H_CLOSE_OPEN, "<br>")
                .replace(LIST_ITEM_P_OPEN, "")
                .replace(LIST_ITEM_P_CLOSE, "")
                .replace(LIST_ITEM_H_OPEN, "<strong>")
                .replace(LIST_ITEM_H_CLOSE, "</strong>")
            "<li$attrs>$inner</li>"
        }
    }
}
