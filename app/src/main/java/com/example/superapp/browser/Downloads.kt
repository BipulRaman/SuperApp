package com.example.superapp.browser

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.widget.Toast
import com.example.superapp.R

/** Saves files a page offers for download into the public Downloads folder, with the page's cookies. */
internal class Downloads(private val activity: Activity, private val requestCode: Int) {

    private var pending: (() -> Unit)? = null

    fun start(url: String, userAgent: String, contentDisposition: String?, mimeType: String?) {
        if (!URLUtil.isNetworkUrl(url)) return toast(activity.getString(R.string.download_unsupported))
        if (Build.VERSION.SDK_INT < 29 &&
            activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            pending = { start(url, userAgent, contentDisposition, mimeType) }
            activity.requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), requestCode)
            return
        }
        val manager = activity.getSystemService(DownloadManager::class.java)
            ?: return toast(activity.getString(R.string.download_failed))
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setMimeType(mimeType)
                .setTitle(fileName)
                .addRequestHeader("User-Agent", userAgent)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            CookieManager.getInstance().getCookie(url)?.let { request.addRequestHeader("Cookie", it) }
            manager.enqueue(request)
            toast(activity.getString(R.string.download_started, fileName))
        } catch (e: RuntimeException) {
            toast(activity.getString(R.string.download_failed))
        }
    }

    /** Resumes a download that was waiting for storage permission. Returns false if not ours. */
    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray): Boolean {
        if (requestCode != this.requestCode) return false
        val retry = pending
        pending = null
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            retry?.invoke()
        } else {
            toast(activity.getString(R.string.download_failed))
        }
        return true
    }

    private fun toast(message: String) = Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
}
