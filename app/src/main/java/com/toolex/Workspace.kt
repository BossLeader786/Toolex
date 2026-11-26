package com.toolex

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

data class Defaults(val theme: String = "System", val paper: String = "A4", val pdf: Int = 88,
    val format: String = "JPG", val jpg: Int = 88)

class Workspace(app: Application): AndroidViewModel(app) {
    val studio = Studio(app)
    val storage = SharedStorage(app)
    private val prefs = app.getSharedPreferences("defaults",0)
    var defaults by mutableStateOf(Defaults(prefs.getString("theme","System")!!,prefs.getString("page","A4")!!,
        prefs.getInt("pdf",88),prefs.getString("format","JPG")!!,prefs.getInt("jpg",88))); private set
    var tab by mutableIntStateOf(0)
    val pages = mutableStateListOf<Photo>()
    var paper by mutableStateOf(Paper(size=defaults.paper,quality=defaults.pdf)); private set
    var photo by mutableStateOf<Photo?>(null); private set
    private fun fresh() = Edit(format=defaults.format, quality=defaults.jpg)
    var edit by mutableStateOf(fresh()); private set
    var image by mutableStateOf<Product?>(null); private set
    var pdf by mutableStateOf<Product?>(null); private set
    var pdfStatus by mutableStateOf(""); private set
    var imageStatus by mutableStateOf(""); private set
    var notice by mutableStateOf("")
    var connected by mutableStateOf(storage.tree != null)
    var saving by mutableStateOf(false); private set
    var importing by mutableStateOf(false); private set
    private val undo = mutableStateListOf<Edit>()
    private val redo = mutableStateListOf<Edit>()
    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()
    private val engine = Mutex()
    private var editing: Job? = null
    private var building: Job? = null
    private val retired = mutableListOf<File>()
    private var writing: File? = null
    private fun retire(product: Product?) {
        product?.file?.let { if (it == writing) retired.add(it) else it.delete() }
    }
    init {
        val cutoff = System.currentTimeMillis()-24*60*60*1000L
        File(app.cacheDir,"toolex").listFiles()?.filter { it.lastModified()<cutoff }?.forEach { it.delete() }
    }
    fun preferences(d: Defaults) {
        defaults = d
        prefs.edit().putString("theme",d.theme).putString("page",d.paper).putInt("pdf",d.pdf)
            .putString("format",d.format).putInt("jpg",d.jpg).apply()
        if (pages.isEmpty()) configure(paper.copy(size=d.paper,quality=d.pdf))
    }
    fun configure(value: Paper) { paper=value; invalidatePdf() }
    private fun invalidatePdf() { building?.cancel(); retire(pdf); pdf=null; pdfStatus="" }
    fun move(i: Int, to: Int) {
        if (i !in pages.indices) return
        val p = pages.removeAt(i)
        if (to >= 0) pages.add(to.coerceAtMost(pages.size),p)
        invalidatePdf()
    }
    fun import(uris: List<Uri>, editor: Boolean) {
        if (importing || uris.isEmpty()) return
        viewModelScope.launch {
            importing=true
            try {
                for (uri in uris) {
                    val p = withContext(Dispatchers.IO) { studio.inspect(uri) }
                    if (editor) { photo=p; undo.clear(); redo.clear(); edit=fresh(); render(); break }
                    pages.add(p); invalidatePdf()
                }
            } catch (e: Exception) { notice=e.message ?: "Image could not be opened." }
            finally { importing=false }
        }
    }
    fun change(e: Edit) {
        if (e == edit) return
        if (undo.size >= 60) undo.removeAt(0)
        undo.add(edit); redo.clear(); edit=e; render()
    }
    fun history(back: Boolean) {
        val from = if (back) undo else redo
        if (from.isEmpty()) return
        (if (back) redo else undo).add(edit)
        edit=from.removeAt(from.lastIndex); render()
    }
    fun reset() = change(fresh())
    private fun render() {
        val p=photo ?: return
        val settings=edit
        editing?.cancel(); retire(image); image=null; imageStatus="Updating preview…"
        editing=viewModelScope.launch {
            var pending: Product?=null
            try {
                delay(200)
                engine.withLock { withContext(Dispatchers.IO) { pending=studio.encode(p,settings) } }
                ensureActive(); image=pending; pending=null; imageStatus=""
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { imageStatus=e.message ?: "Encoding failed." }
            catch (e: OutOfMemoryError) { imageStatus="Choose a smaller image or output resolution." }
            finally { pending?.file?.delete() }
        }
    }
    fun generate() {
        if (pages.isEmpty()) return
        invalidatePdf()
        val inputs=pages.toList(); val settings=paper
        pdfStatus="Preparing pages…"
        building=viewModelScope.launch {
            var pending: Product?=null
            try {
                engine.withLock { withContext(Dispatchers.IO) {
                    pending=studio.document(inputs,settings) { msg -> launch(Dispatchers.Main) { pdfStatus=msg } }
                } }
                ensureActive(); pdf=pending; pending=null
                pdfStatus=if ((pdf?.file?.length() ?: 0) > (settings.limit() ?: Long.MAX_VALUE))
                    "Target could not be reached. You can save this larger PDF." else ""
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { pdfStatus=e.message ?: "Could not create PDF." }
            catch (e: OutOfMemoryError) { pdfStatus="Not enough memory to create this PDF." }
            finally { pending?.file?.delete() }
        }
    }
    fun save(p: Product, name: String) {
        if (saving) return
        saving=true; writing=p.file
        viewModelScope.launch {
            try {
                val uri=withContext(Dispatchers.IO) { storage.save(p.file,name,p.ext,p.mime) }
                notice="Saved to Toolex/" + android.provider.DocumentsContract.getDocumentId(uri).substringAfterLast('/')
            } catch (e: Exception) { notice=e.message ?: "Save failed. Reconnect the folder and retry." }
            finally { saving=false; writing=null; retired.forEach { it.delete() }; retired.clear(); connected=storage.tree!=null }
        }
    }
}
