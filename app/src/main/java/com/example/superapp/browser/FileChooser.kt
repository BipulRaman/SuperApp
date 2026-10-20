package com.example.superapp.browser

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.webkit.ValueCallback
import android.webkit.WebChromeClient.FileChooserParams

/**
 * Answers a page's `<input type="file">` with the system file picker, also offering the camera
 * and camcorder when the page accepts photos or videos.
 */
internal class FileChooser(private val activity: Activity, private val requestCode: Int) {

    private var pending: ValueCallback<Array<Uri>>? = null
    private var captureOutputs: List<Uri> = emptyList()

    fun show(callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
        // Only opening files is supported; returning false makes WebView cancel e.g. save requests.
        if (params.mode != FileChooserParams.MODE_OPEN && params.mode != FileChooserParams.MODE_OPEN_MULTIPLE) return false
        finish(null)
        CaptureProvider.deleteOld(activity)

        val mimeTypes = acceptedMimeTypes(params.acceptTypes) { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
        val pick = Intent(Intent.ACTION_GET_CONTENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(mimeTypes.singleOrNull() ?: "*/*")
        if (mimeTypes.size > 1) pick.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes.toTypedArray())
        if (params.mode == FileChooserParams.MODE_OPEN_MULTIPLE) pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)

        val captures = captureIntents(mimeTypes)
        captureOutputs = captures.map { it.second }
        val chooser = if (captures.isEmpty()) {
            pick
        } else {
            Intent.createChooser(pick, null).putExtra(Intent.EXTRA_INITIAL_INTENTS, captures.map { it.first }.toTypedArray())
        }
        // <input capture> asks for the camera directly; the picker is the fallback.
        val attempts = if (params.isCaptureEnabled) captures.map { it.first } + chooser else listOf(chooser)
        for (intent in attempts) {
            try {
                activity.startActivityForResult(intent, requestCode)
                pending = callback
                return true
            } catch (e: ActivityNotFoundException) {
                // Try the next option.
            }
        }
        finish(null)
        return false
    }

    /** Hands the picked or captured files to the page. Returns false if the result isn't ours. */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != this.requestCode) return false
        val result = if (resultCode == Activity.RESULT_OK) {
            // Camera apps usually return no data and just fill the file they were given.
            data?.let(::pickedUris) ?: captureOutputs.firstOrNull { CaptureProvider.hasContent(activity, it) }?.let { arrayOf(it) }
        } else {
            null
        }
        finish(result)
        return true
    }

    /** Answers the pending request (if any) exactly once and deletes capture files that went unused. */
    private fun finish(result: Array<Uri>?) {
        val callback = pending
        pending = null
        captureOutputs.filter { result == null || it !in result }.forEach { CaptureProvider.delete(activity, it) }
        captureOutputs = emptyList()
        callback?.onReceiveValue(result)
    }

    private fun pickedUris(data: Intent): Array<Uri>? {
        val clip = data.clipData
        val uris = if (clip != null && clip.itemCount > 0) {
            (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
        } else {
            listOfNotNull(data.data)
        }
        return uris.takeIf { it.isNotEmpty() }?.toTypedArray()
    }

    /** Camera and/or camcorder intents, each with the file it writes to, for what the page accepts. */
    private fun captureIntents(mimeTypes: List<String>): List<Pair<Intent, Uri>> {
        val anything = mimeTypes.isEmpty() || "*/*" in mimeTypes
        val photos = anything || mimeTypes.any { it.startsWith("image/") }
        val videos = anything || mimeTypes.any { it.startsWith("video/") }
        return listOfNotNull(
            if (photos) capture(MediaStore.ACTION_IMAGE_CAPTURE, "jpg") else null,
            if (videos) capture(MediaStore.ACTION_VIDEO_CAPTURE, "mp4") else null,
        )
    }

    private fun capture(action: String, extension: String): Pair<Intent, Uri>? {
        val output = runCatching { CaptureProvider.newUri(activity, extension) }.getOrNull() ?: return null
        val intent = Intent(action)
            .putExtra(MediaStore.EXTRA_OUTPUT, output)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = ClipData.newRawUri(null, output)
        return intent to output
    }
}

/** Converts an `<input accept>` list (MIME types and/or `.ext` entries) into MIME types for a file picker. */
internal fun acceptedMimeTypes(acceptTypes: Array<String>?, mimeForExtension: (String) -> String?): List<String> =
    acceptTypes.orEmpty()
        .flatMap { it.split(',') }
        .map { it.trim().lowercase() }
        .mapNotNull { type ->
            when {
                type.startsWith('.') -> mimeForExtension(type.substring(1))
                '/' in type -> type
                else -> null
            }
        }
        .distinct()
