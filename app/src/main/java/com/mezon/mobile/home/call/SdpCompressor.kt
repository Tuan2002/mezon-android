package com.mezon.mobile.home.call

import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import org.json.JSONObject

object SdpCompressor {

    fun compress(input: String): String {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { gzip ->
            gzip.write(input.toByteArray(Charsets.UTF_8))
        }
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }

    fun decompress(compressed: String): String {
        val bytes = Base64.decode(compressed, Base64.NO_WRAP)
        val bis = ByteArrayInputStream(bytes)
        return GZIPInputStream(bis).use { gzip ->
            gzip.bufferedReader(Charsets.UTF_8).readText()
        }
    }

    fun canonicalizeWebRtcSdp(description: String): String {
        val t = description.trim()
        if (t.length < 3 || !t.startsWith("v=")) return description
        val lf = t.replace("\r\n", "\n").replace('\r', '\n')
        val joined = lf.split('\n').joinToString("\r\n")
        return if (joined.endsWith("\r\n")) joined else "$joined\r\n"
    }

    fun sdpPlainTextFromNegotiationJson(parsed: JSONObject): String? {
        val direct = parsed.optString("sdp", "").trim()
        if (direct.isNotEmpty()) {
            expandNegotiationBlobToPlainSdp(direct)?.let { return it }
        }
        val offer = parsed.optString("offer", "").trim()
        if (offer.isEmpty() || offer == "CANCEL_CALL") return null
        return expandNegotiationBlobToPlainSdp(offer)
    }

    private fun expandNegotiationBlobToPlainSdp(blob: String): String? {
        var s = blob.trim()
        if (s.isEmpty()) return null
        repeat(8) {
            if (s.startsWith("v=")) return canonicalizeWebRtcSdp(s)
            try {
                val afterZip = decompress(s).trim()
                if (afterZip != s) {
                    s = afterZip
                    return@repeat
                }
            } catch (_: Exception) {}
            val start = s.trimStart()
            if (start.startsWith("{")) {
                try {
                    val jo = JSONObject(s)
                    val innerSdp = jo.optString("sdp", "").trim()
                    if (innerSdp.isNotEmpty()) {
                        s = innerSdp
                        return@repeat
                    }
                    val innerOffer = jo.optString("offer", "").trim()
                    if (innerOffer.isNotEmpty() && innerOffer != "CANCEL_CALL") {
                        s = innerOffer
                        return@repeat
                    }
                } catch (_: Exception) {
                    return null
                }
                return null
            }
            return null
        }
        return if (s.startsWith("v=")) canonicalizeWebRtcSdp(s) else null
    }
}
