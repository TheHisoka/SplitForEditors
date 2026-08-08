package com.splitforeditors.app

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.roundToLong

data class Cut(val id: Int, val ms: Long)

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { App() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun App() {
 val context = LocalContext.current; val scope = rememberCoroutineScope()
 var uri by remember { mutableStateOf<Uri?>(null) }; var duration by remember { mutableLongStateOf(1) }; var pos by remember { mutableLongStateOf(0) }
 var cuts by remember { mutableStateOf(listOf<Cut>()) }; var nextId by remember { mutableIntStateOf(1) }; var exporting by remember { mutableStateOf(false) }; var msg by remember { mutableStateOf("") }
 val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) { uri=u; cuts=emptyList(); pos=0; msg="" } }
 val player = remember(uri) { uri?.let { ExoPlayer.Builder(context).build().also { p -> p.setMediaItem(MediaItem.fromUri(it)); p.prepare() } } }
 DisposableEffect(player) { onDispose { player?.release() } }
 LaunchedEffect(player) { while (player != null) { pos=player.currentPosition.coerceAtLeast(0); if (player.duration>0) duration=player.duration; delay(80) } }
 MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFF8B7CFF),secondary=Color(0xFF59D6C8),background=Color(0xFF0B0B10),surface=Color(0xFF15151D),surfaceVariant=Color(0xFF20202A))) {
  Scaffold(containerColor=MaterialTheme.colorScheme.background, topBar={ TopAppBar(title={Column{Text("Split For Editors",fontWeight=FontWeight.Bold);Text("Precision video splitting",style=MaterialTheme.typography.labelSmall)}},actions={IconButton({picker.launch(arrayOf("video/*"))}){Icon(Icons.Default.VideoLibrary,"Open")}}) }) { pad ->
   Column(Modifier.fillMaxSize().padding(pad).padding(horizontal=14.dp)) {
    if(uri==null){ Spacer(Modifier.weight(1f)); Icon(Icons.Default.VideoLibrary,null,Modifier.size(70.dp)); Spacer(Modifier.height(12.dp)); Text("Split your video.",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold); Text("Place multiple cuts and export every segment separately."); Spacer(Modifier.height(20.dp)); Button({picker.launch(arrayOf("video/*"))},Modifier.fillMaxWidth().height(54.dp)){Text("SELECT VIDEO")}; Spacer(Modifier.weight(1f)) }
    else {
     Spacer(Modifier.height(8.dp)); Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(18.dp))){AndroidView(factory={PlayerView(it).apply{this.player=player;useController=true}},Modifier.fillMaxSize())}
     Spacer(Modifier.height(12.dp)); Row{Text("TIMELINE",fontWeight=FontWeight.Bold);Spacer(Modifier.weight(1f));Text("${cuts.size+1} clips",color=MaterialTheme.colorScheme.secondary)};Spacer(Modifier.height(8.dp))
     Timeline(duration,pos,cuts,{player?.seekTo(it)},{id,ms->cuts=cuts.map{if(it.id==id)it.copy(ms=ms)else it}.sortedBy{it.ms}})
     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(format(pos),fontWeight=FontWeight.Bold);Text(format(duration))};Spacer(Modifier.height(8.dp))
     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
      Button({val p=pos.coerceIn(1,duration-1);if(cuts.none{abs(it.ms-p)<100})cuts=(cuts+Cut(nextId++,p)).sortedBy{it.ms}},Modifier.weight(1f)){Text("✂  ADD CUT")}
      OutlinedButton({if(cuts.isNotEmpty())cuts=cuts.dropLast(1)},Modifier.weight(1f)){Icon(Icons.Default.Delete,null);Text(" REMOVE")}
     }
     Spacer(Modifier.height(8.dp)); Text("${cuts.size+1} output clips",fontWeight=FontWeight.Bold)
     val edges=listOf(0L)+cuts.map{it.ms}+duration
     LazyColumn(Modifier.heightIn(max=150.dp)){itemsIndexed(edges.dropLast(1)){i,start->Row(Modifier.fillMaxWidth().padding(vertical=2.dp).background(MaterialTheme.colorScheme.surface,RoundedCornerShape(10.dp)).padding(9.dp)){Text("CLIP ${i+1}",fontWeight=FontWeight.Bold,Modifier.width(70.dp));Text("${format(start)} → ${format(edges[i+1])}",Modifier.weight(1f));Text(format(edges[i+1]-start),color=MaterialTheme.colorScheme.secondary)}}}
     Spacer(Modifier.weight(1f)); if(msg.isNotBlank())Text(msg,style=MaterialTheme.typography.bodySmall);Spacer(Modifier.height(6.dp))
     Button(enabled=!exporting&&cuts.isNotEmpty(),onClick={exporting=true;msg="Exporting…";scope.launch{val r=withContext(Dispatchers.IO){Splitter.exportAll(context.contentResolver,uri!!,edges)};exporting=false;msg=r.fold({"✓ ${it.size} clips saved to Movies/Split For Editors"},{"Export failed: ${it.message?:"unknown error"}"});Toast.makeText(context,msg,Toast.LENGTH_LONG).show()}},Modifier.fillMaxWidth().height(56.dp),shape=RoundedCornerShape(16.dp)){Icon(Icons.Default.FileDownload,null);Spacer(Modifier.width(8.dp));Text(if(exporting)"EXPORTING…" else "SAVE ALL ${cuts.size+1} CLIPS")};Spacer(Modifier.height(12.dp))
    }
   }
  }
 }
}

@Composable fun Timeline(duration:Long,pos:Long,cuts:List<Cut>,onSeek:(Long)->Unit,onMove:(Int,Long)->Unit){val width=1000.dp;Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())){Box(Modifier.width(width).height(88.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).pointerInput(duration){detectTapGestures{onSeek((it.x/size.width*duration).roundToLong())}}){Canvas(Modifier.fillMaxSize()){val w=size.width;for(i in 0..40){val x=w*i/40f;drawLine(Color.White.copy(alpha=if(i%5==0).28f else .12f),Offset(x,12f),Offset(x,size.height-12f),if(i%5==0)2f else 1f)};val x=pos.toFloat()/duration.coerceAtLeast(1)*w;drawLine(Color(0xFF59D6C8),Offset(x,0f),Offset(x,size.height),4f)};cuts.forEach{c->val f=c.ms.toFloat()/duration.coerceAtLeast(1);Box(Modifier.offset(x=f*1000.dp-7.dp).fillMaxHeight().width(14.dp).background(Color(0xFF8B7CFF)).pointerInput(c.id){detectDragGestures{ch,d->val x=(f*size.width+d.x).coerceIn(0f,size.width.toFloat());onMove(c.id,(x/size.width*duration).roundToLong());ch.consume()}})}}}}

fun format(ms:Long):String{val x=ms.coerceAtLeast(0);val s=x/1000;return "%02d:%02d.%03d".format(s/60,s%60,x%1000)}

object Splitter{
 fun exportAll(r:ContentResolver,src:Uri,edges:List<Long>):Result<List<Uri>>=runCatching{(0 until edges.size-1).map{i->one(r,src,edges[i],edges[i+1],i+1)}}
 private fun one(r:ContentResolver,src:Uri,start:Long,end:Long,n:Int):Uri{
  val ex=android.media.MediaExtractor();r.openFileDescriptor(src,"r")!!.use{ex.setDataSource(it.fileDescriptor)}
  val v=(0 until ex.trackCount).firstOrNull{ex.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME)?.startsWith("video/")==true}?:error("Video track not found")
  val a=(0 until ex.trackCount).firstOrNull{ex.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/")==true}
  val cv=ContentValues().apply{put(MediaStore.Video.Media.DISPLAY_NAME,"Split_%02d.mp4".format(n));put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");put(MediaStore.Video.Media.RELATIVE_PATH,Environment.DIRECTORY_MOVIES+"/Split For Editors");put(MediaStore.Video.Media.IS_PENDING,1)}
  val out=r.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,cv)?:error("Cannot create output")
  try{r.openFileDescriptor(out,"w")!!.use{pfd->val m=android.media.MediaMuxer(pfd.fileDescriptor,android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);val vo=m.addTrack(ex.getTrackFormat(v));val ao=a?.let{m.addTrack(ex.getTrackFormat(it))};m.start();val buf=ByteBuffer.allocate(2*1024*1024);val info=android.media.MediaCodec.BufferInfo();fun copy(t:Int,o:Int){ex.unselectTrack(v);a?.let{ex.unselectTrack(it)};ex.selectTrack(t);ex.seekTo(start*1000,android.media.MediaExtractor.SEEK_TO_PREVIOUS_SYNC);while(true){val time=ex.sampleTime;if(time<0||time>=end*1000)break;buf.clear();val size=ex.readSampleData(buf,0);if(size<0)break;info.offset=0;info.size=size;info.presentationTimeUs=(time-start*1000).coerceAtLeast(0);info.flags=ex.sampleFlags;m.writeSampleData(o,buf,info);ex.advance()}};copy(v,vo);if(a!=null&&ao!=null)copy(a,ao);m.stop();m.release()};cv.clear();cv.put(MediaStore.Video.Media.IS_PENDING,0);r.update(out,cv,null,null);return out}catch(e:Exception){r.delete(out,null,null);throw e}finally{ex.release()}
 }
}
