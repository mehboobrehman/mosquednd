package com.mosquednd.ui

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mosquednd.R
import com.mosquednd.util.LogHelper

/**
 * Discloses why MosqueDND needs [ACCESS_BACKGROUND_LOCATION][android.Manifest.permission.ACCESS_BACKGROUND_LOCATION]
 * *before* the system permission dialog is shown.
 *
 * The rationale is shown ahead of the permission request so the user understands the
 * purpose (mosque proximity → automatic DND), the monitoring-gated nature of the access,
 * the local-only storage, and the absence of third-party sharing.
 *
 * Communication: the host (e.g. [HomeFragment]) registers a [FragmentResult][androidx.fragment.app.FragmentManager.setFragmentResultListener]
 * listener for [REQUEST_KEY]. When the user confirms, this fragment posts [ACTION_GRANT] and
 * the host launches the system permission request. On dismiss it posts [ACTION_DISMISS].
 */
class BackgroundLocationRationaleFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.bg_rationale_title)
            .setMessage(R.string.bg_rationale_message)
            .setPositiveButton(R.string.bg_rationale_proceed) { _, _ ->
                LogHelper.logd(TAG, "User confirmed rationale — posting GRANT result")
                postAction(ACTION_GRANT)
            }
            .setNegativeButton(R.string.bg_rationale_dismiss) { _, _ ->
                LogHelper.logd(TAG, "User declined rationale — posting DISMISS result")
                postAction(ACTION_DISMISS)
            }
            .setOnCancelListener {
                LogHelper.logd(TAG, "Dialog cancelled (back/outside) — posting DISMISS result")
                postAction(ACTION_DISMISS)
            }
            .create()
    }

    /**
     * Delivers the chosen action to whoever registered a result listener for [REQUEST_KEY]
     * on the same [androidx.fragment.app.FragmentManager] this dialog was added to, then
     * dismisses the dialog.
     */
    private fun postAction(action: String) {
        parentFragmentManager.setFragmentResult(
            REQUEST_KEY,
            Bundle().apply { putString(KEY_ACTION, action) }
        )
    }

    companion object {
        private const val TAG = "BgLocRationale"

        /** Fragment result request key used to communicate the user's choice. */
        const val REQUEST_KEY = "bg_location_rationale"

        /** Bundle key under which the chosen action ([ACTION_GRANT]/[ACTION_DISMISS]) is stored. */
        const val KEY_ACTION = "key_action"

        /** User confirmed the rationale — host should launch the permission request. */
        const val ACTION_GRANT = "action_grant"

        /** User declined/cancelled the rationale — host should abort the permission request. */
        const val ACTION_DISMISS = "action_dismiss"

        /** Show from a [Fragment] host (e.g. HomeFragment). */
        fun show(host: Fragment) {
            BackgroundLocationRationaleFragment().show(host.parentFragmentManager, TAG)
        }

        /** Show from an [AppCompatActivity] host (e.g. MainActivity). */
        fun show(activity: AppCompatActivity) {
            BackgroundLocationRationaleFragment().show(activity.supportFragmentManager, TAG)
        }
    }
}
