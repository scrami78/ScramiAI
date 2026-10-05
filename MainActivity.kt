
package com.scrami.ai

import android.app.AlertDialog
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class MainActivity : AppCompatActivity() {
    private lateinit var chatText: TextView
    private lateinit var input: EditText
    private lateinit var scroll: ScrollView
    private val client = OkHttpClient()
    private val prefs by lazy { getSharedPreferences("scrami", MODE_PRIVATE) }
    private val apiUrl = "https://api.openai.com/v1/responses"
    private val keyAlias = "scrami_api_key"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        chatText = findViewById(R.id.chatText)
        input = findViewById(R.id.messageInput)
        scroll = findViewById(R.id.scroll)

        findViewById<Button>(R.id.settingsButton).setOnClickListener { showSettings() }
        findViewById<Button>(R.id.sendButton).setOnClickListener { sendMessage() }

        chatText.text = prefs.getString("chat", "Йоу. Я Scrami AI.\nНастрой API-ключ и напиши мне.") ?: ""
    }

    private fun model(): String = prefs.getString("model", "gpt-6-luna") ?: "gpt-6-luna"

    private fun showSettings() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 10, 40, 0)
        }

        val key = EditText(this).apply {
            hint = "OpenAI API key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(decrypt(prefs.getString("key_blob", "") ?: ""))
        }
        val model = EditText(this).apply {
            hint = "Модель"
            setText(model())
        }
        box.addView(key)
        box.addView(model)

        AlertDialog.Builder(this)
            .setTitle("Настройки Scrami AI")
            .setMessage("Ключ сохраняется на устройстве в зашифрованном виде. Не отправляй его в чат.")
            .setView(box)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сохранить") { _, _ ->
                val k = key.text.toString().trim()
                if (k.isNotEmpty()) prefs.edit().putString("key_blob", encrypt(k)).apply()
                prefs.edit().putString("model", model.text.toString().trim()).apply()
                Toast.makeText(this, "Настройки сохранены", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun sendMessage() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        val apiKey = decrypt(prefs.getString("key_blob", "") ?: "")
        if (apiKey.isBlank()) {
            showSettings()
            return
        }

        append("\n\nТы: $text\nScrami AI: думаю…")
        input.setText("")

        Thread {
            try {
                val body = JSONObject().apply {
                    put("model", model())
                    put("instructions", """
                        Ты — Scrami AI, личный ИИ пользователя.
                        Отвечай по-русски, естественно и дружелюбно.
                        Помогай с учёбой, творчеством, программированием и повседневными задачами.
                        Не выдумывай факты и честно говори об ограничениях.
                    """.trimIndent())
                    put("input", text)
                }

                val request = Request.Builder()
                    .url(apiUrl)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    val raw = response.body?.string() ?: ""
                    if (!response.isSuccessful) throw Exception("API ${response.code}: $raw")
                    val answer = extractText(JSONObject(raw))
                    runOnUiThread {
                        replaceThinking(answer)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { replaceThinking("Ошибка: ${e.message}") }
            }
        }.start()
    }

    private fun extractText(obj: JSONObject): String {
        val direct = obj.optString("output_text")
        if (direct.isNotBlank()) return direct
        val output = obj.optJSONArray("output") ?: return "Не удалось получить ответ."
        val parts = StringBuilder()
        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            val content = item.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val c = content.optJSONObject(j) ?: continue
                val t = c.optString("text")
                if (t.isNotBlank()) parts.append(t)
            }
        }
        return parts.toString().ifBlank { "Пустой ответ." }
    }

    private fun append(s: String) {
        chatText.append(s)
        prefs.edit().putString("chat", chatText.text.toString()).apply()
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun replaceThinking(answer: String) {
        val current = chatText.text.toString()
        val idx = current.lastIndexOf("Scrami AI: думая…").takeIf { it >= 0 }
            ?: current.lastIndexOf("Scrami AI: думая…")
        val fixed = if (idx >= 0) current.substring(0, idx) + "Scrami AI: $answer" else current + "\nScrami AI: $answer"
        chatText.text = fixed
        prefs.edit().putString("chat", fixed).apply()
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!ks.containsAlias(keyAlias)) {
            val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            gen.init(KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
             .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
             .build())
            gen.generateKey()
        }
        return (ks.getEntry(keyAlias, null) as java.security.KeyStore.SecretKeyEntry).secretKey
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(encrypted)
    }

    private fun decrypt(blob: String): String {
        if (blob.isBlank()) return ""
        return try {
            val all = Base64.getDecoder().decode(blob)
            val iv = all.copyOfRange(0, 12)
            val data = all.copyOfRange(12, all.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(data), StandardCharsets.UTF_8)
        } catch (_: Exception) { "" }
    }
}
