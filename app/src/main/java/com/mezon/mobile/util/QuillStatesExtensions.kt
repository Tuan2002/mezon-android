package com.mezon.mobile.util

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.mohamedrejeb.richeditor.model.RichTextState
import io.tbib.composequill.extensions.fixHtml
import io.tbib.composequill.states.QuillStates
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

private val richParagraphClass = Class.forName("com.mohamedrejeb.richeditor.paragraph.RichParagraph")

@Suppress("UNCHECKED_CAST")
internal fun RichTextState.applyCanvasViewerBlockSpacing(spacingPx: Int = 12) {
    if (spacingPx <= 0) return

    val getParagraphs = RichTextState::class.java.getMethod("getRichParagraphList\$richeditor_compose_release")
    val updateParagraphs = RichTextState::class.java.getMethod(
        "updateRichParagraphList\$richeditor_compose_release",
        List::class.java,
    )
    val isEmptyMethod = richParagraphClass.getMethod("isEmpty", Boolean::class.javaPrimitiveType)

    val source = (getParagraphs.invoke(this) as Iterable<Any>).toList()
    if (source.isEmpty()) return

    val spacingStyle = ParagraphStyle(
        lineHeight = spacingPx.sp,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Top,
            trim = LineHeightStyle.Trim.Both,
        ),
    )
    val spaced = ArrayList<Any>(source.size * 2)

    source.forEach { paragraph ->
        if (isEmptyMethod.invoke(paragraph, true) as Boolean) return@forEach
        if (spaced.isNotEmpty()) {
            spaced.add(createCanvasViewerSpacerParagraph(spacingStyle))
        }
        spaced.add(paragraph)
    }

    val contentCount = source.count { !(isEmptyMethod.invoke(it, true) as Boolean) }
    if (contentCount <= 1) return
    updateParagraphs.invoke(this, spaced)
}

@Suppress("UNCHECKED_CAST")
private fun createCanvasViewerSpacerParagraph(spacingStyle: ParagraphStyle): Any {
    val paragraph = richParagraphClass.getConstructor().newInstance()
    richParagraphClass.getMethod("setParagraphStyle", ParagraphStyle::class.java)
        .invoke(paragraph, spacingStyle)
    return paragraph
}

internal fun QuillStates.editorTextState(): RichTextState {
    val method = QuillStates::class.java.getMethod("getTextState\$ComposeQuill_release")
    return method.invoke(this) as RichTextState
}

internal fun QuillStates.editorImagePath(): String? {
    val method = QuillStates::class.java.getMethod("getImage\$ComposeQuill_release")
    return method.invoke(this) as String?
}

internal fun QuillStates.editorVideoPath(): String? {
    val method = QuillStates::class.java.getMethod("getVideo\$ComposeQuill_release")
    return method.invoke(this) as String?
}

internal fun QuillStates.quillAddImage(path: String) {
    val method = QuillStates::class.java.getMethod(
        "addImage\$ComposeQuill_release",
        String::class.java
    )
    method.invoke(this, path)
}

internal fun QuillStates.quillAddVideo(path: String) {
    val method = QuillStates::class.java.getMethod(
        "addVideo\$ComposeQuill_release",
        String::class.java
    )
    method.invoke(this, path)
}

internal fun RichTextState.editorContentSignature(): String {
    val annotated = annotatedString
    return buildString {
        append(annotated.text)
        annotated.spanStyles.forEach { range ->
            append('#')
            append(range.start)
            append(':')
            append(range.end)
            append(':')
            append(range.item)
        }
        append("::")
        append(toHtml().fixHtml())
    }
}

internal fun RichTextState.applySuperScript() {
    resetScriptStyles()
    toggleSpanStyle(
        SpanStyle(
            fontSize = currentSpanStyle.fontSize,
            baselineShift = BaselineShift.Superscript
        )
    )
}

internal fun RichTextState.applySubScript() {
    resetScriptStyles()
    toggleSpanStyle(
        SpanStyle(
            fontSize = currentSpanStyle.fontSize,
            baselineShift = BaselineShift.Subscript
        )
    )
}

internal fun RichTextState.selectedPlainText(): String {
    val range = selection
    if (range.collapsed) return ""
    return annotatedString.text.substring(range.min, range.max)
}

internal fun RichTextState.applyCanvasLink(url: String, linkText: String = "") {
    val normalizedUrl = normalizeCanvasLinkUrl(url)
    if (normalizedUrl.isEmpty()) {
        if (isLink) removeLink()
        return
    }
    when {
        isLink -> updateLink(normalizedUrl)
        !selection.collapsed -> addLinkToSelection(normalizedUrl)
        linkText.isNotBlank() -> addLink(linkText, normalizedUrl)
    }
}

internal fun normalizeCanvasLinkUrl(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    val lower = trimmed.lowercase()
    if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("mailto:")) {
        return trimmed
    }
    return "https://$trimmed"
}

internal fun RichTextState.resetScriptStyles() {
    when (currentSpanStyle.baselineShift) {
        BaselineShift.Subscript -> toggleSpanStyle(
            SpanStyle(
                fontSize = currentSpanStyle.fontSize,
                baselineShift = BaselineShift(0.5000001f)
            )
        )
        BaselineShift.Superscript -> toggleSpanStyle(
            SpanStyle(
                fontSize = currentSpanStyle.fontSize,
                baselineShift = BaselineShift(-0.5000001f)
            )
        )
        else -> Unit
    }
}

internal enum class CanvasFontSize(val size: TextUnit) {
    SMALL(15.sp),
    NORMAL(20.sp),
    MEDIUM(25.sp),
    LARGE(35.sp),
    XLARGE(50.sp);

    fun applyTo(state: RichTextState) {
        state.toggleSpanStyle(SpanStyle(fontSize = size))
    }

    companion object {
        fun from(current: TextUnit): CanvasFontSize = entries.firstOrNull { it.size == current }
            ?: NORMAL
    }
}

internal fun uriToBase64(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val buffer = ByteArrayOutputStream()
            val data = ByteArray(1024)
            var read: Int
            while (inputStream.read(data).also { read = it } != -1) {
                buffer.write(data, 0, read)
            }
            Base64.encodeToString(buffer.toByteArray(), Base64.DEFAULT)
        }
    } catch (_: Exception) {
        null
    }
}

internal fun base64ToImageFile(base64String: String, cacheDir: File): File? {
    return try {
        val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
        val imageFile = File(cacheDir, "canvas_quill_${System.currentTimeMillis()}.png")
        FileOutputStream(imageFile).use { outputStream ->
            outputStream.write(decodedBytes)
        }
        imageFile
    } catch (_: IOException) {
        null
    }
}

internal fun base64ToVideoFile(base64String: String, cacheDir: File): File? {
    return try {
        val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
        val videoFile = File(cacheDir, "canvas_quill_${System.currentTimeMillis()}.mp4")
        FileOutputStream(videoFile).use { outputStream ->
            outputStream.write(decodedBytes)
        }
        videoFile
    } catch (_: IOException) {
        null
    }
}
