package com.mezon.mobile.home.clans

import com.mezon.mobile.home.ClanMember
import com.mezon.mobile.home.profile.UserController

data class ClanSettingsPermissionState(
    val hasAdminPermission: Boolean,
    val hasManageClanPermission: Boolean,
    val isClanOwner: Boolean
) {
    val isShowOverviewOption: Boolean
        get() = hasAdminPermission || isClanOwner

    val isCanEditRole: Boolean
        get() = isShowOverviewOption || hasManageClanPermission

    companion object {
        private const val SLUG_ADMIN = "administrator"
        private const val SLUG_MANAGE_CLAN = "manage-clan"
        private const val SLUG_CLAN_OWNER = "clan-owner"

        fun evaluateForClanSettings(
            userController: UserController,
            clanId: Long,
            members: List<ClanMember>,
            roles: List<ClanRole>
        ): ClanSettingsPermissionState {
            if (clanId <= 0L) {
                return ClanSettingsPermissionState(false, false, false)
            }
            val userId = userController.userId
            val self = members.firstOrNull { it.userId == userId } ?: return ClanSettingsPermissionState(false, false, false)
            val rolesById = roles.associateBy { it.roleId }
            val slugsFromPermissions = HashSet<String>()
            val roleSlugs = HashSet<String>()
            for (rid in self.roleIds) {
                val role = rolesById[rid] ?: continue
                if (role.slug.isNotEmpty()) roleSlugs.add(role.slug)
                for (p in role.permissionSlugs) {
                    slugsFromPermissions.add(p)
                }
            }
            val ownerBySlug =
                SLUG_CLAN_OWNER in roleSlugs || SLUG_CLAN_OWNER in slugsFromPermissions
            val admin = SLUG_ADMIN in slugsFromPermissions
            val manage = SLUG_MANAGE_CLAN in slugsFromPermissions
            return ClanSettingsPermissionState(
                hasAdminPermission = admin,
                hasManageClanPermission = manage,
                isClanOwner = ownerBySlug
            )
        }
    }
}
