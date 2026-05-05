package com.mezon.mobile.home.chat.poll

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

object PollVotePersistence {

    private const val PREFS_NAME = "mezon_poll_my_votes"
    private const val KEY_MAP = "by_message_id_json"

    private val myAnswersByMessageId = ConcurrentHashMap<Long, List<Int>>()
    @Volatile private var prefs: SharedPreferences? = null

    fun attach(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadFromDisk()
        }
    }

    fun remember(messageId: Long, indices: List<Int>) {
        val sortedDistinct = indices.distinct().sorted()
        synchronized(this) {
            if (sortedDistinct.isEmpty()) myAnswersByMessageId.remove(messageId)
            else myAnswersByMessageId[messageId] = sortedDistinct
            persistToDiskLocked()
        }
    }

    fun peek(messageId: Long): List<Int>? = myAnswersByMessageId[messageId]

    fun clearAll() {
        synchronized(this) {
            myAnswersByMessageId.clear()
            prefs?.edit()?.remove(KEY_MAP)?.apply()
        }
    }

    private fun loadFromDisk() {
        val p = prefs ?: return
        val raw = p.getString(KEY_MAP, null) ?: return
        try {
            val o = JSONObject(raw)
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val mid = k.toLongOrNull() ?: continue
                val arr = o.optJSONArray(k) ?: continue
                val out = ArrayList<Int>(arr.length())
                for (i in 0 until arr.length()) out.add(arr.optInt(i, -1))
                out.removeAll { it < 0 }
                if (out.isNotEmpty()) myAnswersByMessageId[mid] = out.distinct().sorted()
            }
        } catch (_: Exception) {
        }
    }

    private fun persistToDiskLocked() {
        val p = prefs ?: return
        try {
            val o = JSONObject()
            for ((id, indices) in myAnswersByMessageId) {
                val arr = JSONArray()
                for (ix in indices) arr.put(ix)
                o.put(id.toString(), arr)
            }
            p.edit().putString(KEY_MAP, o.toString()).apply()
        } catch (_: Exception) {
        }
    }
}
