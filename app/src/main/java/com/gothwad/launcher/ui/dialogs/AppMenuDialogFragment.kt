package com.gothwad.launcher.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.gothwad.launcher.Actions
import com.gothwad.launcher.R
import com.gothwad.launcher.data.AppEntry
import com.gothwad.launcher.data.ConfigStore
import com.gothwad.launcher.data.LauncherConfig
import kotlinx.coroutines.launch

/**
 * Quick actions for one app tile (long-press / menu key): favourite, hide, lock, app info,
 * uninstall. Built programmatically - one small dialog, no layout file to keep in sync.
 *
 * Every change goes through [ConfigStore]; the home grid re-renders from the config flow.
 */
class AppMenuDialogFragment : DialogFragment() {

    private var app: AppEntry? = null
    private var config: LauncherConfig = LauncherConfig()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext())
        val ctx = requireContext()
        val density = ctx.resources.displayMetrics.density
        val current = app ?: return dialog

        val scroll = ScrollView(ctx)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * density).toInt(), (16 * density).toInt(), (24 * density).toInt(), (8 * density).toInt())
        }
        scroll.addView(root)

        fun button(label: String, onClick: () -> Unit): Button = Button(ctx).apply {
            text = label
            setBackgroundResource(R.drawable.bg_settings_row)
            stateListAnimator = null
            minHeight = (48 * density).toInt()
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding((24 * density).toInt(), 0, (24 * density).toInt(), 0)
            setTextColor(android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt()))
            textSize = 14f
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = (6 * density).toInt()
            layoutParams = lp
            setOnClickListener {
                onClick()
            }
        }

        val store = ConfigStore(ctx)
        fun update(transform: (LauncherConfig) -> LauncherConfig) {
            viewLifecycleOwnerLifecycleScope().launch { store.update(transform) }
        }

        // Text: dark when focused/selected/pressed - same contract as the settings sheet.
        val textStateList = androidx.core.content.ContextCompat.getColorStateList(ctx, R.color.color_settings_btn_text)

        fun styled(label: String, onClick: () -> Unit): Button = button(label, onClick).also {
            if (textStateList != null) it.setTextColor(textStateList)
        }

        root.addView(styled(ctx.getString(R.string.app_menu_launch)) {
            dismiss()
            Actions.launchApp(ctx, current.pkg)
        })
        root.addView(styled(ctx.getString(R.string.app_menu_app_info)) {
            dismiss()
            Actions.openAppInfo(ctx, current.pkg)
        })
        root.addView(styled(ctx.getString(R.string.app_menu_uninstall)) {
            dismiss()
            Actions.uninstall(ctx, current.pkg)
        })

        val isFav = current.pkg in config.favorites
        root.addView(styled(
            ctx.getString(if (isFav) R.string.app_menu_unfavourite else R.string.app_menu_favourite)
        ) {
            update {
                it.copy(
                    favorites = if (isFav) it.favorites - current.pkg
                    else (it.favorites + current.pkg).distinct(),
                    showFavoritesRow = if (isFav) it.showFavoritesRow else true,
                )
            }
            dismiss()
        })

        val isHidden = current.pkg in config.hidden
        root.addView(styled(
            ctx.getString(if (isHidden) R.string.app_menu_unhide else R.string.app_menu_hide)
        ) {
            update {
                it.copy(hidden = if (isHidden) it.hidden - current.pkg else it.hidden + current.pkg)
            }
            dismiss()
        })

        val isLocked = current.pkg in config.lockedApps
        root.addView(styled(
            ctx.getString(if (isLocked) R.string.app_menu_unlock else R.string.app_menu_lock)
        ) {
            update {
                it.copy(
                    lockedApps = if (isLocked) it.lockedApps - current.pkg
                    else it.lockedApps + current.pkg,
                )
            }
            dismiss()
        })

        dialog.setContentView(scroll)
        dialog.window?.setLayout(
            (320 * density).toInt().coerceAtMost(
                ctx.resources.displayMetrics.widthPixels - (48 * density).toInt()
            ),
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        return dialog
    }

    companion object {
        const val TAG = "AppMenuDialog"

        fun newInstance(app: AppEntry, config: LauncherConfig): AppMenuDialogFragment =
            AppMenuDialogFragment().apply {
                this.app = app
                this.config = config
            }
    }
}
