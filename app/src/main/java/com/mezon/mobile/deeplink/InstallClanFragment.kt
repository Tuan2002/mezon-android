package com.mezon.mobile.deeplink

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.mezon.mobile.MainActivity
import com.mezon.mobile.R
import com.mezon.mobile.core.BaseFragment
import com.mezon.mobile.core.LayoutHelper
import com.mezon.mobile.di.FragmentEntryPoint
import com.mezon.mobile.home.chat.ChatFragment
import com.mezon.mobile.home.chat.MezonImageLoader
import com.mezon.mobile.home.clans.CHANNEL_TYPE_APP
import com.mezon.mobile.home.clans.ClanCategoryItem
import com.mezon.mobile.home.clans.ClanEntity
import com.mezon.mobile.home.clans.ChannelController
import com.mezon.mobile.home.clans.ClansController
import com.mezon.mobile.home.profile.UserController
import com.mezon.mobile.network.MezonApi
import com.mezon.mobile.session.SessionManager
import com.mezon.mobile.ui.MezonToast
import com.mezon.mobile.ui.cells.MezonIcon
import com.mezon.mobile.ui.cells.ToastOverlay
import com.mezon.mobile.util.avatarImgproxyUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.Normalizer
import java.util.Date
import java.util.Locale
import kotlin.math.max

class InstallClanFragment : BaseFragment() {

    companion object {
        private const val ARG_APP_ID = "appId"
        private val CHANNEL_LABEL_REGEX = Regex("[^\\p{L}\\p{M}\\p{N} \\-_]")

        fun newInstance(appId: Long): InstallClanFragment {
            return InstallClanFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_APP_ID, appId)
                }
            }
        }
    }

    private lateinit var api: MezonApi
    private lateinit var sessionManager: SessionManager
    private lateinit var clansController: ClansController
    private lateinit var channelController: ChannelController
    private lateinit var userController: UserController

    private var appId = 0L
    private var appName = ""
    private var appCreateTimeSeconds = 0
    private var selectedClan: ClanEntity? = null
    private var selectedCategory: ClanCategoryItem? = null
    private var categories = emptyList<ClanCategoryItem>()
    private var createdChannelId = 0L
    private var createdChannelLabel = ""

    private var appNameView: TextView? = null
    private var accessRequestView: TextView? = null
    private var signedInView: TextView? = null
    private var clanPickerValueView: TextView? = null
    private var clanErrorView: TextView? = null
    private var categorySection: LinearLayout? = null
    private var categoryPickerValueView: TextView? = null
    private var categoryErrorView: TextView? = null
    private var channelNameInput: EditText? = null
    private var privacyFooterView: TextView? = null
    private var activeSinceView: TextView? = null
    private var authorizeButton: TextView? = null
    private var loadingView: ProgressBar? = null
    private var formScroll: ScrollView? = null
    private var successContainer: LinearLayout? = null
    private var logoView: ImageView? = null
    private var logoLoad: MezonImageLoader.Cancellable? = null

    override fun onInject(entryPoint: FragmentEntryPoint) {
        api = entryPoint.mezonApi()
        sessionManager = entryPoint.sessionManager()
        clansController = entryPoint.clansController()
        channelController = entryPoint.channelController()
        userController = entryPoint.userController()
    }

    override fun onFragmentCreate(): Boolean {
        super.onFragmentCreate()
        appId = arguments?.getLong(ARG_APP_ID) ?: 0L
        clansController.loadClans()
        return true
    }

    override fun createView(context: Context): View {
        actionBar = createActionBar(context).apply {
            setTitle(getString(R.string.deeplink_install_authorize))
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(themeColors.chatBackground)
        }
        root.addView(actionBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

        val loading = ProgressBar(context).apply {
            visibility = View.VISIBLE
        }
        loadingView = loading
        root.addView(loading, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER))

        val formScroll = ScrollView(context).apply {
            visibility = View.GONE
        }
        this.formScroll = formScroll

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(LayoutHelper.dp(24), LayoutHelper.dp(24), LayoutHelper.dp(24), LayoutHelper.dp(24))
        }

        val logoSize = LayoutHelper.dp(100)
        val logo = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                cornerRadius = LayoutHelper.dp(16f).toFloat()
                setColor(themeColors.surfaceVariant)
            }
        }
        logoView = logo
        card.addView(logo, LinearLayout.LayoutParams(logoSize, logoSize))

        val appName = TextView(context).apply {
            setTextColor(themeColors.onSurface)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        appNameView = appName
        card.addView(
            appName,
            LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT).apply {
                topMargin = LayoutHelper.dp(16)
            }
        )

        val accessRequest = TextView(context).apply {
            setTextColor(themeColors.onSurfaceVariant)
            textSize = 14f
            gravity = Gravity.CENTER
        }
        accessRequestView = accessRequest
        card.addView(
            accessRequest,
            LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, Gravity.NO_GRAVITY, 0f, 12f, 0f, 0f)
        )

        val signedInRow = buildSignedInRow(context)
        card.addView(
            signedInRow,
            LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, Gravity.NO_GRAVITY, 0f, 16f, 0f, 0f)
        )

        card.addView(formLabel(context, getString(R.string.deeplink_install_add_to_clan)), fieldLabelMargin())

        val clanPickerRow = buildPickerRow(context) { showClanPicker() }
        val clanPickerValue = TextView(context).apply {
            text = getString(R.string.deeplink_install_clan_placeholder)
            setTextColor(themeColors.onSurfaceVariant)
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, LayoutHelper.WRAP_CONTENT, 1f)
        }
        clanPickerValueView = clanPickerValue
        clanPickerRow.addView(clanPickerValue)
        clanPickerRow.addView(chevronView(context))
        card.addView(clanPickerRow, fieldRowMargin())

        val clanError = inlineErrorView(context)
        clanErrorView = clanError
        card.addView(clanError, fieldErrorMargin())

        val categoryBlock = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        categorySection = categoryBlock
        categoryBlock.addView(formLabel(context, getString(R.string.deeplink_install_add_to_category)), fieldLabelMargin())
        val categoryPickerRow = buildPickerRow(context) { showCategoryPicker() }
        val categoryPickerValue = TextView(context).apply {
            text = getString(R.string.deeplink_install_category_placeholder)
            setTextColor(themeColors.onSurfaceVariant)
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, LayoutHelper.WRAP_CONTENT, 1f)
        }
        categoryPickerValueView = categoryPickerValue
        categoryPickerRow.addView(categoryPickerValue)
        categoryPickerRow.addView(chevronView(context))
        categoryBlock.addView(categoryPickerRow, fieldRowMargin())
        val categoryError = inlineErrorView(context)
        categoryErrorView = categoryError
        categoryBlock.addView(categoryError, fieldErrorMargin())
        card.addView(categoryBlock, fieldBlockMargin())

        card.addView(formLabel(context, getString(R.string.deeplink_install_channel_name)), fieldLabelMargin())
        val channelName = EditText(context).apply {
            setTextColor(themeColors.onSurface)
            setHintTextColor(themeColors.onSurfaceVariant)
            textSize = 15f
            background = fieldBackground()
            setPadding(LayoutHelper.dp(12), LayoutHelper.dp(12), LayoutHelper.dp(12), LayoutHelper.dp(12))
            minimumHeight = LayoutHelper.dp(44)
        }
        channelNameInput = channelName
        card.addView(channelName, fieldRowMargin())

        card.addView(buildMetaTextRow(context, MezonIcon.lockIcon) { privacyFooterView = it }, metaRowMargin())
        card.addView(buildMetaTextRow(context, MezonIcon.clockIcon) { activeSinceView = it }, fieldRowMargin())

        card.addView(buildActionRow(context), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT).apply {
            topMargin = LayoutHelper.dp(24)
        })

        formScroll.addView(card, FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

        val success = buildSuccessView(context).apply {
            visibility = View.GONE
        }
        successContainer = success

        root.addView(formScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f))
        root.addView(success, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f))

        fragmentView = root
        loadAppDetail()
        return root
    }

    override fun onFragmentDestroy() {
        logoLoad?.cancel()
        logoLoad = null
        super.onFragmentDestroy()
    }

    private fun loadAppDetail() {
        if (appId == 0L) {
            finishToHome()
            return
        }
        fragmentScope.launch {
            val app = runCatching {
                sessionManager.withAutoRefresh { session ->
                    withContext(Dispatchers.IO) {
                        api.getApp(session.apiUrl, session.token, appId)
                    }
                }
            }.getOrNull()
            withContext(Dispatchers.Main) {
                loadingView?.visibility = View.GONE
                if (fragmentView == null || isPaused) return@withContext
                if (app == null) {
                    MezonToast.show(this@InstallClanFragment, ToastOverlay.ToastType.ERROR, getString(R.string.deeplink_install_error))
                    finishToHome()
                    return@withContext
                }
                formScroll?.visibility = View.VISIBLE
                appName = app.appname
                appCreateTimeSeconds = app.createTimeSeconds
                appNameView?.text = app.appname.ifBlank { getString(R.string.deeplink_install_unknown_app) }
                updateAccessRequestText()
                updateSignedInText()
                updateFooterTexts()
                channelNameInput?.setText(app.appname)
                val logo = logoView
                if (logo != null && app.applogo.isNotBlank()) {
                    val px = max(LayoutHelper.dp(100) * 2, 200)
                    logoLoad = MezonImageLoader.getInstance(logo.context).load(
                        avatarImgproxyUrl(app.applogo, px),
                        px,
                        px,
                        onSuccess = { bmp -> logo.setImageBitmap(bmp) }
                    )
                }
            }
        }
    }

    private fun updateAccessRequestText() {
        val name = appName.ifBlank { getString(R.string.deeplink_install_unknown_app) }
        accessRequestView?.text = getString(R.string.deeplink_install_access_request, name)
    }

    private fun updateSignedInText() {
        val username = userController.username.ifBlank { userController.displayName }
        val signedIn = getString(R.string.deeplink_install_signed_in_as, username)
        val spannable = SpannableString(signedIn)
        if (username.isNotBlank()) {
            val usernameStart = signedIn.indexOf(username).coerceAtLeast(0)
            val usernameEnd = (usernameStart + username.length).coerceAtMost(signedIn.length)
            if (usernameStart < usernameEnd) {
                spannable.setSpan(StyleSpan(Typeface.BOLD), usernameStart, usernameEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(ForegroundColorSpan(themeColors.onSurface), usernameStart, usernameEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        signedInView?.text = spannable
    }

    private fun updateFooterTexts() {
        val displayName = appName.ifBlank { getString(R.string.deeplink_install_unknown_app) }
        privacyFooterView?.text = getString(R.string.deeplink_install_privacy_footer, displayName)
        val dateText = if (appCreateTimeSeconds > 0) {
            DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault())
                .format(Date(appCreateTimeSeconds.toLong() * 1000L))
        } else {
            ""
        }
        activeSinceView?.text = if (dateText.isBlank()) {
            ""
        } else {
            getString(R.string.deeplink_install_active_since, dateText)
        }
        activeSinceView?.visibility = if (dateText.isBlank()) View.GONE else View.VISIBLE
        (activeSinceView?.parent as? View)?.visibility = if (dateText.isBlank()) View.GONE else View.VISIBLE
    }

    private fun showClanPicker() {
        clearClanError()
        val clans = clansController.clans.value
        if (clans.isEmpty()) {
            MezonToast.show(this, ToastOverlay.ToastType.INFO, getString(R.string.deeplink_install_no_clans))
            return
        }
        val activity = getParentActivity() ?: return
        InstallPickerSheet(
            context = activity,
            themeColors = themeColors,
            title = getString(R.string.deeplink_install_add_to_clan),
            items = clans.map { InstallPickerSheet.InstallPickerItem(it.clanId.toString(), it.clanName) },
            onPicked = { item ->
                val clan = clans.firstOrNull { it.clanId.toString() == item.id } ?: return@InstallPickerSheet
                onClanSelected(clan)
            }
        ).show()
    }

    private fun onClanSelected(clan: ClanEntity) {
        selectedClan = clan
        selectedCategory = null
        categories = emptyList()
        clanPickerValueView?.text = clan.clanName
        clanPickerValueView?.setTextColor(themeColors.onSurface)
        categoryPickerValueView?.text = getString(R.string.deeplink_install_category_placeholder)
        categoryPickerValueView?.setTextColor(themeColors.onSurfaceVariant)
        clearCategoryError()
        categorySection?.visibility = View.VISIBLE
        updateAuthorizeState()
        loadCategories(clan.clanId)
    }

    private fun loadCategories(clanId: Long) {
        fragmentScope.launch {
            val list = runCatching {
                withContext(Dispatchers.IO) {
                    channelController.loadCategoriesForClan(clanId, force = true)
                }
            }.getOrElse { emptyList() }
            withContext(Dispatchers.Main) {
                if (fragmentView == null || isPaused) return@withContext
                if (selectedClan?.clanId != clanId) return@withContext
                categories = list
                if (list.isEmpty()) {
                    MezonToast.show(this@InstallClanFragment, ToastOverlay.ToastType.INFO, getString(R.string.deeplink_install_no_categories))
                }
            }
        }
    }

    private fun showCategoryPicker() {
        clearCategoryError()
        if (selectedClan == null) {
            showClanError(getString(R.string.deeplink_install_select_clan_error))
            return
        }
        if (categories.isEmpty()) {
            MezonToast.show(this, ToastOverlay.ToastType.INFO, getString(R.string.deeplink_install_no_categories))
            return
        }
        val activity = getParentActivity() ?: return
        InstallPickerSheet(
            context = activity,
            themeColors = themeColors,
            title = getString(R.string.deeplink_install_add_to_category),
            items = categories.map { InstallPickerSheet.InstallPickerItem(it.categoryId.toString(), it.categoryName) },
            onPicked = { item ->
                val category = categories.firstOrNull { it.categoryId.toString() == item.id } ?: return@InstallPickerSheet
                selectedCategory = category
                categoryPickerValueView?.text = category.categoryName
                categoryPickerValueView?.setTextColor(themeColors.onSurface)
                clearCategoryError()
                updateAuthorizeState()
            }
        ).show()
    }

    private fun updateAuthorizeState() {
        val enabled = selectedClan != null && selectedCategory != null
        authorizeButton?.isEnabled = enabled
        authorizeButton?.alpha = if (enabled) 1f else 0.5f
    }

    private fun onAuthorizeClicked() {
        clearClanError()
        clearCategoryError()
        val clan = selectedClan
        val category = selectedCategory
        var hasError = false
        if (clan == null) {
            showClanError(getString(R.string.deeplink_install_select_clan_error))
            hasError = true
        }
        if (category == null) {
            showCategoryError(getString(R.string.deeplink_install_select_category_error))
            hasError = true
        }
        if (hasError) return

        val channelLabel = sanitizeChannelLabel(channelNameInput?.text?.toString().orEmpty(), appName)
        authorizeButton?.isEnabled = false
        fragmentScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    channelController.createAppChannel(
                        clanId = clan!!.clanId,
                        categoryId = category!!.categoryId,
                        channelLabel = channelLabel,
                        appId = appId
                    )
                }
            }
            withContext(Dispatchers.Main) {
                if (fragmentView == null || isPaused) return@withContext
                result.onSuccess { desc ->
                    createdChannelId = desc.channelId
                    createdChannelLabel = desc.channelLabel.ifBlank { channelLabel }
                    showSuccessState(clan!!)
                }.onFailure {
                    updateAuthorizeState()
                    MezonToast.show(this@InstallClanFragment, ToastOverlay.ToastType.ERROR, getString(R.string.deeplink_install_error))
                }
            }
        }
    }

    private fun showSuccessState(clan: ClanEntity) {
        formScroll?.visibility = View.GONE
        successContainer?.visibility = View.VISIBLE
        val title = successContainer?.findViewWithTag<TextView>("success_title")
        val message = successContainer?.findViewWithTag<TextView>("success_message")
        title?.text = getString(R.string.deeplink_install_success_title)
        message?.text = getString(
            R.string.deeplink_install_success_message,
            appName.ifBlank { getString(R.string.deeplink_install_unknown_app) },
            clan.clanName
        )
        MezonToast.show(this, ToastOverlay.ToastType.SUCCESS, getString(R.string.deeplink_install_success))
    }

    private fun openCreatedChannel() {
        val clan = selectedClan ?: return
        if (createdChannelId == 0L) {
            finishToHome()
            return
        }
        presentFragment(
            ChatFragment.newInstance(
                channelId = createdChannelId,
                channelName = createdChannelLabel.ifBlank { appName },
                clanId = clan.clanId,
                channelType = CHANNEL_TYPE_APP,
                isChannelPrivate = false
            )
        )
        finishFragment()
    }

    private fun finishToHome() {
        finishFragment()
    }

    private fun onNotYouClicked() {
        (getParentActivity() as? MainActivity)?.logoutToChooseDifferentPhone()
    }

    private fun sanitizeChannelLabel(label: String, fallback: String): String {
        val source = label.ifBlank { fallback }
        return Normalizer.normalize(source, Normalizer.Form.NFC)
            .replace(CHANNEL_LABEL_REGEX, "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(32)
    }

    private fun buildSignedInRow(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val signedIn = TextView(context).apply {
                gravity = Gravity.CENTER
                textSize = 14f
                setTextColor(themeColors.onSurfaceVariant)
            }
            signedInView = signedIn
            addView(signedIn, LinearLayout.LayoutParams(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT))
            val notYou = TextView(context).apply {
                text = getString(R.string.deeplink_install_not_you)
                textSize = 14f
                setTextColor(themeColors.primary)
                setPadding(LayoutHelper.dp(4), 0, 0, 0)
                setOnClickListener { onNotYouClicked() }
            }
            addView(notYou, LinearLayout.LayoutParams(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT))
        }
    }

    private fun buildActionRow(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            val back = TextView(context).apply {
                text = getString(R.string.deeplink_install_back)
                setTextColor(themeColors.onSurface)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(LayoutHelper.dp(8), LayoutHelper.dp(12), LayoutHelper.dp(8), LayoutHelper.dp(12))
                setOnClickListener { finishToHome() }
            }
            addView(back, LinearLayout.LayoutParams(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT))

            addView(
                TextView(context).apply {
                    text = getString(R.string.deeplink_install_authorize_hint)
                    setTextColor(themeColors.onSurfaceVariant)
                    textSize = 11f
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(0, LayoutHelper.WRAP_CONTENT, 1f)
            )

            val authorize = TextView(context).apply {
                text = getString(R.string.deeplink_install_authorize)
                gravity = Gravity.CENTER
                setTextColor(themeColors.onPrimary)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                val radius = LayoutHelper.dp(8f).toFloat()
                background = RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x33000000),
                    GradientDrawable().apply {
                        cornerRadius = radius
                        setColor(themeColors.primary)
                    },
                    null
                )
                setPadding(LayoutHelper.dp(16), LayoutHelper.dp(10), LayoutHelper.dp(16), LayoutHelper.dp(10))
                isEnabled = false
                alpha = 0.5f
                setOnClickListener { onAuthorizeClicked() }
            }
            authorizeButton = authorize
            addView(authorize, LinearLayout.LayoutParams(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT))
        }
    }

    private fun buildSuccessView(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(LayoutHelper.dp(24), LayoutHelper.dp(48), LayoutHelper.dp(24), LayoutHelper.dp(24))

            addView(TextView(context).apply {
                tag = "success_title"
                setTextColor(themeColors.onSurface)
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            })

            addView(
                TextView(context).apply {
                    tag = "success_message"
                    setTextColor(themeColors.onSurfaceVariant)
                    textSize = 15f
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT).apply {
                    topMargin = LayoutHelper.dp(12)
                }
            )

            addView(
                buildPrimaryButton(context, getString(R.string.deeplink_install_open_channel)) {
                    openCreatedChannel()
                },
                LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT).apply {
                    topMargin = LayoutHelper.dp(24)
                }
            )

            addView(
                buildSecondaryButton(context, getString(R.string.deeplink_install_done)) {
                    finishToHome()
                },
                LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT).apply {
                    topMargin = LayoutHelper.dp(12)
                }
            )
        }
    }

    private fun buildMetaTextRow(context: Context, icon: MezonIcon, assignTextView: (TextView) -> Unit): LinearLayout {
        val textView = TextView(context).apply {
            setTextColor(themeColors.onSurfaceVariant)
            textSize = 12f
            gravity = Gravity.START
        }
        assignTextView(textView)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val iconSize = LayoutHelper.dp(14)
            addView(ImageView(context).apply {
                setImageDrawable(icon.getDrawable(context, themeColors.onSurfaceVariant))
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
            })
            addView(
                textView,
                LinearLayout.LayoutParams(0, LayoutHelper.WRAP_CONTENT, 1f).apply {
                    leftMargin = LayoutHelper.dp(8)
                }
            )
        }
    }

    private fun buildPickerRow(context: Context, onClick: () -> Unit): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = fieldBackground()
            minimumHeight = LayoutHelper.dp(44)
            setPadding(LayoutHelper.dp(12), LayoutHelper.dp(10), LayoutHelper.dp(12), LayoutHelper.dp(10))
            setOnClickListener { onClick() }
        }
    }

    private fun chevronView(context: Context): ImageView {
        val size = LayoutHelper.dp(18)
        return ImageView(context).apply {
            setImageDrawable(MezonIcon.chevronDownSmallIcon.getDrawable(context, themeColors.onSurfaceVariant))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                leftMargin = LayoutHelper.dp(8)
            }
        }
    }

    private fun inlineErrorView(context: Context): TextView {
        return TextView(context).apply {
            visibility = View.GONE
            setTextColor(themeColors.error)
            textSize = 12f
        }
    }

    private fun showClanError(message: String) {
        clanErrorView?.text = message
        clanErrorView?.visibility = View.VISIBLE
    }

    private fun showCategoryError(message: String) {
        categoryErrorView?.text = message
        categoryErrorView?.visibility = View.VISIBLE
    }

    private fun clearClanError() {
        clanErrorView?.visibility = View.GONE
    }

    private fun clearCategoryError() {
        categoryErrorView?.visibility = View.GONE
    }

    private fun fieldBackground(): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = LayoutHelper.dp(8f).toFloat()
            setColor(themeColors.surface)
            setStroke(LayoutHelper.dp(1), themeColors.outline)
        }
    }

    private fun formLabel(context: Context, text: String): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(themeColors.onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = true
            gravity = Gravity.START
        }
    }

    private fun fieldLabelMargin(): LinearLayout.LayoutParams {
        return LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT).apply {
            topMargin = LayoutHelper.dp(12)
        }
    }

    private fun fieldRowMargin(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT).apply {
            topMargin = LayoutHelper.dp(8)
        }
    }

    private fun fieldErrorMargin(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT).apply {
            topMargin = LayoutHelper.dp(4)
        }
    }

    private fun fieldBlockMargin(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT)
    }

    private fun metaRowMargin(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT).apply {
            topMargin = LayoutHelper.dp(16)
        }
    }

    private fun buildPrimaryButton(context: Context, label: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(themeColors.onPrimary)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            val radius = LayoutHelper.dp(8f).toFloat()
            background = RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x33000000),
                GradientDrawable().apply {
                    cornerRadius = radius
                    setColor(themeColors.primary)
                },
                null
            )
            setPadding(0, LayoutHelper.dp(14), 0, LayoutHelper.dp(14))
            setOnClickListener { onClick() }
        }
    }

    private fun buildSecondaryButton(context: Context, label: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(themeColors.onSurface)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            val radius = LayoutHelper.dp(8f).toFloat()
            background = RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x22000000),
                GradientDrawable().apply {
                    cornerRadius = radius
                    setColor(themeColors.surfaceVariant)
                },
                null
            )
            setPadding(0, LayoutHelper.dp(14), 0, LayoutHelper.dp(14))
            setOnClickListener { onClick() }
        }
    }
}
