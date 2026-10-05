package com.drivelink.core.network.inspector

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory ring buffer of the last [capacity] HTTP attempts for the Network Inspector screen.
 * Thread-safe. [entries] lists the newest entry first. Nothing is persisted.
 */
@Singleton
class NetworkInspector(private val capacity: Int) {

    @Inject
    constructor() : this(DEFAULT_CAPACITY)

    init {
        require(capacity > 0) { "capacity must be > 0" }
    }

    private val ids = AtomicLong(0)
    private val lock = Any()
    private val buffer = ArrayDeque<InspectorEntry>(capacity)
    private val _entries = MutableStateFlow<List<InspectorEntry>>(emptyList())

    val entries: StateFlow<List<InspectorEntry>> = _entries.asStateFlow()

    fun nextId(): Long = ids.incrementAndGet()

    fun record(entry: InspectorEntry) {
        synchronized(lock) {
            buffer.addFirst(entry)
            while (buffer.size > capacity) buffer.removeLast()
            _entries.value = buffer.toList()
        }
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            _entries.value = emptyList()
        }
    }

    /**
     * The entry as a shell `curl` command ("Copy as cURL"). Masked header values stay masked,
     * so the command is safe to share but needs the real token to run.
     */
    fun toCurl(entry: InspectorEntry): String = buildString {
        append("curl -X ").append(entry.method).append(' ').append(quote(entry.url))
        for ((name, value) in entry.requestHeaders) {
            append(" \\\n  -H ").append(quote("$name: $value"))
        }
        if (entry.requestBody != null) {
            append(" \\\n  --data-raw ").append(quote(entry.requestBody))
        }
    }

    companion object {
        const val DEFAULT_CAPACITY = 200
        const val MAX_BODY_BYTES = 64 * 1024
        const val TRUNCATED_MARKER = "\n… [truncated at 64 KB]"
        const val MASK = "••••"

        /** Masks `Bearer <token>` as `Bearer …` plus the last 4 characters. */
        fun maskBearer(value: String): String {
            val token = value.removePrefix("Bearer ").trim()
            val tail = if (token.length > 4) token.takeLast(4) else ""
            return "Bearer …$tail"
        }

        private fun quote(text: String): String = "'" + text.replace("'", "'\\''") + "'"
    }
}
