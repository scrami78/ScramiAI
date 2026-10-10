package com.scrami.ai

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
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
import android.view.animation.AccelerateDecelerateInterpolator
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
    private var pendingPhotoFile: File? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        tts = TextToSpeech(this, this)
        buildUi()
        loadSaved()
        splash()
    }

    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(8))
        }

        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(3), 0, dp(10))
        }
        val menu = TextView(this).apply {
            text = "☰"; textSize = 25f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = rounded(Color.TRANSPARENT, 22f, Color.TRANSPARENT)
            contentDescription = "История чатов"
            setOnClickListener { showHistoryPanel() }
        }
        header.addView(menu, LinearLayout.LayoutParams(dp(48), dp(48)))
        val mark = TextView(this).apply {
            text = "S.AI"; textSize = 25f; gravity = Gravity.CENTER; typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(Color.WHITE); letterSpacing = .04f
        }
        header.addView(mark, LinearLayout.LayoutParams(0, dp(48), 1f))
        status = TextView(this).apply {
            text = ""; visibility = View.GONE; textSize = 9f; setTextColor(Color.rgb(116,120,132))
        }
        val avatar = TextView(this).apply {
            text = "＋"; textSize = 28f; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT
            setTextColor(Color.WHITE); background = rounded(Color.rgb(30,30,34), 24f, Color.TRANSPARENT)
            contentDescription = "Новый чат"
            setOnClickListener { newChat() }
        }
        header.addView(avatar, LinearLayout.LayoutParams(dp(48), dp(48)))
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
            text = "AI"; textSize = 9f; gravity = Gravity.CENTER; setTextColor(Color.rgb(220,222,230))
            background = rounded(Color.rgb(20,49,34), 12f, Color.TRANSPARENT); setPadding(dp(9),dp(5),dp(9),dp(5))
        })
        // Keep internal model status available, but hide the technical model strip from the clean chat UI.
        modelBar.visibility = View.GONE
        main.addView(modelBar)

        val scroll = ScrollView(this).apply { isFillViewport = true; overScrollMode = View.OVER_SCROLL_NEVER }
        chat = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0,dp(10),0,dp(14)) }
        scroll.addView(chat)
        main.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))

                val composer = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = rounded(Color.rgb(22,22,25), 30f, Color.rgb(48,48,54))
        }
        val plus = TextView(this).apply {
            text = "+"; textSize = 28f; gravity = Gravity.CENTER; typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setTextColor(Color.WHITE); contentDescription = "Добавить вложение"; setOnClickListener { showAttachMenu(this) }
        }
        composer.addView(plus, LinearLayout.LayoutParams(dp(42),dp(48)))
        input = EditText(this).apply {
            hint = "Напиши сообщение..."; setHintTextColor(Color.rgb(165,165,170)); setTextColor(Color.WHITE)
            textSize = 16f; maxLines = 5; minLines = 1; gravity = Gravity.CENTER_VERTICAL
            setSingleLine(false); includeFontPadding = false; setPadding(dp(8),0,dp(8),0); background = null
        }
        composer.addView(input, LinearLayout.LayoutParams(0,dp(48),1f))
        send = TextView(this).apply {
            text = "↑"; textSize = 22f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setTextColor(Color.BLACK); background = rounded(Color.WHITE, 23f, Color.TRANSPARENT)
            setOnClickListener { sendMessage() }
        }
        composer.addView(send, LinearLayout.LayoutParams(dp(46),dp(46)).apply { leftMargin = dp(2) })
        main.addView(composer)

        // Clean chat layout: remove technical footer clutter.

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
        // Swipe gestures are handled at Activity level so child views (including the composer)
        // cannot swallow the edge gesture. Keep this method for compatibility with buildUi().
    }

    private var gestureStartX = 0f
    private var gestureStartY = 0f
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        when (ev.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                gestureStartX = ev.rawX
                gestureStartY = ev.rawY
            }
            android.view.MotionEvent.ACTION_UP -> {
                val dx = ev.rawX - gestureStartX
                val dy = kotlin.math.abs(ev.rawY - gestureStartY)
                val edge = dp(36)
                val threshold = dp(56)
                if (historyDialog?.isShowing != true && gestureStartX <= edge && dx >= threshold && dy < dp(160)) {
                    showHistoryPanel()
                } else if (historyDialog?.isShowing == true && dx <= -threshold && dy < dp(160)) {
                    dismissHistoryPanel()
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun showAttachMenu(anchor: View){
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.rgb(20,20,23), 20f, Color.rgb(48,48,54))
            clipToOutline = true
            elevation = dp(12).toFloat()
        }
        val items = listOf("camera" to "Камера", "photo" to "Фото", "file" to "Файлы")
        val popup = PopupWindow(panel, dp(230), -2, true).apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            elevation = dp(12).toFloat()
        }
        items.forEachIndexed { index, item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), 0, dp(14), 0)
                isClickable = true
                val icon = TextView(this@MainActivity).apply {
                    text = when(item.first) { "camera" -> "▢"; "photo" -> "▧"; else -> "▤" }
                    textSize = 23f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
                }
                addView(icon, LinearLayout.LayoutParams(dp(42), dp(56)))
                addView(TextView(this@MainActivity).apply {
                    text = item.second; textSize = 16f; gravity = Gravity.CENTER_VERTICAL
                    setTextColor(Color.WHITE)
                }, LinearLayout.LayoutParams(0, dp(56), 1f))
                addView(TextView(this@MainActivity).apply {
                    text = "›"; textSize = 24f; gravity = Gravity.CENTER
                    setTextColor(Color.rgb(155,155,162))
                }, LinearLayout.LayoutParams(dp(18), dp(56)))
                setOnClickListener {
                    popup.dismiss()
                    when(index) { 0 -> capturePhoto(); 1 -> pickImage(); 2 -> pickFile() }
                }
            }
            panel.addView(row, LinearLayout.LayoutParams(-1, dp(56)))
            if(index < items.lastIndex) panel.addView(View(this).apply {
                setBackgroundColor(Color.rgb(48,48,53))
            }, LinearLayout.LayoutParams(-1, dp(1)))
        }
        popup.showAsDropDown(anchor, -dp(4), -dp(190), Gravity.TOP or Gravity.START)
        panel.post {
            val location = IntArray(2); anchor.getLocationOnScreen(location)
            val y = location[1] - panel.height - dp(8)
            if (y > dp(24)) popup.update(location[0] - dp(2), y, dp(230), -2)
        }
    }
    private fun showSleekDialog(dialog:AlertDialog){
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(rounded(Color.rgb(20,20,23),20f,Color.rgb(55,55,62)))
            dialog.window?.setDimAmount(0.72f)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.WHITE)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.rgb(220,220,225))
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(Color.WHITE)
            dialog.listView?.setBackgroundColor(Color.rgb(20,20,23))
            dialog.listView?.divider=android.graphics.drawable.ColorDrawable(Color.rgb(45,45,50))
            dialog.listView?.dividerHeight=dp(1)
        }
        dialog.show()
    }
    private fun pickImage(){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="image/*";addCategory(Intent.CATEGORY_OPENABLE)},45)}
    private fun capturePhoto() {
        try {
            val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
            if (intent.resolveActivity(packageManager) == null) {
                Toast.makeText(this, "Камера недоступна на устройстве.", Toast.LENGTH_LONG).show()
                return
            }
            val photo = File(cacheDir, "sai_photo_${System.currentTimeMillis()}.jpg")
            if (!photo.createNewFile()) throw IllegalStateException("Не удалось создать файл фотографии")
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photo)
            pendingPhotoFile = photo
            intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            startActivityForResult(intent, 46)
        } catch (e: Exception) {
            pendingPhotoFile?.delete()
            pendingPhotoFile = null
            Toast.makeText(this, "Не удалось открыть камеру: ${e.localizedMessage ?: "ошибка устройства"}", Toast.LENGTH_LONG).show()
        }
    }
    private fun showMemory(){val m=prefs.getString("memory","")?:"";AlertDialog.Builder(this).setTitle("S.AI Memory").setMessage(if(m.isBlank())"Memory is empty. Say: “remember that …”" else m).setPositiveButton("Add"){_,_->val e=EditText(this);e.hint="What should Scrami remember?";AlertDialog.Builder(this).setView(e).setPositiveButton("Save"){_,_->prefs.edit().putString("memory",(m+"\n"+e.text.toString()).trim()).apply()}.setNegativeButton("Cancel",null).show()}.setNegativeButton("Clear"){_,_->prefs.edit().remove("memory").apply()}.show()}
    private fun accountDialog(){val current=prefs.getString("account","Scrami User")?:"Scrami User";val e=EditText(this);e.setText(current);e.hint="Account name";AlertDialog.Builder(this).setTitle("S.AI Account").setMessage("Local account on this device. Cloud sign-in can be connected later.").setView(e).setPositiveButton("Save"){_,_->prefs.edit().putString("account",e.text.toString().ifBlank{"Scrami User"}).apply();Toast.makeText(this,"Account saved",Toast.LENGTH_SHORT).show()}.setNegativeButton("Cancel",null).show()}
    private fun settingsDialog(){
        val e=EditText(this).apply{
            hint="https://your-sai-core.example"
            setText(serverUrl())
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("S.AI Core")
            .setMessage("Укажи HTTPS-адрес облачного S.AI Core. Пусто = офлайн-модель на телефоне; облачный ИИ требует доступного сервера и ключа провайдера.")
            .setView(e)
            .setPositiveButton("Save"){_,_->
                prefs.edit().putString("server_url",e.text.toString().trim().removeSuffix("/")).apply()
                val configured=serverUrl().isNotBlank()
                modelLabel.text=if(configured)"S.AI CORE • CLOUD" else "LOCAL MODEL • QWEN 0.5B • OFFLINE"
                status.text=if(configured)"● CORE URL SAVED" else "● READY • OFFLINE"
                Toast.makeText(this,if(configured)"S.AI Core подключён" else "Офлайн-режим включён",Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Privacy"){_,_->securityDialog()}
            .setNegativeButton("Close",null).show()
    }

    private fun aboutDialog(){
        AlertDialog.Builder(this).setTitle("About S.AI").setMessage("S.AI 1.01 — онлайн ИИ-помощник. Для облачного ИИ нужен доступный S.AI Core и ключ провайдера; офлайн-модель может работать локально.").setPositiveButton("OK",null).show()
    }
    private fun securityDialog(){
        AlertDialog.Builder(this).setTitle("Privacy & security")
            .setMessage("История чатов и память хранятся в приложении. Если настроен облачный S.AI Core, текст запроса отправляется на этот сервер для обработки. Используй только доверенный HTTPS-сервер.")
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
        val overlay = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        box.addView(TextView(this).apply {
            text = "S.AI"; gravity = Gravity.CENTER; textSize = 43f
            typeface = Typeface.create("sans-serif", Typeface.BOLD); setTextColor(Color.WHITE)
            letterSpacing = .02f
        }, LinearLayout.LayoutParams(-1, dp(64)))
        val dots = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(20), 0, 0) }
        val dotViews = (0..2).map {
            View(this).apply { background = rounded(Color.rgb(90,90,96), 8f, Color.TRANSPARENT); alpha = .35f }
                .also { dots.addView(it, LinearLayout.LayoutParams(dp(7), dp(7)).apply { leftMargin = dp(5); rightMargin = dp(5) }) }
        }
        box.addView(dots)
        overlay.addView(box, FrameLayout.LayoutParams(-1, -1))
        addContentView(overlay, FrameLayout.LayoutParams(-1, -1))
        overlay.alpha = 0f
        overlay.animate().alpha(1f).setDuration(180).start()
        dotViews.forEachIndexed { index, dot ->
            val anim = android.animation.ObjectAnimator.ofFloat(dot, "alpha", .25f, 1f, .25f).apply {
                duration = 720L; startDelay = index * 180L; repeatCount = android.animation.ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
            }
            anim.start()
        }
        overlay.postDelayed({
            overlay.animate().alpha(0f).setDuration(250).withEndAction {
                (overlay.parent as? android.view.ViewGroup)?.removeView(overlay)
            }.start()
        }, 1350L)
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
    private fun showHistory(){
        val dialog=AlertDialog.Builder(this)
            .setTitle("История")
            .setMessage((prefs.getString("history","")?:"").takeLast(5000).ifBlank{"История пока пуста."})
            .setPositiveButton("ОК",null)
            .setNegativeButton("Очистить историю"){_,_->
                prefs.edit().remove("history").remove("visual").apply()
                chat.removeAllViews()
                showWelcome()
            }.create()
        showSleekDialog(dialog)
    }
    private var historyDialog: android.app.Dialog? = null
    private fun dismissHistoryPanel(){ historyDialog?.dismiss(); historyDialog = null }
    private fun showHistoryPanel(){
        if (historyDialog?.isShowing == true) return
        val dialog = android.app.Dialog(this)
        historyDialog = dialog
        val root = FrameLayout(this).apply { setBackgroundColor(Color.TRANSPARENT) }
        val dim = View(this).apply { setBackgroundColor(0x99000000.toInt()); setOnClickListener { dialog.dismiss() } }
        root.addView(dim, FrameLayout.LayoutParams(-1,-1))
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(34), dp(14), dp(14))
            background = rounded(Color.rgb(12,12,14), 0f, Color.rgb(30,30,34))
            elevation = dp(12).toFloat()
        }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this).apply {
            text = "S.AI"; textSize = 25f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); letterSpacing = .05f
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        top.addView(TextView(this).apply {
            text = "×"; textSize = 30f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        panel.addView(top)
        panel.addView(TextView(this).apply {
            text = "История"; textSize = 22f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); setPadding(0, dp(24), 0, dp(16))
        })
        val newChatRow = TextView(this).apply {
            text = "＋   Новый чат"; textSize = 16f; setTextColor(Color.WHITE)
            setPadding(dp(14), dp(16), dp(12), dp(16))
            background = rounded(Color.rgb(25,25,28), 16f, Color.rgb(37,37,42))
            setOnClickListener { dialog.dismiss(); newChat() }
        }
        panel.addView(newChatRow, LinearLayout.LayoutParams(-1, -2))
        val historyText = prefs.getString("history","") ?: ""
        val entries = historyText.lines().filter { it.startsWith("USER:") }.takeLast(30).asReversed()
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (entries.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "История пока пуста."; textSize = 15f
                setTextColor(Color.rgb(160,160,168)); setPadding(dp(4), dp(20), 0, dp(10))
            })
        } else entries.forEachIndexed { index, line ->
            val title = line.removePrefix("USER:").trim().ifBlank { "Новый чат" }.take(72)
            list.addView(TextView(this).apply {
                text = "◉   $title"; textSize = 15f; setTextColor(Color.rgb(232,232,236))
                setPadding(dp(12), dp(15), dp(8), dp(15))
                background = rounded(Color.rgb(22,22,25), 14f, Color.TRANSPARENT)
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        }
        panel.addView(ScrollView(this).apply { isFillViewport = true; addView(list) },
            LinearLayout.LayoutParams(-1, 0, 1f))
        val clear = TextView(this).apply {
            text = "▤    Очистить историю       ›"; textSize = 14f; setTextColor(Color.rgb(220,220,226))
            setPadding(dp(4), dp(18), 0, dp(12))
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity).setTitle("Очистить историю?")
                    .setMessage("Все сохранённые сообщения будут удалены.")
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Очистить") { _, _ ->
                        prefs.edit().remove("history").remove("visual").apply()
                        chat.removeAllViews(); showWelcome(); dialog.dismiss()
                    }.show()
            }
        }
        panel.addView(clear)
        root.addView(panel, FrameLayout.LayoutParams((resources.displayMetrics.widthPixels * .82f).roundToInt(), -1, Gravity.START))
        dialog.setContentView(root)
        dialog.window?.let { w ->
            w.setBackgroundDrawableResource(android.R.color.transparent)
            w.setLayout(-1, -1)
            w.setGravity(Gravity.TOP or Gravity.START)
            w.setDimAmount(0f)
            w.setWindowAnimations(android.R.style.Animation_Activity)
        }
        dialog.show()
        dialog.window?.setLayout(-1, -1)
        panel.translationX = -dp(340).toFloat()
        panel.animate().translationX(0f).setDuration(220).start()
        dialog.setOnDismissListener { }
    }
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
            showWelcome()
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
        showWelcome()
    }
    private fun showWelcome() {
        val welcome = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(16), dp(24), dp(16), dp(24)) }
        welcome.addView(TextView(this).apply {
            text = "S.AI"; textSize = 42f; typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER; setTextColor(Color.WHITE); letterSpacing = .02f
        })
        welcome.addView(TextView(this).apply {
            text = "Твой ИИ-помощник. Задавай вопросы,\nполучай ответы, создавай, думай."
            textSize = 16f; gravity = Gravity.CENTER; setTextColor(Color.rgb(205,205,210))
            setLineSpacing(dp(3).toFloat(), 1f); setPadding(0, dp(8), 0, 0)
        })
        chat.addView(welcome, LinearLayout.LayoutParams(-1, 0, 1f))
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
        if (req == 46) {
            val file = pendingPhotoFile
            pendingPhotoFile = null
            if (file != null && file.exists() && file.length() > 0L) {
                input.setText("Фото сделано (${file.name}). Опиши, что нужно с ним сделать.")
                input.setSelection(input.text.length)
                Toast.makeText(this, "Фото сохранено.", Toast.LENGTH_SHORT).show()
            } else {
                file?.delete()
                Toast.makeText(this, "Съёмка отменена или камера не сохранила фото.", Toast.LENGTH_SHORT).show()
            }
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