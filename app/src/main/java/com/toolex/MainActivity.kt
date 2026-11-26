package com.toolex

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel

val Red = Color(0xffcf3024)
val Blue = Color(0xff195a9c)
val Gold = Color(0xfff0b125)
private val Cream = Color(0xfff5f2eb)
private val Ink = Color(0xff141413)
val sizes = listOf("A4","A5","Letter","Original")
class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: Workspace = viewModel()
            val dark = when(vm.defaults.theme) { "Dark" -> true; "Light" -> false; else -> isSystemInDarkTheme() }
            SideEffect {
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.DKGRAY)
                enableEdgeToEdge(style,style)
            }
            MaterialTheme(colorScheme=if (dark) darkColorScheme(background=Color(0xff211f1c),surface=Color(0xff211f1c),
                primary=Color(0xffff9482), secondary=Color(0xff91c1f2)) else lightColorScheme(background=Cream,surface=Cream,
                onSurface=Ink,onBackground=Ink,primary=Red,secondary=Blue,outline=Color(0xff77746d))) { App(vm) }
        }
    }
}
@Composable private fun App(vm: Workspace) {
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var explain by rememberSaveable { mutableStateOf(false) }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) try { vm.storage.connect(uri); vm.connected=true; vm.notice="Toolex folder connected." }
        catch (e: Exception) { vm.notice=e.message ?: "Folder access was denied." }
        else vm.notice="Folder access cancelled. Your export is still available."
    }
    LaunchedEffect(vm.notice) { if (vm.notice.isNotBlank()) { snackbar.showSnackbar(vm.notice); vm.notice="" } }
    val titles=listOf("Image to PDF","Photo Editing","Settings")
    val icons=listOf(Icons.Outlined.PictureAsPdf,Icons.Outlined.Tune,Icons.Outlined.Settings)
    Scaffold(containerColor=MaterialTheme.colorScheme.background,snackbarHost={ SnackbarHost(snackbar) },bottomBar={
        Column(Modifier.background(MaterialTheme.colorScheme.surface).navigationBarsPadding()) {
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                titles.forEachIndexed { i,title ->
                    val color=if (vm.tab==i) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    TextButton(onClick={vm.tab=i},modifier=Modifier.weight(1f)) {
                        Column(horizontalAlignment=Alignment.CenterHorizontally) { Icon(icons[i],title,tint=color); Text(if(i==1)"Edit" else title,color=color,fontSize=12.sp) }
                    }
                }
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding),contentAlignment=Alignment.TopCenter) {
            Crossfade(targetState=vm.tab,label="Workspace") { tab ->
                val scroll= rememberScrollState()
                Column(Modifier.widthIn(max=660.dp).fillMaxWidth().verticalScroll(scroll).padding(bottom=24.dp)) {
                    Hero(titles[tab],listOf("Turn images into a single PDF file","Crop, adjust & make it better","Your preferences, your workflow")[tab],tab)
                    Column(Modifier.padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        when(tab) { 0 -> PdfScreen(vm) { explain=true }; 1 -> EditorScreen(vm) { explain=true }; else -> Settings(vm) { explain=true } }
                    }
                }
            }
        }
    }
    if (explain) AlertDialog(onDismissRequest={explain=false},title={Text("Connect your export folder")},text={
        Text("Open Internal storage. Choose Toolex, or create a folder named Toolex there. Tap “Use this folder”. Your images and PDFs stay there after uninstalling.")
    },confirmButton={TextButton(onClick={
        explain=false
        val initial = if (android.os.Build.VERSION.SDK_INT>=26) DocumentsContract.buildDocumentUri("com.android.externalstorage.documents","primary:Toolex") else null
        try { folder.launch(initial) } catch(e:Exception){vm.notice="System folder picker is unavailable."}
    }){Text("Choose folder")}},dismissButton={TextButton(onClick={explain=false}){Text("Later")}})
}
@Composable private fun Hero(title:String, subtitle:String, index:Int) {
    val ink = MaterialTheme.colorScheme.onSurface
    Box(Modifier.fillMaxWidth().height(if(index==0) 228.dp else 196.dp).clipToBounds()) {
        Canvas(Modifier.align(Alignment.TopEnd).width(132.dp).fillMaxHeight()) {
            val w=size.width;val h=size.height
            when(index) {
                0 -> {
                    drawCircle(Blue,w*.82f,Offset(w*1.12f,0f))
                    drawLine(Red,Offset(w*.29f,24.dp.toPx()),Offset(w*.29f,h*.90f),1.5.dp.toPx())
                    drawArc(ink,263f,73f,false,Offset(w*-.22f,h*.50f),androidx.compose.ui.geometry.Size(w*1.9f,h*.78f),style=androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                    drawCircle(Gold,16.dp.toPx(),Offset(w*.29f,h*.53f))
                }
                1 -> {
                    drawCircle(Gold,w*.58f,Offset(w*1.08f,h*.39f))
                    drawRect(Red,Offset(w*.24f,h*.55f),androidx.compose.ui.geometry.Size(24.dp.toPx(),24.dp.toPx()))
                    drawLine(ink,Offset(w*.05f,h*.34f),Offset(w,h*.93f),1.dp.toPx())
                }
                else -> {
                    drawCircle(Blue,23.dp.toPx(),Offset(w*.55f,h*.52f))
                    drawLine(Gold,Offset(w*.68f,h*.09f),Offset(w*.68f,h*.93f),3.dp.toPx())
                    drawLine(ink,Offset(0f,h*.82f),Offset(w,h*.23f),1.dp.toPx())
                }
            }
        }
        Column(Modifier.align(Alignment.CenterStart).padding(start=24.dp,end=80.dp)) {
            Text(when(index){0->"Image\nto PDF";1->"Photo\nEditing";else->title},fontSize=42.sp,lineHeight=41.sp,letterSpacing=(-2).sp,fontWeight=FontWeight.Normal)
            Spacer(Modifier.height(7.dp)); HorizontalDivider(Modifier.width(if(index==0)210.dp else 112.dp),color=if(index==1) Blue else Red)
            Spacer(Modifier.height(7.dp)); Text(subtitle,Modifier.width(148.dp),style=MaterialTheme.typography.bodyMedium)
        }
    }
}
@Composable fun Entry(title:String, subtitle:String,icon:ImageVector,onClick:()->Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick=onClick)) {
        Row(Modifier.fillMaxWidth().padding(vertical=12.dp,horizontal=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            Icon(icon,null,modifier=Modifier.size(27.dp))
            Column(Modifier.weight(1f)) { Text(title,fontSize=18.sp); Text(subtitle,style=MaterialTheme.typography.bodySmall) }
            Icon(Icons.Outlined.ChevronRight,null,Modifier.size(19.dp))
        }
        HorizontalDivider(thickness=0.5.dp)
    }
}
@Composable fun Choice(label:String,value:String,items:List<String>,set:(String)->Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(label,Modifier.weight(1f)); Box {
            OutlinedButton(onClick={open=true}) { Text(value); Icon(Icons.Outlined.ArrowDropDown,null) }
            DropdownMenu(expanded=open,onDismissRequest={open=false}) {
                items.forEach { DropdownMenuItem(text={Text(it)},onClick={open=false;set(it)}) }
            }
        }
    }
}
@Composable fun Dial(label:String,value:Float,range:ClosedFloatingPointRange<Float>,set:(Float)->Unit) {
    Column {
        Text("$label   ${kotlin.math.round(value*100)/100}",style=MaterialTheme.typography.labelLarge)
        Slider(value=value.coerceIn(range),onValueChange=set,valueRange=range)
    }
}
@Composable fun Settings(vm:Workspace, connect:()->Unit) {
    val d=vm.defaults
    var section by rememberSaveable { mutableStateOf("") }
    fun toggle(value:String) { section=if(section==value) "" else value }
    Entry("Appearance",d.theme,Icons.Outlined.DarkMode){toggle("theme")}
    if(section=="theme") Choice("Appearance",d.theme,listOf("System","Light","Dark")) { vm.preferences(d.copy(theme=it)) }
    Entry("PDF Options","Default size, quality",Icons.Outlined.Description){toggle("pdf")}
    if(section=="pdf") {
        Choice("Default page size",d.paper,sizes) { vm.preferences(d.copy(paper=it)) }
        Dial("Default PDF quality",d.pdf.toFloat(),10f..100f) { vm.preferences(d.copy(pdf=it.toInt())) }
    }
    Entry("Image Options","Default format, JPG quality",Icons.Outlined.Image){toggle("image")}
    if(section=="image") {
        Choice("Default image format",d.format,listOf("JPG","PNG")) { vm.preferences(d.copy(format=it)) }
        Dial("Default JPG quality",d.jpg.toFloat(),1f..100f) { vm.preferences(d.copy(jpg=it.toInt())) }
    }
    Entry("Save location","/storage/emulated/0/Toolex/ · "+if(vm.connected)"Connected" else "Connect folder",Icons.Outlined.Folder,connect)
    Text("ABOUT",style=MaterialTheme.typography.labelSmall)
    Entry("Privacy","Offline processing",Icons.Outlined.VerifiedUser){toggle("privacy")}
    if(section=="privacy") Text("All processing happens on your device. No accounts, analytics, ads or network access.")
    val c=LocalContext.current
    Text("App version   "+remember { c.packageManager.getPackageInfo(c.packageName,0).versionName },style=MaterialTheme.typography.bodySmall)
}
