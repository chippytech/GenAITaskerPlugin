package com.example.genaitaskerplugin

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.textfield.TextInputEditText
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private val providers by lazy {
        arrayOf(
            getString(R.string.provider_openai),
            getString(R.string.provider_gemini),
            getString(R.string.provider_openrouter),
            getString(R.string.provider_claude),
            getString(R.string.provider_ollama)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val testProvider = findViewById<AutoCompleteTextView>(R.id.testProvider)
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, providers)
        testProvider.setAdapter(adapter)
        testProvider.setText(providers[0], false)

        val testApiKey = findViewById<TextInputEditText>(R.id.testApiKey)
        val testBaseUrl = findViewById<TextInputEditText>(R.id.testBaseUrl)
        val btnTestApi = findViewById<Button>(R.id.btnTestApi)
        val testResult = findViewById<TextView>(R.id.testResult)
        val btnOpenTasker = findViewById<Button>(R.id.btnOpenTasker)

        btnOpenTasker.setOnClickListener {
            val taskerPackages = arrayOf("net.dinglisch.android.taskerm", "net.dinglisch.android.tasker")
            var intent: Intent? = null
            for (pkg in taskerPackages) {
                intent = packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) break
            }

            if (intent != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, getString(R.string.msg_tasker_not_found), Toast.LENGTH_SHORT).show()
            }
        }

        btnTestApi.setOnClickListener {
            val provider = testProvider.text.toString().trim()
            val apiKey = testApiKey.text.toString().trim()
            val baseUrl = testBaseUrl.text.toString().trim()
            if (apiKey.isBlank() && provider != getString(R.string.provider_ollama)) {
                testResult.text = getString(R.string.msg_enter_api_key)
                return@setOnClickListener
            }

            testResult.text = getString(R.string.msg_testing_connection)
            performTestCall(provider, apiKey, baseUrl, testResult)
        }
    }

    private fun performTestCall(provider: String, apiKey: String, customBaseUrl: String, resultView: TextView) {
        val client = OkHttpClient()
        val mediaType = "application/json".toMediaTypeOrNull()
        val message = getString(R.string.test_message)

        fun normalizeGeminiBaseUrl(raw: String): String {
            val defaultBase = "https://generativelanguage.googleapis.com/v1"
            val withoutTrailingSlash = raw.trim().ifBlank { defaultBase }.removeSuffix("/")
            return withoutTrailingSlash.removeSuffix("/models")
        }

        val (url, bodyJson, authHeaderName, authHeaderValue) = when (provider) {
            getString(R.string.provider_openai) -> {
                val json = JSONObject().apply {
                    put("model", "gpt-4o-mini")
                    put("messages", JSONArray().put(JSONObject().apply {
                        put("role", "user")
                        put("content", message)
                    }))
                }
                val baseUrl = customBaseUrl.trim().ifBlank { "https://api.openai.com/v1" }.removeSuffix("/")
                listOf("$baseUrl/chat/completions", json.toString(), "Authorization", "Bearer $apiKey")
            }
            getString(R.string.provider_gemini) -> {
                val json = JSONObject().apply {
                    put("contents", JSONArray().put(JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().apply {
                            put("text", message)
                        }))
                    }))
                }
                val baseUrl = normalizeGeminiBaseUrl(customBaseUrl)
                listOf("$baseUrl/models/gemini-1.5-flash:generateContent?key=$apiKey", json.toString(), "", "")
            }
            getString(R.string.provider_openrouter) -> {
                val json = JSONObject().apply {
                    put("model", "google/gemini-flash-1.5")
                    put("messages", JSONArray().put(JSONObject().apply {
                        put("role", "user")
                        put("content", message)
                    }))
                }
                val baseUrl = customBaseUrl.trim().ifBlank { "https://openrouter.ai/api/v1" }.removeSuffix("/")
                listOf("$baseUrl/chat/completions", json.toString(), "Authorization", "Bearer $apiKey")
            }
            getString(R.string.provider_claude) -> {
                val json = JSONObject().apply {
                    put("model", "claude-3-5-sonnet-20240620")
                    put("max_tokens", 1024)
                    put("messages", JSONArray().put(JSONObject().apply {
                        put("role", "user")
                        put("content", message)
                    }))
                }
                val baseUrl = customBaseUrl.trim().ifBlank { "https://api.anthropic.com/v1" }.removeSuffix("/")
                listOf("$baseUrl/messages", json.toString(), "x-api-key", apiKey)
            }
            getString(R.string.provider_ollama) -> {
                val json = JSONObject().apply {
                    put("model", "llama3")
                    put("messages", JSONArray().put(JSONObject().apply {
                        put("role", "user")
                        put("content", message)
                    }))
                    put("stream", false)
                }
                val host = customBaseUrl.trim().ifBlank { "http://10.0.2.2:11434" }.removeSuffix("/")
                val baseUrl = if (host.startsWith("http")) host else "http://$host"
                listOf("$baseUrl/api/chat", json.toString(), "", "")
            }
            else -> return
        }

        val body = bodyJson.toRequestBody(mediaType)
        val requestBuilder = Request.Builder()
            .url(url)
            .post(body)

        if (authHeaderName.isNotBlank()) {
            requestBuilder.addHeader(authHeaderName, authHeaderValue)
        }
        
        if (provider == getString(R.string.provider_claude)) {
            requestBuilder.addHeader("anthropic-version", "2023-06-01")
        }
        
        if (provider == getString(R.string.provider_openrouter)) {
             requestBuilder.addHeader("HTTP-Referer", "https://github.com/joaomgcd/TaskerPluginLibrary")
             requestBuilder.addHeader("X-Title", "Tasker GenAI Plugin")
        }

        val request = requestBuilder.build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { resultView.text = getString(R.string.msg_network_error, e.message) }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseText = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful) {
                        resultView.text = getString(R.string.msg_success, responseText)
                    } else {
                        resultView.text = getString(R.string.msg_error, response.code, responseText)
                    }
                }
            }
        })
    }
}
