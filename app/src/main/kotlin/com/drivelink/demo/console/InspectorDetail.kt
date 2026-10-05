package com.drivelink.demo.console

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivelink.core.designsystem.component.DlOutlinedButton
import com.drivelink.core.designsystem.component.SectionTitle
import com.drivelink.core.designsystem.theme.DlTheme
import com.drivelink.core.domain.DriveLinkJson
import com.drivelink.core.network.inspector.InspectorEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Detail of one inspector entry: summary, request and response headers, bodies (JSON is
 * pretty-printed) and "Copy as cURL". Test tags: `inspector.detail`, `inspector.detail.copyCurl`,
 * `inspector.detail.status`, `inspector.detail.url`, `inspector.detail.requestBody`,
 * `inspector.detail.responseBody`. Back: `topbar_back`.
 */
@Composable
fun InspectorDetail(entry: InspectorEntry, toCurl: (InspectorEntry) -> String, modifier: Modifier = Modifier) {
    val c = DlTheme.colors
    val context = LocalContext.current
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("inspector.detail"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                entry.method,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                color = c.textPrimary,
            )
            Spacer(Modifier.width(10.dp))
            StatusBadge(entry.status, Modifier.testTag("inspector.detail.status"))
            Spacer(Modifier.width(10.dp))
            Text("${entry.durationMs} ms", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        }
        SelectionContainer {
            Text(
                entry.url,
                modifier = Modifier.testTag("inspector.detail.url"),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = c.textPrimary,
            )
        }
        DlOutlinedButton(
            text = "Copy as cURL",
            icon = Icons.Filled.ContentCopy,
            onClick = { copy(context, toCurl(entry)) },
            modifier = Modifier.testTag("inspector.detail.copyCurl"),
        )

        SectionTitle("Summary")
        KeyValues(
            listOfNotNull(
                "Profile" to (entry.profileName ?: "—"),
                "Scenario" to (entry.scenario ?: "default"),
                "Correlation id" to (entry.correlationId ?: "—"),
                "Attempt" to entry.attempt.toString(),
                entry.error?.let { "Error" to it },
            ),
        )

        SectionTitle("Request headers")
        KeyValues(entry.requestHeaders)
        entry.requestBody?.let {
            SectionTitle("Request body")
            CodeBlock(pretty(it), "inspector.detail.requestBody")
        }

        SectionTitle("Response headers")
        if (entry.responseHeaders.isEmpty()) {
            Text("No response.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        } else {
            KeyValues(entry.responseHeaders)
        }
        SectionTitle("Response body")
        CodeBlock(entry.responseBody?.let(::pretty) ?: "(empty)", "inspector.detail.responseBody")
    }
}

@Composable
private fun KeyValues(pairs: List<Pair<String, String>>) {
    val c = DlTheme.colors
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            pairs.forEach { (key, value) ->
                Row {
                    Text(
                        key,
                        modifier = Modifier.width(112.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = c.textSecondary,
                    )
                    Text(
                        value,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        color = c.textPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeBlock(text: String, tag: String) {
    val c = DlTheme.colors
    SelectionContainer {
        Text(
            text,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(c.card)
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
                .testTag(tag),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = c.textPrimary,
        )
    }
}

private val PrettyJson = Json(from = DriveLinkJson) { prettyPrint = true }

/** Pretty-printed JSON, or the text unchanged when it is not JSON. */
private fun pretty(text: String): String = try {
    PrettyJson.encodeToString(JsonElement.serializer(), PrettyJson.parseToJsonElement(text))
} catch (e: IllegalArgumentException) {
    text
}

private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("cURL", text))
    Toast.makeText(context, "cURL copied", Toast.LENGTH_SHORT).show()
}
