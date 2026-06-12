package com.mezon.mobile.home.clans

import com.mezon.mobile.R
import com.mezon.mobile.core.NotificationCenter
import com.mezon.mobile.di.FragmentEntryPoint
import com.mezon.mobile.ui.MezonToast
import com.mezon.mobile.ui.cells.ToastOverlay
import com.mezon.mobile.ui.shared.ImageTransformFragment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ClanEventCoverTransformFragment : ImageTransformFragment() {

    companion object {
        private const val EXPORT_W = 1280
        private const val EXPORT_H = 720
        private const val MAX_MB = 1

        fun newInstance(uriString: String): ClanEventCoverTransformFragment =
            ClanEventCoverTransformFragment().apply {
                arguments = android.os.Bundle().apply {
                    putString(ARG_URI, uriString)
                }
            }
    }

    private lateinit var clanEventController: ClanEventController
    private lateinit var ioDispatcher: CoroutineDispatcher
    private lateinit var mainDispatcher: CoroutineDispatcher

    override fun onInject(entryPoint: FragmentEntryPoint) {
        clanEventController = entryPoint.clanEventController()
        ioDispatcher = entryPoint.ioDispatcher()
        mainDispatcher = entryPoint.mainDispatcher()
    }

    override fun cropAspectWidth(): Float = 16f

    override fun cropAspectHeight(): Float = 9f

    override fun exportWidthPx(): Int = EXPORT_W

    override fun exportHeightPx(): Int = EXPORT_H

    override fun maxSourceBytes(): Long = ClanEventCreateUi.MAX_LOGO_SIZE_BYTES.toLong()

    override fun maxExportBytes(): Int = ClanEventCreateUi.MAX_LOGO_SIZE_BYTES

    override fun cacheFilePrefix(): String = "event_cover"

    override fun onSourceTooLarge() {
        MezonToast.show(
            this,
            ToastOverlay.ToastType.ERROR,
            getString(R.string.clan_image_too_large, MAX_MB),
        )
        finishFragment()
    }

    override fun onDecodeFailed() {
        MezonToast.show(
            this,
            ToastOverlay.ToastType.ERROR,
            getString(R.string.event_creator_cover_upload_failed),
        )
        finishFragment()
    }

    override fun onExportTooLarge() {
        MezonToast.show(
            this,
            ToastOverlay.ToastType.ERROR,
            getString(R.string.clan_image_too_large, MAX_MB),
        )
    }

    override fun onExportReady(jpegFile: File, onWorkFinished: () -> Unit) {
        fragmentScope.launch(mainDispatcher) {
            runCatching {
                val bytes = withContext(ioDispatcher) { jpegFile.readBytes() }
                runCatching { jpegFile.delete() }
                val url = clanEventController.uploadEventCoverJpeg(bytes)
                onWorkFinished()
                notificationCenter.postNotificationNameOnUIThread(
                    NotificationCenter.eventCoverCropped,
                    url,
                )
                finishFragment()
            }.onFailure {
                runCatching { jpegFile.delete() }
                onWorkFinished()
                MezonToast.show(
                    this@ClanEventCoverTransformFragment,
                    ToastOverlay.ToastType.ERROR,
                    getString(R.string.event_creator_cover_upload_failed),
                )
                finishFragment()
            }
        }
    }
}
