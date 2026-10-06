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

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        tts = TextToSpeech(this, this)
        buildUi()
        loadSaved()
        splash()
    }

    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(10,10,11)) }
        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(8))
            setBackgroundColor(Color.rgb(10,10,11))
        }
        val header = LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(0,dp(4),0,dp(8)) }
        val mark = TextView(this).apply {
            text="S";gravity=Gravity.CENTER;textSize=18f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.BLACK)
            background=rounded(Color.WHITE,16f,Color.rgb(220,220,224))
        }
        header.addView(mark,LinearLayout.LayoutParams(dp(40),dp(40)))
        val titleBox=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(10),0,0,0)}
        titleBox.addView(TextView(this).apply{text="S.AI";textSize=18f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE)})
        status=TextView(this).apply{text="PRIVATE • LOCAL • FREE";textSize=9f;setTextColor(Color.rgb(105,105,112));letterSpacing=.08f}
        titleBox.addView(status)
        header.addView(titleBox,LinearLayout.LayoutParams(0,-2,1f))
        header.addView(TextView(this).apply{text="☰";textSize=22f;gravity=Gravity.CENTER;setTextColor(Color.BLACK);setOnClickListener{showTools()}},LinearLayout.LayoutParams(dp(44),dp(42)))
        main.addView(header)

        val modeScroll=HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false}
        val modeRow=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
        buildModeRow(modeRow);modeScroll.addView(modeRow)
        main.addView(modeScroll,LinearLayout.LayoutParams(-1,dp(42)))

        val scroll=ScrollView(this).apply{isFillViewport=true;setPadding(0,dp(4),0,dp(4))}
        chat=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(0,dp(8),0,dp(8))}
        scroll.addView(chat);main.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))

        val composer=LinearLayout(this).apply{gravity=Gravity.BOTTOM;setPadding(0,dp(4),0,dp(2))}
        val plus=TextView(this).apply{
            text="＋";textSize=27f;gravity=Gravity.CENTER;setTextColor(Color.BLACK)
            background=rounded(Color.rgb(25,26,30),28f,Color.rgb(55,56,62));setOnClickListener{showAttachMenu()}
        }
        composer.addView(plus,LinearLayout.LayoutParams(dp(54),dp(56)))
        input=EditText(this).apply{
            hint="Message S.AI";setHintTextColor(Color.rgb(145,145,150));setTextColor(Color.WHITE);textSize=16f
            setPadding(dp(15),dp(10),dp(12),dp(10));background=rounded(Color.rgb(247,247,249),25f,Color.rgb(225,225,229));maxLines=5
        }
        composer.addView(input,LinearLayout.LayoutParams(0,dp(56),1f).apply{leftMargin=dp(7)})
        val mic=TextView(this).apply{
            text="🎙";textSize=18f;gravity=Gravity.CENTER;setTextColor(Color.BLACK);background=rounded(Color.rgb(247,247,249),28f,Color.rgb(225,225,229));setOnClickListener{startVoice()}
            setOnLongClickListener{startVoice();true}
        }
        composer.addView(mic,LinearLayout.LayoutParams(dp(52),dp(56)).apply{leftMargin=dp(6)})
        send=TextView(this).apply{text="↑";gravity=Gravity.CENTER;textSize=23f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);background=rounded(Color.BLACK,28f,Color.TRANSPARENT);setOnClickListener{sendMessage()}}
        composer.addView(send,LinearLayout.LayoutParams(dp(56),dp(56)).apply{leftMargin=dp(6)})
        main.addView(composer)

        modelLabel=TextView(this).apply{text="Preparing S.AI…";textSize=10f;setTextColor(Color.rgb(120,120,125));gravity=Gravity.CENTER}
        progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{max=100}
        val modelBox=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;addView(modelLabel);addView(progress,LinearLayout.LayoutParams(-1,dp(3)))}
        main.addView(modelBox,LinearLayout.LayoutParams(-1,dp(30)))

        root.addView(main,FrameLayout.LayoutParams(-1,-1))
        val side=buildSidebar()
        root.addView(side,FrameLayout.LayoutParams(dp(310),-1).apply{gravity=Gravity.START;leftMargin=-dp(310)})
        installSwipe(root,side)
        setContentView(root)
    }

    private fun buildSidebar():LinearLayout{
        val side=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(34),dp(12),dp(12));setBackgroundColor(Color.WHITE)}
        side.addView(TextView(this).apply{text="S.AI";textSize=22f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.BLACK);setPadding(0,0,0,dp(18))})
        side.addView(TextView(this).apply{text="＋  New chat";textSize=16f;setTextColor(Color.BLACK);setPadding(0,dp(12),0,dp(12));setOnClickListener{newChat()}})
        side.addView(TextView(this).apply{text="⌕  Search";textSize=16f;setTextColor(Color.BLACK);setPadding(0,dp(12),0,dp(12));setOnClickListener{searchHistory()}})
        side.addView(TextView(this).apply{text="▣  Chats";textSize=16f;setTextColor(Color.BLACK);setPadding(0,dp(12),0,dp(12));setOnClickListener{showHistory()}})
        side.addView(TextView(this).apply{text="🧠  Memory";textSize=16f;setTextColor(Color.BLACK);setPadding(0,dp(12),0,dp(12));setOnClickListener{showMemory()}})
        side.addView(TextView(this).apply{text="👤  Account";textSize=16f;setTextColor(Color.BLACK);setPadding(0,dp(12),0,dp(12));setOnClickListener{accountDialog()}})
        side.addView(TextView(this).apply{text="🎨  Image Lab";textSize=16f;setTextColor(Color.BLACK);setPadding(0,dp(12),0,dp(12));setOnClickListener{imageLab()}})
        side.addView(Space(this),LinearLayout.LayoutParams(1,0,1f))
        side.addView(TextView(this).apply{text="S.AI";textSize=10f;setTextColor(Color.rgb(140,140,145))})
        return side
    }

    private fun installSwipe(root:FrameLayout,side:LinearLayout){
        var downX=0f
        root.setOnTouchListener{_,e->
            when(e.action){android.view.MotionEvent.ACTION_DOWN->{downX=e.x;true};android.view.MotionEvent.ACTION_UP->{val dx=e.x-downX;if(dx>80){side.animate().translationX(dp(310).toFloat()).setDuration(180).start()}else if(dx < -80){side.animate().translationX(0f).setDuration(180).start()};true};else->true}
        }
    }

    private fun showAttachMenu(){PopupMenu(this,send).apply{menu.add("📷 Photo");menu.add("📁 File");menu.add("🎨 Image generation");menu.add("✏️ Edit image");menu.add("📎 Document");setOnMenuItemClickListener{when(it.title.toString()){"📷 Photo"->pickImage(); "📁 File"->pickFile(); "🎨 Image generation"->imageLab(); "✏️ Edit image"->imageLab(); "📎 Document"->pickFile()};true};show()}}
    private fun pickImage(){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="image/*";addCategory(Intent.CATEGORY_OPENABLE)},45)}
    private fun showMemory(){val m=prefs.getString("memory","")?:"";AlertDialog.Builder(this).setTitle("Scrami Memory").setMessage(if(m.isBlank())"Memory is empty. Say: “remember that …”" else m).setPositiveButton("Add"){_,_->val e=EditText(this);e.hint="What should Scrami remember?";AlertDialog.Builder(this).setView(e).setPositiveButton("Save"){_,_->prefs.edit().putString("memory",(m+"\n"+e.text.toString()).trim()).apply()}.setNegativeButton("Cancel",null).show()}.setNegativeButton("Clear"){_,_->prefs.edit().remove("memory").apply()}.show()}
    private fun accountDialog(){val current=prefs.getString("account","Scrami User")?:"Scrami User";val e=EditText(this);e.setText(current);e.hint="Account name";AlertDialog.Builder(this).setTitle("Scrami Account").setMessage("Local account on this device. Cloud sign-in can be connected later.").setView(e).setPositiveButton("Save"){_,_->prefs.edit().putString("account",e.text.toString().ifBlank{"Scrami User"}).apply();Toast.makeText(this,"Account saved",Toast.LENGTH_SHORT).show()}.setNegativeButton("Cancel",null).show()}
    private fun settingsDialog(){
        val choices=arrayOf("Auto","Dark","Light")
        val current=prefs.getString("theme","AUTO")?:"AUTO"
        val selected=choices.indexOfFirst{it.uppercase()==current}.coerceAtLeast(0)
        AlertDialog.Builder(this).setTitle("Settings • Theme")
            .setSingleChoiceItems(choices,selected){dialog,which->
                prefs.edit().putString("theme",choices[which].uppercase()).apply()
                dialog.dismiss()
                Toast.makeText(this,"Theme: "+choices[which],Toast.LENGTH_SHORT).show()
            }.setItems(arrayOf("Privacy & security","Memory","Account","About S.AI")){_,which->
                when(which){0->securityDialog();1->showMemory();2->accountDialog();3->aboutDialog()}
            }.setNegativeButton("Close",null).show()
    }

    private fun aboutDialog(){
        AlertDialog.Builder(this).setTitle("About S.AI").setMessage("S.AI 5.1 — private AI assistant. Local-first, minimalist, and designed around your chats. The bundled 0.5B model works offline; stronger cloud models can be connected later without changing the interface.").setPositiveButton("OK",null).show()
    }
    private fun securityDialog(){
        AlertDialog.Builder(this).setTitle("Privacy & security")
            .setMessage("S.AI 5.1 runs the language model locally. Chat history, profile name and memory stay in the app's private storage. Android controls installation and device security; S.AI does not bypass system security.")
            .setPositiveButton("OK",null).show()
    }
    private fun languageDialog(){
        val locales=Locale.getAvailableLocales().distinctBy{it.toLanguageTag()}.sortedBy{it.getDisplayLanguage(Locale.getDefault())}
        val names=locales.take(300).map{val n=it.getDisplayLanguage(Locale.getDefault());if(n.isBlank())it.toLanguageTag() else n+" — "+it.toLanguageTag()}.toTypedArray()
        AlertDialog.Builder(this).setTitle("Languages & voices").setMessage("Scrami uses the Android speech engine for languages installed on the device. Exact coverage depends on the installed TTS engine.").setItems(names){_,which->val locale=locales[which];tts?.language=locale;Toast.makeText(this,"Voice language: "+locale.displayName,Toast.LENGTH_SHORT).show()}.setNegativeButton("Close",null).show()
    }
    private fun imageLab(){val e=EditText(this);e.hint="Example: remove the person in the background";e.setText(input.text);AlertDialog.Builder(this).setTitle("SCRAMI IMAGE LAB").setMessage("Describe exactly what to create or change. The UI is ready for a connected image model/provider. Image generation/editing itself requires an image model backend; I won't pretend the offline 0.5B model can do pixel editing.").setView(e).setPositiveButton("Create / edit"){_,_->input.setText(e.text.toString());Toast.makeText(this,"Image task prepared — connect an image model in Settings.",Toast.LENGTH_LONG).show()}.setNegativeButton("Cancel",null).show()}
    private fun splash() {
        val overlay=FrameLayout(this).apply{setBackgroundColor(Color.rgb(8,9,13))}
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER}
        box.addView(TextView(this).apply{text="S";gravity=Gravity.CENTER;textSize=48f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);background=grad(intArrayOf(Color.rgb(172,91,255),Color.rgb(75,45,154)),32f)},LinearLayout.LayoutParams(dp(96),dp(96)))
        box.addView(TextView(this).apply{text="S.AI";textSize=28f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);gravity=Gravity.CENTER;setPadding(0,dp(18),0,dp(4));letterSpacing=.12f})
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
                                progress.progress = pct
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
                if(text.lowercase().startsWith("remember ")){val m=prefs.getString("memory","")?:"";prefs.edit().putString("memory",(m+"\n"+text.substring(9).trim()).trim()).apply()}
                val memory=prefs.getString("memory","")?:""
                val style=prefs.getString("style","friendly, confident, natural")?: "friendly, confident, natural"
                val prompt="MEMORY:\n"+memory.takeLast(3000)+"\nSTYLE:\n"+style+"\nCONVERSATION:\n"+history.takeLast(7000)+"\nUSER: "+text+"\nASSISTANT:"
                val result=Llama.complete(loaded,prompt=prompt,systemPrompt=systemPromptForMode(),maxTokens=if(currentMode=="FAST")220 else 480)
                val answer=result.text.trim().ifBlank{"Не смог сформировать ответ."}
                prefs.edit().putString("history",(history+"\nUSER: "+text+"\nASSISTANT: "+answer).takeLast(12000)).apply()
                withContext(Dispatchers.Main){
                    answerView.text=answer;status.text="● READY • LOCAL • PRIVATE";send.isEnabled=true;saveVisual()
                }
            }catch(e:Exception){
                withContext(Dispatchers.Main){answerView.text="Ошибка локальной модели: "+(e.message?:"unknown");status.text="● READY";send.isEnabled=true}
            }
        }
    }

    private fun modeName(m:String)=when(m){"FAST"->"⚡ FAST";"SMART"->"🧠 SMART";"CREATIVE"->"🎨 CREATIVE";"CODE"->"💻 CODE";else->"📚 STUDY"}
    private fun buildModeRow(row:LinearLayout){row.removeAllViews();listOf("FAST","SMART","CREATIVE","CODE","STUDY").forEach{m->row.addView(TextView(this).apply{text=modeName(m);textSize=10f;gravity=Gravity.CENTER;setPadding(dp(12),0,dp(12),0);setTextColor(if(m==currentMode)Color.BLACK else Color.WHITE);background=rounded(if(m==currentMode)Color.WHITE else Color.rgb(20,21,26),18f,Color.TRANSPARENT);setOnClickListener{currentMode=m;buildModeRow(row)}},LinearLayout.LayoutParams(dp(105),dp(34)).apply{rightMargin=dp(6)})}}
    private fun systemPromptForMode()=when(currentMode){"FAST"->"Ты Scrami AI FAST. Отвечай максимально быстро и кратко."; "CREATIVE"->"Ты Scrami AI CREATIVE. Ты креативный автор: музыка, тексты, идеи."; "CODE"->"Ты Scrami AI CODE. Ты senior программист. Давай рабочий код."; "STUDY"->"Ты Scrami AI STUDY. Объясняй школьные темы просто и с примерами."; else->"Ты S.AI SMART — личный AI-помощник. Отвечай естественно, точно и полезно. Не повторяй приветствия и имя ассистента без причины. Не выдумывай факты. Если задача сложная — разбей её на понятные действия. Учитывай память и историю диалога. Если пользователь просит готовый текст — дай готовый текст без лишней болтовни."}
    private fun showTools(){
        AlertDialog.Builder(this).setTitle("S.AI")
            .setItems(arrayOf("＋ New chat","💬 History","🔎 Search","🧠 Memory","👤 Account","⚙ Settings","🌐 Web","📁 File","🧮 Calculator","🔊 Read last answer")){_,which->
                when(which){0->newChat();1->showHistory();2->searchHistory();3->showMemory();4->accountDialog();5->settingsDialog();6->openWeb();7->pickFile();8->calculator();9->speakLast()}
            }.setNegativeButton("Close",null).show()
    }
    private fun showHistory(){AlertDialog.Builder(this).setTitle("Chat history").setMessage((prefs.getString("history","")?:"").takeLast(5000).ifBlank{"No saved messages yet."}).setPositiveButton("OK",null).show()}
    private fun searchHistory(){val e=EditText(this);e.hint="Search history";AlertDialog.Builder(this).setTitle("Search").setView(e).setPositiveButton("Find"){_,_->val h=prefs.getString("history","")?:"";val q=e.text.toString();AlertDialog.Builder(this).setTitle("Results").setMessage(h.lines().filter{it.contains(q,true)}.joinToString("\n").take(5000).ifBlank{"Nothing found."}).setPositiveButton("OK",null).show()}.setNegativeButton("Cancel",null).show()}
    private fun openWeb(){val q=input.text.toString().trim();if(q.isBlank()){Toast.makeText(this,"Type a search query first.",Toast.LENGTH_SHORT).show();return};startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/search?q="+Uri.encode(q))))}
    private fun pickFile(){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="*/*";addCategory(Intent.CATEGORY_OPENABLE)},44)}
    private fun calculator(){val e=EditText(this);e.hint="Example: 42";AlertDialog.Builder(this).setTitle("Calculator").setView(e).setPositiveButton("Calculate"){_,_->Toast.makeText(this,e.text.toString().toDoubleOrNull()?.toString()?:"Use a number",Toast.LENGTH_SHORT).show()}.show()}
    private fun profile(){val e=EditText(this);e.hint="How should Scrami speak?";e.setText(prefs.getString("style","friendly, confident, natural"));AlertDialog.Builder(this).setTitle("Scrami Profile").setView(e).setPositiveButton("Save"){_,_->prefs.edit().putString("style",e.text.toString()).apply()}.show()}
    private fun speakLast(){val h=prefs.getString("history","")?:"";val last=h.substringAfterLast("ASSISTANT:").trim();if(last.isNotBlank())tts?.speak(last,TextToSpeech.QUEUE_FLUSH,null,"scrami")}
    private fun startVoice(){if(ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.RECORD_AUDIO),91);return};if(!SpeechRecognizer.isRecognitionAvailable(this)){Toast.makeText(this,"Speech recognition unavailable",Toast.LENGTH_SHORT).show();return};recognizer?.destroy();recognizer=SpeechRecognizer.createSpeechRecognizer(this);recognizer!!.setRecognitionListener(object:RecognitionListener{override fun onResults(b:Bundle){b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let{input.setText(it);input.setSelection(input.text.length)}};override fun onError(e:Int){Toast.makeText(this@MainActivity,"Voice error",Toast.LENGTH_SHORT).show()};override fun onReadyForSpeech(p:Bundle?){status.text="● LISTENING"};override fun onEndOfSpeech(){status.text="● READY"};override fun onBeginningOfSpeech(){};override fun onRmsChanged(v:Float){};override fun onBufferReceived(b:ByteArray?){};override fun onPartialResults(b:Bundle?){};override fun onEvent(t:Int,p:Bundle?){}});recognizer!!.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)})}
    override fun onInit(s:Int){if(s==TextToSpeech.SUCCESS)tts?.language=Locale.getDefault()}

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
    override fun onActivityResult(req:Int,res:Int,data:Intent?){super.onActivityResult(req,res,data);if(res==RESULT_OK&&data?.data!=null){if(req==45){input.setText("IMAGE ATTACHED. Describe the exact edit or analysis you want.");input.setSelection(input.text.length)}else{lifecycleScope.launch(Dispatchers.IO){val t=try{contentResolver.openInputStream(data.data!!)?.bufferedReader()?.use{it.readText().take(10000)}?:""}catch(_:Exception){""};withContext(Dispatchers.Main){input.setText(if(t.isBlank())"Attachment selected. Ask Scrami what to do with it." else "Analyze this document:\n"+t);input.setSelection(input.text.length)}}}}}
    override fun onDestroy(){recognizer?.destroy();tts?.shutdown();super.onDestroy();val m=model;if(m!=null)lifecycleScope.launch(Dispatchers.IO){try{Llama.releaseModel(m)}catch(_:Exception){}}}
}
