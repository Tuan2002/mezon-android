package com.mezon.mobile.home.clans

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mezon.mobile.R
import com.mezon.mobile.util.CanvasFontSize
import com.mezon.mobile.util.applyCanvasLink
import com.mezon.mobile.util.applySubScript
import com.mezon.mobile.util.applySuperScript
import com.mezon.mobile.util.base64ToImageFile
import com.mezon.mobile.util.base64ToVideoFile
import com.mezon.mobile.util.editorTextState
import com.mezon.mobile.util.quillAddImage
import com.mezon.mobile.util.quillAddVideo
import com.mezon.mobile.util.resetScriptStyles
import com.mezon.mobile.util.selectedPlainText
import com.mezon.mobile.util.uriToBase64
import com.mohamedrejeb.richeditor.model.RichTextState
import io.tbib.composequill.states.QuillStates

private const val TOOLS_PER_ROW = 6

data class ChannelCanvasToolBarStyle(
    val iconColor: Color,
    val iconSelectedColor: Color,
    val selectedIconBackgroundColor: Color,
    val backgroundColor: Color,
)

private data class CanvasToolSpec(
    @DrawableRes val iconRes: Int,
    val contentDescription: String,
    val isSelected: Boolean = false,
    val iconTint: Color? = null,
    val onClick: () -> Unit,
)

private data class CanvasToolBarPage(
    val row1: List<CanvasToolSpec>,
    val row2: List<CanvasToolSpec>,
)

private data class LinkDialogState(
    val url: String,
    val text: String,
    val isEditingLink: Boolean,
    val showTextField: Boolean,
)

@Composable
fun ChannelCanvasEditorToolBar(
    quillStates: QuillStates,
    style: ChannelCanvasToolBarStyle,
    showImagePicker: Boolean = true,
    showVideoPicker: Boolean = false,
    onChange: () -> Unit,
) {
    val textState = quillStates.editorTextState()
    val context = LocalContext.current
    val cacheDir = context.cacheDir

    var showFontSizeDialog by remember { mutableStateOf(false) }
    var showTextColorDialog by remember { mutableStateOf(false) }
    var showBackgroundColorDialog by remember { mutableStateOf(false) }
    var linkDialogState by remember { mutableStateOf<LinkDialogState?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val base64 = uriToBase64(context, uri) ?: return@rememberLauncherForActivityResult
        val file = base64ToImageFile(base64, cacheDir) ?: return@rememberLauncherForActivityResult
        quillStates.quillAddImage(file.absolutePath)
        onChange()
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val base64 = uriToBase64(context, uri) ?: return@rememberLauncherForActivityResult
        val file = base64ToVideoFile(base64, cacheDir) ?: return@rememberLauncherForActivityResult
        quillStates.quillAddVideo(file.absolutePath)
        onChange()
    }

    if (showFontSizeDialog) {
        FontSizePickerDialog(
            current = CanvasFontSize.from(textState.currentSpanStyle.fontSize),
            onDismiss = { showFontSizeDialog = false },
            onSelect = { size ->
                size.applyTo(textState)
                showFontSizeDialog = false
                onChange()
            }
        )
    }

    if (showTextColorDialog) {
        ColorPickerDialog(
            title = "Text color",
            onDismiss = { showTextColorDialog = false },
            onSelect = { color ->
                textState.toggleSpanStyle(SpanStyle(color = color))
                showTextColorDialog = false
                onChange()
            }
        )
    }

    if (showBackgroundColorDialog) {
        ColorPickerDialog(
            title = "Background color",
            onDismiss = { showBackgroundColorDialog = false },
            onSelect = { color ->
                textState.toggleSpanStyle(SpanStyle(background = color))
                showBackgroundColorDialog = false
                onChange()
            }
        )
    }

    linkDialogState?.let { dialogState ->
        LinkInsertDialog(
            initialUrl = dialogState.url,
            initialText = dialogState.text,
            isEditingLink = dialogState.isEditingLink,
            showTextField = dialogState.showTextField,
            onDismiss = { linkDialogState = null },
            onRemove = {
                textState.removeLink()
                linkDialogState = null
                onChange()
            },
            onApply = { url, linkText ->
                textState.applyCanvasLink(url, linkText)
                linkDialogState = null
                onChange()
            },
        )
    }

    val paragraphAlign = textState.currentParagraphStyle.textAlign
    val spanStyle = textState.currentSpanStyle

    val allTools = remember(
        paragraphAlign,
        spanStyle,
        textState.isUnorderedList,
        textState.isOrderedList,
        textState.isCodeSpan,
        textState.isLink,
        showImagePicker,
        showVideoPicker,
    ) {
        buildList {
            add(
                CanvasToolSpec(
                    iconRes = R.drawable.ic_format_align_left,
                    contentDescription = "Align left",
                    isSelected = paragraphAlign == TextAlign.Left,
                    onClick = {
                        textState.addParagraphStyle(ParagraphStyle(textAlign = TextAlign.Left))
                        onChange()
                    }
                )
            )
            add(
                CanvasToolSpec(
                    iconRes = R.drawable.ic_format_align_center,
                    contentDescription = "Align center",
                    isSelected = paragraphAlign == TextAlign.Center,
                    onClick = {
                        textState.addParagraphStyle(ParagraphStyle(textAlign = TextAlign.Center))
                        onChange()
                    }
                )
            )
            add(
                CanvasToolSpec(
                    iconRes = R.drawable.ic_format_align_right,
                    contentDescription = "Align right",
                    isSelected = paragraphAlign == TextAlign.Right,
                    onClick = {
                        textState.addParagraphStyle(ParagraphStyle(textAlign = TextAlign.Right))
                        onChange()
                    }
                )
            )
            add(
                CanvasToolSpec(
                    iconRes = R.drawable.ic_format_bold,
                    contentDescription = "Bold",
                    isSelected = spanStyle.fontWeight == FontWeight.Bold,
                    onClick = {
                        textState.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        onChange()
                    }
                )
            )
            add(
                CanvasToolSpec(
                    iconRes = R.drawable.ic_format_italic,
                    contentDescription = "Italic",
                    isSelected = spanStyle.fontStyle == androidx.compose.ui.text.font.FontStyle.Italic,
                    onClick = {
                        textState.toggleSpanStyle(
                            SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                        )
                        onChange()
                    }
                )
            )
            add(
                CanvasToolSpec(
                    iconRes = R.drawable.ic_format_underline,
                    contentDescription = "Underline",
                    isSelected = spanStyle.textDecoration?.contains(TextDecoration.Underline) == true,
                    onClick = {
                        textState.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline))
                        onChange()
                    }
                )
            )
            add(
                CanvasToolSpec(
                    iconRes = R.drawable.ic_format_strikethrough,
                    contentDescription = "Strikethrough",
                    isSelected = spanStyle.textDecoration?.contains(TextDecoration.LineThrough) == true,
                    onClick = {
                        textState.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                        onChange()
                    }
                )
            )
            add(
                CanvasToolSpec(
                    iconRes = R.drawable.ic_format_attachment,
                    contentDescription = "Link",
                    isSelected = textState.isLink,
                    onClick = {
                        val hasSelection = !textState.selection.collapsed
                        val selectedText = textState.selectedPlainText()
                        linkDialogState = LinkDialogState(
                            url = textState.selectedLinkUrl.orEmpty(),
                            text = when {
                                textState.isLink -> textState.selectedLinkText.orEmpty()
                                hasSelection -> selectedText
                                else -> ""
                            },
                            isEditingLink = textState.isLink,
                            showTextField = !textState.isLink && !hasSelection,
                        )
                    }
                )
            )
            add(
                CanvasToolSpec(
                    iconRes = R.drawable.ic_format_list_bulleted,
                    contentDescription = "Bullet list",
                    isSelected = textState.isUnorderedList,
                    onClick = {
                        textState.toggleUnorderedList()
                        onChange()
                    }
                )
            )
            add(
                CanvasToolSpec(
                    iconRes = R.drawable.ic_format_list_numbered,
                    contentDescription = "Numbered list",
                    isSelected = textState.isOrderedList,
                    onClick = {
                        textState.toggleOrderedList()
                        onChange()
                    }
                )
            )
            add(
                CanvasToolSpec(
                    iconRes = R.drawable.ic_format_code,
                    contentDescription = "Code",
                    isSelected = textState.isCodeSpan,
                    onClick = {
                        textState.toggleCodeSpan()
                        onChange()
                    }
                )
            )
            add(
                CanvasToolSpec(
                    iconRes = R.drawable.ic_format_size,
                    contentDescription = "Font size",
                    onClick = { showFontSizeDialog = true }
                )
            )
            add(
                CanvasToolSpec(
                    iconRes = R.drawable.ic_format_color_text,
                    contentDescription = "Text color",
                    iconTint = spanStyle.color.takeIf { it != Color.Unspecified },
                    onClick = { showTextColorDialog = true }
                )
            )
            add(
                CanvasToolSpec(
                    iconRes = R.drawable.ic_format_color_fill,
                    contentDescription = "Background color",
                    iconTint = spanStyle.background.takeIf { it != Color.Unspecified },
                    onClick = { showBackgroundColorDialog = true }
                )
            )
            if (showImagePicker) {
                add(
                    CanvasToolSpec(
                        iconRes = R.drawable.ic_format_image,
                        contentDescription = "Image",
                        onClick = {
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                )
            }
            if (showVideoPicker) {
                add(
                    CanvasToolSpec(
                        iconRes = R.drawable.ic_format_video,
                        contentDescription = "Video",
                        onClick = {
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        }
                    )
                )
            }
            add(
                CanvasToolSpec(
                    iconRes = R.drawable.ic_format_superscript,
                    contentDescription = "Superscript",
                    isSelected = spanStyle.baselineShift == BaselineShift.Superscript,
                    onClick = {
                        if (spanStyle.baselineShift == BaselineShift.Superscript) {
                            textState.resetScriptStyles()
                        } else {
                            textState.applySuperScript()
                        }
                        onChange()
                    }
                )
            )
            add(
                CanvasToolSpec(
                    iconRes = R.drawable.ic_format_subscript,
                    contentDescription = "Subscript",
                    isSelected = spanStyle.baselineShift == BaselineShift.Subscript,
                    onClick = {
                        if (spanStyle.baselineShift == BaselineShift.Subscript) {
                            textState.resetScriptStyles()
                        } else {
                            textState.applySubScript()
                        }
                        onChange()
                    }
                )
            )
        }
    }

    val pages = remember(allTools) { allTools.toToolBarPages() }
    val pagerState = rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(style.backgroundColor)
            .padding(top = 4.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { pageIndex ->
            val page = pages[pageIndex]
            Column(modifier = Modifier.fillMaxWidth()) {
                ToolBarRow(tools = page.row1, style = style)
                ToolBarRow(tools = page.row2, style = style)
            }
        }

        if (pages.size > 1) {
            ToolBarPageIndicator(
                pageCount = pages.size,
                currentPage = pagerState.currentPage,
                style = style,
            )
        } else {
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

private fun List<CanvasToolSpec>.toToolBarPages(): List<CanvasToolBarPage> {
    if (isEmpty()) {
        return listOf(CanvasToolBarPage(emptyList(), emptyList()))
    }
    val toolsPerPage = TOOLS_PER_ROW * 2
    return chunked(toolsPerPage).map { pageTools ->
        CanvasToolBarPage(
            row1 = pageTools.take(TOOLS_PER_ROW),
            row2 = pageTools.drop(TOOLS_PER_ROW).take(TOOLS_PER_ROW),
        )
    }
}

@Composable
private fun ToolBarRow(
    tools: List<CanvasToolSpec>,
    style: ChannelCanvasToolBarStyle,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(TOOLS_PER_ROW) { index ->
            val tool = tools.getOrNull(index)
            if (tool != null) {
                CanvasToolBarIconButton(
                    iconRes = tool.iconRes,
                    contentDescription = tool.contentDescription,
                    style = style,
                    isSelected = tool.isSelected,
                    iconTint = tool.iconTint,
                    onClick = tool.onClick,
                )
            } else {
                Spacer(modifier = Modifier.size(40.dp))
            }
        }
    }
}

@Composable
private fun ToolBarPageIndicator(
    pageCount: Int,
    currentPage: Int,
    style: ChannelCanvasToolBarStyle,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val selected = currentPage == index
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (selected) 7.dp else 5.dp)
                    .background(
                        color = if (selected) {
                            style.selectedIconBackgroundColor
                        } else {
                            style.iconColor.copy(alpha = 0.35f)
                        },
                        shape = CircleShape,
                    )
            )
        }
    }
}

@Composable
private fun CanvasToolBarIconButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    style: ChannelCanvasToolBarStyle,
    isSelected: Boolean = false,
    iconTint: Color? = null,
    onClick: () -> Unit,
) {
    IconButton(
        modifier = Modifier
            .size(40.dp)
            .focusProperties { canFocus = false },
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = if (isSelected) style.iconSelectedColor else iconTint ?: style.iconColor,
        ),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier
                .background(
                    color = if (isSelected) style.selectedIconBackgroundColor else Color.Transparent,
                    shape = CircleShape,
                )
                .padding(7.dp)
                .size(20.dp),
        )
    }
}

@Composable
private fun FontSizePickerDialog(
    current: CanvasFontSize,
    onDismiss: () -> Unit,
    onSelect: (CanvasFontSize) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Font size", style = MaterialTheme.typography.titleMedium)
                CanvasFontSize.entries.forEach { size ->
                    val selected = size == current
                    Text(
                        text = size.name.lowercase().replaceFirstChar { it.titlecase() },
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = size.size),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(size) }
                            .padding(vertical = 8.dp)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                else Color.Transparent,
                                RoundedCornerShape(8.dp),
                            )
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorPickerDialog(
    title: String,
    onDismiss: () -> Unit,
    onSelect: (Color) -> Unit,
) {
    val presetColors = remember {
        listOf(
            Color.Black,
            Color.White,
            Color(0xFFE53935),
            Color(0xFFD81B60),
            Color(0xFF8E24AA),
            Color(0xFF5E35B1),
            Color(0xFF3949AB),
            Color(0xFF1E88E5),
            Color(0xFF039BE5),
            Color(0xFF00ACC1),
            Color(0xFF00897B),
            Color(0xFF43A047),
            Color(0xFF7CB342),
            Color(0xFFC0CA33),
            Color(0xFFFDD835),
            Color(0xFFFFB300),
            Color(0xFFFB8C00),
            Color(0xFFF4511E),
            Color(0xFF6D4C41),
            Color(0xFF757575),
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    presetColors.forEach { color ->
                        IconButton(
                            onClick = { onSelect(color) },
                            modifier = Modifier
                                .background(color, CircleShape)
                                .size(36.dp),
                        ) {}
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkInsertDialog(
    initialUrl: String,
    initialText: String,
    isEditingLink: Boolean,
    showTextField: Boolean,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
    onApply: (url: String, linkText: String) -> Unit,
) {
    var url by remember(initialUrl, initialText, isEditingLink, showTextField) {
        mutableStateOf(initialUrl)
    }
    var linkText by remember(initialUrl, initialText, isEditingLink, showTextField) {
        mutableStateOf(initialText)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (isEditingLink) "Edit link" else "Insert link",
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    label = { Text("URL") },
                    singleLine = true,
                )
                if (showTextField) {
                    OutlinedTextField(
                        value = linkText,
                        onValueChange = { linkText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        label = { Text("Text") },
                        singleLine = true,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isEditingLink) {
                        TextButton(onClick = onRemove) {
                            Text("Remove")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    TextButton(
                        onClick = { onApply(url, linkText) },
                        enabled = url.isNotBlank() || isEditingLink,
                    ) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}
