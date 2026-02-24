@file:Suppress("WildcardImport")

package com.murari.careerpolitics.activities

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.murari.careerpolitics.R

class ForemAppDialog : DialogFragment() {

    companion object {
        private const val PACKAGE_NAME = "com.forem.android"
        private const val FOREM_URL_KEY = "ForemAppDialog.url"

        fun newInstance(url: String): ForemAppDialog {
            return ForemAppDialog().apply {
                arguments = Bundle().apply {
                    putString(FOREM_URL_KEY, url)
                }
            }
        }

        fun isForemAppInstalled(activity: Activity?): Boolean {
            return try {
                activity?.packageManager?.getPackageInfo(PACKAGE_NAME, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w("ForemAppDialog", "Forem app package not found", e)
                false
            }
        }

        fun openForemApp(activity: Activity?, url: String?) {
            val intent = activity?.packageManager?.getLaunchIntentForPackage(PACKAGE_NAME)
            if (!url.isNullOrEmpty()) {
                intent?.putExtra(Intent.EXTRA_TEXT, url)
            }
            activity?.startActivity(intent)
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

        val titleTextView = view.findViewById<TextView>(R.id.download_install_forem_app_text_view)
        val iconImageView = view.findViewById<ImageView>(R.id.download_open_forem_image_view)
        val descTextView = view.findViewById<TextView>(R.id.forem_app_dialog_description_text_view)
        val layout = view.findViewById<ConstraintLayout>(R.id.download_forem_app_layout)

        if (isForemAppInstalled(activity)) {
            titleTextView.text = getString(R.string.open_forem_app)
            descTextView.text = getString(R.string.forem_app_dialog_description_if_installed)
            iconImageView.setImageResource(R.drawable.ic_compass)
        } else {
            titleTextView.text = getString(R.string.download_forem_app)
            descTextView.text = getString(R.string.forem_app_dialog_description)
            iconImageView.setImageResource(R.drawable.ic_baseline_arrow_downward_24)
        }

        layout.setOnClickListener { openForemAppLink() }

        return view
    }

    private fun openForemAppLink() {
        if (isForemAppInstalled(activity)) {
            openForemApp(activity, url)
        } else {
            val intent = try {
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PACKAGE_NAME"))
            } catch (e: ActivityNotFoundException) {
                Log.w("ForemAppDialog", "Play Store not available, using web fallback", e)
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PACKAGE_NAME"))
            }
            startActivity(intent)
        }
        dismiss()
    }
}
