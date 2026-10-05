package com.scrami.ai

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import dev.ffmpegkit.llama.LlamaModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var chat: LinearLayout
    private lateinit var input: EditText
    private lateinit var send: TextView
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var modelLabel: TextView
    private val http = OkHttpClient()
    private val prefs by lazy { getSharedPreferences("scrami", MODE_PRIVATE) }
    private var model: LlamaModel? = null
    private val fileName = "qwen2.5-0.5b-instruct-q4_0.gguf"
    private val url = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_0.gguf"

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        buildUi()
        loadSaved()
        splash()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(Color.rgb(8, 9, 13))
        }

        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(10))
        }
        val mark = TextView(this).apply {
            text = "S"; gravity = Gravity.CENTER; textSize = 19f
            typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            background = grad(intArrayOf(Color.rgb(171,88,255), Color.rgb(70,42,145)), 18f)
        }
        header.addView(mark, LinearLayout.LayoutParams(dp(42), dp(42)))

        val names = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12),0,0,0) }
        names.addView(TextView(this).apply { text="Scrami AI"; textSize=19f; typeface=Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) })
        status = TextView(this).apply { text="PRIVATE • LOCAL • FREE"; textSize=10f; setTextColor(Color.rgb(150,145,168)); letterSpacing=.08f }
        names.addView(status)
        header.addView(names, LinearLayout.LayoutParams(0,-2,1f))
        header.addView(TextView(this).apply {
            text="＋"; textSize=25f; gravity=Gravity.CENTER; setTextColor(Color.WHITE); setOnClickListener{newChat()}
        }, LinearLayout.LayoutParams(dp(48),dp(44)))
        root.addView(header)

        val scroll = ScrollView(this).apply { isFillViewport=true; setPadding(0,dp(8),0,dp(8)) }
        chat = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(0,dp(8),0,dp(8)) }
        scroll.addView(chat)
        root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))

        val card = LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; setPadding(dp(14),dp(8),dp(14),dp(8))
            background=rounded(Color.rgb(18,19,26),18f,Color.rgb(38,39,50))
        }
        modelLabel=TextView(this).apply{text="Preparing local model…";textSize=12f;setTextColor(Color.rgb(197,192,214))}
        progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{max=100}
        card.addView(modelLabel)
        card.addView(progress,LinearLayout.LayoutParams(-1,dp(4)))
        root.addView(card,LinearLayout.LayoutParams(-1,dp(52)).apply{bottomMargin=dp(8)})

        val composer=LinearLayout(this).apply{gravity=Gravity.BOTTOM}
        input=EditText(this).apply{
            hint="Message Scrami…";setHintTextColor(Color.rgb(115,111,129));setTextColor(Color.WHITE);textSize=16f
            setPadding(dp(16),dp(12),dp(12),dp(12));background=rounded(Color.rgb(20,21,29),24f,Color.rgb(43,44,56));maxLines=5
        }
        composer.addView(input,LinearLayout.LayoutParams(0,dp(56),1f))
        send=TextView(this).apply{
            text="↑";gravity=Gravity.CENTER;textSize=24f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE)
            background=grad(intArrayOf(Color.rgb(154,83,255),Color.rgb(104,56,210)),28f);setOnClickListener{sendMessage()}
        }
        composer.addView(send,LinearLayout.LayoutParams(dp(56),dp(56)).apply{leftMargin=dp(8)})
        root.addView(composer)
        setContentView(root)
    }

    private fun splash() {
        val overlay=FrameLayout(this).apply{setBackgroundColor(Color.rgb(8,9,13))}
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER}
        box.addView(TextView(this).apply{text="S";gravity=Gravity.CENTER;textSize=48f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);background=grad(intArrayOf(Color.rgb(172,91,255),Color.rgb(75,45,154)),32f)},LinearLayout.LayoutParams(dp(96),dp(96)))
        box.addView(TextView(this).apply{text="SCRAMI AI";textSize=28f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);gravity=Gravity.CENTER;setPadding(0,dp(18),0,dp(4));letterSpacing=.12f})
        box.addView(TextView(this).apply{text="YOUR AI. YOUR DEVICE.";textSize=11f;setTextColor(Color.rgb(145,139,160));gravity=Gravity.CENTER;letterSpacing=.14f})
        overlay.addView(box,FrameLayout.LayoutParams(-1,-1))
        addContentView(overlay,FrameLayout.LayoutParams(-1,-1))
        overlay.alpha=0f
        overlay.animate().alpha(1f).setDuration(250).withEndAction{
            overlay.animate().alpha(0f).setDuration(450).setStartDelay(450).withEndAction{
                (overlay.parent as? android.view.ViewGroup)?.removeView(overlay);ensureModel()
            }.start()
        }.start()
    }

    private fun ensureModel() {
        val file=File(getExternalFilesDir("models"),fileName)
        if(file.exists() && file.length()>100_000_000){ready(file);return}
        modelLabel.text="Downloading local model • ~429 MB"
        status.text="FIRST RUN • DOWNLOAD REQUIRED"
        lifecycleScope.launch(Dispatchers.IO){
            try{
                file.parentFile?.mkdirs()
                val part=File(file.absolutePath+".part")
                http.newCall(Request.Builder().url(url).build()).execute().use{r->
                    if(!r.isSuccessful) error("HTTP "+r.code)
                    val body=r.body?:error("Empty download")
                    val total=body.contentLength();var done=0L
                    body.byteStream().use{ins->FileOutputStream(part).use{out->
                        val buf=ByteArray(64*1024)
                        while(true){
                            val n=ins.read(buf);if(n<0)break
                            out.write(buf,0,n);done+=n
                            if(total>0)withContext(Dispatchers.Main){
                                val pct=(done*100/total).toInt();progress.progress=pct
                                modelLabel.text="Downloading local model • "+pct+"%"
                            }
                        }
                    }}
                }
                if(part.length()<100_000_000)error("Incomplete model")
                if(file.exists())file.delete()
                part.renameTo(file)
                withContext(Dispatchers.Main){progress.progress=100}
                ready(file)
            }catch(e:Exception){
                withContext(Dispatchers.Main){
                    modelLabel.text="Download failed • tap here to retry"
                    status.text="MODEL NOT READY"
                    modelLabel.setOnClickListener{ensureModel()}
                    Toast.makeText(this@MainActivity,e.message?:"Download error",Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun ready(file:File){
        modelLabel.text="LOCAL MODEL • QWEN 0.5B"
        status.text="● READY • PRIVATE • FREE"
        status.setTextColor(Color.rgb(107,220,150))
        lifecycleScope.launch(Dispatchers.IO){
            try{
                val loaded=Llama.loadModel(file.absolutePath,LlamaConfig(contextSize=2048,threads=maxOf(2,Runtime.getRuntime().availableProcessors()/2)))
                model=loaded
            }catch(e:Exception){
                withContext(Dispatchers.Main){modelLabel.text="Model load error";Toast.makeText(this@MainActivity,e.message?:"Model error",Toast.LENGTH_LONG).show()}
            }
        }
    }

    private fun sendMessage(){
        val text=input.text.toString().trim();if(text.isEmpty())return
        val loaded=model?:run{Toast.makeText(this,"Модель ещё загружается.",Toast.LENGTH_SHORT).show();return}
        input.setText("");addBubble(text,true);val answerView=addBubble("Думаю…",false);send.isEnabled=false;status.text="● THINKING LOCALLY"
        lifecycleScope.launch(Dispatchers.IO){
            try{
                val history=prefs.getString("history","")?:""
                val prompt=if(history.isBlank())text else history.takeLast(8000)+"\nUSER: "+text+"\nASSISTANT:"
                val result=Llama.complete(loaded,prompt=prompt,systemPrompt="Ты — Scrami AI, стильный личный помощник. Отвечай естественно, полезно и по делу. В основном на русском. Не утверждай, что ты ChatGPT. Если не знаешь — честно скажи.",maxTokens=384)
                val answer=result.text.trim().ifBlank{"Не смог сформировать ответ."}
                prefs.edit().putString("history",(history+"\nUSER: "+text+"\nASSISTANT: "+answer).takeLast(12000)).apply()
                withContext(Dispatchers.Main){
                    answerView.text=answer;status.text="● READY • PRIVATE";send.isEnabled=true;saveVisual()
                }
            }catch(e:Exception){
                withContext(Dispatchers.Main){answerView.text="Ошибка локальной модели: "+(e.message?:"unknown");status.text="● READY";send.isEnabled=true}
            }
        }
    }

    private fun addBubble(text:String,user:Boolean):TextView{
        val tv=TextView(this).apply{
            this.text=text;textSize=16f;setTextColor(Color.WHITE);setPadding(dp(16),dp(12),dp(16),dp(12))
            setLineSpacing(0f,1.08f)
            background=if(user)rounded(Color.rgb(103,59,191),20f,Color.TRANSPARENT)else rounded(Color.rgb(25,26,34),20f,Color.rgb(39,40,50))
        }
        val row=LinearLayout(this).apply{gravity=if(user)Gravity.END else Gravity.START;setPadding(0,dp(5),0,dp(5));tag=user}
        row.addView(tv,LinearLayout.LayoutParams((resources.displayMetrics.widthPixels*if (user) .82f else .9f).roundToInt(),-2))
        chat.addView(row)
        return tv
    }

    private fun newChat(){chat.removeAllViews();prefs.edit().clear().apply();addBubble("Йоу. Я Scrami AI.\nЛокальный ИИ прямо на твоём телефоне. Без API и без подписки.",false)}
    private fun saveVisual() {
        val a = mutableListOf<String>()
        for (i in 0 until chat.childCount) {
            val row = chat.getChildAt(i) as? LinearLayout ?: continue
            val tv = row.getChildAt(0) as? TextView ?: continue
            val prefix = if (row.tag == true) "U:" else "A:"
            a.add(prefix + tv.text.toString())
        }
        prefs.edit().putString("visual", a.joinToString("\n---\n").takeLast(16000)).apply()
    }
    private fun saveVisual(){val a=mutableListOf<String>();for(i in 0 until chat.childCount){val r=chat.getChildAt(i)as?LinearLayout?:continue;val t=r.getChildAt(0)as?TextView?:continue;a.add((if(r.tag==true)"U:"else"A:")+t.text)}prefs.edit().putString("visual",a.joinToString("\n---\n").takeLast(16000)).apply()}
    private fun rounded(fill:Int,r:Float,stroke:Int)=GradientDrawable().apply{setColor(fill);cornerRadius=dp(r).toFloat();if(stroke!=Color.TRANSPARENT)setStroke(dp(1),stroke)}
    private fun grad(c:IntArray,r:Float)=GradientDrawable(GradientDrawable.Orientation.TL_BR,c).apply{cornerRadius=dp(r).toFloat()}
    private fun dp(v:Int)= (v*resources.displayMetrics.density).roundToInt()
    private fun dp(v:Float)= (v*resources.displayMetrics.density).roundToInt()
    override fun onDestroy(){super.onDestroy();val m=model;if(m!=null)lifecycleScope.launch(Dispatchers.IO){try{Llama.releaseModel(m)}catch(_:Exception){}}}
}
