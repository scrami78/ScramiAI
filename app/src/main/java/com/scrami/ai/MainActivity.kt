                prefs.edit().putString("history",(history+"\nUSER: "+text+"\nASSISTANT: "+answer).takeLast(12000)).apply()
                withContext(Dispatchers.Main){
                    answerView.text=answer;status.text="● READY • PRIVATE";send.isEnabled=true;saveVisual()
                }
            }catch(e:Exception){
                withContext(Dispatchers.Main){answerView.text="Ошибка локальной модели: "+(e.message?:"unknown");status.text="● READY";send.isEnabled=true}
            }
        }
    }

    private fun modeName(m:String)=when(m){"FAST"->"⚡ FAST";"SMART"->"🧠 SMART";"CREATIVE"->"🎨 CREATIVE";"CODE"->"💻 CODE";else->"📚 STUDY"}
    private fun buildModeRow(row:LinearLayout){row.removeAllViews();listOf("FAST","SMART","CREATIVE","CODE","STUDY").forEach{m->row.addView(TextView(this).apply{text=modeName(m);textSize=10f;gravity=Gravity.CENTER;setPadding(dp(12),0,dp(12),0);setTextColor(if(m==currentMode)Color.BLACK else Color.rgb(90,90,96));background=rounded(if(m==currentMode)Color.rgb(242,242,245) else Color.rgb(250,250,252),18f,Color.rgb(230,230,234));setOnClickListener{currentMode=m;buildModeRow(row)}},LinearLayout.LayoutParams(dp(105),dp(34)).apply{rightMargin=dp(6)})}}
    private fun systemPromptForMode()=when(currentMode){"FAST"->"Ты Scrami AI FAST. Отвечай максимально быстро и кратко."; "CREATIVE"->"Ты Scrami AI CREATIVE. Ты креативный автор: музыка, тексты, идеи."; "CODE"->"Ты Scrami AI CODE. Ты senior программист. Давай рабочий код."; "STUDY"->"Ты Scrami AI STUDY. Объясняй школьные темы просто и с примерами."; else->"Ты S.AI — качественный персональный ИИ-помощник, созданный Scrami. Если тебя спрашивают, кто тебя создал или кто твой создатель, отвечай прямо: «Меня создал Scrami». Не выдумывай другого создателя. Отвечай естественно, уверенно и по существу. Не повторяй вопрос пользователя, не начинай каждый ответ с приветствия и не говори о себе без причины. Не выдумывай факты; если информации недостаточно, прямо скажи об этом. Соблюдай контекст диалога. Отвечай на языке пользователя. Форматируй длинные ответы понятно: короткие абзацы, списки и код там, где это уместно."}
    private fun applyThemePreference(){ if(!prefs.contains("theme")) prefs.edit().putString("theme","auto").apply() }
    private fun isDarkTheme(): Boolean {
        return when (prefs.getString("theme","auto")) {
            "dark" -> true
            "light" -> false
            else -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
    }
    private fun bgColor()=if(isDarkTheme())Color.rgb(8,9,13) else Color.WHITE
    private fun cardColor()=if(isDarkTheme())Color.rgb(25,26,34) else Color.rgb(247,247,249)
    private fun textColor()=if(isDarkTheme())Color.WHITE else Color.rgb(20,20,24)
    private fun mutedColor()=if(isDarkTheme())Color.rgb(155,155,165) else Color.rgb(105,105,112)
    private fun borderColor()=if(isDarkTheme())Color.rgb(50,51,61) else Color.rgb(225,225,229)
    private fun accentColor()=if(isDarkTheme())Color.rgb(220,220,225) else Color.rgb(20,20,24)
    private fun bubbleTextColor()=if(isDarkTheme())Color.WHITE else Color.rgb(30,30,34)
    private fun themeDialog(){ val labels=arrayOf("Авто","Светлая","Тёмная"); val vals=arrayOf("auto","light","dark"); val cur=prefs.getString("theme","auto")?:"auto"; val checked=vals.indexOf(cur); AlertDialog.Builder(this).setTitle("Тема").setSingleChoiceItems(labels,checked){d,w->prefs.edit().putString("theme",vals[w]).apply();d.dismiss();recreate()}.setNegativeButton("Отмена",null).show() }
    private fun showTools(){PopupMenu(this,send).apply{menu.add("＋ New chat");menu.add("💬 History");menu.add("🔎 Search");menu.add("🌐 Web");menu.add("📁 File");menu.add("🧮 Calculator");menu.add("🎨 Profile");menu.add("🎨 Theme");menu.add("🔊 Read last answer");setOnMenuItemClickListener{when(it.title.toString()){"＋ New chat"->newChat();"💬 History"->showHistory();"🔎 Search"->searchHistory();"🌐 Web"->openWeb();"📁 File"->pickFile();"🧮 Calculator"->calculator();"🎨 Profile"->profile();"🎨 Theme"->themeDialog();"🔊 Read last answer"->speakLast()};true};show()}}
