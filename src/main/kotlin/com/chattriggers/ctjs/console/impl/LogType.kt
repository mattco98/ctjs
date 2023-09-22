package com.chattriggers.ctjs.console.impl

import kotlinx.serialization.Serializable

@Serializable
enum class LogType {
    INFO,
    WARN,
    ERROR,
}
