package com.mezon.mobile.util

import android.content.Context
import com.mezon.mobile.R

object AuditLogWire {
    const val ALL_ACTION = "ALL_ACTION_AUDIT"

    val ACTION_OPTIONS: List<String> = listOf(
        ALL_ACTION,
        "UPDATE_CLAN_ACTION_AUDIT",
        "CREATE_CHANNEL_ACTION_AUDIT",
        "UPDATE_CHANNEL_ACTION_AUDIT",
        "UPDATE_CHANNEL_PRIVATE_ACTION_AUDIT",
        "DELETE_CHANNE_ACTION_AUDIT",
        "CREATE_CHANNEL_PERMISSION_ACTION_AUDIT",
        "UPDATE_CHANNEL_PERMISSION_ACTION_AUDIT",
        "DELETE_CHANNEL_PERMISSION_ACTION_AUDIT",
        "KICK_MEMBER_ACTION_AUDIT",
        "PRUNE_MEMBER_ACTION_AUDIT",
        "BAN_MEMBER_ACTION_AUDIT",
        "UNBAN_MEMBER_ACTION_AUDIT",
        "UPDATE_MEMBER_ACTION_AUDIT",
        "UPDATE_ROLES_MEMBER_ACTION_AUDIT",
        "MOVE_MEMBER_ACTION_AUDIT",
        "DISCONNECT_MEMBER_ACTION_AUDIT",
        "ADD_BOT_ACTION_AUDIT",
        "CREATE_THREAD_ACTION_AUDIT",
        "UPDATE_THREAD_ACTION_AUDIT",
        "DELETE_THREAD_ACTION_AUDIT",
        "CREATE_ROLE_ACTION_AUDIT",
        "UPDATE_ROLE_ACTION_AUDIT",
        "DELETE_ROLE_ACTION_AUDIT",
        "CREATE_WEBHOOK_ACTION_AUDIT",
        "UPDATE_WEBHOOK_ACTION_AUDIT",
        "DELETE_WEBHOOK_ACTION_AUDIT",
        "CREATE_EMOJI_ACTION_AUDIT",
        "UPDATE_EMOJI_ACTION_AUDIT",
        "DELETE_EMOJI_ACTION_AUDIT",
        "CREATE_STICKER_ACTION_AUDIT",
        "UPDATE_STICKER_ACTION_AUDIT",
        "DELETE_STICKER_ACTION_AUDIT",
        "CREATE_EVENT_ACTION_AUDIT",
        "UPDATE_EVENT_ACTION_AUDIT",
        "DELETE_EVENT_ACTION_AUDIT",
        "CREATE_CANVAS_ACTION_AUDIT",
        "UPDATE_CANVAS_ACTION_AUDIT",
        "DELETE_CANVAS_ACTION_AUDIT",
        "CREATE_CATEGORY_ACTION_AUDIT",
        "UPDATE_CATEGORY_ACTION_AUDIT",
        "DELETE_CATEGORY_ACTION_AUDIT",
        "ADD_MEMBER_CHANNEL_ACTION_AUDIT",
        "REMOVE_MEMBER_CHANNEL_ACTION_AUDIT",
        "ADD_ROLE_CHANNEL_ACTION_AUDIT",
        "REMOVE_ROLE_CHANNEL_ACTION_AUDIT",
        "ADD_MEMBER_THREAD_ACTION_AUDIT",
        "REMOVE_MEMBER_THREAD_ACTION_AUDIT",
        "ADD_ROLE_THREAD_ACTION_AUDIT",
        "REMOVE_ROLE_THREAD_ACTION_AUDIT",
    )
}

enum class AuditLogMemberRoleChannelVerb {
    NONE,
    ADD,
    REMOVE,
}

fun auditLogMemberRoleChannelVerb(actionLog: String): AuditLogMemberRoleChannelVerb = when (actionLog) {
    "ADD_MEMBER_CHANNEL_ACTION_AUDIT",
    "ADD_ROLE_CHANNEL_ACTION_AUDIT",
    "ADD_MEMBER_THREAD_ACTION_AUDIT",
    "ADD_ROLE_THREAD_ACTION_AUDIT" -> AuditLogMemberRoleChannelVerb.ADD
    "REMOVE_MEMBER_CHANNEL_ACTION_AUDIT",
    "REMOVE_ROLE_CHANNEL_ACTION_AUDIT",
    "REMOVE_MEMBER_THREAD_ACTION_AUDIT",
    "REMOVE_ROLE_THREAD_ACTION_AUDIT" -> AuditLogMemberRoleChannelVerb.REMOVE
    else -> AuditLogMemberRoleChannelVerb.NONE
}

fun auditLogActionStringRes(wire: String): Int? = when (wire) {
    AuditLogWire.ALL_ACTION -> R.string.audit_log_filter_all_actions
    "UPDATE_CLAN_ACTION_AUDIT" -> R.string.audit_log_action_update_clan
    "CREATE_CHANNEL_ACTION_AUDIT" -> R.string.audit_log_action_create_channel
    "UPDATE_CHANNEL_ACTION_AUDIT" -> R.string.audit_log_action_update_channel
    "UPDATE_CHANNEL_PRIVATE_ACTION_AUDIT" -> R.string.audit_log_action_update_channel_private
    "DELETE_CHANNE_ACTION_AUDIT" -> R.string.audit_log_action_delete_channel
    "CREATE_CHANNEL_PERMISSION_ACTION_AUDIT" -> R.string.audit_log_action_create_channel_permission
    "UPDATE_CHANNEL_PERMISSION_ACTION_AUDIT" -> R.string.audit_log_action_update_channel_permission
    "DELETE_CHANNEL_PERMISSION_ACTION_AUDIT" -> R.string.audit_log_action_delete_channel_permission
    "KICK_MEMBER_ACTION_AUDIT" -> R.string.audit_log_action_kick_member
    "PRUNE_MEMBER_ACTION_AUDIT" -> R.string.audit_log_action_prune_member
    "BAN_MEMBER_ACTION_AUDIT" -> R.string.audit_log_action_ban_member
    "UNBAN_MEMBER_ACTION_AUDIT" -> R.string.audit_log_action_unban_member
    "UPDATE_MEMBER_ACTION_AUDIT" -> R.string.audit_log_action_update_member
    "UPDATE_ROLES_MEMBER_ACTION_AUDIT" -> R.string.audit_log_action_update_roles_member
    "MOVE_MEMBER_ACTION_AUDIT" -> R.string.audit_log_action_move_member
    "DISCONNECT_MEMBER_ACTION_AUDIT" -> R.string.audit_log_action_disconnect_member
    "ADD_BOT_ACTION_AUDIT" -> R.string.audit_log_action_add_bot
    "CREATE_THREAD_ACTION_AUDIT" -> R.string.audit_log_action_create_thread
    "UPDATE_THREAD_ACTION_AUDIT" -> R.string.audit_log_action_update_thread
    "DELETE_THREAD_ACTION_AUDIT" -> R.string.audit_log_action_delete_thread
    "CREATE_ROLE_ACTION_AUDIT" -> R.string.audit_log_action_create_role
    "UPDATE_ROLE_ACTION_AUDIT" -> R.string.audit_log_action_update_role
    "DELETE_ROLE_ACTION_AUDIT" -> R.string.audit_log_action_delete_role
    "CREATE_WEBHOOK_ACTION_AUDIT" -> R.string.audit_log_action_create_webhook
    "UPDATE_WEBHOOK_ACTION_AUDIT" -> R.string.audit_log_action_update_webhook
    "DELETE_WEBHOOK_ACTION_AUDIT" -> R.string.audit_log_action_delete_webhook
    "CREATE_EMOJI_ACTION_AUDIT" -> R.string.audit_log_action_create_emoji
    "UPDATE_EMOJI_ACTION_AUDIT" -> R.string.audit_log_action_update_emoji
    "DELETE_EMOJI_ACTION_AUDIT" -> R.string.audit_log_action_delete_emoji
    "CREATE_STICKER_ACTION_AUDIT" -> R.string.audit_log_action_create_sticker
    "UPDATE_STICKER_ACTION_AUDIT" -> R.string.audit_log_action_update_sticker
    "DELETE_STICKER_ACTION_AUDIT" -> R.string.audit_log_action_delete_sticker
    "CREATE_EVENT_ACTION_AUDIT" -> R.string.audit_log_action_create_event
    "UPDATE_EVENT_ACTION_AUDIT" -> R.string.audit_log_action_update_event
    "DELETE_EVENT_ACTION_AUDIT" -> R.string.audit_log_action_delete_event
    "CREATE_CANVAS_ACTION_AUDIT" -> R.string.audit_log_action_create_canvas
    "UPDATE_CANVAS_ACTION_AUDIT" -> R.string.audit_log_action_update_canvas
    "DELETE_CANVAS_ACTION_AUDIT" -> R.string.audit_log_action_delete_canvas
    "CREATE_CATEGORY_ACTION_AUDIT" -> R.string.audit_log_action_create_category
    "UPDATE_CATEGORY_ACTION_AUDIT" -> R.string.audit_log_action_update_category
    "DELETE_CATEGORY_ACTION_AUDIT" -> R.string.audit_log_action_delete_category
    "ADD_MEMBER_CHANNEL_ACTION_AUDIT" -> R.string.audit_log_action_add_member_channel
    "REMOVE_MEMBER_CHANNEL_ACTION_AUDIT" -> R.string.audit_log_action_remove_member_channel
    "ADD_ROLE_CHANNEL_ACTION_AUDIT" -> R.string.audit_log_action_add_role_channel
    "REMOVE_ROLE_CHANNEL_ACTION_AUDIT" -> R.string.audit_log_action_remove_role_channel
    "ADD_MEMBER_THREAD_ACTION_AUDIT" -> R.string.audit_log_action_add_member_thread
    "REMOVE_MEMBER_THREAD_ACTION_AUDIT" -> R.string.audit_log_action_remove_member_thread
    "ADD_ROLE_THREAD_ACTION_AUDIT" -> R.string.audit_log_action_add_role_thread
    "REMOVE_ROLE_THREAD_ACTION_AUDIT" -> R.string.audit_log_action_remove_role_thread
    else -> null
}

fun auditLogActionDisplayLabel(context: Context, wire: String): String {
    val res = auditLogActionStringRes(wire) ?: return wire
    return context.getString(res)
}
