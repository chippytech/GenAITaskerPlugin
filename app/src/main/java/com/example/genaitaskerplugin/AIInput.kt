package com.example.genaitaskerplugin

import com.joaomgcd.taskerpluginlibrary.input.*

@TaskerInputRoot
class AIInput @JvmOverloads constructor(
    @field:TaskerInputField("provider") var provider: String? = "OpenAI",
    @field:TaskerInputField("apikey") var apiKey: String? = null,
    @field:TaskerInputField("model") var model: String? = null,
    @field:TaskerInputField("baseUrl") var baseUrl: String? = null,
    @field:TaskerInputField("imageUri") var imageUri: String? = null,
    @field:TaskerInputField("messagesJson") var messagesJson: String? = null
)