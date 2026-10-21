package com.example.superapp

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.pm.PackageManager
import android.os.Environment
import com.example.superapp.browser.Downloads
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class PermissionsTest {

    @Test
    fun `the app asks for no permissions, only internet access granted at install`() {
        val context = RuntimeEnvironment.getApplication()
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        assertEquals(listOf(Manifest.permission.INTERNET), info.requestedPermissions?.toList())
    }

    @Test
    fun `downloads need no permission`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        Downloads(activity).start("https://example.com/files/report.pdf", "Mozilla/5.0", null, "application/pdf")

        assertNull(shadowOf(activity).lastRequestedPermission)
        val manager = activity.getSystemService(DownloadManager::class.java)
        assertEquals(1, shadowOf(manager).requestCount)
        val request = (0L..2L).firstNotNullOf { shadowOf(manager).getRequest(it) }
        val destination = shadowOf(request).destination.toString()
        assertTrue(destination, destination.contains(Environment.DIRECTORY_DOWNLOADS) && destination.endsWith("report.pdf"))
    }
}
