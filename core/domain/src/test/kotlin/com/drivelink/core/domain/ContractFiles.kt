package com.drivelink.core.domain

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** One entry of api/examples/index.json. Only the fields the tests use. */
@Serializable
data class IndexEntry(
    val operationId: String,
    val status: Int,
    val example: String,
    /** Null for documentation-only examples. */
    val scenario: String? = null,
    /** Path relative to api/examples. Null when the response has no body (204). */
    val file: String? = null,
    /** Request matchers, for example X-Poll-Attempt to "^[12]$". */
    val match: Map<String, String> = emptyMap(),
    val invalidOnPurpose: Boolean = false,
)

/** Access to the API contract files. build.gradle.kts sets the system property drivelink.repoRoot. */
object ContractFiles {
    val repoRoot: File by lazy {
        val path = System.getProperty("drivelink.repoRoot")
            ?: error("System property drivelink.repoRoot is not set. Run the tests through Gradle.")
        File(path)
    }

    val examplesDir: File get() = repoRoot.resolve("api/examples")

    val rawDir: File get() = repoRoot.resolve("api/examples-raw")

    val index: List<IndexEntry> by lazy {
        DriveLinkJson.decodeFromString(
            ListSerializer(IndexEntry.serializer()),
            examplesDir.resolve("index.json").readText(),
        )
    }

    fun read(entry: IndexEntry): String =
        examplesDir.resolve(requireNotNull(entry.file) { "No file for $entry" }).readText()
}
