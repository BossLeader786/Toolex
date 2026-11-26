package com.toolex

import android.content.Context
import android.os.Environment
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import java.io.File
import java.io.IOException

object SharedStorage {
    fun hasAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    @Synchronized
    fun save(context: Context, bytes: ByteArray, name: String, extension: String): File {
        require(extension in setOf("jpg", "png", "pdf"))
        check(hasAccess(context)) { "Allow storage access to save files." }
        val directory = File(Environment.getExternalStorageDirectory(), "Toolex")
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create Toolex folder")
        val safe = name.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(80).ifBlank { "Toolex" }
        var output = File(directory, "$safe.$extension")
        var index = 1
        while (!output.createNewFile()) {
            output = File(directory, "${safe}_${index++}.$extension")
        }
        try {
            output.writeBytes(bytes)
        } catch (e: Exception) {
            output.delete()
            throw e
        }
        android.media.MediaScannerConnection.scanFile(context, arrayOf(output.absolutePath), null, null)
        return output
    }
}
