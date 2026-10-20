package com.example.superapp.browser

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.webkit.ValueCallback
import android.webkit.WebChromeClient.FileChooserParams
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File

@RunWith(RobolectricTestRunner::class)
class FileChooserTest {

    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val chooser = FileChooser(activity, REQUEST)
    private val answers = mutableListOf<Array<Uri>?>()
    private val callback = ValueCallback<Array<Uri>> { answers += it }
    private val extensions = mapOf("jpg" to "image/jpeg", "pdf" to "application/pdf")

    private fun params(mode: Int = FileChooserParams.MODE_OPEN, accept: Array<String> = arrayOf(""), capture: Boolean = false) =
        object : FileChooserParams() {
            override fun getMode() = mode
            override fun getAcceptTypes() = accept
            override fun isCaptureEnabled() = capture
            override fun getTitle(): CharSequence? = null
            override fun getFilenameHint(): String? = null
            override fun createIntent() = Intent(Intent.ACTION_GET_CONTENT)
        }

    private fun startedIntent(): Intent = shadowOf(activity).nextStartedActivityForResult.intent

    private fun cameraIntents(chooserIntent: Intent): List<Intent> =
        chooserIntent.getParcelableArrayExtra(Intent.EXTRA_INITIAL_INTENTS, Intent::class.java).orEmpty().toList()

    private fun captureFiles() = File(activity.cacheDir, "captures").listFiles().orEmpty()

    @Test
    fun `accept attribute maps to picker MIME types`() {
        assertEquals(
            listOf("image/*", "image/jpeg", "application/pdf"),
            acceptedMimeTypes(arrayOf("image/*, .JPG", ".pdf", ".unknown", "", "image/*"), extensions::get),
        )
        assertEquals(emptyList<String>(), acceptedMimeTypes(arrayOf(""), extensions::get))
        assertEquals(emptyList<String>(), acceptedMimeTypes(null, extensions::get))
    }

    @Test
    fun `save requests are declined so WebView cancels them`() {
        assertFalse(chooser.show(callback, params(mode = FileChooserParams.MODE_SAVE)))
        assertNull(shadowOf(activity).nextStartedActivityForResult)
        assertTrue(answers.isEmpty())
    }

    @Test
    fun `photo uploads offer the camera next to the file picker`() {
        assertTrue(chooser.show(callback, params(accept = arrayOf("image/*"))))

        val chooserIntent = startedIntent()
        assertEquals(Intent.ACTION_CHOOSER, chooserIntent.action)
        val pick = chooserIntent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals(Intent.ACTION_GET_CONTENT, pick.action)
        assertEquals("image/*", pick.type)
        assertEquals(listOf(MediaStore.ACTION_IMAGE_CAPTURE), cameraIntents(chooserIntent).map { it.action })
    }

    @Test
    fun `documents only get the file picker`() {
        chooser.show(callback, params(accept = arrayOf("application/pdf")))

        assertEquals(Intent.ACTION_GET_CONTENT, startedIntent().action)
        assertTrue(captureFiles().isEmpty())
    }

    @Test
    fun `a photo taken with the camera is uploaded`() {
        chooser.show(callback, params(accept = arrayOf("image/*", "video/*")))
        val (photo, video) = cameraIntents(startedIntent()).map { it.getParcelableExtra(MediaStore.EXTRA_OUTPUT, Uri::class.java)!! }
        File(activity.cacheDir, "captures/${photo.lastPathSegment}").writeBytes(byteArrayOf(1, 2, 3))

        chooser.onActivityResult(REQUEST, Activity.RESULT_OK, null)

        assertEquals(1, answers.size)
        assertArrayEquals(arrayOf(photo), answers.single())
        // The photo stays for the upload; the unused camcorder file is removed.
        assertEquals(listOf(photo.lastPathSegment), captureFiles().map { it.name })
        assertFalse(File(activity.cacheDir, "captures/${video.lastPathSegment}").exists())
    }

    @Test
    fun `cancelling answers the page once and cleans up`() {
        chooser.show(callback, params(accept = arrayOf("image/*", "video/*")))

        chooser.onActivityResult(REQUEST, Activity.RESULT_CANCELED, null)
        chooser.onActivityResult(REQUEST, Activity.RESULT_CANCELED, null)

        assertEquals(listOf<Array<Uri>?>(null), answers)
        assertTrue(captureFiles().isEmpty())
    }

    @Test
    fun `picked files are passed through and items without a URI are ignored`() {
        chooser.show(callback, params(mode = FileChooserParams.MODE_OPEN_MULTIPLE))
        val picked = Uri.parse("content://media/external/images/media/1")
        val data = Intent().apply {
            clipData = ClipData("files", arrayOf("image/*"), ClipData.Item(picked)).apply { addItem(ClipData.Item("text only")) }
        }

        chooser.onActivityResult(REQUEST, Activity.RESULT_OK, data)

        assertArrayEquals(arrayOf(picked), answers.single())
    }

    @Test
    fun `a new request answers an abandoned one`() {
        chooser.show(callback, params())
        chooser.show(callback, params())

        assertEquals(listOf<Array<Uri>?>(null), answers)
    }

    @Test
    fun `results for other requests are not handled`() {
        assertFalse(chooser.onActivityResult(REQUEST + 1, Activity.RESULT_OK, null))
    }

    private companion object {
        const val REQUEST = 7
    }
}
