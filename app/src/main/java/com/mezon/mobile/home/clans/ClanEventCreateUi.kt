package com.mezon.mobile.home.clans

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.StringRes
import com.mezon.mobile.R
import com.mezon.mobile.core.LayoutHelper
import com.mezon.mobile.core.ThemeColors
import com.mezon.mobile.home.chat.MezonImageLoader
import com.mezon.mobile.util.createImgproxyUrl
import com.mezon.mobile.ui.cells.ActionBarView
import com.mezon.mobile.ui.cells.MezonIcon
import com.mezon.mobile.ui.cells.RadioCell
import com.mezon.mobile.util.DateTimeUtil
import java.util.Calendar
import java.util.Locale

object ClanEventCreateArgs {
    const val ARG_CLAN_ID = "clanId"
    const val ARG_EVENT_ID = "eventId"
    const val ARG_OPTION = "option"
    const val ARG_CHANNEL_VOICE_ID = "channelVoiceId"
    const val ARG_ADDRESS = "address"
    const val ARG_CHANNEL_ID = "channelId"
    const val ARG_IS_PRIVATE = "isPrivate"
    const val ARG_TITLE = "title"
    const val ARG_DESCRIPTION = "description"
    const val ARG_START_TIME_SECONDS = "startTimeSeconds"
    const val ARG_END_TIME_SECONDS = "endTimeSeconds"
    const val ARG_REPEAT_TYPE = "repeatType"
    const val ARG_LOGO_URL = "logoUrl"

    fun baseBundle(
        clanId: Long,
        option: Int,
        channelVoiceId: Long,
        address: String,
        channelId: Long,
        isPrivate: Boolean,
    ) = android.os.Bundle().apply {
        putLong(ARG_CLAN_ID, clanId)
        putInt(ARG_OPTION, option)
        putLong(ARG_CHANNEL_VOICE_ID, channelVoiceId)
        putString(ARG_ADDRESS, address)
        putLong(ARG_CHANNEL_ID, channelId)
        putBoolean(ARG_IS_PRIVATE, isPrivate)
    }

    fun readDraft(bundle: android.os.Bundle?): CreateEventDraft {
        val b = bundle ?: return CreateEventDraft()
        return CreateEventDraft(
            option = b.getInt(ARG_OPTION, 0),
            channelVoiceId = b.getLong(ARG_CHANNEL_VOICE_ID, 0L),
            address = b.getString(ARG_ADDRESS).orEmpty(),
            channelId = b.getLong(ARG_CHANNEL_ID, 0L),
            isPrivate = b.getBoolean(ARG_IS_PRIVATE, false),
            title = b.getString(ARG_TITLE).orEmpty(),
            description = b.getString(ARG_DESCRIPTION).orEmpty(),
            startTimeSeconds = b.getInt(ARG_START_TIME_SECONDS, 0),
            endTimeSeconds = b.getInt(ARG_END_TIME_SECONDS, 0),
            repeatType = b.getInt(ARG_REPEAT_TYPE, ClanEventRepeatType.DOES_NOT_REPEAT),
            logoUrl = b.getString(ARG_LOGO_URL).orEmpty(),
        )
    }

    fun clanId(bundle: android.os.Bundle?): Long = bundle?.getLong(ARG_CLAN_ID, 0L) ?: 0L

    fun eventId(bundle: android.os.Bundle?): Long = bundle?.getLong(ARG_EVENT_ID, 0L) ?: 0L
}

object ClanEventCreateUi {

    private const val MAX_LOGO_BYTES = 1 * 1024 * 1024
    const val EVENT_THUMB_SIZE_DP = 56
    const val EVENT_THUMB_CORNER_DP = 8f
    const val EVENT_BANNER_CORNER_DP = 12f

    fun buildEventLogoThumbnail(
        context: Context,
        theme: ThemeColors,
        logoUrl: String,
        onLoadToken: (MezonImageLoader.Cancellable) -> Unit = {},
    ): FrameLayout {
        val corner = LayoutHelper.dpf(EVENT_THUMB_CORNER_DP)
        val size = LayoutHelper.dp(EVENT_THUMB_SIZE_DP)
        val logoWrap = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = corner
                setColor(theme.tertiary)
            }
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
        }
        val logoView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        logoWrap.addView(logoView, FrameLayout.LayoutParams(size, size))
        val trimmed = logoUrl.trim()
        if (trimmed.isNotEmpty()) {
            val token = MezonImageLoader.getInstance(context).load(trimmed, size * 2, size, onSuccess = { bitmap ->
                logoView.setImageBitmap(bitmap)
            })
            onLoadToken(token)
        }
        return logoWrap
    }

    fun nearTime(addMinutes: Int): Calendar = Calendar.getInstance().apply {
        add(Calendar.MINUTE, addMinutes)
    }

    fun applyEpochSeconds(calendar: Calendar, epochSeconds: Int) {
        calendar.timeInMillis = DateTimeUtil.epochToMillis(epochSeconds.toLong())
    }

    fun optionFromEvent(event: ClanEventEntity): Int = when {
        event.channelVoiceId != 0L -> ClanEventOption.SPEAKER
        event.isOfflineEvent() -> ClanEventOption.LOCATION
        event.isPrivate -> ClanEventOption.PRIVATE
        else -> ClanEventOption.SPEAKER
    }

    fun sectionHeader(context: Context, theme: ThemeColors, titleRes: Int, subtitleRes: Int): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, LayoutHelper.dp(24), 0, LayoutHelper.dp(16))
            addView(TextView(context).apply {
                text = context.getString(titleRes)
                setTextColor(theme.textStrong)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            })
            addView(TextView(context).apply {
                text = context.getString(subtitleRes)
                setTextColor(theme.colorText)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                gravity = Gravity.CENTER
                setPadding(LayoutHelper.dp(8), LayoutHelper.dp(8), LayoutHelper.dp(8), 0)
            })
        }
    }

    fun sectionCaption(context: Context, theme: ThemeColors, textRes: Int): TextView {
        return TextView(context).apply {
            text = context.getString(textRes)
            setTextColor(theme.onSurfaceVariant)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    fun sectionDescription(context: Context, theme: ThemeColors, textRes: Int): TextView {
        return TextView(context).apply {
            text = context.getString(textRes)
            setTextColor(theme.onSurfaceVariant)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setLineSpacing(LayoutHelper.dp(2).toFloat(), 1f)
        }
    }

    fun mutedLabel(context: Context, theme: ThemeColors, textRes: Int): TextView =
        sectionCaption(context, theme, textRes).apply {
            setPadding(0, 0, 0, LayoutHelper.dp(8))
        }

    fun createOptionPicker(
        context: Context,
        theme: ThemeColors,
        @StringRes headingRes: Int,
        icon: MezonIcon,
        clearable: Boolean = false,
        placeholder: CharSequence? = null,
        onPick: () -> Unit,
    ): ClanEventOptionPicker {
        return ClanEventOptionPicker(
            context = context,
            theme = theme,
            heading = context.getString(headingRes),
            defaultIcon = icon,
            placeholder = placeholder,
            clearable = clearable,
        ).apply {
            onPickRequested = onPick
        }
    }

    fun pickerLayoutParams(bottomMargin: Int = 0) =
        LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT).apply {
            if (bottomMargin > 0) this.bottomMargin = bottomMargin
        }

    fun buildActionBar(
        context: Context,
        theme: ThemeColors,
        titleRes: Int,
        showBack: Boolean,
        onBack: () -> Unit,
        onClose: () -> Unit,
    ): ActionBarView {
        return ActionBarView(context, theme).apply {
            occupyStatusBar = false
            setTitle(context.getString(titleRes))
            setTitleColor(theme.textStrong)
            setCenterTitle(true)
            if (showBack) {
                setBackButtonImage(MezonIcon.arrowLargeLeftIcon.resId)
                setMenuOnItemClick(object : ActionBarView.ActionBarMenuOnItemClick() {
                    override fun onItemClick(id: Int) {
                        if (id == -1) onBack()
                    }
                })
            } else {
                setBackButtonImage(0)
            }
            createMenu().addItem(1, "").also { cell ->
                cell.addView(
                    ImageView(context).apply {
                        setImageDrawable(MezonIcon.closeLargeIcon.getDrawable(context, theme.textStrong))
                        scaleType = ImageView.ScaleType.CENTER_INSIDE
                        val px = LayoutHelper.dp(16)
                        setPadding(px, px, px, px)
                        setOnClickListener { onClose() }
                    },
                    LayoutHelper.createFrame(
                        LayoutHelper.WRAP_CONTENT,
                        LayoutHelper.MATCH_PARENT,
                        Gravity.CENTER_VERTICAL or Gravity.END,
                        0f, 0f, 8f, 0f,
                    ),
                )
            }
        }
    }

    fun buildNextButton(context: Context, theme: ThemeColors, enabled: Boolean, onClick: () -> Unit): FrameLayout {
        val radius = LayoutHelper.dp(10).toFloat()
        val fill = GradientDrawable().apply {
            setColor(theme.blurple)
            cornerRadius = radius
        }
        val mask = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = radius
        }
        val label = TextView(context).apply {
            text = context.getString(R.string.event_creator_action_next)
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val p = LayoutHelper.dp(14)
            setPadding(p, p, p, p)
            background = RippleDrawable(
                ColorStateList.valueOf(0x33FFFFFF),
                fill,
                mask,
            )
            setOnClickListener { if (isEnabled) onClick() }
        }
        return FrameLayout(context).apply {
            addView(label, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ))
            setNextEnabled(this, enabled)
        }
    }

    fun setNextEnabled(container: FrameLayout, enabled: Boolean) {
        val btn = container.getChildAt(0) as? TextView ?: return
        btn.isEnabled = enabled
        btn.alpha = if (enabled) 1f else 0.5f
    }

    fun addTypeOption(
        context: Context,
        theme: ThemeColors,
        parent: LinearLayout,
        option: Int,
        selectedOption: Int,
        icon: MezonIcon,
        titleRes: Int,
        descRes: Int,
        enabled: Boolean,
        radioCells: MutableList<RadioCell>,
        cardRadius: Float,
        rowPad: Int,
        showTopDivider: Boolean,
        onSelect: (Int) -> Unit,
    ) {
        if (showTopDivider) {
            parent.addView(
                View(context).apply { setBackgroundColor(theme.outlineVariant) },
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1),
            )
        }
        val radio = RadioCell(context, theme).apply { drawSelectionAsCheckmark = false }
        radioCells.add(radio)
        val selected = selectedOption == option
        radio.setChecked(selected, animated = false)

        val borderColor = if (selected) theme.blurple else theme.outlineVariant
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            isClickable = enabled
            isFocusable = enabled
            alpha = if (enabled) 1f else 0.45f
            setPadding(rowPad, rowPad, rowPad, rowPad)
            background = GradientDrawable().apply {
                setColor(theme.surfaceVariant)
                cornerRadius = cardRadius
                setStroke(LayoutHelper.dp(if (selected) 2 else 1), borderColor)
            }
            setOnClickListener {
                if (enabled) onSelect(option)
            }
        }
        row.addView(
            ImageView(context).apply {
                setImageDrawable(icon.getDrawable(context, theme.textStrong))
                scaleType = ImageView.ScaleType.FIT_CENTER
            },
            LayoutHelper.createLinear(24, 24, 0f, Gravity.CENTER_VERTICAL, 0f, 0f, 12f, 0f),
        )
        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply {
                text = context.getString(titleRes)
                setTextColor(theme.textStrong)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(context).apply {
                text = context.getString(descRes)
                setTextColor(theme.onSurfaceVariant)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, LayoutHelper.dp(4), 0, 0)
            })
        }
        row.addView(texts)
        row.addView(radio, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0f, Gravity.CENTER_VERTICAL))
        parent.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT).apply {
            bottomMargin = LayoutHelper.dp(10)
        })
    }

    fun refreshTypeRadios(radioCells: List<RadioCell>, options: List<Int>, selectedOption: Int) {
        radioCells.forEachIndexed { index, cell ->
            val opt = options.getOrNull(index) ?: return@forEachIndexed
            cell.setChecked(opt == selectedOption, animated = true)
        }
    }

    fun formatDate(context: Context, cal: Calendar): String =
        DateFormat.getMediumDateFormat(context).format(cal.time)

    fun formatTime(context: Context, cal: Calendar): String {
        return if (DateFormat.is24HourFormat(context)) {
            String.format(Locale.getDefault(), "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        } else {
            val hour = cal.get(Calendar.HOUR)
            val displayHour = if (hour == 0) 12 else hour
            val amPm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
            String.format(Locale.getDefault(), "%d:%02d %s", displayHour, cal.get(Calendar.MINUTE), amPm)
        }
    }

    fun showDatePicker(context: Context, cal: Calendar, minToday: Boolean, onPicked: () -> Unit) {
        DatePickerDialog(
            context,
            { _, y, m, d ->
                cal.set(Calendar.YEAR, y)
                cal.set(Calendar.MONTH, m)
                cal.set(Calendar.DAY_OF_MONTH, d)
                onPicked()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH),
        ).apply {
            if (minToday) datePicker.minDate = startOfDay(Calendar.getInstance()).timeInMillis
        }.show()
    }

    fun showTimePicker(context: Context, cal: Calendar, onPicked: () -> Unit) {
        TimePickerDialog(
            context,
            { _, hour, minute ->
                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, minute)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                onPicked()
            },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            DateFormat.is24HourFormat(context),
        ).show()
    }

    fun combineDateAndTime(date: Calendar, time: Calendar): Calendar {
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, date.get(Calendar.YEAR))
            set(Calendar.MONTH, date.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, date.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, time.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, time.get(Calendar.MINUTE))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    fun isSameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    fun startOfDay(cal: Calendar): Calendar = (cal.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    fun repeatTypeLabels(context: Context, start: Calendar): List<Pair<Int, String>> {
        val dayName = start.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()).orEmpty()
        val weekOfMonth = start.get(Calendar.DAY_OF_WEEK_IN_MONTH)
        val monthName = start.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()).orEmpty()
        val dayOfMonth = start.get(Calendar.DAY_OF_MONTH)
        return listOf(
            ClanEventRepeatType.DOES_NOT_REPEAT to context.getString(R.string.event_creator_repeat_none),
            ClanEventRepeatType.WEEKLY_ON_DAY to context.getString(R.string.event_creator_repeat_weekly, dayName),
            ClanEventRepeatType.EVERY_OTHER_DAY to context.getString(R.string.event_creator_repeat_every_other, dayName),
            ClanEventRepeatType.MONTHLY to context.getString(R.string.event_creator_repeat_monthly, weekOfMonth, dayName),
            ClanEventRepeatType.ANNUALLY to context.getString(R.string.event_creator_repeat_annually, monthName, dayOfMonth),
            ClanEventRepeatType.EVERY_WEEKDAY to context.getString(R.string.event_creator_repeat_weekday),
        )
    }

    fun validateDetails(
        context: Context,
        title: String,
        option: Int,
        start: Calendar,
        end: Calendar,
        allowPastStart: Boolean = false,
    ): Boolean {
        if (title.trim().isEmpty()) return false
        val now = Calendar.getInstance()
        if (!allowPastStart) {
            if (startOfDay(start).before(startOfDay(now))) return false
            if (isSameDay(start, now) && start.timeInMillis <= now.timeInMillis) return false
        }
        if (option != ClanEventOption.LOCATION) {
            if (startOfDay(end).before(startOfDay(start))) return false
            if (isSameDay(start, end) && !end.after(start)) return false
        }
        return true
    }

    const val REQUEST_PICK_LOGO = 8401
    const val MAX_LOGO_SIZE_BYTES = MAX_LOGO_BYTES

    fun buildCoverBannerSection(
        context: Context,
        theme: ThemeColors,
        onPick: () -> Unit,
        onClear: () -> Unit,
    ): EventCoverBannerPicker {
        return EventCoverBannerPicker.create(context, theme, onPick, onClear)
    }
}

class EventCoverBannerPicker private constructor(
    val section: LinearLayout,
    private val bannerFrame: FrameLayout,
    private val imageView: ImageView,
    private val cameraBadge: ImageView,
    private val clearButton: ImageView,
    private val progressBar: ProgressBar,
    private val context: Context,
) {
    var onPickRequested: (() -> Unit)? = null
    var onClearRequested: (() -> Unit)? = null
    private var previewLoad: MezonImageLoader.Cancellable? = null
    private var previewUrl: String? = null

    fun setUploading(uploading: Boolean) {
        progressBar.visibility = if (uploading) View.VISIBLE else View.GONE
        bannerFrame.isEnabled = !uploading
        imageView.alpha = if (uploading) 0.6f else 1f
    }

    fun loadPreview(url: String) {
        previewUrl = url.trim()
        if (previewUrl.isNullOrEmpty()) {
            clearImage()
            return
        }
        renderPreview()
    }

    fun clearImage() {
        previewLoad?.cancel()
        previewLoad = null
        previewUrl = null
        imageView.setImageDrawable(null)
        cameraBadge.visibility = View.VISIBLE
        clearButton.visibility = View.GONE
    }

    private fun renderPreview() {
        val url = previewUrl ?: return
        previewLoad?.cancel()
        val width = bannerFrame.width.coerceAtLeast(LayoutHelper.dp(300))
        val height = LayoutHelper.dp(BANNER_HEIGHT_DP)
        if (bannerFrame.width <= 0) {
            bannerFrame.post { renderPreview() }
            return
        }
        cameraBadge.visibility = View.GONE
        clearButton.visibility = View.VISIBLE
        val proxyUrl = createImgproxyUrl(url, width, height, "fit")
        previewLoad = MezonImageLoader.getInstance(context).load(
            proxyUrl,
            width,
            height,
            onSuccess = { bitmap -> imageView.setImageBitmap(bitmap) },
        )
    }

    companion object {
        private const val BANNER_HEIGHT_DP = 200

        fun create(
            context: Context,
            theme: ThemeColors,
            onPick: () -> Unit,
            onClear: () -> Unit,
        ): EventCoverBannerPicker {
            val imageView = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                isClickable = false
                isFocusable = false
            }
            val cameraBadge = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER
                val pad = LayoutHelper.dp(11)
                setPadding(pad, pad, pad, pad)
                setImageDrawable(MezonIcon.cameraIcon.getDrawable(context, Color.WHITE))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0x99000000.toInt())
                }
                elevation = LayoutHelper.dpf(2f)
            }
            val clearButton = ImageView(context).apply {
                visibility = View.GONE
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setImageDrawable(MezonIcon.closeSmallBold.getDrawable(context, theme.error))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xCC000000.toInt())
                }
                val inset = LayoutHelper.dp(5)
                setPadding(inset, inset, inset, inset)
                elevation = LayoutHelper.dpf(4f)
            }
            val progressBar = ProgressBar(context).apply {
                visibility = View.GONE
                isIndeterminate = true
            }
            val bannerFrame = FrameLayout(context).apply {
                background = GradientDrawable().apply {
                    cornerRadius = LayoutHelper.dpf(ClanEventCreateUi.EVENT_BANNER_CORNER_DP)
                    setColor(theme.surfaceVariant)
                }
                clipToOutline = true
                outlineProvider = ViewOutlineProvider.BACKGROUND
                isClickable = true
                isFocusable = true
                addView(imageView, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ))
                addView(
                    cameraBadge,
                    FrameLayout.LayoutParams(LayoutHelper.dp(44), LayoutHelper.dp(44), Gravity.CENTER),
                )
                addView(
                    clearButton,
                    FrameLayout.LayoutParams(LayoutHelper.dp(30), LayoutHelper.dp(30), Gravity.END or Gravity.TOP).apply {
                        topMargin = LayoutHelper.dp(6)
                        marginEnd = LayoutHelper.dp(6)
                    },
                )
                addView(
                    progressBar,
                    FrameLayout.LayoutParams(LayoutHelper.dp(32), LayoutHelper.dp(32), Gravity.CENTER),
                )
            }
            val section = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(bannerFrame, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, BANNER_HEIGHT_DP))
            }
            return EventCoverBannerPicker(
                section = section,
                bannerFrame = bannerFrame,
                imageView = imageView,
                cameraBadge = cameraBadge,
                clearButton = clearButton,
                progressBar = progressBar,
                context = context,
            ).apply {
                onPickRequested = onPick
                onClearRequested = onClear
                bannerFrame.setOnClickListener { onPickRequested?.invoke() }
                cameraBadge.setOnClickListener { onPickRequested?.invoke() }
                clearButton.setOnClickListener { onClearRequested?.invoke() }
            }
        }
    }
}
