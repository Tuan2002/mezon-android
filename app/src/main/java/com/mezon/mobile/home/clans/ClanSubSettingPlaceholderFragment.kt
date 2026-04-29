package com.mezon.mobile.home.clans

import android.content.Context
import android.os.Bundle
import android.widget.LinearLayout
import com.mezon.mobile.core.BaseFragment
import com.mezon.mobile.core.LayoutHelper
import com.mezon.mobile.ui.cells.ActionBarView
import com.mezon.mobile.ui.cells.ScreenStateView

class ClanSubSettingPlaceholderFragment : BaseFragment() {

    companion object {
        private const val ARG_TITLE_RES = "titleRes"
        private const val ARG_CLAN_ID = "clanId"

        fun newInstance(titleResId: Int, clanId: Long = 0L): ClanSubSettingPlaceholderFragment =
            ClanSubSettingPlaceholderFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_TITLE_RES, titleResId)
                    putLong(ARG_CLAN_ID, clanId)
                }
            }
    }

    override fun createView(context: Context): android.view.View {
        val titleRes = arguments?.getInt(ARG_TITLE_RES)
            ?: com.mezon.mobile.R.string.clan_settings_title
        actionBar = ActionBarView(context, themeColors).apply {
            setTitle(getString(titleRes))
            setBackButtonImage(com.mezon.mobile.R.drawable.ic_arrow_left_svgrepo_com)
            setMenuOnItemClick(object : ActionBarView.ActionBarMenuOnItemClick() {
                override fun onItemClick(id: Int) {
                    if (id == -1) finishFragment()
                }
            })
        }
        val body = ScreenStateView(context, themeColors).apply {
            showEmpty(getString(com.mezon.mobile.R.string.feature_coming_soon))
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(actionBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
        root.addView(body, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f))
        fragmentView = root
        return root
    }
}
