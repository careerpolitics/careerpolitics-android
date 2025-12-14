package com.murari.careerpolitics.activities

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.DialogFragment
import com.murari.careerpolitics.R
import androidx.core.net.toUri

class ForemAppDialog : DialogFragment() {

    companion object {
        private const val PACKAGE_NAME = "com.murari.careerpolitics"
        private const val FOREM_URL_KEY = "ForemAppDialog.url"

        fun newInstance(url: String): ForemAppDialog {
            return ForemAppDialog().apply {
                arguments = Bundle().apply {
                    putString(FOREM_URL_KEY, url)
                }
            }
        }

        fun isAppInstalled(activity: Activity): Boolean {
            return try {
                activity.packageManager.getPackageInfo(PACKAGE_NAME, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }

        fun openApp(activity: Activity, url: String?) {
            val intent = activity.packageManager.getLaunchIntentForPackage(PACKAGE_NAME)
            if (url != null) {
                intent?.data = Uri.parse(url)
            }
            if (intent != null) {
                activity.startActivity(intent)
            }
        }
    }

    private lateinit var url: String

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        dialog?.window?.setBackgroundDrawableResource(R.drawable.forem_dialog_fragment_background)
        val view = inflater.inflate(R.layout.forem_app_dialog, container, false)

        url = arguments?.getString(FOREM_URL_KEY).orEmpty()

        val title = view.findViewById<TextView>(R.id.download_install_forem_app_text_view)
        val icon = view.findViewById<ImageView>(R.id.download_open_forem_image_view)
        val description = view.findViewById<TextView>(R.id.forem_app_dialog_description_text_view)
        val layout = view.findViewById<ConstraintLayout>(R.id.download_forem_app_layout)

        val installed = isAppInstalled(requireActivity())

        if (installed) {
            title.text = getString(R.string.open_forem_app)
            description.text = getString(R.string.forem_app_dialog_description_if_installed)
            icon.setImageResource(R.drawable.ic_compass)
        } else {
            title.text = getString(R.string.download_forem_app)
            description.text = getString(R.string.forem_app_dialog_description)
            icon.setImageResource(R.drawable.ic_baseline_arrow_downward_24)
        }

        layout.setOnClickListener { openAppLink(installed) }

        return view
    }

    private fun openAppLink(installed: Boolean) {
        if (installed) {
            openApp(requireActivity(), url)
        } else {
            val playIntent = try {
                Intent(Intent.ACTION_VIEW, "market://details?id=$PACKAGE_NAME".toUri())
            } catch (e: ActivityNotFoundException) {
                Intent(Intent.ACTION_VIEW,
                    "https://play.google.com/store/apps/details?id=$PACKAGE_NAME".toUri())
            }
            startActivity(playIntent)
        }
        dismiss()
    }
}
