package com.mezon.mobile.home.clans

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mezon.mobile.core.ThemeColors
import com.mezon.mobile.util.CanvasComposeQuillBridge
import com.mezon.mobile.util.applyCanvasViewerBlockSpacing
import com.mezon.mobile.util.editorContentSignature
import com.mezon.mobile.util.editorImagePath
import com.mezon.mobile.util.editorTextState
import com.mezon.mobile.util.editorVideoPath
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor
import com.mohamedrejeb.richeditor.ui.material3.RichText
import io.tbib.composequill.components.QuillEditorStyle
import io.tbib.composequill.extensions.fixHtml
import io.tbib.composequill.states.rememberQuillStates
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun ChannelCanvasEditor(
    editorKey: String,
    composeQuillJson: String,
    readOnly: Boolean,
    isDarkTheme: Boolean,
    themeColors: ThemeColors,
    onContentChange: (String) -> Unit,
    onRegisterContentReader: ((() -> String) -> Unit)? = null
) {
    val palette = remember(isDarkTheme, themeColors) {
        if (isDarkTheme) {
            darkColorScheme(
                background = Color(themeColors.background),
                surface = Color(themeColors.surface),
                onSurface = Color(themeColors.onSurface),
                primary = Color(themeColors.blurple)
            )
        } else {
            lightColorScheme(
                background = Color(themeColors.background),
                surface = Color(themeColors.surface),
                onSurface = Color(themeColors.onSurface),
                primary = Color(themeColors.blurple)
            )
        }
    }

    val editorHtml = remember(composeQuillJson) {
        CanvasComposeQuillBridge.htmlFromComposeQuillJson(composeQuillJson)
    }

    MaterialTheme(colorScheme = palette) {
        key(editorKey) {
            if (readOnly) {
                val textState = remember(editorKey, editorHtml) {
                    RichTextState().apply {
                        setHtml(editorHtml)
                        applyCanvasViewerBlockSpacing()
                    }
                }
                LaunchedEffect(editorHtml) {
                    textState.setHtml(editorHtml)
                    textState.applyCanvasViewerBlockSpacing()
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(themeColors.background))
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    RichText(
                        state = textState,
                        style = TextStyle(
                            fontSize = 15.sp,
                            color = Color(themeColors.onSurface)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                val quillStates = rememberQuillStates()
                quillStates.SetCache()
                val textState = quillStates.editorTextState()
                var contentLoaded by remember(editorKey) { mutableStateOf(false) }
                val editorStyle = remember(themeColors) {
                    QuillEditorStyle(
                        minLines = 12,
                        maxLines = Int.MAX_VALUE,
                        textStyle = TextStyle(
                            fontSize = 15.sp,
                            color = Color(themeColors.onSurface)
                        ),
                        cursorBrush = SolidColor(Color(themeColors.blurple))
                    )
                }
                val toolBarStyle = remember(themeColors) {
                    ChannelCanvasToolBarStyle(
                        iconColor = Color(themeColors.onSurface),
                        iconSelectedColor = Color.White,
                        selectedIconBackgroundColor = Color(themeColors.blurple),
                        backgroundColor = Color(themeColors.surface)
                    )
                }

                LaunchedEffect(composeQuillJson, editorKey) {
                    contentLoaded = false
                    if (composeQuillJson.isNotBlank()) {
                        quillStates.sendData(composeQuillJson)
                    }
                    contentLoaded = true
                }

                LaunchedEffect(editorKey, contentLoaded) {
                    if (!contentLoaded) return@LaunchedEffect
                    onRegisterContentReader?.invoke {
                        CanvasComposeQuillBridge.composeQuillJsonFromEditorHtml(
                            textState.toHtml().fixHtml()
                        )
                    }
                    snapshotFlow {
                        textState.annotatedString
                        EditorSnapshot(
                            contentSignature = textState.editorContentSignature(),
                            html = textState.toHtml().fixHtml(),
                            imagePath = quillStates.editorImagePath(),
                            videoPath = quillStates.editorVideoPath(),
                        )
                    }
                        .distinctUntilChanged()
                        .collect { snapshot ->
                            val json = CanvasComposeQuillBridge.composeQuillJsonFromEditorHtml(snapshot.html)
                            onContentChange(json)
                        }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(themeColors.background))
                        .imePadding()
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        BasicRichTextEditor(
                            modifier = editorStyle.modifier.fillMaxWidth(),
                            state = textState,
                            minLines = editorStyle.minLines,
                            maxLines = editorStyle.maxLines,
                            enabled = editorStyle.enable,
                            textStyle = editorStyle.textStyle,
                            keyboardOptions = editorStyle.keyboardOptions,
                            keyboardActions = editorStyle.keyboardActions,
                            cursorBrush = editorStyle.cursorBrush,
                            decorationBox = editorStyle.decorationBox,
                        )

                        quillStates.editorImagePath()?.let { imagePath ->
                            val bitmap = remember(imagePath) {
                                runCatching {
                                    BitmapFactory.decodeFile(imagePath)?.asImageBitmap()
                                }.getOrNull()
                            }
                            bitmap?.let { imageBitmap ->
                                val height = if (imageBitmap.height > 200) 200 else imageBitmap.height
                                Image(
                                    bitmap = imageBitmap,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(height.dp)
                                        .padding(top = 8.dp)
                                )
                            }
                        }
                    }

                    ChannelCanvasEditorToolBar(
                        quillStates = quillStates,
                        style = toolBarStyle,
                        showImagePicker = true,
                        onChange = {
                            val json = CanvasComposeQuillBridge.composeQuillJsonFromEditorHtml(
                                textState.toHtml().fixHtml()
                            )
                            onContentChange(json)
                        }
                    )
                }
            }
        }
    }
}

private data class EditorSnapshot(
    val contentSignature: String,
    val html: String,
    val imagePath: String?,
    val videoPath: String?,
)
