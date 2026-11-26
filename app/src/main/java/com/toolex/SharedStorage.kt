package com.toolex

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract as Docs
import java.io.File

class SharedStorage(private val context: Context) {
    private val resolver = context.contentResolver
    private val prefs = context.getSharedPreferences("storage", Context.MODE_PRIVATE)
    val tree: Uri?
        get() =
            prefs.getString("tree", null)?.let(Uri::parse)?.takeIf { uri ->
                resolver.persistedUriPermissions.any {
                    it.uri == uri && it.isWritePermission && it.isReadPermission
                }
            }

    fun connect(uri: Uri) {
        require(
            uri.authority == "com.android.externalstorage.documents" &&
                (Docs.getTreeDocumentId(uri) == "primary:Toolex" ||
                    (android.os.Build.VERSION.SDK_INT < 30 &&
                        Docs.getTreeDocumentId(uri) == "primary:"))
        ) {
            "Choose Internal storage → Toolex. Create the Toolex folder in the picker if needed."
        }
        resolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        prefs.edit().putString("tree", uri.toString()).apply()
    }

    @Synchronized
    fun save(file: File, title: String, ext: String, mime: String): Uri {
        val folder = requireNotNull(tree) { "Connect the Toolex folder in Settings." }
        val id = Docs.getTreeDocumentId(folder)
        val parent =
            if (id == "primary:") {
                val child = Docs.buildDocumentUriUsingTree(folder, "primary:Toolex")
                val children = Docs.buildChildDocumentsUriUsingTree(folder, id)
                val exists =
                    requireNotNull(
                            resolver.query(
                                children,
                                arrayOf(
                                    Docs.Document.COLUMN_DOCUMENT_ID,
                                    Docs.Document.COLUMN_MIME_TYPE,
                                ),
                                null,
                                null,
                                null,
                            )
                        )
                        .use { cursor ->
                            var found = false
                            while (cursor.moveToNext()) {
                                if (cursor.getString(0) == "primary:Toolex") {
                                    require(cursor.getString(1) == Docs.Document.MIME_TYPE_DIR) {
                                        "A file already uses the Toolex folder name."
                                    }
                                    found = true
                                    break
                                }
                            }
                            found
                        }
                if (exists) child
                else {
                    val root = Docs.buildDocumentUriUsingTree(folder, id)
                    requireNotNull(
                            Docs.createDocument(
                                resolver,
                                root,
                                Docs.Document.MIME_TYPE_DIR,
                                "Toolex",
                            )
                        )
                        .also {
                            require(Docs.getDocumentId(it) == "primary:Toolex") {
                                "The Toolex folder could not be created."
                            }
                        }
                }
            } else Docs.buildDocumentUriUsingTree(folder, id)
        val stem =
            title.trim().replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), "_").take(80).ifBlank {
                "Toolex"
            }
        // ExternalStorageProvider creates a unique file when the name is taken.
        val uri =
            requireNotNull(Docs.createDocument(resolver, parent, mime, "$stem.$ext")) {
                "Could not create the file. Check folder access."
            }
        try {
            requireNotNull(resolver.openOutputStream(uri, "w")).use { sink ->
                file.inputStream().use { it.copyTo(sink) }
            }
        } catch (e: Exception) {
            runCatching { Docs.deleteDocument(resolver, uri) }
            throw e
        }
        return uri
    }
}
