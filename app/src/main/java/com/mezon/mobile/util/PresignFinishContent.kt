package com.mezon.mobile.util

import org.json.JSONArray
import org.json.JSONObject

object PresignFinishContent {
    const val FIELD_KEY = "presign_finish"

    fun parseKeys(content: String): List<String>? {
        if (content.isBlank()) return null
        return try {
            val json = JSONObject(content)
            if (!json.has(FIELD_KEY)) return null
            val arr = json.optJSONArray(FIELD_KEY) ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                arr.optString(i).takeIf { it.isNotEmpty() }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun presignKey(cdnUrl: String): String {
        val trimmed = cdnUrl.trim()
        if (trimmed.isEmpty()) return ""
        val noQuery = trimmed.substringBefore('?')
        val last = noQuery.substringAfterLast('/')
        if (last.isEmpty()) return ""
        val dot = last.lastIndexOf('.')
        val withoutExt = if (dot > 0) last.substring(0, dot) else last
        return withoutExt.ifEmpty { last }
    }

    fun isAttachmentReady(url: String, presignFinish: List<String>?): Boolean {
        val keys = presignFinish ?: return true
        val key = presignKey(url)
        if (key.isEmpty()) return false
        return keys.contains(key)
    }

    fun injectPresignFinish(content: String, keys: List<String>): String {
        return try {
            val json = if (content.isNotBlank()) JSONObject(content) else JSONObject()
            val arr = JSONArray()
            keys.forEach { arr.put(it) }
            json.put(FIELD_KEY, arr)
            json.toString()
        } catch (_: Exception) {
            content
        }
    }

    fun injectEmptyPresignFinish(content: String): String = injectPresignFinish(content, emptyList())

    fun hasPresignFinishField(content: String): Boolean {
        if (content.isBlank()) return false
        return try {
            JSONObject(content).has(FIELD_KEY)
        } catch (_: Exception) {
            false
        }
    }

    fun contentBaseWithoutPresign(content: String): String {
        if (content.isBlank()) return content
        return try {
            val json = JSONObject(content)
            json.remove(FIELD_KEY)
            json.toString()
        } catch (_: Exception) {
            content
        }
    }

    fun mergePresignFinishContent(local: String, server: String): String {
        val localKeys = parseKeys(local) ?: emptyList()
        val serverKeys = parseKeys(server) ?: emptyList()
        if (localKeys.isEmpty() && serverKeys.isEmpty()) {
            return server.ifBlank { local }
        }
        val mergedKeys = if (serverKeys.size >= localKeys.size) {
            serverKeys
        } else {
            val keys = localKeys.toMutableList()
            serverKeys.forEach { key ->
                if (!keys.contains(key)) keys.add(key)
            }
            keys
        }
        val base = if (server.isNotBlank()) {
            contentBaseWithoutPresign(server)
        } else {
            contentBaseWithoutPresign(local)
        }
        return injectPresignFinish(base, mergedKeys)
    }

    fun isPresignFinishOnlyChange(newContent: String, oldContent: String): Boolean {
        if (newContent.isBlank() || oldContent.isBlank()) return false
        if (!hasPresignFinishField(newContent)) return false
        return try {
            val newJson = JSONObject(newContent)
            val oldJson = JSONObject(oldContent)
            newJson.remove(FIELD_KEY)
            oldJson.remove(FIELD_KEY)
            newJson.toString() == oldJson.toString()
        } catch (_: Exception) {
            false
        }
    }
}
