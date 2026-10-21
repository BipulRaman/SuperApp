package com.example.superapp.browser

import android.app.Activity
import android.app.DownloadManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.widget.Toast
import com.example.superapp.R

/**
 * Saves files a page offers for download, with the page's cookies, and without any storage
 * permission: into the public Downloads folder on Android 10+, and into SuperApp's own Downloads
 * folder (Android/data/...) on Android 8-9, where the public one would need a permission. Either
 * way the download notification opens the file.
 */
internal class Downloads(private val activity: Activity) {

    fun start(url: String, userAgent: String, contentDisposition: String?, mimeType: String?) {
        if (!URLUtil.isNetworkUrl(url)) return toast(activity.getString(R.string.download_unsupported))
        val manager = activity.getSystemService(DownloadManager::class.java)
            ?: return toast(activity.getString(R.string.download_failed))
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setMimeType(mimeType)
                .setTitle(fileName)
                .addRequestHeader("User-Agent", userAgent)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            if (Build.VERSION.SDK_INT >= 29) {
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            } else {
                request.setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            CookieManager.getInstance().getCookie(url)?.let { request.addRequestHeader("Cookie", it) }
            manager.enqueue(request)
            toast(activity.getString(R.string.download_started, fileName))
        } catch (e: RuntimeException) {
            toast(activity.getString(R.string.download_failed))
        }
    }

    private fun toast(message: String) = Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
}
