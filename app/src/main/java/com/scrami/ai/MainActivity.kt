package com.scrami.ai

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var root: LinearLayout
    private lateinit var chat: LinearLayout
    private lateinit var input: EditText
    private lateinit var send: TextView
    private lateinit var status: TextView
    private lateinit var modelProgress: ProgressBar
    private lateinit var modelLabel: TextView
    private val http = OkHttpClient()
    private val prefs by lazy { getSharedPreferences("scrami", MODE_PRIVATE) }
    private var model: Any? = null
    private val modelFileName = "qwen2.5-0.5b-instruct-q4_0.gguf"
    private val modelUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_0.gguf"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        loadSavedChat()
        showSplash()
    }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(Color.rgb(8, 9, 13))
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(4), 0, dp(10)) }
        val logo = TextView(this).apply {
            text = "S"; gravity = Gravity.CENTER; textSize = 19f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); background = gradient(intArrayOf(Color.rgb(154,83,255), Color.rgb(74,45,150)), 18f)
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(42), dp(42)))
        val titleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12),0,0,0) }
        val title = TextView(this).apply { text = "Scrami AI"; textSize = 19f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) }
        status = TextView(this).apply { text = "PRIVATE • LOCAL • FREE"; textSize = 10f; setTextColor(Color.rgb(158,153,174)); letterSpacing = 0.08f }
        titleBox.addView(title); titleBox.addView(status)
        header.addView(titleBox, LinearLayout.LayoutParams(0,-2,1f))
        val newChat = TextView(this).apply { text = "＋"; textSize = 25f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setOnClickListener { newChat() } }
        header.addView(newChat, LinearLayout.LayoutParams(dp(48),dp(44)))
        root.addView(header)
        root.addView(View(this).apply { setBackgroundColor(Color.rgb(30,31,40)) }, LinearLayout.LayoutParams(-1,dp(1)))
        val scroll = ScrollView(this).apply { isFillViewport = true; setPadding(0,dp(8),0,dp(8)); clipToPadding = false }
        chat = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0,dp(8),0,dp(8)) }
        scroll.addView(chat); root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))
        val modelCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14),dp(10),dp(14),dp(10)); background = rounded(Color.rgb(18,19,26),18f,Color.rgb(38,39,50)) }
        modelLabel = TextView(this).apply { text = "Preparing local model…"; textSize = 12f; setTextColor(Color.rgb(197,192,214)) }
        modelProgress = ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply { max=100; progress=0 }
        modelCard.addView(modelLabel); modelCard.addView(modelProgress,LinearLayout.LayoutParams(-1,dp(4)))
        root.addView(modelCard,LinearLayout.LayoutParams(-1,dp(54)).apply { bottomMargin=dp(8) })
        val composer = LinearLayout(this).apply { gravity=Gravity.BOTTOM }
        input = EditText(this).apply {
            hint="Message Scrami…"; hintTextColor=Color.rgb(115,111,129); setTextColor(Color.WHITE); textSize=16f
            setPadding(dp(16),dp(12),dp(12),dp(12)); background=rounded(Color.rgb(20,21,29),24f,Color.rgb(43,44,56)); maxLines=5; minLines=1
        }
        composer.addView(input,LinearLayout.LayoutParams(0,dp(56),1f))
        send = TextView(this).apply {
            text="↑"; gravity=Gravity.CENTER; textSize=24f; typeface=Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            background=gradient(intArrayOf(Color.rgb(154,83,255),Color.rgb(104,56,210)),28f); setOnClickListener { sendMessage() }
        }
        composer.addView(send,LinearLayout.LayoutParams(dp(56),dp(56)).apply { leftMargin=dp(8) }); root.addView(composer)
        setContentView(root)
    }

    private fun showSplash() {
        val overlay = FrameLayout(this).apply { setBackgroundColor(Color.rgb(8,9,13)) }
        val box = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER }
        val mark = TextView(this).apply { text="S"; gravity=Gravity.CENTER; textSize=48f; typeface=Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); background=gradient(intArrayOf(Color.rgb(172,91,255),Color.rgb(75,45,154)),32f) }
        box.addView(mark,LinearLayout.LayoutParams(dp(96),dp(96)))
        box.addView(TextView(this).apply { text="SCRAMI AI"; textSize=28f; typeface=Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); gravity=Gravity.CENTER; setPadding(0,dp(18),0,dp(4)); letterSpacing=0.12f })
        box.addView(TextView(this).apply { text="YOUR AI. YOUR DEVICE."; textSize=11f; setTextColor(Color.rgb(145,139,160)); gravity=Gravity.CENTER; letterSpacing=0.14f })
        overlay.addView(box,FrameLayout.LayoutParams(-1,-1)); addContentView(overlay,FrameLayout.LayoutParams(-1,-1))
        overlay.alpha=0f
        overlay.animate().alpha(1f).setDuration(250).withEndAction { overlay.animate().alpha(0f).setDuration(500).setStartDelay(450).withEndAction { (overlay.parent as? android.view.ViewGroup)?.removeView(overlay); ensureModel() }.start() }.start()
    }

    private fun ensureModel() {
        val file=File(getExternalFilesDir("models"),modelFileName)
        if(file.exists() && file.length()>100_000_000){ modelLabel.text="LOCAL MODEL READY"; status.text="● ON-DEVICE • NO API"; status.setTextColor(Color.rgb(107,220,150)); loadModel(file); return }
        modelLabel.text="Downloading local model • ~429 MB"; status.text="FIRST RUN • DOWNLOAD REQUIRED"
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                file.parentFile?.mkdirs(); val tmp=File(file.absolutePath+".part"); val response=http.newCall(Request.Builder().url(modelUrl).build()).execute()
                response.use { if(!it.isSuccessful) error("Download failed: HTTP ${it.code}"); val body=it.body?:error("Empty download"); val total=body.contentLength(); var done=0L
                    body.byteStream().use { ins -> FileOutputStream(tmp).use { out -> val buffer=ByteArray(64*1024); while(true){ val n=ins.read(buffer); if(n<0) break; out.write(buffer,0,n); done+=n; if(total>0){ val pct=(done*100/total).toInt(); withContext(Dispatchers.Main){ modelProgress.progress=pct; modelLabel.text="Downloading local model • $pct%" } } } } }
                }
                if(tmp.length()<100_000_000) error("Downloaded model is incomplete"); if(file.exists()) file.delete(); tmp.renameTo(file)
                withContext(Dispatchers.Main){ modelProgress.progress=100; modelLabel.text="LOCAL MODEL READY"; status.text="● ON-DEVICE • NO API"; status.setTextColor(Color.rgb(107,220,150)) }
                loadModel(file)
            } catch(e:Exception){ withContext(Dispatchers.Main){ modelLabel.text="Model download failed — tap bar to retry"; status.text="OFFLINE MODEL NOT READY"; Toast.makeText(this@MainActivity,e.message?:"Download error",Toast.LENGTH_LONG).show(); modelProgress.setOnClickListener{ensureModel()} } }
        }
    }

    private fun loadModel(file:File){ lifecycleScope.launch(Dispatchers.IO){ try { val loaded=Llama.loadModel(modelPath=file.absolutePath,config=LlamaConfig(contextSize=2048,threads=maxOf(2,Runtime.getRuntime().availableProcessors()/2))); model=loaded; withContext(Dispatchers.Main){ modelLabel.text="LOCAL MODEL READY • QWEN 0.5B"; status.text="● READY • PRIVATE • FREE" } } catch(e:Exception){ withContext(Dispatchers.Main){ modelLabel.text="Model could not be loaded"; Toast.makeText(this@MainActivity,e.message?:"Model error",Toast.LENGTH_LONG).show() } } } }

    private fun sendMessage(){
        val text=input.text.toString().trim(); if(text.isEmpty()) return
        val loaded=model?:run{Toast.makeText(this,"Модель ещё загружается. Подожди немного.",Toast.LENGTH_SHORT).show();return}
        input.setText(""); addBubble(text,true); val thinking=addBubble("Думаю…",false); send.isEnabled=false; status.text="● THINKING LOCALLY"
        lifecycleScope.launch(Dispatchers.IO){ try {
            val history=prefs.getString("history","")?:""; val prompt=buildPrompt(history,text)
            val result=Llama.complete(loaded,prompt=prompt,systemPrompt="Ты — Scrami AI, стильный личный помощник. Отвечай естественно, полезно и по делу. В основном отвечай на русском. Не утверждай, что ты ChatGPT. Если не знаешь — честно скажи.",maxTokens=384)
            val answer=result.text.trim().ifBlank{"Не смог сформировать ответ."}; val newHistory=(history+"\nUSER: "+text+"\nASSISTANT: "+answer).takeLast(12000); prefs.edit().putString("history",newHistory).apply()
            withContext(Dispatchers.Main){ thinking.text=answer; thinking.background=rounded(Color.rgb(25,26,34),20f,Color.rgb(39,40,50)); status.text="● READY • PRIVATE"; send.isEnabled=true; saveChatVisual() }
        } catch(e:Exception){ withContext(Dispatchers.Main){ thinking.text="Ошибка локальной модели: ${e.message?:"unknown"}"; status.text="● READY • CHECK MODEL"; send.isEnabled=true } } }
    }

    private fun buildPrompt(history:String,current:String)=if(history.isBlank()) current else history.takeLast(8000)+"\nUSER: "+current+"\nASSISTANT:"

    private fun addBubble(text:String,user:Boolean):TextView{
        val tv=TextView(this).apply{ this.text=text; textSize=16f; setTextColor(Color.WHITE); setPadding(dp(16),dp(12),dp(16),dp(12)); setLineSpacing(0f,1.08f); background=if(user) rounded(Color.rgb(103,59,191),20f,Color.TRANSPARENT) else rounded(Color.rgb(25,26,34),20f,Color.rgb(39,40,50)) }
        val row=LinearLayout(this).apply{ gravity=if(user) Gravity.END else Gravity.START; setPadding(0,dp(5),0,dp(5)); tag=user }
        row.addView(tv,LinearLayout.LayoutParams((resources.displayMetrics.widthPixels*if(user)0.82f else 0.9f).roundToInt(),-2)); chat.addView(row); return tv
    }

    private fun newChat(){ chat.removeAllViews(); prefs.edit().remove("history").remove("visual_chat").apply(); addBubble("Йоу. Я Scrami AI.\nЛокальный ИИ прямо на твоём телефоне. Без API и без подписки.",false) }
    private fun loadSavedChat(){ val saved=prefs.getString("visual_chat","")?:""; if(saved.isBlank()) addBubble("Йоу. Я Scrami AI.\nЛокальный ИИ прямо на твоём телефоне. Без API и без подписки.",false) else saved.split("\n---\n").forEach{ if(it.startsWith("U:")) addBubble(it.removePrefix("U:"),true); if(it.startsWith("A:")) addBubble(it.removePrefix("A:"),false) } }
    private fun saveChatVisual(){ val parts=mutableListOf<String>(); for(i in 0 until chat.childCount){ val row=chat.getChildAt(i) as? LinearLayout?:continue; val tv=row.getChildAt(0) as? TextView?:continue; val user=row.tag==true; parts.add((if(user)"U:" else "A:")+tv.text.toString()) }; prefs.edit().putString("visual_chat",parts.joinToString("\n---\n").takeLast(16000)).apply() }
    private fun rounded(fill:Int,radius:Float,stroke:Int)=GradientDrawable().apply{setColor(fill);cornerRadius=dp(radius).toFloat();if(stroke!=Color.TRANSPARENT)setStroke(dp(1),stroke)}
    private fun gradient(colors:IntArray,radius:Float)=GradientDrawable(GradientDrawable.Orientation.TL_BR,colors).apply{cornerRadius=dp(radius).toFloat()}
    private fun dp(v:Int)= (v*resources.displayMetrics.density).roundToInt()
    private fun dp(v:Float)= (v*resources.displayMetrics.density).roundToInt()
    override fun onDestroy(){ super.onDestroy(); val loaded=model; if(loaded!=null) lifecycleScope.launch(Dispatchers.IO){try{Llama.releaseModel(loaded)}catch(_:Exception){}} }
}
