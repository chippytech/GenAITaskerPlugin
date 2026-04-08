package com.example.genaitaskerplugin

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerAction
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// ---------------- CONFIG HELPER ----------------

class AIHelper(config: TaskerPluginConfig<AIInput>) :
    TaskerPluginConfigHelper<AIInput, AIOutput, AIRunner>(config) {

    override val runnerClass = AIRunner::class.java
    override val inputClass = AIInput::class.java
    override val outputClass = AIOutput::class.java

    override fun addToStringBlurb(input: TaskerInput<AIInput>, blurbBuilder: StringBuilder) {
        val messages = try {
            JSONArray(input.regular.messagesJson ?: "[]")
        } catch (e: Exception) {
            JSONArray()
        }
        val firstMessage = if (messages.length() > 0) {
            messages.getJSONObject(0).optString("content", "")
        } else ""
        blurbBuilder.append("${input.regular.provider}: $firstMessage")
    }
}

// ---------------- CONFIG ACTIVITY (UI) ----------------

class AIPluginActivity : AppCompatActivity(), TaskerPluginConfig<AIInput> {

    private val helper by lazy { AIHelper(this) }

    override val context: Context
        get() = applicationContext

    private lateinit var providerAutoComplete: MaterialAutoCompleteTextView
    private lateinit var apiKeyInput: TextInputEditText
    private lateinit var modelInput: TextInputEditText
    private lateinit var baseUrlInput: TextInputEditText
    private lateinit var imageUriInput: TextInputEditText

    private lateinit var tabLayout: TabLayout
    private lateinit var manualContainer: LinearLayout
    private lateinit var variableContainer: LinearLayout
    private lateinit var messagesContainer: LinearLayout
    private lateinit var variableInput: TextInputEditText

    private val providers = arrayOf("OpenAI", "Gemini", "OpenRouter", "Claude", "Grok", "Ollama")
    private val roles = arrayOf("user", "system", "assistant")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this)
        scrollView.isFillViewport = true
        scrollView.setBackgroundColor(Color.parseColor("#F5F5F5"))

        val rootLayout = LinearLayout(this)
        rootLayout.orientation = LinearLayout.VERTICAL
        val padding = (16 * resources.displayMetrics.density).toInt()
        rootLayout.setPadding(padding, padding, padding, padding)
        rootLayout.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val configCard = createCard()
        val configLayout = LinearLayout(this)
        configLayout.orientation = LinearLayout.VERTICAL
        configLayout.setPadding(padding, padding, padding, padding)

        val providerTil = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle)
        providerTil.hint = "Provider"
        providerTil.endIconMode = TextInputLayout.END_ICON_DROPDOWN_MENU
        providerTil.layoutParams = margins(0, 0, 0, 12)
        providerAutoComplete = MaterialAutoCompleteTextView(providerTil.context)
        providerAutoComplete.inputType = InputType.TYPE_NULL
        providerAutoComplete.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, providers))
        providerTil.addView(providerAutoComplete)
        configLayout.addView(providerTil)

        val apiKeyTil = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle)
        apiKeyTil.hint = "API Key"
        apiKeyTil.layoutParams = margins(0, 0, 0, 12)
        apiKeyInput = TextInputEditText(apiKeyTil.context)
        apiKeyInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        apiKeyTil.addView(apiKeyInput)
        configLayout.addView(apiKeyTil)

        val modelTil = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle)
        modelTil.hint = "Model (Default if blank)"
        modelTil.layoutParams = margins(0, 0, 0, 12)
        modelInput = TextInputEditText(modelTil.context)
        modelTil.addView(modelInput)
        configLayout.addView(modelTil)

        val baseUrlTil = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle)
        baseUrlTil.hint = "Base URL (Optional)"
        baseUrlTil.layoutParams = margins(0, 0, 0, 12)
        baseUrlInput = TextInputEditText(baseUrlTil.context)
        baseUrlInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        baseUrlTil.addView(baseUrlInput)
        configLayout.addView(baseUrlTil)

        val imageUriTil = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle)
        imageUriTil.hint = "Image URI / Variable (Optional)"
        imageUriTil.layoutParams = margins(0, 0, 0, 12)
        imageUriInput = TextInputEditText(imageUriTil.context)
        imageUriInput.inputType = InputType.TYPE_CLASS_TEXT
        imageUriTil.addView(imageUriInput)
        configLayout.addView(imageUriTil)

        configCard.addView(configLayout)
        rootLayout.addView(configCard)

        tabLayout = TabLayout(this)
        tabLayout.addTab(tabLayout.newTab().setText("Manual Messages"))
        tabLayout.addTab(tabLayout.newTab().setText("Variable / JSON"))
        tabLayout.layoutParams = margins(0, 8, 0, 16)
        rootLayout.addView(tabLayout)

        manualContainer = LinearLayout(this)
        manualContainer.orientation = LinearLayout.VERTICAL
        messagesContainer = LinearLayout(this)
        messagesContainer.orientation = LinearLayout.VERTICAL
        manualContainer.addView(messagesContainer)

        val addMessageButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        addMessageButton.text = "Add Message"
        addMessageButton.setIconResource(android.R.drawable.ic_input_add)
        addMessageButton.layoutParams = margins(0, 16, 0, 16)
        addMessageButton.setOnClickListener { addMessageView("user", "") }
        manualContainer.addView(addMessageButton)
        rootLayout.addView(manualContainer)

        variableContainer = LinearLayout(this)
        variableContainer.orientation = LinearLayout.VERTICAL
        variableContainer.visibility = View.GONE

        val varCard = createCard()
        val varLayout = LinearLayout(this)
        varLayout.orientation = LinearLayout.VERTICAL
        varLayout.setPadding(padding, padding, padding, padding)
        val varTil = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle)
        varTil.hint = "Tasker Variable or JSON Array"
        variableInput = TextInputEditText(varTil.context)
        variableInput.setLines(5)
        variableInput.gravity = Gravity.TOP
        varTil.addView(variableInput)
        varLayout.addView(varTil)
        varCard.addView(varLayout)
        variableContainer.addView(varCard)
        rootLayout.addView(variableContainer)

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (tab?.position == 0) {
                    manualContainer.visibility = View.VISIBLE
                    variableContainer.visibility = View.GONE
                } else {
                    manualContainer.visibility = View.GONE
                    variableContainer.visibility = View.VISIBLE
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        val saveButton = MaterialButton(this)
        saveButton.text = "Save Configuration"
        saveButton.layoutParams = margins(0, 32, 0, 0)
        saveButton.setOnClickListener { helper.finishForTasker() }
        rootLayout.addView(saveButton)

        scrollView.addView(rootLayout)
        setContentView(scrollView)
        helper.onCreate()
    }

    private fun createCard(): MaterialCardView {
        val card = MaterialCardView(this)
        card.layoutParams = margins(0, 0, 0, 16)
        card.radius = 12 * resources.displayMetrics.density
        card.cardElevation = 4 * resources.displayMetrics.density
        return card
    }

    private fun margins(l: Int, t: Int, r: Int, b: Int): LinearLayout.LayoutParams {
        val density = resources.displayMetrics.density
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.setMargins((l * density).toInt(), (t * density).toInt(), (r * density).toInt(), (b * density).toInt())
        return params
    }

    private fun addMessageView(role: String, content: String) {
        val card = createCard()
        val messageLayout = LinearLayout(this)
        messageLayout.orientation = LinearLayout.VERTICAL
        val p = (12 * resources.displayMetrics.density).toInt()
        messageLayout.setPadding(p, p, p, p)
        val topRow = LinearLayout(this)
        topRow.orientation = LinearLayout.HORIZONTAL
        topRow.gravity = Gravity.CENTER_VERTICAL
        val roleSpinner = Spinner(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, roles)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        roleSpinner.adapter = adapter
        roleSpinner.setSelection(roles.indexOf(role).coerceAtLeast(0))
        topRow.addView(roleSpinner, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val deleteButton = ImageButton(this, null, android.R.attr.borderlessButtonStyle)
        deleteButton.setImageResource(android.R.drawable.ic_menu_delete)
        deleteButton.setColorFilter(Color.RED)
        deleteButton.setOnClickListener { messagesContainer.removeView(card) }
        topRow.addView(deleteButton)
        messageLayout.addView(topRow)
        val contentTil = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle)
        contentTil.hint = "Message Content"
        val contentInput = TextInputEditText(contentTil.context)
        contentInput.gravity = Gravity.TOP
        contentInput.setLines(3)
        contentTil.addView(contentInput)
        messageLayout.addView(contentTil)
        card.addView(messageLayout)
        messagesContainer.addView(card)
    }

    override fun assignFromInput(input: TaskerInput<AIInput>) {
        val regular = input.regular
        providerAutoComplete.setText(regular.provider ?: "OpenAI", false)
        apiKeyInput.setText(regular.apiKey)
        modelInput.setText(regular.model)
        baseUrlInput.setText(regular.baseUrl)
        imageUriInput.setText(regular.imageUri)
        val json = regular.messagesJson ?: "[]"
        if (json.trim().startsWith("[") && !json.contains("%")) {
            tabLayout.getTabAt(0)?.select()
            messagesContainer.removeAllViews()
            try {
                val jsonArray = JSONArray(json)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    addMessageView(obj.optString("role", "user"), obj.optString("content", ""))
                }
            } catch (e: Exception) { addMessageView("user", "") }
        } else {
            tabLayout.getTabAt(1)?.select()
            variableInput.setText(json)
            messagesContainer.removeAllViews()
            addMessageView("user", "")
        }
        if (messagesContainer.childCount == 0) addMessageView("user", "")
    }

    override val inputForTasker: TaskerInput<AIInput>
        get() {
            val jsonToSave = if (tabLayout.selectedTabPosition == 0) {
                val jsonArray = JSONArray()
                for (i in 0 until messagesContainer.childCount) {
                    val card = messagesContainer.getChildAt(i) as? MaterialCardView ?: continue
                    val layout = card.getChildAt(0) as? LinearLayout ?: continue
                    val topRow = layout.getChildAt(0) as? LinearLayout ?: continue
                    val roleSpinner = topRow.getChildAt(0) as? Spinner ?: continue
                    val contentTil = layout.getChildAt(1) as? TextInputLayout ?: continue
                    val contentInput = contentTil.editText ?: continue
                    val obj = JSONObject()
                    obj.put("role", roleSpinner.selectedItem.toString())
                    obj.put("content", contentInput.text.toString())
                    jsonArray.put(obj)
                }
                jsonArray.toString()
            } else { variableInput.text.toString() }
            return TaskerInput(AIInput(providerAutoComplete.text.toString(), apiKeyInput.text.toString(), modelInput.text.toString(), baseUrlInput.text.toString(), imageUriInput.text.toString(), jsonToSave))
        }
}

// ---------------- RUNNER (AI CALL) - 2026 FIXED ----------------

class AIRunner : TaskerPluginRunnerAction<AIInput, AIOutput>() {

    override fun run(context: Context, input: TaskerInput<AIInput>): TaskerPluginResult<AIOutput> {
        val provider = input.regular.provider ?: "OpenAI"
        val apiKey = input.regular.apiKey
        val customBaseUrl = input.regular.baseUrl ?: ""
        val imageUri = input.regular.imageUri ?: ""

        // 2026 DEFAULTS
        val model = input.regular.model?.takeIf { it.isNotBlank() } ?: when (provider) {
            "OpenAI" -> "gpt-4o"
            "Gemini" -> "gemini-3.1-flash"
            "OpenRouter" -> "google/gemini-flash-1.5"
            "Claude" -> "claude-4-6-sonnet"
            "Grok" -> "grok-2-latest"
            "Ollama" -> "llama3.1"
            else -> ""
        }

        if (apiKey.isNullOrBlank() && provider != "Ollama") return TaskerPluginResultSucess(AIOutput("Error: Missing API Key"))

        val messagesJson = input.regular.messagesJson ?: "[]"
        val messagesArray = try {
            val trimmed = messagesJson.trim()
            if (trimmed.startsWith("[")) JSONArray(trimmed)
            else JSONArray().put(JSONObject().apply { put("role", "user"); put("content", messagesJson) })
        } catch (e: Exception) {
            JSONArray().put(JSONObject().apply { put("role", "user"); put("content", messagesJson) })
        }

        var imageBase64: String? = null
        var imageMimeType: String? = "image/jpeg"
        if (imageUri.isNotBlank()) {
            try {
                val uri = android.net.Uri.parse(imageUri)
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()
                if (bytes != null) {
                    imageBase64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    imageMimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                }
            } catch (e: Exception) { }
        }

// Make sure to add this import at the very top of AIPlugin.kt:
// import java.util.concurrent.TimeUnit

        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS) // AI needs time to "think"
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
        val mediaType = "application/json".toMediaTypeOrNull()

        // 2026 FIX: Gemini uses v1beta for Gemini 3
        fun normalizeGeminiBaseUrl(raw: String?): String {
            val trimmed = raw?.trim().orEmpty()
            val defaultBase = "https://generativelanguage.googleapis.com/v1beta"
            return trimmed.ifBlank { defaultBase }.removeSuffix("/").removeSuffix("/models")
        }

        val (url, bodyJson, authHeaderName, authHeaderValue) = when (provider) {
            "OpenAI", "OpenRouter", "Grok" -> {
                val processedMessages = JSONArray()
                for (i in 0 until messagesArray.length()) {
                    val msg = messagesArray.getJSONObject(i)
                    if (i == messagesArray.length() - 1 && imageBase64 != null) {
                        val contentArray = JSONArray().apply {
                            put(JSONObject().put("type", "text").put("text", msg.optString("content")))
                            put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:$imageMimeType;base64,$imageBase64")))
                        }
                        processedMessages.put(JSONObject().put("role", msg.optString("role")).put("content", contentArray))
                    } else { processedMessages.put(msg) }
                }
                val defaultBase = when (provider) {
                    "OpenAI" -> "https://api.openai.com/v1"
                    "Grok" -> "https://api.x.ai/v1"
                    else -> "https://openrouter.ai/api/v1"
                }
                listOf("${customBaseUrl.ifBlank { defaultBase }.removeSuffix("/")}/chat/completions", JSONObject().put("model", model).put("messages", processedMessages).toString(), "Authorization", "Bearer $apiKey")
            }
            "Gemini" -> {
                // 1. MUST use v1beta for Gemini 3 Flash as of April 2026
                val baseUrl = customBaseUrl.ifBlank { "https://generativelanguage.googleapis.com/v1beta" }.removeSuffix("/")

                // 2. Clean the model string to prevent double-prefixing
                val cleanModel = model.removePrefix("models/").removePrefix("/")
                val finalUrl = "$baseUrl/models/$cleanModel:generateContent?key=$apiKey"

                // 3. Construct the 2026-compliant JSON body
                val contents = JSONArray()
                for (i in 0 until messagesArray.length()) {
                    val msg = messagesArray.getJSONObject(i)
                    // Gemini 3 Flash expects "model" instead of "assistant"
                    val role = if (msg.optString("role") == "assistant") "model" else "user"

                    val parts = JSONArray().put(JSONObject().put("text", msg.optString("content")))

                    // Handle image if present (Flash 3 supports high-density multi-modal)
                    if (i == messagesArray.length() - 1 && imageBase64 != null) {
                        parts.put(JSONObject().put("inline_data", JSONObject().apply {
                            put("mime_type", imageMimeType)
                            put("data", imageBase64)
                        }))
                    }
                    contents.put(JSONObject().put("role", role).put("parts", parts))
                }

                val body = JSONObject().put("contents", contents).toString()
                listOf(finalUrl, body, "", "")
            }
            "Claude" -> {
                val processedMessages = JSONArray()
                for (i in 0 until messagesArray.length()) {
                    val msg = messagesArray.getJSONObject(i)
                    if (msg.optString("role") == "system") continue
                    if (i == messagesArray.length() - 1 && imageBase64 != null) {
                        val contentArray = JSONArray().apply {
                            put(JSONObject().put("type", "image").put("source", JSONObject().put("type", "base64").put("media_type", imageMimeType).put("data", imageBase64)))
                            put(JSONObject().put("type", "text").put("text", msg.optString("content")))
                        }
                        processedMessages.put(JSONObject().put("role", msg.optString("role")).put("content", contentArray))
                    } else { processedMessages.put(msg) }
                }
                listOf("https://api.anthropic.com/v1/messages", JSONObject().put("model", model).put("max_tokens", 1024).put("messages", processedMessages).toString(), "x-api-key", apiKey ?: "")
            }
            "Ollama" -> {
                val processedMessages = JSONArray()
                for (i in 0 until messagesArray.length()) {
                    val msg = messagesArray.getJSONObject(i)
                    val obj = JSONObject().put("role", msg.optString("role")).put("content", msg.optString("content"))
                    if (i == messagesArray.length() - 1 && imageBase64 != null) obj.put("images", JSONArray().put(imageBase64))
                    processedMessages.put(obj)
                }
                val host = customBaseUrl.ifBlank { "http://10.0.2.2:11434" }.removeSuffix("/")
                listOf("$host/api/chat", JSONObject().put("model", model).put("messages", processedMessages).put("stream", false).toString(), "", "")
            }
            else -> return TaskerPluginResultSucess(AIOutput("Error: Unknown Provider"))
        }

        val requestBuilder = Request.Builder().url(url as String).post((bodyJson as String).toRequestBody(mediaType))
        if (authHeaderName.toString().isNotBlank()) requestBuilder.addHeader(authHeaderName as String, authHeaderValue as String)
        if (provider == "Claude") requestBuilder.addHeader("anthropic-version", "2023-06-01")
        if (provider == "OpenRouter") {
            requestBuilder.addHeader("HTTP-Referer", "https://github.com/chippytech/GenAITaskerPlugin")
            requestBuilder.addHeader("X-Title", "GenAI Tasker Plugin")
        }

        return try {
            val response = client.newCall(requestBuilder.build()).execute()
            val responseText = response.body?.string()
            if (!response.isSuccessful) return TaskerPluginResultSucess(AIOutput("Error ${response.code}: $responseText"))
            val obj = JSONObject(responseText ?: "")
            val reply = when (provider) {
                "OpenAI", "OpenRouter", "Grok" -> obj.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                "Gemini" -> obj.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                "Claude" -> obj.getJSONArray("content").getJSONObject(0).getString("text")
                "Ollama" -> obj.getJSONObject("message").getString("content")
                else -> "Error"
            }
            TaskerPluginResultSucess(AIOutput(reply, responseText))
        } catch (e: Exception) { TaskerPluginResultSucess(AIOutput("Network Error: ${e.message}")) }
    }
}