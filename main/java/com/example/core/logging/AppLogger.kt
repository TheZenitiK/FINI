package com.example.core.logging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    const val MAX_LINES = 300

    private val lock = Any()
    private val buffer = ArrayDeque<String>(MAX_LINES + 1)
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var enabled: Boolean = false

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    fun isEnabled(): Boolean = enabled

    fun setEnabled(isEnabled: Boolean, startupBanner: String? = null) {
        synchronized(lock) {
            val wasEnabled = enabled
            enabled = isEnabled
            if (isEnabled && !wasEnabled) {
                appendLocked(
                    "APP",
                    startupBanner ?: "Журнал событий включён"
                )
            }
        }
    }

    fun log(tag: String, message: String) {
        if (!enabled) return
        synchronized(lock) {
            if (!enabled) return
            appendLocked(tag, message)
        }
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            _logs.value = emptyList()
        }
    }

    fun exportAll(): String {
        synchronized(lock) {
            return buffer.joinToString("\n")
        }
    }

    private fun appendLocked(tag: String, message: String) {
        val ts = synchronized(timeFormat) { timeFormat.format(Date()) }
        val lines = message.lines()
        for (raw in lines) {
            val clean = raw.trimEnd()
            if (clean.isEmpty() && lines.size > 1) continue
            val entry = "[$ts] [$tag] $clean"
            if (buffer.size >= MAX_LINES) {
                buffer.removeFirst()
            }
            buffer.addLast(entry)
        }
        _logs.value = buffer.toList()
    }
}
