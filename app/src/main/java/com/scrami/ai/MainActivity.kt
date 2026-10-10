package com.scrami.ai

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import dev.ffmpegkit.llama.LlamaModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var chat: LinearLayout
    private lateinit var input: EditText
    private lateinit var send: TextView
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var modelLabel: TextView
    private val prefs by lazy { getSharedPreferences("scrami", MODE_PRIVATE) }
    private var model: LlamaModel? = null
    private val fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf"
    private val modelAssetName = "qwen2.5-0.5b-instruct-q4_k_m.gguf"
    private var currentMode = "SMART"
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private var pendingMessage: String? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.BLACK
        }
        tts = TextToSpeech(this, this)
        buildUi()
        loadSaved()
        splash()
    }

    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(0), dp(14), dp(4))
            fitsSystemWindows = false
        }

        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(3), 0, dp(10))
        }
        val menu = TextView(this).apply {
            text = "☰"; textSize = 20f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = rounded(Color.rgb(22,23,28), 22f, Color.rgb(48,49,57))
            setOnClickListener { showTools() }
        }
        header.addView(menu, LinearLayout.LayoutParams(dp(44), dp(44)))

        val mark = TextView(this).apply {
            text = "S.AI"; textSize = 13f; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.BLACK); background = rounded(Color.WHITE, 15f, Color.TRANSPARENT)
        }
        header.addView(mark, LinearLayout.LayoutParams(dp(42), dp(42)).apply { leftMargin = dp(5) })

        val names = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10),0,0,0) }
        names.addView(TextView(this).apply {
            text = "S.AI 7.1"; textSize = 18f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
        })
        status = TextView(this).apply {
            text = "PRIVATE • LOCAL • FREE"; textSize = 9f; setTextColor(Color.rgb(116,120,132)); letterSpacing = .08f
        }
        names.addView(status)
        header.addView(names, LinearLayout.LayoutParams(0,-2,1f))
        val avatar = TextView(this).apply {
            text = "SA"; textSize = 12f; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); background = rounded(Color.rgb(39,42,51), 22f, Color.rgb(69,72,84))
            setOnClickListener { accountDialog() }
        }
        header.addView(avatar, LinearLayout.LayoutParams(dp(44), dp(44)))
        main.addView(header)

        val modelBar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Color.rgb(15,17,22), 18f, Color.rgb(37,40,48))
            setPadding(dp(12),0,dp(8),0)
        }
        modelLabel = TextView(this).apply {
            text = "🧠  S.AI Smart"; textSize = 12f; setTextColor(Color.rgb(224,226,233))
        }
        modelBar.addView(modelLabel, LinearLayout.LayoutParams(0,dp(40),1f))
        modelBar.addView(TextView(this).apply {
            text = "LOCAL"; textSize = 9f; gravity = Gravity.CENTER; setTextColor(Color.rgb(108,224,153))
            background = rounded(Color.rgb(20,49,34), 12f, Color.TRANSPARENT); setPadding(dp(9),dp(5),dp(9),dp(5))
        })
        main.addView(modelBar)

        val scroll = ScrollView(this).apply { isFillViewport = true; overScrollMode = View.OVER_SCROLL_NEVER }
        chat = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0,dp(10),0,dp(14)) }
        scroll.addView(chat)
        main.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))

        val quick = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0,0,0,dp(6)) }
        val quickItems = arrayOf("✦ Create","📷 Camera","▣ Analyze","⌕ Search")
        quickItems.forEachIndexed { i, label ->
            quick.addView(TextView(this).apply {
                text = label; textSize = 10f; gravity = Gravity.CENTER; setTextColor(Color.rgb(220,222,230))
                background = rounded(Color.rgb(20,22,28), 17f, Color.rgb(43,46,55))
                setPadding(dp(9),0,dp(9),0)
                setOnClickListener { when(i){0->imageLab();1->capturePhoto();2->pickFile();else->openWeb()} }
            }, LinearLayout.LayoutParams(0,dp(34),1f).apply { rightMargin = dp(5) })
        }
        main.addView(quick)

        val composer = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            // Minimal ChatGPT-like composer: black and subtle outline, no glow.
            background = rounded(Color.BLACK, 30f, Color.rgb(58,58,58))
        }
        val plus = TextView(this).apply {
            text = "+"; textSize = 24f; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); setOnClickListener { showAttachMenu() }
        }
        composer.addView(plus, LinearLayout.LayoutParams(dp(42),dp(48)))
        input = EditText(this).apply {
            hint = "MESSAGE S.AI"; setHintTextColor(Color.rgb(120,124,136)); setTextColor(Color.WHITE)
            textSize = 16f; maxLines = 5; minLines = 1; gravity = Gravity.CENTER_VERTICAL
            setSingleLine(false); includeFontPadding = false; setPadding(dp(8),0,dp(8),0); background = null
        }
        composer.addView(input, LinearLayout.LayoutParams(0,dp(48),1f))
        val camera = TextView(this).apply {
            text = "⌾"; textSize = 22f; gravity = Gravity.CENTER; setTextColor(Color.rgb(224,226,233))
            setOnClickListener { capturePhoto() }
        }
        composer.addView(camera, LinearLayout.LayoutParams(dp(42),dp(48)))
        val mic = TextView(this).apply {
            text = "●"; textSize = 13f; gravity = Gravity.CENTER; setTextColor(Color.rgb(224,226,233))
            setOnClickListener { startVoice() }
        }
        composer.addView(mic, LinearLayout.LayoutParams(dp(38),dp(48)))
        send = TextView(this).apply {
            text = "↑"; textSize = 22f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setTextColor(Color.BLACK); background = rounded(Color.WHITE, 23f, Color.TRANSPARENT)
            setOnClickListener { sendMessage() }
        }
        composer.addView(send, LinearLayout.LayoutParams(dp(46),dp(46)).apply { leftMargin = dp(2) })
        main.addView(composer)

        val footer = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(3),dp(4),dp(3),0) }
        val footerText = TextView(this).apply {
            text = "S.AI 7.1  •  Local-first  •  Your chats stay on device"
            textSize = 9f; setTextColor(Color.rgb(91,95,105))
        }
        footer.addView(footerText, LinearLayout.LayoutParams(0,-2,1f))
        val memory = TextView(this).apply {
            text = "◉"; textSize = 14f; gravity = Gravity.CENTER; setTextColor(Color.rgb(125,130,142))
            setOnClickListener { showMemory() }
        }
        footer.addView(memory, LinearLayout.LayoutParams(dp(30),dp(24)))
        main.addView(footer)

        root.addView(main, FrameLayout.LayoutParams(-1,-1))
        val side = buildSidebar()
        root.addView(side, FrameLayout.LayoutParams(dp(310),-1).apply { gravity = Gravity.START; leftMargin = -dp(310) })
        installSwipe(root,side)
        setContentView(root)
    }

    private fun buildSidebar():LinearLayout{
        val side=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(18),dp(34),dp(12),dp(12))
            setBackgroundColor(Color.rgb(13,14,18))
            elevation=dp(8).toFloat()
        }
        side.addView(TextView(this).apply{
            text="S.AI";textSize=24f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.BLACK)
            setPadding(0,0,0,dp(20))
        })
        side.addView(TextView(this).apply{
            text="＋  New chat";textSize=16f;setTextColor(Color.WHITE)
            setPadding(0,dp(12),0,dp(12));setOnClickListener{newChat()}
        })
        side.addView(TextView(this).apply{
            text="Chats";textSize=12f;setTextColor(Color.rgb(130,134,145))
            setPadding(0,dp(22),0,dp(8))
        })
        side.addView(TextView(this).apply{
            text=(prefs.getString("history","")?:"").lines().filter{it.startsWith("USER:")}.takeLast(30).asReversed().joinToString("\n"){it.removePrefix("USER:").trim()}
            textSize=14f;setTextColor(Color.rgb(220,222,230));setPadding(0,dp(4),0,dp(8))
        },LinearLayout.LayoutParams(-1,0,1f))
        side.addView(TextView(this).apply{
            text="⚙  Settings";textSize=15f;setTextColor(Color.WHITE)
            setPadding(0,dp(16),0,dp(12));setOnClickListener{settingsDialog()}
        })
        return side
    }

    private fun installSwipe(root:FrameLayout,side:LinearLayout){
        var downX=0f
        var tracking=false
        root.setOnTouchListener{_,e->
            when(e.action){
                android.view.MotionEvent.ACTION_DOWN->{
                    downX=e.x
                    tracking=downX < dp(42) || side.translationX > 1f
                    tracking
                }
                android.view.MotionEvent.ACTION_UP->{
                    if(!tracking) return@setOnTouchListener false
                    val dx=e.x-downX
                    if(dx>80) side.animate().translationX(dp(310).toFloat()).setDuration(180).start()
                    else if(dx < -80) side.animate().translationX(0f).setDuration(180).start()
                    tracking=false
                    true
                }
                android.view.MotionEvent.ACTION_CANCEL->{tracking=false;false}
                else->tracking
            }
        }
    }

    private fun showAttachMenu(){PopupMenu(this,send).apply{menu.add("📷 Camera");menu.add("🖼 Photo from gallery");menu.add("📁 File");menu.add("🎨 Image generation");menu.add("✏️ Edit image");menu.add("📎 Document");setOnMenuItemClickListener{when(it.title.toString()){"📷 Camera"->capturePhoto(); "🖼 Photo from gallery"->pickImage(); "📁 File"->pickFile(); "🎨 Image generation"->imageLab(); "✏️ Edit image"->imageLab(); "📎 Document"->pickFile()};true};show()}}
    private fun pickImage(){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="image/*";addCategory(Intent.CATEGORY_OPENABLE)},45)}
    private fun capturePhoto(){
        val intent=Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
        if(intent.resolveActivity(packageManager)==null){Toast.makeText(this,"Камера недоступна на устройстве.",Toast.LENGTH_SHORT).show();return}
        startActivityForResult(intent,46)
    }
    private fun showMemory(){val m=prefs.getString("memory","")?:"";AlertDialog.Builder(this).setTitle("S.AI Memory").setMessage(if(m.isBlank())"Memory is empty. Say: “remember that …”" else m).setPositiveButton("Add"){_,_->val e=EditText(this);e.hint="What should Scrami remember?";AlertDialog.Builder(this).setView(e).setPositiveButton("Save"){_,_->prefs.edit().putString("memory",(m+"\n"+e.text.toString()).trim()).apply()}.setNegativeButton("Cancel",null).show()}.setNegativeButton("Clear"){_,_->prefs.edit().remove("memory").apply()}.show()}
    private fun accountDialog(){val current=prefs.getString("account","Scrami User")?:"Scrami User";val e=EditText(this);e.setText(current);e.hint="Account name";AlertDialog.Builder(this).setTitle("S.AI Account").setMessage("Local account on this device. Cloud sign-in can be connected later.").setView(e).setPositiveButton("Save"){_,_->prefs.edit().putString("account",e.text.toString().ifBlank{"Scrami User"}).apply();Toast.makeText(this,"Account saved",Toast.LENGTH_SHORT).show()}.setNegativeButton("Cancel",null).show()}
    private fun settingsDialog(){
        val e=EditText(this).apply{
            hint="http://192.168.1.10:8787"
            setText(serverUrl())
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("S.AI Core")
            .setMessage("Укажи адрес локального S.AI Core на ПК. Пусто = полностью офлайн Qwen 0.5B.")
            .setView(e)
            .setPositiveButton("Save"){_,_->
                prefs.edit().putString("server_url",e.text.toString().trim().removeSuffix("/")).apply()
                val configured=serverUrl().isNotBlank()
                modelLabel.text=if(configured)"S.AI CORE • LOCAL PC" else "LOCAL MODEL • QWEN 0.5B • OFFLINE"
                status.text=if(configured)"● CORE READY • PRIVATE • FREE" else "● READY • OFFLINE • FREE"
                Toast.makeText(this,if(configured)"S.AI Core подключён" else "Офлайн-режим включён",Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Privacy"){_,_->securityDialog()}
            .setNegativeButton("Close",null).show()
    }

    private fun aboutDialog(){
        AlertDialog.Builder(this).setTitle("About S.AI").setMessage("S.AI 7.1 — private AI assistant. Local-first, minimalist, and designed around your chats. The bundled 0.5B model works offline; stronger cloud models can be connected later without changing the interface.").setPositiveButton("OK",null).show()
    }
    private fun securityDialog(){
        AlertDialog.Builder(this).setTitle("Privacy & security")
            .setMessage("S.AI 7.1 runs the language model locally. Chat history, profile name and memory stay in the app's private storage. Android controls installation and device security; S.AI does not bypass system security.")
            .setPositiveButton("OK",null).show()
    }
    private fun languageDialog(){
        val locales=Locale.getAvailableLocales().distinctBy{it.toLanguageTag()}.sortedBy{it.getDisplayLanguage(Locale.getDefault())}
        val names=locales.take(300).map{val n=it.getDisplayLanguage(Locale.getDefault());if(n.isBlank())it.toLanguageTag() else n+" — "+it.toLanguageTag()}.toTypedArray()
        AlertDialog.Builder(this).setTitle("Languages & voices").setMessage("Scrami uses the Android speech engine for languages installed on the device. Exact coverage depends on the installed TTS engine.").setItems(names){_,which->val locale=locales[which];tts?.language=locale;Toast.makeText(this,"Voice language: "+locale.displayName,Toast.LENGTH_SHORT).show()}.setNegativeButton("Close",null).show()
    }
    private fun imageLab(){
        val e=EditText(this)
        e.hint="Describe the image to create"
        e.setText(input.text)
        AlertDialog.Builder(this).setTitle("S.AI IMAGE LAB")
            .setMessage("Generation uses your local S.AI Core when a server URL is configured. No paid image API is required.")
            .setView(e)
            .setPositiveButton("Generate"){_,_->
                val prompt=e.text.toString().trim()
                if(prompt.isBlank()) return@setPositiveButton
                val base=serverUrl()
                if(base.isBlank()){
                    Toast.makeText(this,"Set S.AI Core URL in Settings first.",Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                Toast.makeText(this,"Генерирую изображение локально…",Toast.LENGTH_SHORT).show()
                lifecycleScope.launch(Dispatchers.IO){
                    try{
                        val result=SaiServerClient.image(base,prompt)
                        SaiServerClient.savePng(this@MainActivity,result.base64)
                        withContext(Dispatchers.Main){
                            Toast.makeText(this@MainActivity,"Готово: изображение сохранено в Pictures/S.AI",Toast.LENGTH_LONG).show()
                        }
                    }catch(e:Exception){
                        withContext(Dispatchers.Main){Toast.makeText(this@MainActivity,"Image error: "+(e.message?:"unknown"),Toast.LENGTH_LONG).show()}
                    }
                }
            }.setNegativeButton("Cancel",null).show()
}
    private fun splash() {
        val overlay=FrameLayout(this).apply{setBackgroundColor(Color.rgb(8,9,13))}
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER}
        box.addView(TextView(this).apply{text="S.AI";gravity=Gravity.CENTER;textSize=30f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);background=rounded(Color.BLACK,32f,Color.rgb(48,48,54));letterSpacing=.08f},LinearLayout.LayoutParams(dp(96),dp(96)))
        box.addView(TextView(this).apply{text="S.AI";textSize=28f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);gravity=Gravity.CENTER;setPadding(0,dp(18),0,dp(4));letterSpacing=.12f})
        box.addView(TextView(this).apply{text="YOUR AI. YOUR DEVICE.";textSize=11f;setTextColor(Color.rgb(145,139,160));gravity=Gravity.CENTER;letterSpacing=.14f})
        overlay.addView(box,FrameLayout.LayoutParams(-1,-1))
        addContentView(overlay,FrameLayout.LayoutParams(-1,-1))
        overlay.alpha=0f
        overlay.animate().alpha(1f).setDuration(250).withEndAction{
            overlay.animate().alpha(0f).setDuration(450).setStartDelay(450).withEndAction{
                (overlay.parent as? android.view.ViewGroup)?.removeView(overlay)
            }.start()
        }.start()
    }

    private fun ensureModel() {
        val file = File(filesDir, fileName)
        if (file.exists() && file.length() > 450_000_000L) { ready(file); return }
        modelLabel.text = "INSTALLING BUNDLED MODEL • 0%"
        status.text = "LOCAL MODEL • INSTALLING"
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                assets.open(modelAssetName).use { input ->
                    FileOutputStream(file).use { out ->
                        val buf = ByteArray(1024 * 1024)
                        var done = 0L
                        val total = input.available().toLong()
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) withContext(Dispatchers.Main) {
                                val pct = ((done * 100L) / total).toInt().coerceIn(0, 100)
                                modelLabel.text = "INSTALLING BUNDLED MODEL • " + pct + "%"
                            }
                        }
                    }
                }
                if (file.length() < 450_000_000L) error("Bundled model is incomplete")
                withContext(Dispatchers.Main) { ready(file) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    status.text = "MODEL INSTALL FAILED"
                    modelLabel.text = "Tap to retry"
                    modelLabel.setOnClickListener { ensureModel() }
                    Toast.makeText(this@MainActivity, e.message ?: "Model error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun ready(file:File){
        modelLabel.text="LOCAL MODEL • QWEN 0.5B • OFFLINE"
        status.text="● READY • C++ NATIVE • PRIVATE • FREE"
        status.setTextColor(Color.rgb(107,220,150))
        lifecycleScope.launch(Dispatchers.IO){
            try{
                val loaded=Llama.loadModel(file.absolutePath,LlamaConfig(contextSize=4096,threads=maxOf(2,Runtime.getRuntime().availableProcessors()/2)))
                model=loaded
            }catch(e:Exception){
                withContext(Dispatchers.Main){modelLabel.text="Model load error";Toast.makeText(this@MainActivity,e.message?:"Model error",Toast.LENGTH_LONG).show()}
            }
        }
    }

    private fun serverUrl() = prefs.getString("server_url","")?.trim().orEmpty()

    private fun sendMessage(){
        val text=input.text.toString().trim();if(text.isEmpty())return
        input.setText("");addBubble(text,true);val answerView=addBubble("Думаю…",false);send.isEnabled=false
        val base=serverUrl()
        lifecycleScope.launch(Dispatchers.IO){
            try{
                val history=prefs.getString("history","")?:""
                if(text.lowercase().startsWith("remember ")){val m=prefs.getString("memory","")?:"";prefs.edit().putString("memory",(m+"\n"+text.substring(9).trim()).trim()).apply()}
                val memory=prefs.getString("memory","")?:""
                if(base.isNotBlank()){
                    withContext(Dispatchers.Main){status.text="● THINKING • S.AI CORE";modelLabel.text="LOCAL SERVER • FREE"}
                    val result=SaiServerClient.chat(base,text,history,memory,currentMode)
                    val answer=result.text.trim().ifBlank{"Не смог сформировать ответ."}
                    prefs.edit().putString("history",(history+"\nUSER: "+text+"\nASSISTANT: "+answer).takeLast(12000)).apply()
                    withContext(Dispatchers.Main){
                        answerView.text=answer + if(result.sources.isNotEmpty()) "\n\nИсточники:\n" + result.sources.mapIndexed { i, s -> "["+(i+1)+"] "+s.title+"\n"+s.url }.joinToString("\n") else "";status.text=if(result.sources.isNotEmpty()) "● READY • WEB + S.AI CORE" else "● READY • S.AI CORE • LOCAL";modelLabel.text=result.model;send.isEnabled=true;saveVisual()
                    }
                    return@launch
                }
                val loaded=model
                if(loaded==null){
                    withContext(Dispatchers.Main){
                        pendingMessage=text
                        answerView.text="Загружаю офлайн-модель…"
                        status.text="● LOADING LOCAL MODEL"
                        modelLabel.text="LOADING QWEN 0.5B • PLEASE WAIT"
                        send.isEnabled=true
                        ensureModel()
                    }
                    return@launch
                }
                withContext(Dispatchers.Main){status.text="● THINKING LOCALLY"}
                val style=prefs.getString("style","friendly, confident, natural")?: "friendly, confident, natural"
                val prompt=NativeCore.preparePrompt("MEMORY:\n"+memory.takeLast(1600)+"\nSTYLE:\n"+style+"\nCONVERSATION:\n"+history.takeLast(4200)+"\nUSER: "+text+"\nASSISTANT:")
                val result=Llama.complete(loaded,prompt=prompt,systemPrompt=systemPromptForMode(),maxTokens=if(currentMode=="FAST")260 else 700)
                val answer=result.text.trim().ifBlank{"Не смог сформировать ответ."}
                prefs.edit().putString("history",(history+"\nUSER: "+text+"\nASSISTANT: "+answer).takeLast(12000)).apply()
                withContext(Dispatchers.Main){
                    answerView.text=answer;status.text="● READY • LOCAL • PRIVATE";send.isEnabled=true;saveVisual()
                }
            }catch(e:Exception){
                withContext(Dispatchers.Main){answerView.text="Ошибка S.AI: "+(e.message?:"unknown");status.text="● READY";send.isEnabled=true}
            }
        }
    }

    private fun modeName(m:String)=when(m){"FAST"->"⚡ FAST";"SMART"->"🧠 SMART";"CREATIVE"->"🎨 CREATIVE";"CODE"->"💻 CODE";else->"📚 STUDY"}
    private fun buildModeRow(row:LinearLayout){row.removeAllViews();listOf("FAST","SMART","CREATIVE","CODE","STUDY").forEach{m->row.addView(TextView(this).apply{text=modeName(m);textSize=10f;gravity=Gravity.CENTER;setPadding(dp(12),0,dp(12),0);setTextColor(if(m==currentMode)Color.BLACK else Color.WHITE);background=rounded(if(m==currentMode)Color.WHITE else Color.rgb(20,21,26),18f,Color.TRANSPARENT);setOnClickListener{currentMode=m;buildModeRow(row)}},LinearLayout.LayoutParams(dp(105),dp(34)).apply{rightMargin=dp(6)})}}
    private fun systemPromptForMode()="Ты S.AI — универсальный личный AI-помощник. Отвечай на том же языке, на котором пользователь пишет последнее сообщение. Если пользователь пишет по-русски — отвечай только по-русски. Не переключайся на китайский, английский или другой язык без явной просьбы. Не используй китайские иероглифы случайно. Давай максимально полезный, точный и естественный результат. Не выдумывай факты, источники, результаты действий или доступ к данным. Если информации недостаточно — прямо скажи, чего не хватает. Для программирования давай рабочий код, для учёбы — понятное объяснение. Помни контекст текущего диалога и память пользователя. Не начинай каждый ответ с приветствия и не повторяй запрос без необходимости."
    private fun showTools(){
        AlertDialog.Builder(this).setTitle("S.AI")
            .setItems(arrayOf("＋ New chat","💬 History","🔎 Search","🧠 Memory","👤 Account","⚙ Settings","🌐 Web","📁 File","🧮 Calculator","🔊 Read last answer")){_,which->
                when(which){0->newChat();1->showHistory();2->searchHistory();3->showMemory();4->accountDialog();5->settingsDialog();6->openWeb();7->pickFile();8->calculator();9->speakLast()}
            }.setNegativeButton("Close",null).show()
    }
    private fun showHistory(){AlertDialog.Builder(this).setTitle("Chat history").setMessage((prefs.getString("history","")?:"").takeLast(5000).ifBlank{"No saved messages yet."}).setPositiveButton("OK",null).show()}
    private fun searchHistory(){val e=EditText(this);e.hint="Search history";AlertDialog.Builder(this).setTitle("Search").setView(e).setPositiveButton("Find"){_,_->val h=prefs.getString("history","")?:"";val q=e.text.toString();AlertDialog.Builder(this).setTitle("Results").setMessage(h.lines().filter{it.contains(q,true)}.joinToString("\n").take(5000).ifBlank{"Nothing found."}).setPositiveButton("OK",null).show()}.setNegativeButton("Cancel",null).show()}
    private fun openWeb(){
        val q=input.text.toString().trim()
        if(q.isBlank()){Toast.makeText(this,"Напиши запрос для поиска.",Toast.LENGTH_SHORT).show();return}
        val base=serverUrl()
        if(base.isBlank()){addBubble("Для веб-поиска подключи S.AI Core в настройках. Офлайн-режим остаётся доступен.",false);return}
        addBubble("🔎 Ищу в интернете: "+q,false)
        lifecycleScope.launch(Dispatchers.IO){
            try{
                val results=SaiServerClient.search(base,q)
                withContext(Dispatchers.Main){
                    if(results.isEmpty()){addBubble("Ничего не нашёл или веб-поиск временно недоступен.",false)}
                    else{
                        val text=results.mapIndexed{index,r->"["+(index+1)+"] "+r.title+"\n"+r.url+"\n"+r.snippet}.joinToString("\n\n")
                        addBubble(text,false);saveVisual()
                    }
                }
            }catch(e:Exception){withContext(Dispatchers.Main){addBubble("Ошибка веб-поиска: "+(e.message?:"unknown"),false)}}
        }
    }
    private fun pickFile(){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="*/*";addCategory(Intent.CATEGORY_OPENABLE)},44)}
    private fun calculator(){val e=EditText(this);e.hint="Example: 42";AlertDialog.Builder(this).setTitle("Calculator").setView(e).setPositiveButton("Calculate"){_,_->Toast.makeText(this,e.text.toString().toDoubleOrNull()?.toString()?:"Use a number",Toast.LENGTH_SHORT).show()}.show()}
    private fun profile(){val e=EditText(this);e.hint="How should Scrami speak?";e.setText(prefs.getString("style","friendly, confident, natural"));AlertDialog.Builder(this).setTitle("S.AI Profile").setView(e).setPositiveButton("Save"){_,_->prefs.edit().putString("style",e.text.toString()).apply()}.show()}
    private fun speakLast(){val h=prefs.getString("history","")?:"";val last=h.substringAfterLast("ASSISTANT:").trim();if(last.isNotBlank())tts?.speak(last,TextToSpeech.QUEUE_FLUSH,null,"scrami")}
    private fun startVoice(){if(ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.RECORD_AUDIO),91);return};if(!SpeechRecognizer.isRecognitionAvailable(this)){Toast.makeText(this,"Speech recognition unavailable",Toast.LENGTH_SHORT).show();return};recognizer?.destroy();recognizer=SpeechRecognizer.createSpeechRecognizer(this);recognizer!!.setRecognitionListener(object:RecognitionListener{override fun onResults(b:Bundle){b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let{input.setText(it);input.setSelection(input.text.length)}};override fun onError(e:Int){Toast.makeText(this@MainActivity,"Voice error",Toast.LENGTH_SHORT).show()};override fun onReadyForSpeech(p:Bundle?){status.text="● LISTENING"};override fun onEndOfSpeech(){status.text="● READY"};override fun onBeginningOfSpeech(){};override fun onRmsChanged(v:Float){};override fun onBufferReceived(b:ByteArray?){};override fun onPartialResults(b:Bundle?){};override fun onEvent(t:Int,p:Bundle?){}});recognizer!!.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)})}
    override fun onInit(s:Int){if(s==TextToSpeech.SUCCESS)tts?.language=Locale.getDefault()}

    private fun addBubble(text:String,user:Boolean):TextView{
        val tv=TextView(this).apply{
            this.text=text;textSize=16f;setTextColor(Color.WHITE);setPadding(dp(16),dp(12),dp(16),dp(12))
            setLineSpacing(0f,1.08f)
            background=if(user)rounded(Color.rgb(34,36,43),20f,Color.rgb(58,61,70))else rounded(Color.rgb(18,20,25),20f,Color.rgb(43,46,54))
        }
        val row=LinearLayout(this).apply{gravity=if(user)Gravity.END else Gravity.START;setPadding(0,dp(5),0,dp(5));tag=user}
        row.addView(tv,LinearLayout.LayoutParams((resources.displayMetrics.widthPixels*if (user) .82f else .9f).roundToInt(),-2))
        chat.addView(row)
        return tv
    }

    private fun loadSaved() {
        val s = prefs.getString("visual", "") ?: ""
        if (s.isBlank()) {
            addBubble("S.AI готов. Напиши, что нужно сделать — я отвечу без шаблонного приветствия.", false)
        } else {
            s.split("\n---\n").forEach { part ->
                if (part.startsWith("U:")) addBubble(part.substring(2), true)
                if (part.startsWith("A:")) addBubble(part.substring(2), false)
            }
        }
    }
    private fun newChat() {
        chat.removeAllViews()
        prefs.edit().remove("visual").remove("history").apply()
        addBubble("S.AI готов. Напиши, что нужно сделать — я отвечу без шаблонного приветствия.", false)
    }
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
    private fun rounded(fill:Int,r:Float,stroke:Int)=GradientDrawable().apply{setColor(fill);cornerRadius=dp(r).toFloat();if(stroke!=Color.TRANSPARENT)setStroke(dp(1),stroke)}
    private fun grad(c:IntArray,r:Float)=GradientDrawable(GradientDrawable.Orientation.TL_BR,c).apply{cornerRadius=dp(r).toFloat()}
    private fun dp(v:Int)= (v*resources.displayMetrics.density).roundToInt()
    private fun dp(v:Float)= (v*resources.displayMetrics.density).roundToInt()
    override fun onActivityResult(req:Int,res:Int,data:Intent?){
        super.onActivityResult(req,res,data)
        if(res!=RESULT_OK)return
        if(req==46){
            val bitmap=data?.extras?.get("data") as? android.graphics.Bitmap
            if(bitmap!=null){
                val file=File(cacheDir,"photo_${System.currentTimeMillis()}.jpg")
                try{FileOutputStream(file).use{bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,92,it)}}catch(_:Exception){}
            }
            input.setText("PHOTO CAPTURED. Describe what S.AI should do with this photo.")
            input.setSelection(input.text.length)
            Toast.makeText(this,"Фото сделано и готово к отправке.",Toast.LENGTH_SHORT).show()
            return
        }
        if(data?.data!=null){
            if(req==45){
                input.setText("IMAGE ATTACHED. Describe the exact edit or analysis you want.")
                input.setSelection(input.text.length)
            }else{
                lifecycleScope.launch(Dispatchers.IO){
                    val t=try{contentResolver.openInputStream(data.data!!)?.bufferedReader()?.use{it.readText().take(10000)}?:""}catch(_:Exception){""}
                    withContext(Dispatchers.Main){
                        input.setText(if(t.isBlank())"Attachment selected. Ask Scrami what to do with it." else "Analyze this document:\n"+t)
                        input.setSelection(input.text.length)
                    }
                }
            }
        }
    }
    override fun onDestroy(){recognizer?.destroy();tts?.shutdown();super.onDestroy();val m=model;if(m!=null)lifecycleScope.launch(Dispatchers.IO){try{Llama.releaseModel(m)}catch(_:Exception){}}}
}
