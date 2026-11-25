package com.translateai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.translateai.data.GeminiRepository
import com.translateai.data.GroundingLink
import com.translateai.data.SecureKeyStore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val keyStore = SecureKeyStore(applicationContext)
        val repository = GeminiRepository()
        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            ) {
                TranslateAiScreen(
                    keyStore = keyStore,
                    repository = repository,
                    onOpenUrl = { raw -> openOrCopy(raw) },
                    onCopy = { raw -> copyText(raw) }
                )
            }
        }
    }

    private fun openOrCopy(raw: String) {
        val uri = validatedWebUri(raw)
        if (uri == null) {
            Toast.makeText(this, "ลิงก์ไม่ถูกต้อง", Toast.LENGTH_SHORT).show()
            copyText(raw)
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: Exception) {
            Toast.makeText(this, "เปิดเบราว์เซอร์ไม่ได้ กำลังคัดลอกลิงก์", Toast.LENGTH_LONG).show()
            copyText(uri.toString())
        }
    }

    private fun copyText(raw: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("TranslateAI link", raw))
        Toast.makeText(this, "คัดลอกแล้ว", Toast.LENGTH_SHORT).show()
    }

    private fun validatedWebUri(raw: String): Uri? {
        return try {
            val uri = Uri.parse(raw.trim())
            val scheme = uri.scheme?.lowercase()
            if ((scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()) uri else null
        } catch (_: Exception) {
            null
        }
    }
}

private data class ChatMessage(
    val id: Long,
    val question: String,
    val answer: String,
    val links: List<GroundingLink>,
    val error: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TranslateAiScreen(
    keyStore: SecureKeyStore,
    repository: GeminiRepository,
    onOpenUrl: (String) -> Unit,
    onCopy: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var apiKey by remember { mutableStateOf(keyStore.getApiKey().orEmpty()) }
    var keyInput by remember { mutableStateOf(apiKey) }
    var showKey by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(apiKey.isBlank()) }
    var searchEnabled by remember { mutableStateOf(true) }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val messages = remember { mutableStateListOf<ChatMessage>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TranslateAI") },
                actions = {
                    IconButton(onClick = { keyInput = apiKey; showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "ตั้งค่า API")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ค้นหาข้อมูลบน Google", modifier = Modifier.weight(1f))
                Switch(checked = searchEnabled, onCheckedChange = { searchEnabled = it })
            }
            if (searchEnabled) {
                Text(
                    "โหมดค้นหาใช้ Google Search grounding ของ Gemini เพื่อค้นเว็บและแนบแหล่งข้อมูล",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "โหมดแชตปกติ ไม่เรียกค้นเว็บ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))
            if (messages.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("สวัสดี! ฉันคือ TranslateAI", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text("พิมพ์คำถามได้เลย เปิดโหมดค้นหาเพื่อให้ AI ค้นเว็บและแสดงลิงก์อ้างอิง")
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("คุณ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text(message.question)
                            Spacer(Modifier.height(10.dp))
                            Text("TranslateAI", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text(message.answer.ifBlank { message.error ?: "ไม่มีข้อความตอบกลับ" })
                            message.error?.let {
                                Spacer(Modifier.height(6.dp))
                                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                            if (message.links.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Text("แหล่งข้อมูล / ลิงก์", style = MaterialTheme.typography.titleSmall)
                                message.links.forEach { link ->
                                    LinkRow(link = link, onOpen = onOpenUrl, onCopy = onCopy)
                                }
                            }
                        }
                    }
                }
                if (loading) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("กำลังประมวลผล…")
                        }
                    }
                }
            }

            status?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading,
                label = { Text(if (searchEnabled) "พิมพ์สิ่งที่ต้องการค้นหา / ถาม AI" else "พิมพ์ข้อความถึง AI") },
                minLines = 2,
                maxLines = 4
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val question = input.trim()
                    if (question.isBlank() || loading) return@Button
                    if (apiKey.isBlank()) {
                        status = "กรุณาเพิ่ม Gemini API key ในการตั้งค่า"
                        showSettings = true
                        return@Button
                    }
                    input = ""
                    status = null
                    loading = true
                    scope.launch {
                        try {
                            val history = messages.map { it.question to it.answer }.filter { it.second.isNotBlank() }.takeLast(12)
                            val response = repository.ask(apiKey, question, searchEnabled, history)
                            messages.add(
                                ChatMessage(
                                    id = System.nanoTime(),
                                    question = question,
                                    answer = response.answer,
                                    links = response.links
                                )
                            )
                        } catch (e: Exception) {
                            messages.add(
                                ChatMessage(
                                    id = System.nanoTime(),
                                    question = question,
                                    answer = "",
                                    links = emptyList(),
                                    error = e.message ?: "เกิดข้อผิดพลาดในการเชื่อมต่อ"
                                )
                            )
                        } finally {
                            loading = false
                        }
                    }
                },
                enabled = !loading && input.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (searchEnabled) "ค้นหาและถาม AI" else "ส่งข้อความ")
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { if (apiKey.isNotBlank()) showSettings = false },
            title = { Text("ตั้งค่า Gemini API") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "ใส่ Gemini API key เพื่อใช้งานแชต AI และ Google Search grounding " +
                            "คีย์จะถูกเก็บแบบเข้ารหัสในเครื่องนี้"
                    )
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it.trim() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Gemini API key") },
                        singleLine = true,
                        visualTransformation = if (showKey) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = showKey, onCheckedChange = { showKey = it })
                        Text("แสดง API key")
                    }
                    Text(
                        "สร้างคีย์ได้ที่ aistudio.google.com/app/apikey",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (keyInput.isNotBlank()) {
                        keyStore.saveApiKey(keyInput)
                        apiKey = keyInput
                        status = null
                        showSettings = false
                    }
                }) { Text("บันทึก") }
            },
            dismissButton = {
                TextButton(onClick = { if (apiKey.isNotBlank()) showSettings = false }) { Text("ปิด") }
            }
        )
    }
}

@Composable
private fun LinkRow(
    link: GroundingLink,
    onOpen: (String) -> Unit,
    onCopy: (String) -> Unit
) {
    val uri = link.url.trim()
    val display = link.title.ifBlank { uri }
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(display, style = MaterialTheme.typography.bodyMedium)
                Text(uri, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { onOpen(uri) }) {
                Icon(Icons.Default.OpenInNew, contentDescription = "เปิดลิงก์")
            }
            IconButton(onClick = { onCopy(uri) }) {
                Icon(Icons.Default.ContentCopy, contentDescription = "คัดลอกลิงก์")
            }
        }
    }
}
