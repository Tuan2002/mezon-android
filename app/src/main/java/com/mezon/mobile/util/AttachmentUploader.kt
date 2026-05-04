package com.mezon.mobile.util

import android.content.ContentResolver
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object AttachmentUploader {

    const val MAX_UPLOAD_BYTES: Long = 100L * 1024 * 1024

    fun isOverSizeLimit(sizeBytes: Long): Boolean = sizeBytes > MAX_UPLOAD_BYTES

    fun readUriBytesSafely(
        contentResolver: ContentResolver,
        uri: Uri,
        mimeType: String,
        cacheDir: File
    ): ByteArray? {
        val tmpInput: InputStream = contentResolver.openInputStream(uri) ?: return null
        val tmpFile = File(cacheDir, "upload_" + System.nanoTime())
        try {
            tmpInput.use { input ->
                FileOutputStream(tmpFile).use { output ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        total += n
                        if (total > MAX_UPLOAD_BYTES) {
                            tmpFile.delete()
                            return null
                        }
                        output.write(buf, 0, n)
                    }
                }
            }

            if (mimeType.equals("image/jpeg", true) ||
                mimeType.equals("image/jpg", true) ||
                mimeType.equals("image/heic", true) ||
                mimeType.equals("image/heif", true)
            ) {
                stripExifSensitive(tmpFile)
            }

            return tmpFile.readBytes()
        } finally {
            runCatching { tmpFile.delete() }
        }
    }

    private fun stripExifSensitive(file: File) {
        runCatching {
            val exif = ExifInterface(file.absolutePath)
            EXIF_SENSITIVE_TAGS.forEach { tag -> exif.setAttribute(tag, null) }
            exif.saveAttributes()
        }
    }

    private val EXIF_SENSITIVE_TAGS = arrayOf(
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_PROCESSING_METHOD,
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL
    )
}
