package com.mezon.mobile.home.call

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.mezon.mobile.R
import com.mezon.mobile.core.AlertDialog

object CallPermissionUi {

    fun permissionGrantedInResult(
        permissions: Array<out String>,
        grantResults: IntArray,
        permission: String
    ): Boolean {
        for (i in permissions.indices) {
            if (permissions[i] == permission &&
                grantResults.getOrNull(i) == PackageManager.PERMISSION_GRANTED) {
                return true
            }
        }
        return false
    }

    fun startCameraRequestIfNeededToEnableVideo(
        activity: Activity,
        videoEnabled: Boolean,
        requestCode: Int,
        markPendingRequest: () -> Unit
    ): Boolean {
        if (videoEnabled) return false
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED) {
            return false
        }
        markPendingRequest()
        activity.requestPermissions(arrayOf(Manifest.permission.CAMERA), requestCode)
        return true
    }

    fun startMicRequestIfNeededToUnmute(
        activity: Activity,
        audioEnabled: Boolean,
        requestCode: Int,
        markPendingRequest: () -> Unit
    ): Boolean {
        if (audioEnabled) return false
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED) {
            return false
        }
        markPendingRequest()
        activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), requestCode)
        return true
    }

    fun completePendingToggleAfterSinglePermissionResult(
        wasPending: Boolean,
        clearPending: () -> Unit,
        permissions: Array<out String>,
        grantResults: IntArray,
        permission: String,
        activity: Activity,
        deniedMessageRes: Int,
        onDeniedToast: (String) -> Unit,
        onGranted: () -> Unit
    ) {
        if (!wasPending) return
        clearPending()
        if (permissionGrantedInResult(permissions, grantResults, permission)) {
            onGranted()
            return
        }
        onDeniedToast(activity.getString(deniedMessageRes))
        showMicOrCameraDeniedFeedback(activity, permission, deniedMessageRes)
    }

    fun permissionResultMessageForCall(
        permissions: Array<out String>,
        grantResults: IntArray,
        requestIncludedCamera: Boolean
    ): Int? {
        var audioRequested = false
        var cameraRequested = false
        var audioOk = true
        var cameraOk = true
        for (i in permissions.indices) {
            val grant = grantResults.getOrNull(i) ?: continue
            when (permissions[i]) {
                Manifest.permission.RECORD_AUDIO -> {
                    audioRequested = true
                    audioOk = grant == PackageManager.PERMISSION_GRANTED
                }
                Manifest.permission.CAMERA -> {
                    cameraRequested = true
                    cameraOk = grant == PackageManager.PERMISSION_GRANTED
                }
            }
        }
        if ((!audioRequested || audioOk) && (!cameraRequested || cameraOk)) return null
        return when {
            requestIncludedCamera &&
                audioRequested &&
                cameraRequested &&
                (!audioOk || !cameraOk) ->
                R.string.permission_no_camera_mic_video
            !audioOk ->
                R.string.permission_no_audio
            requestIncludedCamera && cameraRequested && !cameraOk ->
                R.string.permission_no_camera
            else ->
                R.string.permission_no_audio
        }
    }

    fun showMicOrCameraDeniedFeedback(activity: Activity, permission: String, messageRes: Int) {
        val denied = ContextCompat.checkSelfPermission(activity, permission) !=
            PackageManager.PERMISSION_GRANTED
        if (!denied) return
        if (!activity.shouldShowRequestPermissionRationale(permission)) {
            showOpenAppSettingsPrompt(activity, messageRes)
        }
    }

    fun showOpenAppSettingsPrompt(activity: Activity, messageRes: Int) {
        AlertDialog.Builder(activity)
            .setMessage(activity.getString(messageRes))
            .setPositiveButton(activity.getString(R.string.permission_open_settings)) { _, _ ->
                runCatching {
                    activity.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", activity.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                }
            }
            .setNegativeButton(activity.getString(R.string.permission_not_now), null)
            .show()
    }
}
