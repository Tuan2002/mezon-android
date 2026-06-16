package com.mezon.mobile.home.clans

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.mezon.mobile.R
import com.mezon.mobile.core.AlertDialog
import com.mezon.mobile.core.BaseFragment
import com.mezon.mobile.core.LayoutHelper
import com.mezon.mobile.core.NotificationCenter
import com.mezon.mobile.di.FragmentEntryPoint
import com.mezon.mobile.home.profile.UserController
import com.mezon.mobile.ui.MezonToast
import com.mezon.mobile.ui.cells.ActionBarMenuItem
import com.mezon.mobile.ui.cells.ActionBarView
import com.mezon.mobile.ui.cells.MezonIcon
import com.mezon.mobile.ui.cells.PopupMenu
import com.mezon.mobile.ui.cells.ToastOverlay
import com.mezon.mobile.ui.theme.ThemeMode
import com.mezon.mobile.util.CanvasComposeQuillBridge

class ChannelCanvasFragment : BaseFragment() {

    companion object {
        private const val ARG_CLAN_ID = "clanId"
        private const val ARG_CHANNEL_ID = "channelId"
        private const val ARG_CHANNEL_TYPE = "channelType"
        private const val ARG_CANVAS_ID = "canvasId"
        private const val ARG_INITIAL_TITLE = "initialTitle"
        private const val ARG_READ_ONLY = "readOnly"
        private const val MENU_SAVE = 1
        private const val MENU_MORE = 2
        private const val NEW_CANVAS_ID = 0L

        fun newInstance(
            clanId: Long,
            channelId: Long,
            channelType: Int,
            canvasId: Long,
            initialTitle: String = "",
            readOnly: Boolean = true
        ): ChannelCanvasFragment = ChannelCanvasFragment().apply {
            arguments = Bundle().apply {
                putLong(ARG_CLAN_ID, clanId)
                putLong(ARG_CHANNEL_ID, channelId)
                putInt(ARG_CHANNEL_TYPE, channelType)
                putLong(ARG_CANVAS_ID, canvasId)
                putString(ARG_INITIAL_TITLE, initialTitle)
                putBoolean(ARG_READ_ONLY, readOnly)
            }
        }

        fun newCreateInstance(
            clanId: Long,
            channelId: Long,
            channelType: Int
        ): ChannelCanvasFragment = newInstance(
            clanId = clanId,
            channelId = channelId,
            channelType = channelType,
            canvasId = NEW_CANVAS_ID,
            readOnly = false
        )
    }

    private var clanId = 0L
    private var channelId = 0L
    private var channelType = 0
    private var canvasId = 0L
    private var initialTitle = ""
    private var startReadOnly = true

    private lateinit var channelCanvasController: ChannelCanvasController
    private lateinit var userController: UserController

    private var titleField: EditText? = null
    private var editorComposeView: ComposeView? = null
    private var loadingView: ProgressBar? = null
    private var errorView: TextView? = null
    private var saveMenuItem: ActionBarMenuItem? = null
    private var moreMenuItem: ActionBarMenuItem? = null
    private var canvasMenuPopup: PopupMenu? = null

    private var loadedCanvas: ChannelCanvasData? = null
    private var isEditable = false
    private var composeQuillJson: String = ""
    private var latestComposeQuillJson: String = ""
    private var baselineComposeQuillJson: String = ""
    private var baselineEditorHtml: String = ""
    private var readLiveEditorComposeQuillJson: (() -> String)? = null
    private var editorSessionKey: String = ""
    private var editorRefreshGeneration = 0L
    private var suppressDirtyTracking = false
    private var hasUnsavedChanges = false
    private var isSaving = false

    private val isCreateMode: Boolean
        get() = canvasId == NEW_CANVAS_ID

    override fun onFragmentCreate(): Boolean {
        super.onFragmentCreate()
        clanId = arguments?.getLong(ARG_CLAN_ID) ?: 0L
        channelId = arguments?.getLong(ARG_CHANNEL_ID) ?: 0L
        channelType = arguments?.getInt(ARG_CHANNEL_TYPE) ?: 0
        canvasId = arguments?.getLong(ARG_CANVAS_ID) ?: 0L
        initialTitle = arguments?.getString(ARG_INITIAL_TITLE).orEmpty()
        startReadOnly = arguments?.getBoolean(ARG_READ_ONLY, true) ?: true

        observe(NotificationCenter.channelCanvasDetailDidLoad) { _, _, args ->
            val ch = args.firstOrNull() as? Long ?: return@observe
            val id = args.getOrNull(1) as? Long ?: return@observe
            if (ch == channelId && id == canvasId) applyLoadedCanvas()
        }
        observe(NotificationCenter.channelCanvasDetailLoadError) { _, _, args ->
            val ch = args.firstOrNull() as? Long ?: return@observe
            val id = args.getOrNull(1) as? Long ?: return@observe
            if (ch == channelId && id == canvasId) showLoadError()
        }
        observe(NotificationCenter.channelCanvasSaved) { _, _, args ->
            val ch = args.firstOrNull() as? Long ?: return@observe
            val id = args.getOrNull(1) as? Long ?: return@observe
            if (ch != channelId || id == 0L) return@observe
            isSaving = false
            if (id == canvasId) {
                loadedCanvas = channelCanvasController.getCanvasDetail(channelId, canvasId)
                hasUnsavedChanges = false
                baselineComposeQuillJson = latestComposeQuillJson
                if (isEditable) {
                    exitEditModeAfterSave()
                } else {
                    updateActionBarMenus()
                }
            }
        }
        observe(NotificationCenter.channelCanvasDeleted) { _, _, args ->
            val ch = args.firstOrNull() as? Long ?: return@observe
            val id = args.getOrNull(1) as? Long ?: return@observe
            if (ch == channelId && id == canvasId) finishFragment()
        }

        if (!isCreateMode) {
            channelCanvasController.loadCanvasDetail(channelId, clanId, channelType, canvasId)
        }
        return true
    }

    override fun onInject(entryPoint: FragmentEntryPoint) {
        channelCanvasController = entryPoint.channelCanvasController()
        userController = entryPoint.userController()
    }

    override fun createView(context: Context): View {
        val root = FrameLayout(context).apply {
            setBackgroundColor(themeColors.background)
        }

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = LayoutHelper.dp(16f)
            setPadding(pad, LayoutHelper.dp(12f), pad, LayoutHelper.dp(12f))
        }

        titleField = EditText(context).apply {
            hint = getString(R.string.channel_canvas_title_hint)
            setHintTextColor(themeColors.textDisabled)
            setTextColor(themeColors.onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 28f)
            typeface = Typeface.DEFAULT_BOLD
            setBackgroundColor(0)
            isSingleLine = false
            maxLines = 3
            if (initialTitle.isNotBlank()) setText(initialTitle)
            addTextChangedListener(titleChangeWatcher)
        }
        column.addView(titleField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

        editorComposeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            val activity = getParentActivity()
            if (activity is FragmentActivity) {
                setViewTreeLifecycleOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
            }
            setContent {
                ChannelCanvasEditor(
                    editorKey = editorSessionKey,
                    composeQuillJson = composeQuillJson,
                    readOnly = !isEditable,
                    isDarkTheme = themeColors.resolvedMode != ThemeMode.LIGHT,
                    themeColors = themeColors,
                    onContentChange = ::handleEditorContentChange,
                    onRegisterContentReader = { reader ->
                        readLiveEditorComposeQuillJson = reader
                    }
                )
            }
        }
        column.addView(
            editorComposeView,
            LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f, topMargin = 12f)
        )

        errorView = TextView(context).apply {
            setTextColor(themeColors.textDisabled)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        column.addView(errorView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

        root.addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT))

        loadingView = ProgressBar(context)
        root.addView(loadingView, LayoutHelper.createFrame(48, 48, Gravity.CENTER))

        val title = if (isCreateMode) {
            getString(R.string.channel_canvas_create)
        } else {
            initialTitle.ifBlank { getString(R.string.channel_canvas_untitled) }
        }
        val content = wrapWithActionBar(title, root)
        setupActionBarMenu(context)
        if (isCreateMode) {
            applyCreateCanvas()
        } else {
            applyLoadedCanvas()
        }
        return content
    }

    override fun onFragmentDestroy() {
        dismissCanvasMenu()
        readLiveEditorComposeQuillJson = null
        super.onFragmentDestroy()
    }

    private fun setupActionBarMenu(context: Context) {
        val bar = actionBar ?: return
        val menu = bar.createMenu()

        val saveItem = menu.addItem(MENU_SAVE, MezonIcon.checkmarkSmallIcon.resId)
        saveMenuItem = saveItem
        saveItem.setIconColor(themeColors.blurple)
        saveItem.contentDescription = getString(R.string.common_save)
        saveItem.getItemIconView().apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = FrameLayout.LayoutParams(
                LayoutHelper.dp(24f),
                LayoutHelper.dp(24f),
                Gravity.CENTER
            )
        }

        moreMenuItem = menu.addItem(MENU_MORE, R.drawable.ic_more_vertical_24).also { item ->
            item.setIconColor(themeColors.textStrong)
            item.contentDescription = getString(R.string.channel_canvas_menu_content_desc)
            item.getItemIconView().apply {
                scaleType = ImageView.ScaleType.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    LayoutHelper.dp(48f),
                    LayoutHelper.dp(48f),
                    Gravity.CENTER
                )
            }
        }

        bar.setMenuOnItemClick(object : ActionBarView.ActionBarMenuOnItemClick() {
            override fun onItemClick(id: Int) {
                when (id) {
                    -1 -> finishFragment()
                    MENU_SAVE -> saveCanvas()
                    MENU_MORE -> showCanvasMenu()
                }
            }
        })
        updateActionBarMenus()
    }

    private fun updateActionBarMenus() {
        val showSave = isEditable
        saveMenuItem?.visibility = if (showSave) View.VISIBLE else View.GONE
        saveMenuItem?.isEnabled = showSave && hasUnsavedChanges && !isSaving
        saveMenuItem?.alpha = if (showSave && hasUnsavedChanges && !isSaving) 1f else 0.4f

        val canvas = loadedCanvas
        val canManage = !isCreateMode && canvas != null &&
            canEditChannelCanvas(canvas, userController.userId)
        moreMenuItem?.visibility = if (canManage) View.VISIBLE else View.GONE
    }

    private fun showCanvasMenu() {
        val canvas = loadedCanvas ?: return
        val userId = userController.userId
        val canEdit = canEditChannelCanvas(canvas, userId)
        val canDelete = canDeleteChannelCanvas(canvas, userId)
        if (!canEdit && !canDelete) return
        val ctx = fragmentView?.context ?: return
        val anchor = moreMenuItem ?: return
        dismissCanvasMenu()
        val popup = PopupMenu(ctx, themeColors)
        var itemIndex = 0
        val editIndex = if (!isEditable && canEdit) {
            popup.addItem(
                getString(R.string.channel_canvas_menu_edit),
                MezonIcon.pencilIcon.getDrawable(ctx, themeColors.colorText),
            )
            itemIndex++
        } else {
            -1
        }
        val deleteIndex = if (canDelete) {
            popup.addItem(
                getString(R.string.channel_canvas_delete_confirm_title),
                MezonIcon.trashIcon.getDrawable(ctx, themeColors.error),
                destructive = true
            )
            itemIndex
        } else {
            -1
        }
        popup.setOnItemClickListener { index ->
            when (index) {
                editIndex -> {
                    dismissCanvasMenu()
                    enterEditMode()
                }
                deleteIndex -> {
                    dismissCanvasMenu()
                    confirmDeleteCanvas()
                }
            }
        }
        canvasMenuPopup = popup
        popup.show(anchor)
    }

    private fun enterEditMode() {
        val data = loadedCanvas ?: return
        if (!canEditChannelCanvas(data, userController.userId)) return
        startReadOnly = false
        isEditable = true
        titleField?.apply {
            isEnabled = true
            isFocusable = true
            isFocusableInTouchMode = true
        }
        bindEditor()
        updateActionBarMenus()
    }

    private fun exitEditModeAfterSave() {
        startReadOnly = true
        isEditable = false
        titleField?.apply {
            isEnabled = false
            isFocusable = false
            isFocusableInTouchMode = false
            clearFocus()
        }
        bindEditor()
        updateActionBarMenus()
    }

    private fun dismissCanvasMenu() {
        canvasMenuPopup?.dismiss()
        canvasMenuPopup = null
    }

    private val titleChangeWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            if (!isEditable || suppressDirtyTracking) return
            markDirty()
        }
    }

    private fun markDirty() {
        if (!isEditable || suppressDirtyTracking) return
        hasUnsavedChanges = true
        updateActionBarMenus()
    }

    private fun applyCreateCanvas() {
        isEditable = true
        loadedCanvas = null
        loadingView?.visibility = View.GONE
        errorView?.visibility = View.GONE
        editorComposeView?.visibility = View.VISIBLE

        suppressDirtyTracking = true
        hasUnsavedChanges = false
        titleField?.apply {
            setText("")
            isEnabled = true
            isFocusable = true
            isFocusableInTouchMode = true
        }

        composeQuillJson = CanvasComposeQuillBridge.composeQuillJsonFromApiContent("")
        latestComposeQuillJson = composeQuillJson
        baselineComposeQuillJson = composeQuillJson
        baselineEditorHtml = CanvasComposeQuillBridge.htmlFromComposeQuillJson(composeQuillJson)
        editorSessionKey = "create_${channelId}_${System.currentTimeMillis()}"
        bindEditor()
        suppressDirtyTracking = false
        updateActionBarMenus()
    }

    private fun applyLoadedCanvas() {
        val data = channelCanvasController.getCanvasDetail(channelId, canvasId)
            ?: channelCanvasController.getCanvases(channelId).firstOrNull { it.id == canvasId }
        val fetching = channelCanvasController.isFetchingDetail(channelId, canvasId)
        loadingView?.visibility = if (fetching && data == null) View.VISIBLE else View.GONE
        if (data == null) return

        loadedCanvas = data
        val canEdit = canEditChannelCanvas(data, userController.userId)
        isEditable = !startReadOnly && canEdit

        val displayTitle = data.title.replace("\n", " ").ifBlank { getString(R.string.channel_canvas_untitled) }
        actionBar?.setTitle(displayTitle)

        suppressDirtyTracking = true
        hasUnsavedChanges = false
        titleField?.apply {
            setText(data.title)
            isEnabled = isEditable
            isFocusable = isEditable
            isFocusableInTouchMode = isEditable
        }

        composeQuillJson = CanvasComposeQuillBridge.composeQuillJsonFromApiContent(data.content)
        latestComposeQuillJson = composeQuillJson
        baselineComposeQuillJson = composeQuillJson
        baselineEditorHtml = CanvasComposeQuillBridge.htmlFromComposeQuillJson(composeQuillJson)
        editorRefreshGeneration++
        editorSessionKey = "${channelId}_${canvasId}_${editorRefreshGeneration}_${data.content.hashCode()}"
        errorView?.visibility = View.GONE
        editorComposeView?.visibility = View.VISIBLE
        bindEditor()
        suppressDirtyTracking = false
        updateActionBarMenus()
    }

    private fun bindEditor() {
        editorComposeView?.setContent {
            ChannelCanvasEditor(
                editorKey = editorSessionKey,
                composeQuillJson = composeQuillJson,
                readOnly = !isEditable,
                isDarkTheme = themeColors.resolvedMode != ThemeMode.LIGHT,
                themeColors = themeColors,
                onContentChange = ::handleEditorContentChange,
                onRegisterContentReader = { reader ->
                    readLiveEditorComposeQuillJson = reader
                }
            )
        }
    }

    private fun showLoadError() {
        loadingView?.visibility = View.GONE
        if (loadedCanvas != null) return
        editorComposeView?.visibility = View.GONE
        errorView?.apply {
            text = getString(R.string.channel_canvas_load_error)
            visibility = View.VISIBLE
        }
    }

    private fun saveCanvas() {
        if (!isEditable || isSaving || !hasUnsavedChanges) return
        val title = titleField?.text?.toString().orEmpty()
        val composeJson = resolveComposeQuillJson()
        latestComposeQuillJson = composeJson
        val content = CanvasComposeQuillBridge.apiContentFromComposeQuillJson(
            composeQuillJson = composeJson,
            originalApiContent = loadedCanvas?.content
        )
        if (isCreateMode) {
            if (title.isBlank() && isBlankCanvasContent(content)) {
                MezonToast.show(this, ToastOverlay.ToastType.INFO, getString(R.string.channel_canvas_save_empty))
                return
            }
            isSaving = true
            updateActionBarMenus()
            channelCanvasController.createCanvas(
                channelId = channelId,
                clanId = clanId,
                channelType = channelType,
                title = title,
                content = content,
                creatorId = userController.userId
            ) { savedId ->
                runOnUi {
                    isSaving = false
                    if (savedId == null || savedId == 0L) {
                        updateActionBarMenus()
                        showSaveError()
                        return@runOnUi
                    }
                    canvasId = savedId
                    hasUnsavedChanges = false
                    exitEditModeAfterSave()
                    refreshSavedCanvas { showSaveSuccess() }
                }
            }
            return
        }

        val previous = loadedCanvas ?: return
        isSaving = true
        updateActionBarMenus()
        channelCanvasController.updateCanvas(
            channelId = channelId,
            clanId = clanId,
            channelType = channelType,
            canvasId = canvasId,
            title = title,
            content = content,
            isDefault = previous.isDefault
        ) { success ->
            runOnUi {
                isSaving = false
                if (!success) {
                    updateActionBarMenus()
                    showSaveError()
                    return@runOnUi
                }
                hasUnsavedChanges = false
                exitEditModeAfterSave()
                refreshSavedCanvas { showSaveSuccess() }
            }
        }
    }

    private fun refreshSavedCanvas(onComplete: (() -> Unit)? = null) {
        channelCanvasController.loadCanvasDetail(
            channelId = channelId,
            clanId = clanId,
            channelType = channelType,
            canvasId = canvasId,
            forceRefresh = true
        ) { data ->
            runOnUi {
                if (data != null) applyLoadedCanvas()
                onComplete?.invoke()
            }
        }
    }

    private fun confirmDeleteCanvas() {
        val canvas = loadedCanvas ?: return
        if (!canDeleteChannelCanvas(canvas, userController.userId)) return
        val activity = getParentActivity() ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        AlertDialog.Builder(activity)
            .setTitle(getString(R.string.channel_canvas_delete_confirm_title))
            .setMessage(getString(R.string.channel_canvas_delete_confirm_message))
            .setPositiveButton(getString(R.string.common_yes)) { _, _ -> deleteCanvas() }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .create()
            .show()
    }

    private fun deleteCanvas() {
        channelCanvasController.deleteCanvas(channelId, clanId, channelType, canvasId) { success ->
            getParentActivity()?.runOnUiThread {
                if (success) {
                    MezonToast.show(this, ToastOverlay.ToastType.INFO, getString(R.string.channel_canvas_deleted))
                    finishFragment()
                } else {
                    MezonToast.show(this, ToastOverlay.ToastType.ERROR, getString(R.string.channel_canvas_delete_error))
                }
            }
        }
    }

    private fun showSaveSuccess() {
        MezonToast.show(this, ToastOverlay.ToastType.INFO, getString(R.string.channel_canvas_saved))
    }

    private fun showSaveError() {
        MezonToast.show(this, ToastOverlay.ToastType.ERROR, getString(R.string.channel_canvas_save_error))
    }

    private fun resolveComposeQuillJson(): String {
        return readLiveEditorComposeQuillJson?.invoke() ?: latestComposeQuillJson
    }

    private fun handleEditorContentChange(json: String) {
        latestComposeQuillJson = json
        runOnUi {
            if (!suppressDirtyTracking && hasEditorContentChanged(json)) {
                markDirty()
            }
        }
    }

    private fun hasEditorContentChanged(json: String): Boolean {
        val currentHtml = CanvasComposeQuillBridge.htmlFromComposeQuillJson(json)
        return !CanvasComposeQuillBridge.canvasHtmlEquals(currentHtml, baselineEditorHtml)
    }

    private fun isBlankCanvasContent(content: String): Boolean {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return true
        return trimmed == """{"ops":[{"insert":"\n"}]}""" ||
            trimmed == """{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":""}]}]}"""
    }

    private fun runOnUi(block: () -> Unit) {
        val activity = getParentActivity() ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            activity.runOnUiThread(block)
        }
    }
}
