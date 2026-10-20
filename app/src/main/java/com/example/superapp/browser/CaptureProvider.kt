package com.example.superapp.browser

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException

/**
 * Gives camera apps a private file to write a photo or video into when a page asks for an upload
 * (a minimal FileProvider; the app uses no AndroidX). Not exported: another app can only reach a
 * file through the one-off URI permission granted with the capture intent.
 */
class CaptureProvider : ContentProvider() {

    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        ParcelFileDescriptor.open(fileFor(requireNotNull(context), uri), ParcelFileDescriptor.parseMode(mode))

    override fun getType(uri: Uri): String? =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileFor(requireNotNull(context), uri).extension)

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val file = fileFor(requireNotNull(context), uri)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val row = columns.map { column ->
            when (column) {
                OpenableColumns.DISPLAY_NAME -> file.name
                OpenableColumns.SIZE -> file.length()
                else -> null
            }
        }
        return MatrixCursor(columns, 1).apply { addRow(row) }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        private const val DIR = "captures"

        // Kept for a day, so an upload that is still in progress can read its file.
        private const val MAX_AGE_MS = 24 * 60 * 60 * 1000L

        private fun authority(context: Context) = "${context.packageName}.captures"

        /** A new, empty file for one capture, as a content URI. */
        fun newUri(context: Context, extension: String): Uri {
            val dir = File(context.cacheDir, DIR).apply { mkdirs() }
            val file = File.createTempFile("capture-", ".$extension", dir)
            return Uri.Builder().scheme("content").authority(authority(context)).appendPath(file.name).build()
        }

        fun hasContent(context: Context, uri: Uri): Boolean =
            runCatching { fileFor(context, uri).length() > 0 }.getOrDefault(false)

        fun delete(context: Context, uri: Uri) {
            runCatching { fileFor(context, uri).delete() }
        }

        /** Removes captures from earlier sessions. */
        fun deleteOld(context: Context) {
            val cutoff = System.currentTimeMillis() - MAX_AGE_MS
            File(context.cacheDir, DIR).listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        }

        private fun fileFor(context: Context, uri: Uri): File {
            val dir = File(context.cacheDir, DIR).canonicalFile
            val name = uri.lastPathSegment
            if (uri.authority != authority(context) || name == null) throw FileNotFoundException(uri.toString())
            val file = File(dir, name).canonicalFile
            // Only plain file names inside the captures folder; no path tricks.
            if (file.parentFile != dir) throw FileNotFoundException(uri.toString())
            return file
        }
    }
}
