package com.mezon.mobile.home.clans.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.content.DialogInterface
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.CheckBox
import android.widget.TextView
import android.view.inputmethod.EditorInfo
import androidx.core.widget.CompoundButtonCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mezon.mobile.BuildConfig
import com.mezon.mobile.R
import com.mezon.mobile.core.AlertDialog
import com.mezon.mobile.core.AndroidUtilities
import com.mezon.mobile.core.BaseFragment
import com.mezon.mobile.core.LayoutHelper
import com.mezon.mobile.core.NotificationCenter
import com.mezon.mobile.di.FragmentEntryPoint
import com.mezon.mobile.home.ClanMember
import com.mezon.mobile.home.chat.EmojiController
import com.mezon.mobile.home.chat.EmojiItem
import com.mezon.mobile.home.chat.MezonImageLoader
import com.mezon.mobile.home.clans.ClansController
import com.mezon.mobile.home.clans.CreateClanRnUiTokens
import com.mezon.mobile.home.clans.RoleController
import com.mezon.mobile.home.profile.UserController
import com.mezon.mobile.network.MezonApi
import com.mezon.mobile.session.SessionManager
import com.mezon.mobile.ui.MezonToast
import com.mezon.mobile.ui.cells.InputCell
import com.mezon.mobile.ui.cells.ActionBarView
import com.mezon.mobile.ui.cells.AvatarView
import com.mezon.mobile.ui.cells.MezonIcon
import com.mezon.mobile.ui.cells.ToastOverlay
import com.mezon.mobile.home.UserClanController
import com.mezon.mobile.util.isClanEmojiNameValid
import com.mezon.mobile.util.getEmojiUrl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ThreadLocalRandom

class EmojiSettingFragment : BaseFragment() {

    companion object {
        private const val ARG_CLAN_ID = "clanId"
        private const val REQUEST_PICK_EMOJI = 4021
        private const val MAX_CLAN_EMOJI_SLOTS = 250
        private const val MAX_UPLOAD_BYTES = 256 * 1024
        private const val MIN_NAME_INNER = 3
        private const val MAX_NAME_INNER = 62

        fun newInstance(clanId: Long): EmojiSettingFragment =
            EmojiSettingFragment().apply {
                arguments = Bundle().apply { putLong(ARG_CLAN_ID, clanId) }
            }

        fun newEmojiNumericId(): Long =
            ThreadLocalRandom.current().nextLong(10_000_000_000_000L, Long.MAX_VALUE / 4)
    }

    private var clanId = 0L

    private lateinit var emojiController: EmojiController
    private lateinit var api: MezonApi
    private lateinit var sessionManager: SessionManager
    private lateinit var userClanController: UserClanController
    private lateinit var roleController: RoleController
    private lateinit var clansController: ClansController
    private lateinit var userController: UserController
    private lateinit var ioDispatcher: CoroutineDispatcher
    private lateinit var mainDispatcher: CoroutineDispatcher

    private var permState: ClanSettingsPermissionState? = null

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: EmojiSettingAdapter
    private var blockingOverlay: FrameLayout? = null

    override fun onInject(entryPoint: FragmentEntryPoint) {
        emojiController = entryPoint.emojiController()
        api = entryPoint.mezonApi()
        sessionManager = entryPoint.sessionManager()
        userClanController = entryPoint.userClanController()
        roleController = entryPoint.roleController()
        clansController = entryPoint.clansController()
        userController = entryPoint.userController()
        ioDispatcher = entryPoint.ioDispatcher()
        mainDispatcher = entryPoint.mainDispatcher()
    }

    override fun onFragmentCreate(): Boolean {
        super.onFragmentCreate()
        clanId = arguments?.getLong(ARG_CLAN_ID) ?: 0L
        if (clanId == 0L) return false

        observe(NotificationCenter.emojisNeedReload) { _, _, _ ->
            reloadListUi()
        }
        observe(NotificationCenter.clanEmojiCropExportReady) { _, _, args ->
            val cid = args.getOrNull(0) as? Long ?: return@observe
            if (cid != clanId) return@observe
            val path = args.getOrNull(1) as? String ?: return@observe
            AndroidUtilities.runOnUIThread {
                if (isFinished) return@runOnUIThread
                showUploadPreviewDialog(File(path), isGif = false)
            }
        }
        observe(NotificationCenter.clanMembersDidLoad) { _, _, args ->
            if (isPaused) return@observe
            val id = args.firstOrNull() as? Long ?: return@observe
            if (id == clanId) refreshPermissionsAndList()
        }
        observe(NotificationCenter.clanRolesDidLoad) { _, _, args ->
            if (isPaused) return@observe
            val id = args.firstOrNull() as? Long ?: return@observe
            if (id == clanId) refreshPermissionsAndList()
        }

        if (clanId != 0L) {
            roleController.loadRolesForClan(clanId, force = false)
            userClanController.loadClanMembers(clanId)
        }
        emojiController.invalidateEmojiCacheAndReload()
        return true
    }

    override fun onBecomeFullyVisible() {
        super.onBecomeFullyVisible()
        if (clanId != 0L) {
            roleController.loadRolesForClan(clanId, force = false)
            userClanController.loadClanMembers(clanId)
        }
        refreshPermissionsAndList()
        emojiController.invalidateEmojiCacheAndReload()
    }

    override fun createView(context: Context): View {
        val root = FrameLayout(context).apply {
            setBackgroundColor(themeColors.background)
        }

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        actionBar = ActionBarView(context, themeColors).apply {
            setTitle(getString(R.string.menu_clan_emoji))
            setBackButtonImage(R.drawable.ic_close_24)
            setBackButtonContentDescription(getString(R.string.common_close))
            setCenterTitle(true)
            setMenuOnItemClick(object : ActionBarView.ActionBarMenuOnItemClick() {
                override fun onItemClick(id: Int) {
                    if (id == -1) finishFragment()
                }
            })
        }
        actionBar!!.backButton.apply {
            scaleType = ImageView.ScaleType.CENTER
            layoutParams = (layoutParams as android.widget.FrameLayout.LayoutParams).apply {
                width = LayoutHelper.dp(48f)
                height = LayoutHelper.dp(48f)
            }
        }
        column.addView(actionBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

        recycler = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        adapter = EmojiSettingAdapter()
        recycler.adapter = adapter

        column.addView(recycler, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT))

        root.addView(column, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))

        blockingOverlay = FrameLayout(context).apply {
            visibility = View.GONE
            isClickable = true
            setBackgroundColor(0x88000000.toInt())
            val pb = ProgressBar(context).apply {
                isIndeterminate = true
                indeterminateTintList = android.content.res.ColorStateList.valueOf(themeColors.colorText)
            }
            addView(
                pb,
                FrameLayout.LayoutParams(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER),
            )
        }
        root.addView(
            blockingOverlay,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )

        fragmentView = root
        refreshPermissionsAndList()
        return root
    }

    private fun setBlocking(active: Boolean) {
        blockingOverlay?.visibility = if (active) View.VISIBLE else View.GONE
    }

    private fun refreshPermissionsAndList() {
        val members = userClanController.getClanMembers(clanId)
        val roles = roleController.getRoles(clanId)
        val clan = clansController.clans.value.firstOrNull { it.clanId == clanId }
        permState = ClanSettingsPermissionState.evaluateForClanSettings(
            userController,
            clanId,
            members,
            roles,
            clan?.creatorId ?: 0L,
        )
        reloadListUi()
    }

    private fun reloadListUi() {
        if (!::adapter.isInitialized) return
        adapter.submit(buildClanEmojiRows())
    }

    private fun buildClanEmojiRows(): List<EmojiItem> {
        val cid = clanId.toString()
        if (cid.isBlank() || cid == "0") return emptyList()
        return synchronized(emojiController) {
            emojiController.emojis.filter { it.clanId == cid }
        }
    }

    private fun openImagePicker() {
        val current = buildClanEmojiRows().size
        if (current >= MAX_CLAN_EMOJI_SLOTS) {
            MezonToast.show(this, ToastOverlay.ToastType.ERROR, getString(R.string.clan_emoji_limit_slots))
            return
        }
        val pick = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
        val getContent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        val chooser = Intent.createChooser(getContent, getString(R.string.clan_emoji_upload_button)).apply {
            putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(pick))
        }
        startActivityForResult(chooser, REQUEST_PICK_EMOJI)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PICK_EMOJI || resultCode != Activity.RESULT_OK) return
        val uri = data?.clipData?.getItemAt(0)?.uri ?: data?.data ?: return
        val ctx = getContext() ?: return
        val mime = ctx.contentResolver.getType(uri).orEmpty()
        val isGif = mime.equals("image/gif", ignoreCase = true) ||
            uri.toString().contains(".gif", ignoreCase = true)

        fragmentScope.launch(ioDispatcher) {
            val size = runCatching {
                ctx.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
            }.getOrDefault(0L)
            if (size > MAX_UPLOAD_BYTES) {
                withContext(mainDispatcher) {
                    MezonToast.show(this@EmojiSettingFragment, ToastOverlay.ToastType.ERROR, getString(R.string.clan_emoji_error_size_limit))
                }
                return@launch
            }
            withContext(mainDispatcher) {
                if (isGif) {
                    showUploadPreviewFromUri(uri, isGif = true)
                } else {
                    presentFragment(ClanEmojiCropFragment.newInstance(clanId, uri.toString()))
                }
            }
        }
    }

    private fun showUploadPreviewFromUri(uri: Uri, isGif: Boolean) {
        fragmentScope.launch(ioDispatcher) {
            val ctx = getContext() ?: return@launch
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { stream -> stream.readBytes() }
            if (bytes == null) {
                withContext(mainDispatcher) {
                    MezonToast.show(this@EmojiSettingFragment, ToastOverlay.ToastType.ERROR, getString(R.string.clan_emoji_upload_failed))
                }
                return@launch
            }
            if (bytes.size > MAX_UPLOAD_BYTES) {
                withContext(mainDispatcher) {
                    MezonToast.show(this@EmojiSettingFragment, ToastOverlay.ToastType.ERROR, getString(R.string.clan_emoji_error_size_limit))
                }
                return@launch
            }
            val tmp = File(ctx.cacheDir, "clan_emoji_pick_${System.currentTimeMillis()}.${if (isGif) "gif" else "bin"}")
            try {
                FileOutputStream(tmp).use { stream -> stream.write(bytes) }
                withContext(mainDispatcher) {
                    showUploadPreviewDialog(tmp, isGif = isGif)
                }
            } catch (_: Exception) {
                tmp.delete()
                withContext(mainDispatcher) {
                    MezonToast.show(this@EmojiSettingFragment, ToastOverlay.ToastType.ERROR, getString(R.string.clan_emoji_upload_failed))
                }
            }
        }
    }

    private fun showUploadPreviewDialog(file: File, isGif: Boolean) {
        val ctx = getContext() ?: run {
            file.delete()
            return
        }
        val pad = LayoutHelper.dp(16f)
        val scroll = ScrollView(ctx)
        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        val emojiSection = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }

        emojiSection.addView(
            TextView(ctx).apply {
                text = getString(R.string.clan_emoji_section_label)
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER_HORIZONTAL
                setTextColor(themeColors.onSurfaceVariant)
            },
            LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                0f,
                Gravity.CENTER_HORIZONTAL,
                0f,
                0f,
                0f,
                0f,
            ),
        )

        val previewSide = LayoutHelper.dp(96f)
        val previewIv = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val bmp = BitmapFactory.decodeFile(file.absolutePath)
        if (bmp != null) {
            previewIv.setImageBitmap(bmp)
        }
        val previewFrame = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = LayoutHelper.dp(8f)
            }
        }
        previewFrame.addView(
            previewIv,
            FrameLayout.LayoutParams(previewSide, previewSide, Gravity.CENTER),
        )
        emojiSection.addView(previewFrame)

        body.addView(
            emojiSection,
            LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT),
        )

        val nameCell = InputCell(ctx, themeColors).apply {
            setLabel(getString(R.string.clan_emoji_name_label))
            setHint(getString(R.string.clan_emoji_name_hint))
            setText("emoji_${System.currentTimeMillis()}")
            setMaxCharacter(MAX_NAME_INNER)
            onTextChanged = { setError(null) }
        }
        body.addView(
            nameCell,
            LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT).apply {
                topMargin = LayoutHelper.dp(14f)
            },
        )

        val saleRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val saleCheck = CheckBox(ctx).apply {
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
            CompoundButtonCompat.setButtonTintList(this, ColorStateList.valueOf(themeColors.onSurface))
        }
        saleRow.addView(
            saleCheck,
            LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT),
        )
        val saleLabel = TextView(ctx).apply {
            text = getString(R.string.clan_emoji_for_sale)
            textSize = 14f
            setTextColor(themeColors.colorText)
            setPadding(LayoutHelper.dp(6f), 0, 0, 0)
            setOnClickListener { saleCheck.toggle() }
        }
        saleRow.addView(
            saleLabel,
            LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT),
        )
        body.addView(
            saleRow,
            LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, Gravity.START).apply {
                topMargin = LayoutHelper.dp(14f)
            },
        )

        scroll.addView(body)

        val dialog = AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.clan_emoji_preview_dialog_title))
            .setView(scroll, LayoutHelper.WRAP_CONTENT)
            .setDismissDialogByButtons(false)
            .setNegativeButton(getString(R.string.common_cancel)) { d, _ ->
                file.delete()
                d.dismiss()
            }
            .setPositiveButton(getString(R.string.clan_emoji_confirm_upload), null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.setOnClickListener {
                val inner = nameCell.getText().trim()
                if (inner.length !in MIN_NAME_INNER..MAX_NAME_INNER || !isClanEmojiNameValid(inner)) {
                    nameCell.setError(
                        getString(R.string.clan_emoji_validate_name, MIN_NAME_INNER, MAX_NAME_INNER),
                    )
                    return@setOnClickListener
                }
                nameCell.setError(null)
                dialog.dismiss()
                runUploadFlow(file, isGif, inner, saleCheck.isChecked)
            }
        }
        dialog.show()
    }

    private fun runUploadFlow(file: File, isGif: Boolean, innerName: String, isForSale: Boolean) {
        fragmentScope.launch(ioDispatcher) {
            try {
                setBlockingUi(true)
                val primaryId = newEmojiNumericId()
                val bytes = file.readBytes()
                file.delete()

                val mime = when {
                    isGif -> "image/gif"
                    file.name.endsWith(".webp", true) -> "image/webp"
                    else -> "image/jpeg"
                }
                val ext = when {
                    isGif -> "gif"
                    mime == "image/webp" -> "webp"
                    else -> "jpg"
                }

                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                val w = opts.outWidth.takeIf { it > 0 } ?: 128
                val h = opts.outHeight.takeIf { it > 0 } ?: 128

                val primaryFilename = "emojis/$primaryId.$ext"
                val primaryUrl = uploadBytes(bytes, primaryFilename, mime, w, h)

                val emojiRecordId = if (isForSale) {
                    val thumbBmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?: throw IllegalStateException("decode thumb")
                    val scaled = Bitmap.createScaledBitmap(thumbBmp, 35, 35, true)
                    if (scaled !== thumbBmp) thumbBmp.recycle()
                    val thumbId = newEmojiNumericId()
                    val thumbBytes = encodeTinyJpeg(scaled)
                    scaled.recycle()
                    val thumbName = "emojis/$thumbId.jpg"
                    uploadBytes(thumbBytes, thumbName, "image/jpeg", 35, 35)
                    thumbId
                } else {
                    primaryId
                }

                val shortname = ":$innerName:"
                sessionManager.withAutoRefresh { session ->
                    api.createClanEmoji(
                        session.apiUrl,
                        session.token,
                        clanId,
                        emojiRecordId,
                        primaryUrl,
                        shortname,
                        "Custom",
                        isForSale,
                    )
                }
                withContext(mainDispatcher) {
                    setBlockingUi(false)
                    emojiController.invalidateEmojiCacheAndReload()
                }
            } catch (e: Exception) {
                withContext(mainDispatcher) {
                    setBlockingUi(false)
                    MezonToast.show(this@EmojiSettingFragment, ToastOverlay.ToastType.ERROR, getString(R.string.clan_emoji_upload_failed))
                }
            }
        }
    }

    private suspend fun uploadBytes(
        bytes: ByteArray,
        filename: String,
        mime: String,
        width: Int,
        height: Int,
    ): String {
        return sessionManager.withAutoRefresh { session ->
            val presign = api.uploadAttachmentFile(
                session.apiUrl,
                session.token,
                filename,
                mime,
                bytes.size,
                width.coerceAtLeast(1),
                height.coerceAtLeast(1),
            )
            api.putFileToPresignedUrl(presign.url, bytes, mime)
            "${BuildConfig.MEZON_BASE_IMG_URL}/${presign.filename}"
        }
    }

    private fun encodeTinyJpeg(bitmap: Bitmap): ByteArray {
        var quality = 35
        while (quality >= 10) {
            val bout = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, bout)
            val arr = bout.toByteArray()
            if (arr.isNotEmpty()) return arr
            quality -= 5
        }
        val bout = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 10, bout)
        return bout.toByteArray()
    }

    private suspend fun setBlockingUi(active: Boolean) {
        withContext(mainDispatcher) { setBlocking(active) }
    }

    private fun canEditOrDelete(item: EmojiItem): Boolean {
        val perm = permState ?: return false
        val uid = userController.userId
        if (uid == 0L) return false
        if (perm.hasAdminPermission || perm.isClanOwner || perm.hasManageClanPermission) return true
        val creator = item.creatorId.toLongOrNull() ?: return false
        return creator == uid
    }

    private fun resolveMember(userId: Long): ClanMember? =
        userClanController.getClanMembers(clanId).firstOrNull { it.userId == userId }

    private fun displayAuthor(item: EmojiItem): String {
        val creatorId = item.creatorId.toLongOrNull() ?: return ""
        val m = resolveMember(creatorId) ?: return ""
        return m.clanNick.ifBlank { m.displayName.ifBlank { m.username } }
    }

    private fun commitEmojiRename(item: EmojiItem, inner: String) {
        val trimmed = inner.trim()
        if (trimmed.length !in MIN_NAME_INNER..MAX_NAME_INNER || !isClanEmojiNameValid(trimmed)) {
            MezonToast.show(this, ToastOverlay.ToastType.ERROR, getString(R.string.clan_emoji_validate_name, MIN_NAME_INNER, MAX_NAME_INNER))
            reloadListUi()
            return
        }
        val shortname = ":$trimmed:"
        if (shortname == item.shortname) return
        val emojiId = item.id.toLongOrNull() ?: return
        fragmentScope.launch(ioDispatcher) {
            try {
                setBlockingUi(true)
                sessionManager.withAutoRefresh { session ->
                    api.updateClanEmojiById(session.apiUrl, session.token, emojiId, clanId, shortname)
                }
                withContext(mainDispatcher) {
                    setBlockingUi(false)
                    emojiController.invalidateEmojiCacheAndReload()
                }
            } catch (_: Exception) {
                withContext(mainDispatcher) {
                    setBlockingUi(false)
                    MezonToast.show(this@EmojiSettingFragment, ToastOverlay.ToastType.ERROR, getString(R.string.clan_emoji_update_failed))
                    reloadListUi()
                }
            }
        }
    }

    private fun confirmDelete(item: EmojiItem) {
        val ctx = getContext() ?: return
        AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.clan_emoji_delete_title))
            .setMessage(getString(R.string.clan_emoji_delete_message, item.shortname))
            .setNegativeButton(getString(R.string.common_cancel), null)
            .setPositiveButton(getString(R.string.clan_emoji_delete_confirm)) { _, _ ->
                deleteEmoji(item)
            }
            .show()
    }

    private fun deleteEmoji(item: EmojiItem) {
        val emojiId = item.id.toLongOrNull() ?: return
        fragmentScope.launch(ioDispatcher) {
            try {
                setBlockingUi(true)
                sessionManager.withAutoRefresh { session ->
                    api.deleteByIdClanEmoji(session.apiUrl, session.token, emojiId, clanId, item.shortname)
                }
                withContext(mainDispatcher) {
                    setBlockingUi(false)
                    emojiController.invalidateEmojiCacheAndReload()
                }
            } catch (_: Exception) {
                withContext(mainDispatcher) {
                    setBlockingUi(false)
                    MezonToast.show(this@EmojiSettingFragment, ToastOverlay.ToastType.ERROR, getString(R.string.clan_emoji_delete_failed))
                }
            }
        }
    }

    private inner class EmojiSettingAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val viewTypeHeader = 1
        private val viewTypeRow = 2

        private var rows: List<EmojiItem> = emptyList()

        fun submit(newRows: List<EmojiItem>) {
            rows = newRows
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = 1 + rows.size

        override fun getItemViewType(position: Int): Int =
            if (position == 0) viewTypeHeader else viewTypeRow

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val ctx = parent.context
            if (viewType == viewTypeHeader) {
                val outer = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(LayoutHelper.dp(16f), LayoutHelper.dp(8f), LayoutHelper.dp(16f), LayoutHelper.dp(8f))
                }
                val uploadWrap = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_HORIZONTAL
                }
                uploadWrap.addView(
                    ClanSettingsUiHelpers.buildHorizontalActionButton(
                        ctx,
                        themeColors,
                        MezonIcon.faceIcon,
                        getString(R.string.clan_emoji_upload_button),
                        Runnable { openImagePicker() },
                    ),
                )
                outer.addView(uploadWrap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

                outer.addView(
                    TextView(ctx).apply {
                        text = getString(R.string.clan_emoji_description_body)
                        textSize = 13f
                        setTextColor(CreateClanRnUiTokens.textDisabled(themeColors))
                    },
                    LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT).apply {
                        topMargin = LayoutHelper.dp(12f)
                    },
                )
                outer.addView(
                    TextView(ctx).apply {
                        text = getString(R.string.clan_emoji_requirements_title)
                        textSize = 12f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(themeColors.colorText)
                    },
                    LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT).apply {
                        topMargin = LayoutHelper.dp(14f)
                    },
                )
                outer.addView(
                    TextView(ctx).apply {
                        text = getString(R.string.clan_emoji_requirements_list)
                        textSize = 12f
                        setTextColor(CreateClanRnUiTokens.textDisabled(themeColors))
                    },
                    LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT).apply {
                        topMargin = LayoutHelper.dp(6f)
                    },
                )

                val labels = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, LayoutHelper.dp(18f), 0, LayoutHelper.dp(6f))
                }
                labels.addView(
                    TextView(ctx).apply {
                        text = getString(R.string.clan_emoji_column_image)
                        textSize = 11f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(themeColors.textDisabled)
                    },
                    LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 0.22f),
                )
                labels.addView(
                    TextView(ctx).apply {
                        text = getString(R.string.clan_emoji_column_name)
                        textSize = 11f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(themeColors.textDisabled)
                    },
                    LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 0.38f),
                )
                labels.addView(
                    TextView(ctx).apply {
                        text = getString(R.string.clan_emoji_column_uploaded_by)
                        textSize = 11f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(themeColors.textDisabled)
                        gravity = Gravity.END
                    },
                    LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 0.4f),
                )
                labels.addView(
                    View(ctx),
                    LayoutHelper.createLinear(40, LayoutHelper.WRAP_CONTENT),
                )
                outer.addView(labels, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

                outer.layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT,
                )
                return object : RecyclerView.ViewHolder(outer) {}
            }

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = LayoutHelper.dp(60f)
                setPadding(LayoutHelper.dp(14f), LayoutHelper.dp(8f), LayoutHelper.dp(14f), LayoutHelper.dp(8f))
                setBackgroundColor(themeColors.border)
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT,
                )
            }

            val emojiCol = FrameLayout(ctx)
            val emojiIv = ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            emojiCol.addView(
                emojiIv,
                FrameLayout.LayoutParams(LayoutHelper.dp(40), LayoutHelper.dp(40), Gravity.CENTER),
            )
            row.addView(
                emojiCol,
                LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 0.22f, Gravity.CENTER_VERTICAL),
            )

            val nameCol = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val edit = EditText(ctx).apply {
                textSize = 15f
                setTextColor(themeColors.colorText)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setHorizontallyScrolling(false)
                imeOptions = EditorInfo.IME_ACTION_DONE
                setCompoundDrawables(null, null, null, null)
                compoundDrawablePadding = 0
            }
            nameCol.addView(edit, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f))
            row.addView(
                nameCol,
                LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 0.38f, Gravity.CENTER_VERTICAL),
            )

            val av = AvatarView(ctx).apply {
                setSizeDp(28)
                setRoundRadius(14f)
            }
            val uploaderCol = FrameLayout(ctx)
            uploaderCol.addView(
                av,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.END or Gravity.CENTER_VERTICAL,
                ),
            )
            row.addView(
                uploaderCol,
                LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 0.4f, Gravity.CENTER_VERTICAL),
            )

            val deleteBtn = ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                contentDescription = ctx.getString(R.string.clan_emoji_delete_title)
                setPadding(LayoutHelper.dp(6f), LayoutHelper.dp(6f), LayoutHelper.dp(6f), LayoutHelper.dp(6f))
            }
            val removeCol = FrameLayout(ctx)
            removeCol.addView(
                deleteBtn,
                FrameLayout.LayoutParams(
                    LayoutHelper.dp(28),
                    LayoutHelper.dp(28),
                    Gravity.CENTER,
                ),
            )
            row.addView(
                removeCol,
                LayoutHelper.createLinear(40, LayoutHelper.WRAP_CONTENT, 0f, Gravity.CENTER_VERTICAL),
            )

            return RowVH(row, emojiIv, edit, av, deleteBtn)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (getItemViewType(position) == viewTypeHeader) return
            val item = rows[position - 1]
            holder as RowVH
            holder.item = item

            val url = item.src.ifBlank { getEmojiUrl(item.id).orEmpty() }
            if (url.isNotBlank()) {
                MezonImageLoader.getInstance(holder.emojiIv.context).load(
                    url,
                    LayoutHelper.dp(40f),
                    LayoutHelper.dp(40f),
                    onSuccess = { bmp -> holder.emojiIv.setImageBitmap(bmp) },
                    onError = { _: Exception -> holder.emojiIv.setImageDrawable(null) },
                )
            } else {
                holder.emojiIv.setImageDrawable(null)
            }

            val inner = item.shortname.trim(':')
            holder.edit.setText(inner)

            val allow = canEditOrDelete(item)
            holder.edit.isEnabled = allow
            holder.edit.isFocusable = allow
            holder.edit.isFocusableInTouchMode = allow

            holder.edit.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                if (!hasFocus && holder.item?.id == item.id) {
                    commitEmojiRename(item, holder.edit.text?.toString().orEmpty())
                }
            }

            val creatorId = item.creatorId.toLongOrNull() ?: 0L
            val m = resolveMember(creatorId)
            val avUrl = m?.clanAvatar.orEmpty().ifBlank { m?.avatarUrl.orEmpty() }
            holder.avatarView.setInfo(creatorId, displayAuthor(item))
            if (avUrl.isNotBlank()) holder.avatarView.setImageUrl(avUrl) else holder.avatarView.setImageUrl(null)

            val del = MezonIcon.closeSmallBold.getDrawable(holder.deleteBtn.context, themeColors.error)
            holder.deleteBtn.setImageDrawable(del)
            holder.deleteBtn.visibility = if (allow) View.VISIBLE else View.INVISIBLE
            holder.deleteBtn.setOnClickListener {
                confirmDelete(item)
            }
        }

        private inner class RowVH(
            view: View,
            val emojiIv: ImageView,
            val edit: EditText,
            val avatarView: AvatarView,
            val deleteBtn: ImageView,
        ) : RecyclerView.ViewHolder(view) {
            var item: EmojiItem? = null
        }
    }
}
