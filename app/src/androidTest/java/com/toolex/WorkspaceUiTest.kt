package com.toolex

import android.provider.DocumentsContract
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import java.util.regex.Pattern
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test
    fun navigateEditAndChangeTheme() {
        ui.onNodeWithText("Edit", substring = false).performClick()
        ui.waitUntil(30000) {
            ui.onAllNodesWithText("Output   ", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithText("Crop", substring = false).performScrollTo().performClick()
        ui.onNodeWithText("Rotate 90°").performScrollTo().performClick()
        ui.onNodeWithText("Settings", substring = false).performClick()
        ui.onNodeWithText("Appearance").performScrollTo().performClick()
        ui.onAllNodesWithText("System").onLast().performClick()
        ui.onNodeWithText("Dark").performClick()
        Assert.assertEquals(
            "Dark",
            ui.activity.getSharedPreferences("defaults", 0).getString("theme", ""),
        )
        ui.onAllNodesWithText("Dark").onLast().performClick()
        ui.onNodeWithText("Light").performClick()
        Assert.assertEquals(
            "Light",
            ui.activity.getSharedPreferences("defaults", 0).getString("theme", ""),
        )
    }

    @Test
    fun folderConsentAndTwoByteIdenticalExports() {
        Assume.assumeTrue(
            InstrumentationRegistry.getArguments().getString("toolexStorageE2e") == "true"
        )
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("mkdir -p /sdcard/Toolex")
        ui.onNodeWithText("Settings", substring = false).performClick()
        ui.onNodeWithText("Save location").performScrollTo().performClick()
        ui.onNodeWithText("Choose folder").performClick()
        fun button(names: String): UiObject2? =
            device.wait(
                Until.findObject(By.text(Pattern.compile(names, Pattern.CASE_INSENSITIVE))),
                15000,
            )
        val select =
            if (android.os.Build.VERSION.SDK_INT <= 29) {
                device.wait(Until.findObject(By.res("android:id/button1")), 20000)
            } else button("Use this folder|Select|Allow access to .*|Choose|Save")
        requireNotNull(select) {
                "Folder picker selection button missing in " + device.currentPackageName
            }
            .click()
        button("Allow|Use this folder|OK")?.click()
        val storage = SharedStorage(ui.activity)
        ui.waitUntil(15000) { storage.tree != null }
        val studio = Studio(ui.activity)
        val sample =
            studio.inspect(
                android.net.Uri.parse("android.resource://com.toolex/${R.drawable.studio_photo}")
            )
        val encoded = studio.encode(sample, Edit(format = "PNG", width = 80, height = 60))
        val resolver = ui.activity.contentResolver
        val first = storage.save(encoded.file, "Toolex-CI-export", "png", "image/png")
        val second = storage.save(encoded.file, "Toolex-CI-export", "png", "image/png")
        Assert.assertNotEquals(first, second)
        for (uri in listOf(first, second)) {
            Assert.assertTrue(DocumentsContract.getDocumentId(uri).startsWith("primary:Toolex/"))
            Assert.assertArrayEquals(
                encoded.file.readBytes(),
                resolver.openInputStream(uri)!!.use { it.readBytes() },
            )
        }
    }
}
