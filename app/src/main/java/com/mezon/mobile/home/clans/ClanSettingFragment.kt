package com.mezon.mobile.home.clans

import android.content.Context
import android.os.Bundle
import android.view.View
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
import com.mezon.mobile.home.profile.UserController
import com.mezon.mobile.network.CHANNEL_TYPE_CHANNEL
import com.mezon.mobile.ui.MezonToast
import com.mezon.mobile.ui.cells.AvatarView
import com.mezon.mobile.ui.cells.MezonIcon
import com.mezon.mobile.ui.cells.ToastOverlay

class ClanSettingFragment : BaseFragment() {

    companion object {
        private const val ARG_CLAN_ID = "clanId"

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
        }

        val headerTop = AndroidUtilities.statusBarHeight + LayoutHelper.dp(10f)
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(LayoutHelper.dp(20f), headerTop, LayoutHelper.dp(20f), LayoutHelper.dp(8f))
        }
        val back = ImageView(context).apply {
            setImageDrawable(MezonIcon.closeSmallBold.getDrawable(context))
            colorFilter = android.graphics.PorterDuffColorFilter(
                CreateClanRnUiTokens.closeIcon(themeColors),
                android.graphics.PorterDuff.Mode.SRC_IN
            )
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(0, LayoutHelper.dp(10f), 0, LayoutHelper.dp(10f))
            setOnClickListener { finishFragment() }
        }
        header.addView(back, LayoutHelper.createLinear(60, LayoutHelper.WRAP_CONTENT))

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

        header.addView(View(context), LayoutHelper.createLinear(60, 1))

        root.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

        val scroll = ClanSettingsUiHelpers.newMezonScrollRoot(context)
        scrollInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(LayoutHelper.dp(20f), 0, LayoutHelper.dp(20f), LayoutHelper.dp(24f))
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

        scrollInner.addView(buildLogoStrip(clan))

        val members = userClanController.getClanMembers(clanId)
        val roles = roleController.getRoles(clanId)
        val perm = ClanSettingsPermissionState.evaluateForClanSettings(userController, clanId, members, roles)

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

    private fun buildLogoStrip(clan: ClanEntity): LinearLayout {
        val ctx = scrollInner.context
        val outer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(0, LayoutHelper.dp(8f), 0, LayoutHelper.dp(24f))
        }
        outer.addView(AvatarView(ctx).apply {
            setSizeDp(72)
            setInfo(clan.clanId, clan.clanName)
            if (clan.logo.isNotEmpty()) setImageUrl(clan.logo)
        })

        outer.addView(TextView(ctx).apply {
            text = clan.clanName
            textSize = 14f
            setTextColor(CreateClanRnUiTokens.textStrong(themeColors))
            setPadding(0, LayoutHelper.dp(10f), 0, 0)
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
        return outer
    }

    private fun buildSettingsRows(ctx: Context, perm: ClanSettingsPermissionState): List<View> {
        val rows = ArrayList<View>()
        if (perm.isShowOverviewOption) {
            rows.add(navigationRow(ctx, MezonIcon.circleInformation, R.string.clan_settings_overview, R.string.menu_clan_overview_settings))
        }
        if (perm.isCanEditRole) {
            rows.add(navigationRow(ctx, MezonIcon.clipboardIcon, R.string.clan_settings_audit_log, R.string.menu_clan_audit_log))
        }
        if (perm.hasAdminPermission || perm.hasManageClanPermission) {
            rows.add(navigationRow(ctx, MezonIcon.gameControllerIcon, R.string.clan_settings_integrations, R.string.menu_clan_integrations))
        }
        rows.add(navigationRow(ctx, MezonIcon.faceIcon, R.string.clan_settings_emoji, R.string.menu_clan_emoji))
        rows.add(navigationRow(ctx, MezonIcon.sticker, R.string.clan_settings_sticker, R.string.menu_clan_sticker))
        rows.add(navigationRow(ctx, MezonIcon.voiceLowIcon, R.string.clan_settings_sound, R.string.menu_clan_sound))
        if (perm.hasManageClanPermission) {
            rows.add(navigationRow(ctx, MezonIcon.localCommunityIcon, R.string.clan_settings_enable_community, R.string.menu_clan_enable_community))
        }
        return rows
    }

    private fun buildUserRows(ctx: Context, perm: ClanSettingsPermissionState): List<View> {
        val rows = ArrayList<View>()
        rows.add(navigationRow(ctx, MezonIcon.groupIcon, R.string.clan_settings_members, R.string.menu_clan_members))
        if (perm.isCanEditRole) {
            rows.add(navigationRow(ctx, MezonIcon.shieldUserIcon, R.string.clan_settings_roles, R.string.menu_clan_roles))
        }
        rows.add(
            ClanSettingsUiHelpers.buildMezonChevronRow(
                ctx,
                themeColors,
                MezonIcon.linkIcon,
                getString(R.string.clan_settings_invites),
                null,
                Runnable { openInviteSheet() }
            )
        )
        return rows
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
