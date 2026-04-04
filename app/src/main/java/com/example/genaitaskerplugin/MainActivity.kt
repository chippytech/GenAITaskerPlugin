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

    private val providers = arrayOf("OpenAI", "Gemini", "OpenRouter")

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
                Toast.makeText(this, "Tasker not found", Toast.LENGTH_SHORT).show()
            }
        }

        btnTestApi.setOnClickListener {
            val provider = testProvider.text.toString()
            val apiKey = testApiKey.text.toString()
            if (apiKey.isBlank()) {
                testResult.text = "Please enter an API Key"
                return@setOnClickListener
            }

            testResult.text = "Testing connection..."
            performTestCall(provider, apiKey, testResult)
        }
    }

    private fun performTestCall(provider: String, apiKey: String, resultView: TextView) {
        val client = OkHttpClient()
        val mediaType = "application/json".toMediaTypeOrNull()
        val message = "Hello, are you there?"

        val (url, bodyJson, authHeaderName, authHeaderValue) = when (provider) {
            "OpenAI" -> {
                val json = JSONObject().apply {
                    put("model", "gpt-4o-mini")
                    put("messages", JSONArray().put(JSONObject().apply {
                        put("role", "user")
                        put("content", message)
                    }))
                }
                listOf("https://api.openai.com/v1/chat/completions", json.toString(), "Authorization", "Bearer $apiKey")
            }
            "Gemini" -> {
                val json = JSONObject().apply {
                    put("contents", JSONArray().put(JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().apply {
                            put("text", message)
                        }))
                    }))
                }
                listOf("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey", json.toString(), "Content-Type", "application/json")
            }
            "OpenRouter" -> {
                val json = JSONObject().apply {
                    put("model", "google/gemini-flash-1.5")
                    put("messages", JSONArray().put(JSONObject().apply {
                        put("role", "user")
                        put("content", message)
                    }))
                }
                listOf("https://openrouter.ai/api/v1/chat/completions", json.toString(), "Authorization", "Bearer $apiKey")
            }
            else -> return
        }

        val body = bodyJson.toRequestBody(mediaType)
        val requestBuilder = Request.Builder()
            .url(url)
            .post(body)

        if (provider != "Gemini") {
            requestBuilder.addHeader(authHeaderName, authHeaderValue)
        }

        val request = requestBuilder.build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { resultView.text = "Network Error: ${e.message}" }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseText = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful) {
                        resultView.text = "Success!\nResponse: $responseText"
                    } else {
                        resultView.text = "Error: ${response.code}\n$responseText"
                    }
                }
            }
        })
    }
}