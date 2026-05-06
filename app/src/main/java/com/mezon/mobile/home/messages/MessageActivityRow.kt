package com.mezon.mobile.home.messages

data class MessageActivityRow(
    val userId: Long,
    val displayName: String,
    val username: String,
    val avatarUrl: String,
    val activityText: String
)
