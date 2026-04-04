package com.example.genaitaskerplugin

import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable

@TaskerOutputObject
class AIOutput(
    @get:TaskerOutputVariable("ai_response", labelResId = 0) val response: String? = null,
    @get:TaskerOutputVariable("ai_full_json", labelResId = 0) val fullJson: String? = null
)