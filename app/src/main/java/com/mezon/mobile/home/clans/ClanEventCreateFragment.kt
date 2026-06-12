package com.mezon.mobile.home.clans

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.mezon.mobile.R
import com.mezon.mobile.core.BaseFragment
import com.mezon.mobile.core.LayoutHelper
import com.mezon.mobile.core.NotificationCenter
import com.mezon.mobile.di.FragmentEntryPoint
import com.mezon.mobile.home.profile.AccountController
import com.mezon.mobile.ui.MezonToast
import com.mezon.mobile.ui.cells.ActionBarView
import com.mezon.mobile.ui.cells.InputCell
import com.mezon.mobile.ui.cells.MezonIcon
import com.mezon.mobile.ui.cells.RadioCell
import com.mezon.mobile.ui.cells.SelectPopup
import com.mezon.mobile.ui.cells.ToastOverlay
import com.mezon.mobile.util.DateTimeUtil
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

class ClanEventCreateFragment : BaseFragment() {

    companion object {
        private enum class Step { TYPE, DETAILS, PREVIEW }

        fun newInstance(clanId: Long): ClanEventCreateFragment = newInstance(clanId, eventId = 0L)

        fun newInstance(clanId: Long, eventId: Long): ClanEventCreateFragment = ClanEventCreateFragment().apply {
            arguments = Bundle().apply {
                putLong(ClanEventCreateArgs.ARG_CLAN_ID, clanId)
                if (eventId != 0L) putLong(ClanEventCreateArgs.ARG_EVENT_ID, eventId)
            }
        }
    }

    private lateinit var clanEventController: ClanEventController
    private lateinit var accountController: AccountController

    private var clanId = 0L
    private var editingEventId = 0L
    private var editingChannelIdOld = 0L
    private var currentStep = Step.TYPE

    private var selectedOption = 0
    private var channelVoiceId = 0L
    private var address = ""
    private var channelId = 0L
    private var isPrivate = false

    private var startDate: Calendar = ClanEventCreateUi.nearTime(120)
    private var startTime: Calendar = ClanEventCreateUi.nearTime(120)
    private var endDate: Calendar = ClanEventCreateUi.nearTime(240)
    private var endTime: Calendar = ClanEventCreateUi.nearTime(240)
    private var repeatType = ClanEventRepeatType.DOES_NOT_REPEAT
    private var logoUrl = ""
    private var originalLogoUrl = ""
    private var isUploadingLogo = false
    private var submitting = false

    private val optionValues = intArrayOf(
        ClanEventOption.SPEAKER,
        ClanEventOption.LOCATION,
        ClanEventOption.PRIVATE,
    )
    private val radioCells = ArrayList<RadioCell>(3)

    private lateinit var primaryActionText: TextView
    private lateinit var stepTypePanel: LinearLayout
    private lateinit var stepDetailsPanel: LinearLayout
    private lateinit var stepPreviewPanel: LinearLayout
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var formScroll: ScrollView

    private lateinit var voiceSelectSection: LinearLayout
    private lateinit var voicePickerRow: ClanEventOptionPicker
    private lateinit var addressCell: InputCell
    private lateinit var channelSection: LinearLayout
    private lateinit var channelPickerRow: ClanEventOptionPicker

    private lateinit var titleCell: InputCell
    private lateinit var descriptionCell: InputCell
    private lateinit var startDatePicker: ClanEventOptionPicker
    private lateinit var startTimePicker: ClanEventOptionPicker
    private lateinit var endDatePicker: ClanEventOptionPicker
    private lateinit var endTimePicker: ClanEventOptionPicker
    private lateinit var endSection: LinearLayout
    private lateinit var repeatPicker: ClanEventOptionPicker
    private lateinit var startDateError: TextView
    private lateinit var startTimeError: TextView
    private lateinit var endDateError: TextView
    private lateinit var endTimeError: TextView
    private lateinit var coverBannerPicker: EventCoverBannerPicker
    private lateinit var previewHost: LinearLayout

    override fun onInject(entryPoint: FragmentEntryPoint) {
        clanEventController = entryPoint.clanEventController()
        accountController = entryPoint.accountController()
    }

    override fun onFragmentCreate(): Boolean {
        clanId = arguments?.getLong(ClanEventCreateArgs.ARG_CLAN_ID, 0L) ?: 0L
        editingEventId = ClanEventCreateArgs.eventId(arguments)
        if (isEditMode && clanId != 0L) {
            clanEventController.loadEvents(clanId, force = false)
            observe(NotificationCenter.clanEventsDidLoad) { _, _, args ->
                if (isPaused) return@observe
                val id = args.firstOrNull() as? Long ?: return@observe
                if (id == clanId) applyLoadedEventIfNeeded()
            }
        }
        observe(NotificationCenter.eventCoverCropped) { _, _, args ->
            if (isPaused) return@observe
            val url = args.firstOrNull() as? String ?: return@observe
            onCoverUploaded(url)
        }
        return super.onFragmentCreate()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != ClanEventCreateUi.REQUEST_PICK_LOGO || resultCode != Activity.RESULT_OK) return
        val uri = data?.clipData?.getItemAt(0)?.uri ?: data?.data ?: return
        presentFragment(ClanEventCoverTransformFragment.newInstance(uri.toString()))
    }

    override fun createView(context: Context): View {
        val screenPadH = LayoutHelper.dp(16)
        val sectionGap = LayoutHelper.dp(8)
        val majorGap = LayoutHelper.dp(24)
        val cardRadius = LayoutHelper.dpf(12f)
        val cardInnerPad = LayoutHelper.dp(16)

        val voiceChannels = clanEventController.voiceChannels(clanId)

        primaryActionText = TextView(context).apply {
            text = getString(R.string.event_creator_action_next)
            setTextColor(themeColors.primary)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            setPadding(LayoutHelper.dp(16), 0, LayoutHelper.dp(16), 0)
            setOnClickListener { onPrimaryAction() }
        }

        actionBar = ActionBarView(context, themeColors).apply {
            occupyStatusBar = false
            setBackButtonImage(MezonIcon.closeLargeIcon.resId)
            setTitle(getString(R.string.event_creator_screen_title))
            setTitleColor(themeColors.textStrong)
            setCenterTitle(true)
            createMenu().addItem(1, "").also { cell ->
                cell.addView(
                    primaryActionText,
                    LayoutHelper.createFrame(
                        LayoutHelper.WRAP_CONTENT,
                        LayoutHelper.MATCH_PARENT,
                        Gravity.CENTER_VERTICAL,
                        0f, 3f, 0f, 0f,
                    ),
                )
            }
            setMenuOnItemClick(object : ActionBarView.ActionBarMenuOnItemClick() {
                override fun onItemClick(id: Int) {
                    when (id) {
                        -1 -> onToolbarBack()
                        1 -> onPrimaryAction()
                    }
                }
            })
            getBackButtonView()?.apply {
                val px = LayoutHelper.dp(16)
                setPadding(px, px, px, px)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
        }

        stepTypePanel = buildTypeStep(context, screenPadH, sectionGap, majorGap, cardRadius, cardInnerPad, voiceChannels)
        stepDetailsPanel = buildDetailsStep(context, screenPadH, sectionGap, majorGap)
        stepPreviewPanel = buildPreviewStep(context, screenPadH)

        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(stepTypePanel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
            addView(stepDetailsPanel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
            addView(stepPreviewPanel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
        }

        formScroll = ScrollView(context).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = true
            addView(
                body,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        val scroll = formScroll

        loadingOverlay = FrameLayout(context).apply {
            visibility = View.GONE
            setBackgroundColor(0x40000000)
            isClickable = true
            addView(
                ProgressBar(context).apply { isIndeterminate = true },
                FrameLayout.LayoutParams(LayoutHelper.dp(48), LayoutHelper.dp(48), Gravity.CENTER),
            )
        }

        val bodyRoot = FrameLayout(context).apply {
            setBackgroundColor(themeColors.background)
            addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT))
            addView(loadingOverlay, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT))
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(actionBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
            addView(bodyRoot, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f))
        }

        fragmentView = root
        refreshTypeConditionalSections()
        if (isEditMode) {
            if (applyLoadedEventIfNeeded()) {
                goToStep(Step.TYPE)
            } else {
                loadingOverlay.visibility = View.VISIBLE
                clanEventController.loadEvents(clanId, force = true)
            }
        } else {
            goToStep(Step.TYPE)
        }
        return root
    }

    private val isEditMode: Boolean get() = editingEventId != 0L

    private fun applyLoadedEventIfNeeded(): Boolean {
        if (!isEditMode) return false
        val event = clanEventController.getEvent(clanId, editingEventId) ?: return false
        applyEventToState(event)
        if (::titleCell.isInitialized) applyEventToViews(event)
        if (::loadingOverlay.isInitialized) loadingOverlay.visibility = View.GONE
        refreshPrimaryAction()
        return true
    }

    private fun applyEventToState(event: ClanEventEntity) {
        editingChannelIdOld = event.channelId
        selectedOption = ClanEventCreateUi.optionFromEvent(event)
        isPrivate = event.isPrivate
        channelVoiceId = event.channelVoiceId
        address = event.address
        channelId = event.channelId
        repeatType = event.repeatType
        logoUrl = event.logo
        originalLogoUrl = event.logo
        ClanEventCreateUi.applyEpochSeconds(startDate, event.startTimeSeconds)
        ClanEventCreateUi.applyEpochSeconds(startTime, event.startTimeSeconds)
        ClanEventCreateUi.applyEpochSeconds(endDate, event.endTimeSeconds)
        ClanEventCreateUi.applyEpochSeconds(endTime, event.endTimeSeconds)
    }

    private fun applyEventToViews(event: ClanEventEntity) {
        titleCell.setText(event.title)
        descriptionCell.setText(event.description)
        addressCell.setText(event.address)
        refreshTypeRadios()
        refreshTypeConditionalSections()
        refreshVoicePickerRow()
        refreshChannelPickerRow()
        refreshDateTimeLabels()
        validateAndRefresh()
        if (event.logo.isNotBlank()) {
            coverBannerPicker.loadPreview(event.logo)
        }
        refreshPrimaryAction()
    }

    private fun buildTypeStep(
        context: Context,
        screenPadH: Int,
        sectionGap: Int,
        majorGap: Int,
        cardRadius: Float,
        cardInnerPad: Int,
        voiceChannels: List<ClanChannelEntity>,
    ): LinearLayout {
        val typeBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(themeColors.surfaceVariant)
                cornerRadius = cardRadius
            }
        }
        addTypeOptionInCard(context, typeBox, ClanEventOption.SPEAKER, MezonIcon.channelVoice,
            R.string.event_creator_type_voice_title, R.string.event_creator_type_voice_desc,
            voiceChannels.isNotEmpty(), showTopDivider = false, rowPad = cardInnerPad)
        addTypeOptionInCard(context, typeBox, ClanEventOption.LOCATION, MezonIcon.locationIcon,
            R.string.event_creator_type_location_title, R.string.event_creator_type_location_desc,
            true, showTopDivider = true, rowPad = cardInnerPad)
        addTypeOptionInCard(context, typeBox, ClanEventOption.PRIVATE, MezonIcon.lockIcon,
            R.string.event_creator_type_private_title, R.string.event_creator_type_private_desc,
            true, showTopDivider = true, rowPad = cardInnerPad)
        refreshTypeRadios()

        voicePickerRow = ClanEventOptionPicker(
            context,
            themeColors,
            getString(R.string.event_creator_voice_channel_label),
            MezonIcon.channelVoice,
        ).apply {
            onPickRequested = { showVoiceChannelPicker(context, voiceChannels) }
            onClearRequested = {
                channelVoiceId = 0L
                clearSelection()
                refreshPrimaryAction()
            }
            bind(channelVoiceId, voiceChannels)
        }
        voiceSelectSection = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(voicePickerRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
        }

        addressCell = InputCell(context, themeColors).apply {
            setLabel(null, false, false)
            setHint(getString(R.string.event_creator_address_placeholder))
            onTextChanged = { address = it; refreshPrimaryAction() }
        }

        val textChannels = clanEventController.textChannels(clanId)
        channelPickerRow = ClanEventOptionPicker(
            context,
            themeColors,
            getString(R.string.event_creator_linked_channel_label),
        ).apply {
            onPickRequested = { showTextChannelPicker(context) }
            onClearRequested = {
                channelId = 0L
                clearSelection()
            }
            bind(channelId, textChannels)
        }
        channelSection = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(channelPickerRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(screenPadH, LayoutHelper.dp(16), screenPadH, LayoutHelper.dp(28))
            addView(
                ClanEventCreateUi.sectionCaption(context, themeColors, R.string.event_creator_type_title),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT),
            )
            addView(
                ClanEventCreateUi.sectionDescription(context, themeColors, R.string.event_creator_type_subtitle),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT).apply {
                    bottomMargin = sectionGap
                },
            )
            addView(
                ClanEventCreateUi.sectionCaption(context, themeColors, R.string.event_creator_type_header),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT).apply {
                    topMargin = LayoutHelper.dp(12)
                    bottomMargin = sectionGap
                },
            )
            addView(typeBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
            addView(voiceSelectSection, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT).apply {
                topMargin = majorGap
            })
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    visibility = View.GONE
                    addView(
                        ClanEventCreateUi.sectionCaption(context, themeColors, R.string.event_creator_address_label),
                        LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT).apply {
                            topMargin = majorGap
                            bottomMargin = sectionGap
                        },
                    )
                    addView(addressCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
                }.also { addressSection = it },
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT),
            )
            addView(channelSection, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT).apply {
                topMargin = majorGap
            })
        }
    }

    private lateinit var addressSection: LinearLayout

    private fun buildDetailsStep(
        context: Context,
        screenPadH: Int,
        sectionGap: Int,
        majorGap: Int,
    ): LinearLayout {
        titleCell = InputCell(context, themeColors).apply {
            setLabel(null, false, false)
            setMaxCharacter(255)
            setHint(getString(R.string.event_creator_name_placeholder))
            onTextChanged = { setError(null); refreshPrimaryAction() }
        }

        startDatePicker = ClanEventCreateUi.createOptionPicker(
            context,
            themeColors,
            R.string.event_creator_start_date_label,
            MezonIcon.calendarIcon,
        ) {
            ClanEventCreateUi.showDatePicker(context, startDate, minToday = true) {
                refreshDateTimeLabels()
                validateAndRefresh()
            }
        }
        startTimePicker = ClanEventCreateUi.createOptionPicker(
            context,
            themeColors,
            R.string.event_creator_start_time_label,
            MezonIcon.eventTimeIcon,
        ) {
            ClanEventCreateUi.showTimePicker(context, startTime) {
                refreshDateTimeLabels()
                validateAndRefresh()
            }
        }
        endDatePicker = ClanEventCreateUi.createOptionPicker(
            context,
            themeColors,
            R.string.event_creator_end_date_label,
            MezonIcon.calendarIcon,
        ) {
            ClanEventCreateUi.showDatePicker(context, endDate, minToday = false) {
                refreshDateTimeLabels()
                validateAndRefresh()
            }
        }
        endTimePicker = ClanEventCreateUi.createOptionPicker(
            context,
            themeColors,
            R.string.event_creator_end_time_label,
            MezonIcon.eventTimeIcon,
        ) {
            ClanEventCreateUi.showTimePicker(context, endTime) {
                refreshDateTimeLabels()
                validateAndRefresh()
            }
        }
        startDateError = errorText(context)
        startTimeError = errorText(context)
        endDateError = errorText(context)
        endTimeError = errorText(context)

        endSection = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(endDatePicker, ClanEventCreateUi.pickerLayoutParams(sectionGap))
            addView(endTimePicker, ClanEventCreateUi.pickerLayoutParams())
            addView(endDateError)
            addView(endTimeError)
        }

        descriptionCell = InputCell(context, themeColors).apply {
            setLabel(null, false, false)
            setHint(getString(R.string.event_creator_description_placeholder))
            setTextarea(true, 255)
        }

        repeatPicker = ClanEventCreateUi.createOptionPicker(
            context,
            themeColors,
            R.string.event_creator_repeat_label,
            MezonIcon.calendarIcon,
        ) {
            showRepeatPicker(context)
        }
        startDatePicker.bindValue(ClanEventCreateUi.formatDate(context, startDate))
        startTimePicker.bindValue(ClanEventCreateUi.formatTime(context, startTime))
        endDatePicker.bindValue(ClanEventCreateUi.formatDate(context, endDate))
        endTimePicker.bindValue(ClanEventCreateUi.formatTime(context, endTime))
        repeatPicker.bindValue(repeatLabel(context))

        coverBannerPicker = ClanEventCreateUi.buildCoverBannerSection(
            context,
            themeColors,
            onPick = { if (!isUploadingLogo) openCoverPicker() },
            onClear = {
                logoUrl = ""
                coverBannerPicker.clearImage()
                refreshPrimaryAction()
            },
        )

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(screenPadH, LayoutHelper.dp(16), screenPadH, LayoutHelper.dp(28))
            addView(
                ClanEventCreateUi.sectionCaption(context, themeColors, R.string.event_creator_details_title),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT),
            )
            addView(
                ClanEventCreateUi.sectionDescription(context, themeColors, R.string.event_creator_details_subtitle),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT).apply {
                    bottomMargin = sectionGap
                },
            )
            addView(
                coverBannerPicker.section,
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT).apply {
                    topMargin = LayoutHelper.dp(12)
                    bottomMargin = majorGap
                },
            )
            addView(
                ClanEventCreateUi.sectionCaption(context, themeColors, R.string.event_creator_name_label),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT).apply {
                    bottomMargin = sectionGap
                },
            )
            addView(titleCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
            addView(startDatePicker, ClanEventCreateUi.pickerLayoutParams(sectionGap).apply { topMargin = majorGap })
            addView(startTimePicker, ClanEventCreateUi.pickerLayoutParams())
            addView(startDateError)
            addView(startTimeError)
            addView(endSection, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT).apply {
                topMargin = sectionGap
            })
            addView(
                ClanEventCreateUi.sectionCaption(context, themeColors, R.string.event_creator_description_label),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT).apply {
                    topMargin = majorGap
                    bottomMargin = sectionGap
                },
            )
            addView(descriptionCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
            addView(repeatPicker, ClanEventCreateUi.pickerLayoutParams().apply { topMargin = majorGap })
        }
    }

    private fun buildPreviewStep(context: Context, screenPadH: Int): LinearLayout {
        previewHost = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(screenPadH, LayoutHelper.dp(16), screenPadH, LayoutHelper.dp(28))
            addView(previewHost, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
            addView(
                ClanEventCreateUi.sectionCaption(context, themeColors, R.string.event_creator_preview_title),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT).apply {
                    topMargin = LayoutHelper.dp(20)
                },
            )
            addView(
                TextView(context).apply {
                    tag = "preview_subtitle"
                    setTextColor(themeColors.onSurfaceVariant)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setLineSpacing(LayoutHelper.dp(2).toFloat(), 1f)
                    setPadding(0, LayoutHelper.dp(8), 0, 0)
                },
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT),
            )
        }
    }

    private fun errorText(context: Context): TextView = TextView(context).apply {
        setTextColor(themeColors.badgeRed)
        textSize = 12f
        visibility = View.GONE
        setPadding(LayoutHelper.dp(4), LayoutHelper.dp(4), 0, 0)
    }

    private fun addTypeOptionInCard(
        context: Context,
        parent: LinearLayout,
        typeConst: Int,
        icon: MezonIcon,
        titleRes: Int,
        descRes: Int,
        enabled: Boolean,
        showTopDivider: Boolean,
        rowPad: Int,
    ) {
        if (showTopDivider) {
            parent.addView(
                View(context).apply { setBackgroundColor(themeColors.outlineVariant) },
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1),
            )
        }
        val radio = RadioCell(context, themeColors).apply { drawSelectionAsCheckmark = false }
        radioCells.add(radio)

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            isClickable = enabled
            isFocusable = enabled
            alpha = if (enabled) 1f else 0.45f
            setPadding(rowPad, rowPad, rowPad, rowPad)
            setOnClickListener {
                if (enabled) selectOption(typeConst)
            }
        }
        row.addView(
            ImageView(context).apply {
                setImageDrawable(icon.getDrawable(context, themeColors.textStrong))
                scaleType = ImageView.ScaleType.FIT_CENTER
            },
            LayoutHelper.createLinear(24, LayoutHelper.MATCH_PARENT, 0f, Gravity.CENTER_VERTICAL, 0f, 0f, 12f, 0f),
        )
        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply {
                text = getString(titleRes)
                setTextColor(themeColors.textStrong)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(context).apply {
                text = getString(descRes)
                setTextColor(themeColors.onSurfaceVariant)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, LayoutHelper.dp(4), 0, 0)
            })
        }
        row.addView(texts)
        row.addView(radio, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0f, Gravity.CENTER_VERTICAL))
        parent.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
    }

    private fun selectOption(option: Int) {
        selectedOption = option
        isPrivate = option == ClanEventOption.PRIVATE
        refreshTypeRadios()
        refreshTypeConditionalSections()
        refreshPrimaryAction()
    }

    private fun refreshTypeRadios() {
        radioCells.forEachIndexed { index, cell ->
            val opt = optionValues.getOrNull(index) ?: return@forEachIndexed
            cell.setChecked(opt == selectedOption, animated = true)
        }
    }

    private fun refreshTypeConditionalSections() {
        if (!::voiceSelectSection.isInitialized) return
        val hasVoice = clanEventController.voiceChannels(clanId).isNotEmpty()
        voiceSelectSection.visibility =
            if (selectedOption == ClanEventOption.SPEAKER && hasVoice) View.VISIBLE else View.GONE
        if (::addressSection.isInitialized) {
            addressSection.visibility =
                if (selectedOption == ClanEventOption.LOCATION) View.VISIBLE else View.GONE
        }
        channelSection.visibility =
            if (selectedOption != ClanEventOption.PRIVATE) View.VISIBLE else View.GONE
        if (::endSection.isInitialized) {
            endSection.visibility =
                if (selectedOption == ClanEventOption.LOCATION) View.GONE else View.VISIBLE
        }
    }

    private fun goToStep(step: Step) {
        currentStep = step
        stepTypePanel.visibility = if (step == Step.TYPE) View.VISIBLE else View.GONE
        stepDetailsPanel.visibility = if (step == Step.DETAILS) View.VISIBLE else View.GONE
        stepPreviewPanel.visibility = if (step == Step.PREVIEW) View.VISIBLE else View.GONE
        if (::formScroll.isInitialized) formScroll.scrollTo(0, 0)
        updateToolbarForStep(step)
        if (step == Step.PREVIEW) bindPreview()
        refreshPrimaryAction()
    }

    private fun updateToolbarForStep(step: Step) {
        val bar = actionBar ?: return
        when (step) {
            Step.TYPE -> {
                bar.setBackButtonImage(MezonIcon.closeLargeIcon.resId)
                bar.setTitle(
                    getString(
                        if (isEditMode) R.string.event_creator_edit_screen_title
                        else R.string.event_creator_screen_title,
                    ),
                )
                primaryActionText.text = getString(R.string.event_creator_action_next)
            }
            Step.DETAILS -> {
                bar.setBackButtonImage(MezonIcon.arrowLargeLeftIcon.resId)
                bar.setTitle(
                    getString(
                        if (isEditMode) R.string.event_creator_edit_screen_title
                        else R.string.event_creator_details_header,
                    ),
                )
                primaryActionText.text = getString(R.string.event_creator_action_next)
                refreshDateTimeLabels()
                validateAndRefresh()
            }
            Step.PREVIEW -> {
                bar.setBackButtonImage(MezonIcon.arrowLargeLeftIcon.resId)
                bar.setTitle(getString(R.string.event_creator_preview_header))
                primaryActionText.text = getString(
                    if (isEditMode) R.string.event_creator_action_save else R.string.event_creator_action_create,
                )
            }
        }
    }

    private fun onToolbarBack() {
        when (currentStep) {
            Step.TYPE -> finishFragment()
            Step.DETAILS -> goToStep(Step.TYPE)
            Step.PREVIEW -> goToStep(Step.DETAILS)
        }
    }

    private fun onPrimaryAction() {
        when (currentStep) {
            Step.TYPE -> proceedFromType()
            Step.DETAILS -> proceedFromDetails()
            Step.PREVIEW -> submitCreate()
        }
    }

    private fun proceedFromType() {
        if (!canProceedType()) {
            val msg = when {
                selectedOption == 0 -> getString(R.string.event_creator_error_type)
                selectedOption == ClanEventOption.LOCATION && address.trim().isEmpty() ->
                    getString(R.string.event_creator_error_location)
                else -> getString(R.string.event_creator_error_type)
            }
            MezonToast.show(this@ClanEventCreateFragment, ToastOverlay.ToastType.ERROR, msg)
            return
        }
        goToStep(Step.DETAILS)
    }

    private fun proceedFromDetails() {
        if (titleCell.getText().trim().isEmpty()) {
            titleCell.setError(getString(R.string.event_creator_name_error))
            return
        }
        if (!ClanEventCreateUi.validateDetails(
                fragmentView?.context ?: return,
                titleCell.getText(),
                selectedOption,
                combinedStart(),
                combinedEnd(),
                allowPastStart = isEditMode,
            )
        ) {
            validateAndRefresh()
            return
        }
        goToStep(Step.PREVIEW)
    }

    private fun canProceedType(): Boolean {
        if (selectedOption == 0) return false
        if (selectedOption == ClanEventOption.LOCATION && address.trim().isEmpty()) return false
        if (selectedOption == ClanEventOption.SPEAKER && channelVoiceId == 0L) return false
        return true
    }

    private fun refreshPrimaryAction() {
        if (!::primaryActionText.isInitialized) return
        val enabled = when (currentStep) {
            Step.TYPE -> canProceedType()
            Step.DETAILS -> ClanEventCreateUi.validateDetails(
                fragmentView?.context ?: return,
                titleCell.getText(),
                selectedOption,
                combinedStart(),
                combinedEnd(),
                allowPastStart = isEditMode,
            ) && !isUploadingLogo
            Step.PREVIEW -> !submitting
        }
        primaryActionText.alpha = if (enabled) 1f else 0.45f
    }

    private fun refreshVoicePickerRow() {
        if (!::voicePickerRow.isInitialized) return
        voicePickerRow.bind(channelVoiceId, clanEventController.voiceChannels(clanId))
    }

    private fun refreshChannelPickerRow() {
        if (!::channelPickerRow.isInitialized) return
        channelPickerRow.bind(channelId, clanEventController.textChannels(clanId))
    }

    private fun showVoiceChannelPicker(context: Context, channels: List<ClanChannelEntity>) {
        if (channels.isEmpty()) return
        ChannelPickerSheet(
            context,
            themeColors,
            channels.sortedBy { it.channelLabel.lowercase(Locale.getDefault()) },
            getString(R.string.event_creator_voice_channel_label),
        ) { picked ->
            channelVoiceId = picked.channelId
            refreshVoicePickerRow()
            refreshPrimaryAction()
        }.show()
    }

    private fun showTextChannelPicker(context: Context) {
        val channels = clanEventController.textChannels(clanId)
            .sortedBy { it.channelLabel.lowercase(Locale.getDefault()) }
        if (channels.isEmpty()) {
            MezonToast.show(this@ClanEventCreateFragment, ToastOverlay.ToastType.INFO, getString(R.string.clan_invite_need_channel))
            return
        }
        ChannelPickerSheet(
            context,
            themeColors,
            channels,
            getString(R.string.event_creator_channel_picker_title),
        ) { picked ->
            channelId = picked.channelId
            refreshChannelPickerRow()
        }.show()
    }

    private fun refreshDateTimeLabels() {
        val ctx = fragmentView?.context ?: return
        startDatePicker.bindValue(ClanEventCreateUi.formatDate(ctx, startDate))
        startTimePicker.bindValue(ClanEventCreateUi.formatTime(ctx, startTime))
        endDatePicker.bindValue(ClanEventCreateUi.formatDate(ctx, endDate))
        endTimePicker.bindValue(ClanEventCreateUi.formatTime(ctx, endTime))
        repeatPicker.bindValue(repeatLabel(ctx))
    }

    private fun repeatLabel(context: Context): String {
        val combined = ClanEventCreateUi.combineDateAndTime(startDate, startTime)
        return ClanEventCreateUi.repeatTypeLabels(context, combined)
            .firstOrNull { it.first == repeatType }?.second.orEmpty()
    }

    private fun showRepeatPicker(context: Context) {
        val combined = ClanEventCreateUi.combineDateAndTime(startDate, startTime)
        val labels = ClanEventCreateUi.repeatTypeLabels(context, combined)
        val popup = SelectPopup(context, themeColors)
        popup.setItems(labels.map { SelectPopup.SelectItem(it.first.toString(), it.second) }, repeatType.toString())
        popup.onItemSelected = { item ->
            repeatType = item.id.toIntOrNull() ?: ClanEventRepeatType.DOES_NOT_REPEAT
            repeatPicker.bindValue(item.label)
        }
        popup.show(repeatPicker, matchAnchorWidth = true)
    }

    private fun combinedStart(): Calendar = ClanEventCreateUi.combineDateAndTime(startDate, startTime)
    private fun combinedEnd(): Calendar = ClanEventCreateUi.combineDateAndTime(endDate, endTime)

    private fun validateAndRefresh() {
        val now = Calendar.getInstance()
        val start = combinedStart()
        val end = combinedEnd()

        val startDateErr = !isEditMode && ClanEventCreateUi.startOfDay(start).before(ClanEventCreateUi.startOfDay(now))
        val startTimeErr = !isEditMode && ClanEventCreateUi.isSameDay(start, now) && start.timeInMillis <= now.timeInMillis
        startDateError.visibility = if (startDateErr) View.VISIBLE else View.GONE
        startDateError.text = getString(R.string.event_creator_start_date_error)
        startTimeError.visibility = if (startTimeErr) View.VISIBLE else View.GONE
        startTimeError.text = getString(R.string.event_creator_start_time_error)

        if (selectedOption != ClanEventOption.LOCATION) {
            val endDateErr = ClanEventCreateUi.startOfDay(end).before(ClanEventCreateUi.startOfDay(start))
            val endTimeErr = ClanEventCreateUi.isSameDay(start, end) && !end.after(start)
            endDateError.visibility = if (endDateErr) View.VISIBLE else View.GONE
            endDateError.text = getString(R.string.event_creator_end_date_error)
            endTimeError.visibility = if (endTimeErr) View.VISIBLE else View.GONE
            endTimeError.text = getString(R.string.event_creator_end_time_error)
        }

        refreshPrimaryAction()
    }

    private fun openCoverPicker() {
        val pick = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
        val getContent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        val chooser = Intent.createChooser(getContent, getString(R.string.event_creator_cover_label)).apply {
            putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(pick))
        }
        startActivityForResult(chooser, ClanEventCreateUi.REQUEST_PICK_LOGO)
    }

    private fun onCoverUploaded(url: String) {
        if (!::coverBannerPicker.isInitialized) return
        logoUrl = url
        isUploadingLogo = false
        coverBannerPicker.setUploading(false)
        coverBannerPicker.loadPreview(url)
        refreshPrimaryAction()
    }

    private fun buildDraft(): CreateEventDraft {
        val startSec = (combinedStart().timeInMillis / 1000L).toInt()
        val endSec = (combinedEnd().timeInMillis / 1000L).toInt()
        return CreateEventDraft(
            option = selectedOption,
            channelVoiceId = if (selectedOption == ClanEventOption.SPEAKER) channelVoiceId else 0L,
            address = if (selectedOption == ClanEventOption.LOCATION) address.trim() else "",
            channelId = if (selectedOption == ClanEventOption.PRIVATE) 0L else channelId,
            isPrivate = isPrivate,
            title = titleCell.getText().trim(),
            description = descriptionCell.getText().trim(),
            startTimeSeconds = startSec,
            endTimeSeconds = endSec,
            repeatType = repeatType,
            logoUrl = logoUrl,
            originalLogoUrl = if (isEditMode) originalLogoUrl else null,
            editingEventId = editingEventId,
            editingChannelIdOld = editingChannelIdOld,
        )
    }

    private fun bindPreview() {
        val context = fragmentView?.context ?: return
        val draft = buildDraft()
        val subtitleRes = when {
            isEditMode -> R.string.event_creator_preview_subtitle_edit
            draft.option == ClanEventOption.LOCATION -> R.string.event_creator_preview_subtitle_location
            else -> R.string.event_creator_preview_subtitle_voice
        }
        (stepPreviewPanel.findViewWithTag<TextView>("preview_subtitle"))?.text = getString(subtitleRes)
        previewHost.removeAllViews()
        previewHost.addView(buildPreviewCard(context, draft))
    }

    private fun buildPreviewCard(context: Context, draft: CreateEventDraft): View {
        val pad = LayoutHelper.dp(16)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(themeColors.surfaceVariant)
                cornerRadius = LayoutHelper.dpf(12f)
            }
        }
        val pattern = if (DateFormat.is24HourFormat(context)) "EEE, MMM d · HH:mm" else "EEE, MMM d · h:mm a"
        val timeLabel = DateTimeUtil.formatEpochSeconds(draft.startTimeSeconds, pattern, Locale.getDefault())
        root.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(ImageView(context).apply {
                    setImageDrawable(MezonIcon.eventTimeIcon.getDrawable(context, themeColors.textStrong))
                }, LayoutHelper.createLinear(18, 18))
                addView(TextView(context).apply {
                    text = timeLabel
                    textSize = 12f
                    setTextColor(themeColors.textStrong)
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(LayoutHelper.dp(6), 0, 0, 0)
                })
            },
            LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, Gravity.NO_GRAVITY, 0f, 0f, 0f, 10f),
        )
        val mainRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LayoutHelper.WRAP_CONTENT, 1f)
        }
        val badge = when {
            draft.isPrivate -> context.getString(R.string.clan_event_badge_private)
            draft.channelId != 0L -> context.getString(R.string.clan_event_badge_channel)
            else -> context.getString(R.string.clan_event_badge_clan)
        }
        textCol.addView(TextView(context).apply {
            text = badge
            textSize = 11f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = Typeface.DEFAULT_BOLD
            val ph = LayoutHelper.dp(8)
            val pv = LayoutHelper.dp(3)
            setPadding(ph, pv, ph, pv)
            background = GradientDrawable().apply {
                cornerRadius = LayoutHelper.dp(6f).toFloat()
                setColor(if (draft.isPrivate) themeColors.onSurfaceVariant else themeColors.blurple)
            }
        }, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0f, Gravity.START, 0f, 0f, 0f, 6f))
        textCol.addView(TextView(context).apply {
            text = draft.title
            textSize = 15f
            setTextColor(themeColors.textStrong)
            typeface = Typeface.DEFAULT_BOLD
        })
        if (draft.description.isNotBlank()) {
            textCol.addView(TextView(context).apply {
                text = draft.description
                textSize = 13f
                setTextColor(themeColors.onSurfaceVariant)
                maxLines = 2
            }, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, Gravity.START, 0f, 4f, 0f, 0f))
        }
        val locationText = when (draft.option) {
            ClanEventOption.LOCATION -> draft.address
            ClanEventOption.SPEAKER ->
                clanEventController.getChannel(clanId, draft.channelVoiceId)?.channelLabel
                    ?: context.getString(R.string.clan_event_private_room)
            else -> context.getString(R.string.clan_event_private_room)
        }
        val locationIcon = if (draft.option == ClanEventOption.LOCATION) MezonIcon.locationIcon else MezonIcon.channelVoice
        textCol.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(ImageView(context).apply {
                    setImageDrawable(locationIcon.getDrawable(context, themeColors.textStrong))
                }, LayoutHelper.createLinear(18, 18))
                addView(TextView(context).apply {
                    text = locationText
                    textSize = 14f
                    setTextColor(themeColors.onSurfaceVariant)
                    setPadding(LayoutHelper.dp(8), 0, 0, 0)
                })
            },
            LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, Gravity.START, 0f, 4f, 0f, 0f),
        )
        mainRow.addView(textCol)
        if (draft.logoUrl.isNotBlank()) {
            mainRow.addView(
                ClanEventCreateUi.buildEventLogoThumbnail(context, themeColors, draft.logoUrl),
                LinearLayout.LayoutParams(LayoutHelper.dp(ClanEventCreateUi.EVENT_THUMB_SIZE_DP), LayoutHelper.dp(ClanEventCreateUi.EVENT_THUMB_SIZE_DP)).apply {
                    leftMargin = LayoutHelper.dp(10)
                },
            )
        }
        root.addView(mainRow)
        return root
    }

    private fun submitCreate() {
        if (submitting) return
        submitting = true
        loadingOverlay.visibility = View.VISIBLE
        refreshPrimaryAction()
        val creatorId = accountController.accountInfo.value.userId
        val draft = buildDraft()
        val onDone: (Boolean, String?) -> Unit = { success, error ->
            submitting = false
            loadingOverlay.visibility = View.GONE
            if (success) {
                val message = if (isEditMode) {
                    getString(R.string.event_creator_update_success)
                } else {
                    getString(R.string.event_creator_create_success)
                }
                MezonToast.show(this@ClanEventCreateFragment, ToastOverlay.ToastType.SUCCESS, message)
                finishFragment()
            } else {
                refreshPrimaryAction()
                val fallback = if (isEditMode) {
                    getString(R.string.event_creator_update_failed)
                } else {
                    getString(R.string.event_creator_create_failed)
                }
                MezonToast.show(this@ClanEventCreateFragment, ToastOverlay.ToastType.ERROR, error ?: fallback)
            }
        }
        if (isEditMode) {
            clanEventController.updateEvent(draft, clanId, creatorId, onDone)
        } else {
            clanEventController.createEvent(draft, clanId, creatorId, onDone)
        }
    }
}
