package com.example.superapp.browser

import android.content.Context
import android.net.Uri
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.FileNotFoundException

@RunWith(RobolectricTestRunner::class)
class CaptureProviderTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val authority = "${context.packageName}.captures"

    init {
        Robolectric.setupContentProvider(CaptureProvider::class.java, authority)
    }

    @Test
    fun `a capture file can be written and read back through its URI`() {
        val uri = CaptureProvider.newUri(context, "jpg")
        assertFalse(CaptureProvider.hasContent(context, uri))

        context.contentResolver.openOutputStream(uri)!!.use { it.write(byteArrayOf(1, 2, 3)) }

        assertTrue(CaptureProvider.hasContent(context, uri))
        assertArrayEquals(byteArrayOf(1, 2, 3), context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
    }

    @Test
    fun `paths outside the captures folder are refused`() {
        val outside = Uri.parse("content://$authority/..%2Fshared_prefs%2Fsecrets.xml")
        val otherAuthority = Uri.parse("content://other.authority/capture-1.jpg")

        assertThrows(FileNotFoundException::class.java) { context.contentResolver.openInputStream(outside) }
        assertFalse(CaptureProvider.hasContent(context, otherAuthority))
    }
}
