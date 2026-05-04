package com.mezon.mobile.home.clans.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.mezon.mobile.R
import com.mezon.mobile.core.AndroidUtilities
import com.mezon.mobile.core.BaseFragment
import com.mezon.mobile.core.LayoutHelper
import com.mezon.mobile.core.NotificationCenter
import com.mezon.mobile.di.FragmentEntryPoint
import com.mezon.mobile.home.UserClanController
import com.mezon.mobile.home.clans.ChannelController
import com.mezon.mobile.home.clans.ClanEntity
import com.mezon.mobile.home.clans.ClansController
import com.mezon.mobile.home.clans.ClanSubSettingPlaceholderFragment
import com.mezon.mobile.home.clans.CreateClanRnUiTokens
import com.mezon.mobile.home.clans.RoleController
import com.mezon.mobile.home.profile.UserController
import com.mezon.mobile.network.CHANNEL_TYPE_CHANNEL
import com.mezon.mobile.ui.MezonToast
import com.mezon.mobile.ui.cells.AvatarView
import com.mezon.mobile.ui.cells.MezonIcon
import com.mezon.mobile.ui.cells.ToastOverlay

class ClanSettingFragment : BaseFragment() {

    companion object {
        private const val ARG_CLAN_ID = "clanId"
        private const val REQUEST_CODE_PICK_CLAN_LOGO = 2010

        fun newInstance(clanId: Long): ClanSettingFragment = ClanSettingFragment().apply {
            arguments = Bundle().apply { putLong(ARG_CLAN_ID, clanId) }
        }
    }

    private var clanId = 0L
    private lateinit var clansController: ClansController
    private lateinit var channelController: ChannelController
    private lateinit var userClanController: UserClanController
    private lateinit var roleController: RoleController
    private lateinit var userController: UserController

    private lateinit var scrollInner: LinearLayout

    override fun onInject(entryPoint: FragmentEntryPoint) {
        clansController = entryPoint.clansController()
        channelController = entryPoint.channelController()
        userClanController = entryPoint.userClanController()
        roleController = entryPoint.roleController()
        userController = entryPoint.userController()
    }

    override fun onFragmentCreate(): Boolean {
        super.onFragmentCreate()
        clanId = arguments?.getLong(ARG_CLAN_ID) ?: 0L
        if (clanId != 0L) {
            roleController.loadRolesForClan(clanId, force = true)
            userClanController.loadClanMembers(clanId)
        }
        observe(NotificationCenter.clanMembersDidLoad) { _, _, args ->
            if (isPaused) return@observe
            val id = args.firstOrNull() as? Long ?: return@observe
            if (id == clanId) refreshMenu()
        }
        observe(NotificationCenter.clanRolesDidLoad) { _, _, args ->
            if (isPaused) return@observe
            val id = args.firstOrNull() as? Long ?: return@observe
            if (id == clanId) refreshMenu()
        }
        observe(NotificationCenter.clanInfoUpdated) { _, _, args ->
            if (isPaused) return@observe
            val id = args.firstOrNull() as? Long ?: return@observe
            if (id == clanId) refreshMenu()
        }
        return true
    }

    override fun onBecomeFullyVisible() {
        super.onBecomeFullyVisible()
        if (clanId != 0L) {
            roleController.loadRolesForClan(clanId, force = true)
            userClanController.loadClanMembers(clanId)
        }
        refreshMenu()
    }

    override fun createView(context: Context): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = CreateClanRnUiTokens.clanSettingDiagonalGradient(themeColors)
            clipChildren = false
        }

        val headerTop = AndroidUtilities.statusBarHeight + LayoutHelper.dp(10f)
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(LayoutHelper.dp(20f), headerTop, LayoutHelper.dp(20f), LayoutHelper.dp(8f))
        }
        val closeTargetDp = 44
        val closeIconDp = 28
        val closeWrap = FrameLayout(context).apply {
            isClickable = true
            isFocusable = true
            clipChildren = false
            val rippleMask = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFFFFFFF.toInt())
            }
            background = RippleDrawable(
                ColorStateList.valueOf(CreateClanRnUiTokens.menuText(themeColors) and 0x1AFFFFFF),
                ColorDrawable(Color.TRANSPARENT),
                rippleMask
            )
            setOnClickListener { finishFragment() }
        }
        closeWrap.addView(
            ImageView(context).apply {
                setImageDrawable(MezonIcon.closeIcon.getDrawable(context, CreateClanRnUiTokens.closeIcon(themeColors)))
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
            },
            FrameLayout.LayoutParams(LayoutHelper.dp(closeIconDp), LayoutHelper.dp(closeIconDp), Gravity.CENTER)
        )
        header.addView(
            closeWrap,
            LayoutHelper.createLinear(closeTargetDp, closeTargetDp, 0f, Gravity.CENTER_VERTICAL)
        )

        header.addView(
            TextView(context).apply {
                text = getString(R.string.clan_settings_title)
                textSize = 18f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(themeColors.colorText)
                gravity = android.view.Gravity.CENTER
            },
            LinearLayout.LayoutParams(0, LayoutHelper.WRAP_CONTENT, 1f)
        )

        header.addView(
            View(context),
            LayoutHelper.createLinear(closeTargetDp, closeTargetDp, 0f, Gravity.CENTER_VERTICAL)
        )

        root.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

        val scroll = ClanSettingsUiHelpers.newMezonScrollRoot(context)
        scrollInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(LayoutHelper.dp(20f), 0, LayoutHelper.dp(20f), LayoutHelper.dp(24f))
            clipChildren = false
            clipToPadding = false
        }
        scroll.addView(scrollInner, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        root.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f))

        refreshMenu()
        fragmentView = root
        return root
    }

    private fun refreshMenu() {
        if (!::scrollInner.isInitialized) return
        scrollInner.removeAllViews()
        val clan = clansController.clans.value.firstOrNull { it.clanId == clanId } ?: return

        val members = userClanController.getClanMembers(clanId)
        val roles = roleController.getRoles(clanId)
        val perm = ClanSettingsPermissionState.evaluateForClanSettings(
            userController,
            clanId,
            members,
            roles,
            clan.creatorId,
        )

        scrollInner.addView(
            buildLogoStrip(clan, perm.isShowOverviewOption),
            LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                0f,
                Gravity.CENTER_HORIZONTAL,
            )
        )

        val ctx = scrollInner.context
        scrollInner.addView(
            ClanSettingsUiHelpers.buildMezonSection(
                ctx,
                themeColors,
                getString(R.string.clan_settings_section_settings),
                buildSettingsRows(ctx, perm)
            ),
            LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, android.view.Gravity.NO_GRAVITY, 0f, 18f, 0f, 0f)
        )

        scrollInner.addView(
            ClanSettingsUiHelpers.buildMezonSection(
                ctx,
                themeColors,
                getString(R.string.clan_settings_section_user_management),
                buildUserRows(ctx, perm)
            ),
            LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, android.view.Gravity.NO_GRAVITY, 0f, 18f, 0f, 0f)
        )
    }

    private fun buildLogoStrip(clan: ClanEntity, canEditClanLogo: Boolean): LinearLayout {
        val ctx = scrollInner.context
        val outer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            setPadding(0, LayoutHelper.dp(40f), 0, LayoutHelper.dp(40f))
        }
        val avatarDp = 60
        val avPx = LayoutHelper.dp(avatarDp)
        val padding = 12
        val wrapperSize = avatarDp + padding
        val removeInset = LayoutHelper.dp(6f)
        val logoWrap = FrameLayout(ctx).apply {
            clipChildren = false
            clipToPadding = false
        }
        val innerHolder = FrameLayout(ctx).apply {
            clipChildren = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = LayoutHelper.dpf(20f)
                setColor(Color.TRANSPARENT)
                setStroke(LayoutHelper.dp(1), themeColors.borderDim)
            }
        }
        val avatar = AvatarView(ctx).apply {
            setSizeDp(avatarDp)
            setRoundRadius(20f)
            setInfo(clan.clanId, clan.clanName)
            if (clan.logo.isNotEmpty()) setImageUrl(clan.logo)
        }
        innerHolder.addView(
            avatar,
            FrameLayout.LayoutParams(avPx, avPx, Gravity.CENTER)
        )
        logoWrap.addView(
            innerHolder,
            FrameLayout.LayoutParams(avPx, avPx, Gravity.BOTTOM or Gravity.START)
        )
        if (canEditClanLogo) {
            avatar.isClickable = true
            avatar.setOnClickListener { openClanLogoPicker() }
            if (clan.logo.isNotEmpty()) {
                val removeSz = LayoutHelper.dp(24)
                val removeBtn = ImageView(ctx).apply {
                    setImageDrawable(MezonIcon.circleXIcon.getDrawable(context, themeColors.error))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    isClickable = true
                    setOnClickListener {
                        removeClanLogo()
                    }
                    elevation = LayoutHelper.dpf(1f)
                }
                logoWrap.addView(
                    removeBtn,
                    FrameLayout.LayoutParams(removeSz, removeSz).apply {
                        gravity = Gravity.TOP or Gravity.END
                        topMargin = removeInset
                        marginEnd = removeInset
                    }
                )
            }
        }
        outer.addView(
            logoWrap,
            LayoutHelper.createLinear(
                wrapperSize,
                wrapperSize,
                0f,
                Gravity.CENTER_HORIZONTAL,
            )
        )
        outer.addView(
            TextView(ctx).apply {
                text = clan.clanName
                textSize = 14f
                setTextColor(CreateClanRnUiTokens.textStrong(themeColors))
                setPadding(0, LayoutHelper.dp(10f), 0, 0)
                gravity = Gravity.CENTER_HORIZONTAL
            },
            LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                0f,
                Gravity.CENTER_HORIZONTAL,
            )
        )
        return outer
    }

    private fun openClanLogoPicker() {
        val pick = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
        val getContent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        val chooser = Intent.createChooser(getContent, getString(R.string.clan_settings_change_logo)).apply {
            putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(pick))
        }
        startActivityForResult(chooser, REQUEST_CODE_PICK_CLAN_LOGO)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK || requestCode != REQUEST_CODE_PICK_CLAN_LOGO) return
        // TODO: Persist picked clan logo
        // val uri = resolvePickedImageUri(data) ?: return
    }

    // TODO: Enable when logo upload is wired.
    private fun handleClanLogoPicked(uri: Uri) {
       
    }
    
    // TODO: Remove clan logo
    private fun removeClanLogo() {
      
    }

    private fun buildSettingsRows(ctx: Context, perm: ClanSettingsPermissionState): List<View> {
        return ClanSetting.settingsSectionRows(perm).map { menuRowToView(ctx, it) }
    }

    private fun buildUserRows(ctx: Context, perm: ClanSettingsPermissionState): List<View> {
        return ClanSetting.userManagementSectionRows(perm).map { menuRowToView(ctx, it) }
    }

    private fun menuRowToView(ctx: Context, row: ClanSetting.MenuRow): View {
        return when (row) {
            is ClanSetting.MenuRow.Navigate ->
                navigationRow(ctx, row.icon, row.labelRes, row.subScreenTitleRes)
            ClanSetting.MenuRow.InvitePeople ->
                ClanSettingsUiHelpers.buildMezonChevronRow(
                    ctx,
                    themeColors,
                    MezonIcon.linkIcon,
                    getString(R.string.clan_settings_invites),
                    null,
                    Runnable { openInviteSheet() }
                )
        }
    }

    private fun navigationRow(ctx: Context, icon: MezonIcon, labelRes: Int, placeholderTitleRes: Int): View {
        return ClanSettingsUiHelpers.buildMezonChevronRow(
            ctx,
            themeColors,
            icon,
            getString(labelRes),
            null,
            Runnable { presentFragment(ClanSubSettingPlaceholderFragment.newInstance(placeholderTitleRes, clanId)) }
        )
    }

    private fun openInviteSheet() {
        val ctx = getContext() ?: return
        val ch = channelController.getChannels(clanId).firstOrNull { it.type == CHANNEL_TYPE_CHANNEL }
        if (ch == null) {
            MezonToast.show(this, ToastOverlay.ToastType.INFO, getString(R.string.clan_invite_need_channel))
            return
        }
        val sheet = com.mezon.mobile.home.chat.channelinfo.InviteMembersBottomSheet(
            ctx,
            clanId,
            ch.channelId,
            ch.channelLabel
        )
        sheet.setDrawNavigationBar(true)
        sheet.show()
    }
}
