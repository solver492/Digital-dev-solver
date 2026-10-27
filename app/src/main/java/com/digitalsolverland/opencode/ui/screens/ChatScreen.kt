package com.digitalsolverland.opencode.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.digitalsolverland.opencode.data.model.AgentInfo
import com.digitalsolverland.opencode.data.model.MessageContent
import com.digitalsolverland.opencode.data.model.ModelInfo
import com.digitalsolverland.opencode.data.model.ModelRef
import com.digitalsolverland.opencode.data.model.PromptPart
import com.digitalsolverland.opencode.data.model.SessionInfo
import com.digitalsolverland.opencode.data.model.SessionMessage
import com.digitalsolverland.opencode.data.model.ToolState
import com.digitalsolverland.opencode.ui.components.MarkdownText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* --------------------------- Chat entries -------------------------- */

private data class UserAttachment(
    val type: String,
    val url: String?,
    val mime: String?,
    val text: String?,
)

private sealed interface ChatEntry {
    val key: String

    data class User(
        override val key: String,
        val text: String,
        val attachments: List<UserAttachment> = emptyList(),
    ) : ChatEntry

    data class Assistant(
        override val key: String,
        val contents: List<MessageContent>,
        val error: String?,
        val running: Boolean,
    ) : ChatEntry

    data class Notice(override val key: String, val text: String, val warn: Boolean) : ChatEntry
}

private fun buildEntries(messages: List<SessionMessage>): List<ChatEntry> {
    val entries = mutableListOf<ChatEntry>()
    messages.forEach { message ->
        when (message.type) {
            "user" -> {
                val text = message.text.orEmpty()
                val attachments = mutableListOf<UserAttachment>()
                (message.allContent).forEach { content ->
                    val ctext = content.text
                    if (!ctext.isNullOrBlank() && ctext.startsWith("data:")) {
                        val mime = ctext.substringAfter("data:").substringBefore(";")
                        attachments += UserAttachment(
                            type = when {
                                ctext.startsWith("data:image") -> "image"
                                ctext.startsWith("data:audio") -> "audio"
                                content.type == "image" -> "image"
                                content.type == "audio" -> "audio"
                                else -> "file"
                            },
                            url = ctext,
                            mime = mime,
                            text = null,
                        )
                    } else if (content.type == "image" && !ctext.isNullOrBlank()) {
                        attachments += UserAttachment(type = "image", url = ctext, mime = null, text = null)
                    } else if (content.type == "audio" && !ctext.isNullOrBlank()) {
                        attachments += UserAttachment(type = "audio", url = ctext, mime = null, text = null)
                    } else if (content.type == "file" && !ctext.isNullOrBlank()) {
                        attachments += UserAttachment(type = "file", url = ctext, mime = null, text = null)
                    }
                }
                if (text.isNotBlank() || attachments.isNotEmpty()) {
                    entries += ChatEntry.User(message.id, text, attachments)
                }
            }
            "assistant" -> {
                val previous = entries.lastOrNull() as? ChatEntry.Assistant
                val error = jsonMessage(message.error)
                val running = message.time?.completed == null && error == null
                if (previous != null && previous.error == null &&
                    previous.contents.size <= 30
                ) {
                    entries[entries.lastIndex] = previous.copy(
                        contents = previous.contents + message.content,
                        error = error,
                        running = running,
                    )
                } else {
                    entries += ChatEntry.Assistant(
                        key = message.id,
                        contents = message.content,
                        error = error,
                        running = running,
                    )
                }
            }
            // "system" / "synthetic" are internal prompt material: never shown.
            "system", "synthetic" -> Unit
            "compaction" -> entries += ChatEntry.Notice(
                message.id,
                message.summary ?: "Contexte compacté",
                warn = false,
            )
            "shell" -> entries += ChatEntry.Notice(
                message.id,
                message.command ?: message.text ?: "Commande exécutée",
                warn = false,
            )
            "idle" -> Unit
            else -> Unit
        }
    }
    return entries
}

private fun jsonMessage(element: JsonElement?): String? {
    val obj = element as? JsonObject ?: return element?.toString()?.takeIf { it.isNotBlank() }
    val candidates = listOf("message", "name", "type")
    for (key in candidates) {
        val value = obj[key]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
        if (!value.isNullOrBlank() && value != "UnknownError") return value
    }
    val nested = obj["data"] as? JsonObject
    nested?.get("message")?.let {
        val text = runCatching { it.jsonPrimitive.content }.getOrNull()
        if (!text.isNullOrBlank()) return text
    }
    return obj.toString().take(300)
}

private fun ModelRef.label(): String = id.substringAfterLast('/')

/* ----------------------------- Screen ------------------------------ */

private data class PendingAttachment(
    val uri: Uri,
    val name: String,
    val mime: String,
    val data: String,
    val preview: Bitmap?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    session: SessionInfo,
    messages: List<SessionMessage>,
    models: List<ModelInfo>,
    agents: List<AgentInfo>,
    selectedModel: ModelRef?,
    selectedAgent: String?,
    isWorking: Boolean,
    activity: String?,
    onBack: () -> Unit,
    onSendMessage: (String, List<PromptPart>?) -> Unit,
    onStop: () -> Unit,
    onSelectModel: (ModelInfo) -> Unit,
    onSelectAgent: (AgentInfo?) -> Unit,
    onOpenSettings: () -> Unit,
    onCompact: () -> Unit = {},
    quickActions: List<Pair<String, String>> = emptyList(),
) {
    var inputText by remember { mutableStateOf("") }
    var showModelSheet by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showAgentSheet by remember { mutableStateOf(false) }
    val entries = remember(messages) { buildEntries(messages) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    val activeModel = session.model ?: selectedModel
    val activeAgent = session.agent ?: selectedAgent

    LaunchedEffect(entries.size, entries.lastOrNull()?.let { it as? ChatEntry.Assistant }?.contents?.size) {
        if (entries.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(entries.lastIndex) }
        }
    }

    val context = LocalContext.current
    var pendingAttachments by remember { mutableStateOf<List<PendingAttachment>>(emptyList()) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }

    fun readUri(uri: Uri): PendingAttachment? {
        return try {
            val contentResolver = context.contentResolver
            val mime = contentResolver.getType(uri) ?: "application/octet-stream"
            val name = uri.lastPathSegment ?: "file"
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val preview = if (mime.startsWith("image/")) {
                runCatching {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                }.getOrNull()
            } else null
            val compressed = if (mime.startsWith("image/") && preview != null) {
                val out = ByteArrayOutputStream()
                preview.compress(Bitmap.CompressFormat.JPEG, 80, out)
                out.toByteArray()
            } else bytes
            PendingAttachment(
                uri = uri,
                name = name,
                mime = if (mime.startsWith("image/") && compressed !== bytes) "image/jpeg" else mime,
                data = Base64.encodeToString(compressed, Base64.NO_WRAP),
                preview = if (mime.startsWith("image/")) preview else null,
            )
        } catch (_: Exception) {
            null
        }
    }

    fun readFile(file: File, mime: String): PendingAttachment? {
        return try {
            val bytes = file.readBytes()
            val preview = if (mime.startsWith("image/")) {
                runCatching {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                }.getOrNull()
            } else null
            PendingAttachment(
                uri = Uri.fromFile(file),
                name = file.name,
                mime = mime,
                data = Base64.encodeToString(bytes, Base64.NO_WRAP),
                preview = preview,
            )
        } catch (_: Exception) {
            null
        }
    }

    fun startRecording() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(context.cacheDir, "AUD_${timeStamp}.m4a")
            recordingFile = file
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder = r
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(128000)
            r.setAudioSamplingRate(44100)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            isRecording = true
        } catch (_: Exception) {
            recorder?.release()
            recorder = null
            isRecording = false
        }
    }

    fun stopRecording() {
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {
        }
        recorder = null
        isRecording = false
        val file = recordingFile
        if (file != null && file.exists()) {
            readFile(file, "audio/mp4")?.let { att ->
                pendingAttachments = pendingAttachments + att
            }
        }
        recordingFile = null
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            readUri(uri)?.let { att ->
                pendingAttachments = pendingAttachments + att
            }
        }
    }
    val imagePickerMultiple = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            readUri(uri)?.let { att ->
                pendingAttachments = pendingAttachments + att
            }
        }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            readUri(uri)?.let { att ->
                pendingAttachments = pendingAttachments + att
            }
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && cameraUri != null) {
            readUri(cameraUri!!)?.let { att ->
                pendingAttachments = pendingAttachments + att
            }
        }
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && !isRecording) {
            startRecording()
        }
    }

    fun createImageUri(): Uri {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFile = File.createTempFile("IMG_${timeStamp}_", ".jpg", context.cacheDir)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = session.title.ifBlank { "Nouvelle session" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = buildString {
                                append(activeAgent ?: "build")
                                append(" · ")
                                append(activeModel?.label() ?: "modèle auto")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Plus")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Compacter le contexte") },
                                enabled = !isWorking,
                                onClick = {
                                    showMenu = false
                                    onCompact()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Réglages") },
                                onClick = {
                                    showMenu = false
                                    onOpenSettings()
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        bottomBar = {
            Column {
                if (pendingAttachments.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(pendingAttachments) { att ->
                            Box {
                                if (att.preview != null) {
                                    Image(
                                        bitmap = att.preview.asImageBitmap(),
                                        contentDescription = att.name,
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(10.dp)),
                                        contentScale = ContentScale.Crop,
                                    )
                                } else {
                                    Surface(
                                        modifier = Modifier.size(64.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                        ) {
                                            Icon(
                                                if (att.mime.startsWith("audio/")) Icons.Default.MusicNote else Icons.Default.AudioFile,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                            Text(
                                                att.name.takeLast(8),
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = { pendingAttachments = pendingAttachments - att },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(20.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Retirer",
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                ChatComposer(
                    value = inputText,
                    onValueChange = { inputText = it },
                    isWorking = isWorking,
                    modelLabel = activeModel?.label(),
                    agentLabel = activeAgent,
                    onModelClick = { showModelSheet = true },
                    onAgentClick = { showAgentSheet = true },
                    onAttachClick = { showAttachMenu = true },
                    onSend = {
                        val text = inputText.trim()
                        if ((text.isNotEmpty() || pendingAttachments.isNotEmpty())) {
                            val parts = pendingAttachments.map { att ->
                                PromptPart(
                                    type = if (att.mime.startsWith("image/")) "image" else if (att.mime.startsWith("audio/")) "audio" else "file",
                                    mime = att.mime,
                                    url = "data:${att.mime};base64,${att.data}",
                                    filename = att.name,
                                    data = att.data,
                                )
                            }
                            onSendMessage(text, if (parts.isNotEmpty()) parts else null)
                            inputText = ""
                            pendingAttachments = emptyList()
                            keyboard?.hide()
                        }
                    },
                    onStop = onStop,
                    isRecording = isRecording,
                    onRecordStart = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            startRecording()
                        } else {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onRecordStop = { stopRecording() },
                    audioPermissionLauncher = audioPermissionLauncher,
                )
                if (showAttachMenu) {
                    ModalBottomSheet(onDismissRequest = { showAttachMenu = false }) {
                        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                            Text(
                                "Ajouter une pièce jointe",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            )
                            ListItem(
                                headlineContent = { Text("Galerie (images)") },
                                leadingContent = { Icon(Icons.Default.Image, null) },
                                modifier = Modifier.clickable {
                                    showAttachMenu = false
                                    imagePicker.launch("image/*")
                                },
                            )
                            ListItem(
                                headlineContent = { Text("Prendre une photo") },
                                leadingContent = { Icon(Icons.Default.CameraAlt, null) },
                                modifier = Modifier.clickable {
                                    showAttachMenu = false
                                    val uri = createImageUri()
                                    cameraUri = uri
                                    cameraLauncher.launch(uri)
                                },
                            )
                            ListItem(
                                headlineContent = { Text("Fichiers images") },
                                leadingContent = { Icon(Icons.Default.AttachFile, null) },
                                modifier = Modifier.clickable {
                                    showAttachMenu = false
                                    imagePickerMultiple.launch(arrayOf("image/*"))
                                },
                            )
                            ListItem(
                                headlineContent = { Text("Enregistrer un audio (micro)") },
                                leadingContent = { Icon(Icons.Default.Mic, null) },
                                modifier = Modifier.clickable {
                                    showAttachMenu = false
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        startRecording()
                                    } else {
                                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                            )
                            ListItem(
                                headlineContent = { Text("Fichier audio depuis l'appareil") },
                                leadingContent = { Icon(Icons.Default.MusicNote, null) },
                                modifier = Modifier.clickable {
                                    showAttachMenu = false
                                    audioPicker.launch("audio/*")
                                },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (entries.isEmpty()) {
            EmptyChat(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                modelLabel = activeModel?.label(),
                quickActions = quickActions,
                onQuickAction = { text ->
                    inputText = text
                },
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(entries, key = { it.key }) { entry ->
                    when (entry) {
                        is ChatEntry.User -> UserBubble(entry.text, entry.attachments)
                        is ChatEntry.Assistant -> AssistantBlock(entry)
                        is ChatEntry.Notice -> NoticeChip(entry.text)
                    }
                }
                if (isWorking) {
                    item(key = "working") { WorkingIndicator(activity) }
                }
            }
        }
    }

    if (showModelSheet) {
        ModelPickerSheet(
            models = models,
            selected = activeModel,
            onDismiss = { showModelSheet = false },
            onPick = {
                onSelectModel(it)
                showModelSheet = false
            },
        )
    }
    if (showAgentSheet) {
        AgentPickerSheet(
            agents = agents,
            selected = activeAgent,
            onDismiss = { showAgentSheet = false },
            onPick = {
                onSelectAgent(it)
                showAgentSheet = false
            },
        )
    }
}

/* ----------------------------- Bubbles ----------------------------- */

@Composable
private fun UserBubble(text: String, attachments: List<UserAttachment> = emptyList()) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 330.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (attachments.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.End) {
                    attachments.forEach { att ->
                        if (att.type == "image" && att.url != null) {
                            AsyncImage(
                                model = att.url,
                                contentDescription = null,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .fillMaxWidth(0.9f)
                                    .heightIn(max = 240.dp),
                                contentScale = ContentScale.Crop,
                            )
                        } else if (att.type == "audio" && att.url != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                                    Text("Audio joint", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
                                }
                            }
                        } else if (att.url != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(Icons.Default.AttachFile, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                                    Text("Fichier joint", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
                                }
                            }
                        }
                    }
                }
            }
            if (text.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
                ) {
                    Text(
                        text = text,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantBlock(entry: ChatEntry.Assistant) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Avatar()
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val texts = entry.contents.filter { it.type == "text" && !it.text.isNullOrBlank() }
            val reasonings = entry.contents.filter { it.type == "reasoning" && !it.text.isNullOrBlank() }
            val tools = entry.contents.filter { it.type == "tool" }

            reasonings.forEach { ReasoningBlock(it.text.orEmpty()) }

            if (texts.isEmpty() && tools.isEmpty() && entry.error == null && entry.running) {
                TypingDots()
            }

            texts.forEach { part -> MarkdownText(part.text.orEmpty()) }

            if (tools.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    tools.forEach { ToolRow(it) }
                }
            }

            entry.error?.let { message ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                            RoundedCornerShape(8.dp),
                        )
                        .padding(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun Avatar() {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.SmartToy,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
private fun ReasoningBlock(text: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                RoundedCornerShape(8.dp),
            )
            .clickable { expanded = !expanded }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Memory,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Raisonnement",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Text(
                text = text,
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToolRow(part: MessageContent) {
    val state = part.state
    val status = state?.status ?: "pending"
    var expanded by remember { mutableStateOf(false) }
    val summary = toolSummary(state) ?: when (status) {
        "completed" -> "Terminé"
        "error" -> jsonMessage(state?.error) ?: "Échec"
        "running" -> "En cours…"
        else -> "En attente…"
    }
    val tint = when (status) {
        "completed" -> MaterialTheme.colorScheme.tertiary
        "error" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                RoundedCornerShape(8.dp),
            )
            .clickable { expanded = !expanded }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (status == "running" || status == "pending") {
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    strokeWidth = 1.6.dp,
                    color = tint,
                )
            } else {
                Icon(
                    if (status == "completed") Icons.Default.Check else Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(14.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = part.name ?: "tool",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                toolOutput(state)?.let { output ->
                    Text(
                        text = output,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 24,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                toolInput(state)?.let { input ->
                    Text(
                        text = input,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 6.dp),
                        maxLines = 12,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Pulls the most meaningful field out of a tool call's input object. */
private fun toolSummary(state: ToolState?): String? {
    val input = state?.input as? JsonObject ?: return state?.title
    val keys = listOf(
        "command", "filePath", "path", "pattern", "query", "url", "description", "prompt", "name",
    )
    for (key in keys) {
        val value = input[key]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
        if (!value.isNullOrBlank()) return value.lineSequence().first().take(120)
    }
    return state?.title
}

private fun toolOutput(state: ToolState?): String? {
    val pieces = state?.content.orEmpty().mapNotNull { it.text }.filter { it.isNotBlank() }
    if (pieces.isNotEmpty()) return pieces.joinToString("\n").take(2000)
    return state?.output?.takeIf { it.isNotBlank() }
}

private fun toolInput(state: ToolState?): String? {
    val element = state?.input ?: return null
    val text = element.toString()
    return if (text.length <= 2) null else text.take(600)
}

@Composable
private fun NoticeChip(text: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    RoundedCornerShape(50),
                )
                .padding(horizontal = 12.dp, vertical = 5.dp),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "typing")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "alpha",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha)),
            )
        }
    }
}

@Composable
private fun WorkingIndicator(activity: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar()
        Spacer(Modifier.width(10.dp))
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 1.8.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = activity ?: "OpenCode travaille…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/* ---------------------------- Composer ----------------------------- */

@Composable
private fun ChatComposer(
    value: String,
    onValueChange: (String) -> Unit,
    isWorking: Boolean,
    modelLabel: String?,
    agentLabel: String?,
    onModelClick: () -> Unit,
    onAgentClick: () -> Unit,
    onAttachClick: () -> Unit = {},
    onSend: () -> Unit,
    onStop: () -> Unit,
    isRecording: Boolean = false,
    onRecordStart: () -> Unit = {},
    onRecordStop: () -> Unit = {},
    audioPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>? = null,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SelectorChip(
                    label = modelLabel ?: "Modèle auto",
                    icon = Icons.Default.Memory,
                    onClick = onModelClick,
                )
                SelectorChip(
                    label = agentLabel ?: "Agent build",
                    icon = Icons.Default.Build,
                    onClick = onAgentClick,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (isRecording) {
                    IconButton(onClick = onRecordStop) {
                        Icon(Icons.Default.StopCircle, contentDescription = "Arrêter l'enregistrement", tint = MaterialTheme.colorScheme.error)
                    }
                } else {
                    Row {
                        IconButton(onClick = onRecordStart) {
                            Icon(Icons.Default.Mic, contentDescription = "Enregistrer un audio")
                        }
                        IconButton(onClick = onAttachClick) {
                            Icon(Icons.Default.AttachFile, contentDescription = "Joindre un fichier")
                        }
                    }
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Demandez n'importe quoi…") },
                    shape = RoundedCornerShape(22.dp),
                    maxLines = 6,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        focusedContainerColor = MaterialTheme.colorScheme.background,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background,
                    ),
                )
                if (isWorking) {
                    FilledIconButton(
                        onClick = onSend,
                        enabled = value.isNotBlank(),
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Ajouter à la file")
                    }
                } else {
                    FilledIconButton(
                        onClick = onSend,
                        enabled = value.isNotBlank(),
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Envoyer")
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectorChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/* --------------------------- Empty state --------------------------- */

@Composable
private fun EmptyChat(
    modifier: Modifier = Modifier,
    modelLabel: String?,
    quickActions: List<Pair<String, String>>,
    onQuickAction: (String) -> Unit,
) {
    Column(
        modifier = modifier
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.SmartToy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("Nouvelle session", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Modèle : ${modelLabel ?: "par défaut du serveur"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        quickActions.take(3).forEach { (title, prompt) ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onQuickAction(prompt) },
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(title, style = MaterialTheme.typography.labelLarge)
                    Text(
                        prompt,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/* --------------------------- Pickers ------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerSheet(
    models: List<ModelInfo>,
    selected: ModelRef?,
    onDismiss: () -> Unit,
    onPick: (ModelInfo) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var freeOnly by remember { mutableStateOf(false) }
    val filtered = remember(models, query, freeOnly) {
        models.filter { model ->
            (!freeOnly || model.isFree) &&
                (query.isBlank() ||
                    model.label.contains(query, true) ||
                    model.refId.contains(query, true) ||
                    model.providerID.contains(query, true))
        }
    }
    val grouped = remember(filtered) { filtered.groupBy { it.providerID }.toSortedMap() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Choisir un modèle",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                placeholder = { Text("Rechercher (ex. claude, gpt, free)") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = freeOnly,
                    onClick = { freeOnly = !freeOnly },
                    label = { Text("Gratuits uniquement") },
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${filtered.size} modèles",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(modifier = Modifier.heightIn(max = 460.dp)) {
                grouped.forEach { (provider, list) ->
                    item(key = "h-$provider") {
                        Text(
                            text = provider.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(list, key = { provider + "/" + it.refId }) { model ->
                        val isSelected =
                            selected?.id == model.refId && selected.providerID == model.providerID
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(model) }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    model.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (model.isFree) Badge("GRATUIT", MaterialTheme.colorScheme.tertiary)
                                    if (model.capabilities?.reasoning == true) Badge("RAISONNEMENT", MaterialTheme.colorScheme.primary)
                                    if (model.capabilities?.tools == true) Badge("OUTILS", MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            "Aucun modèle ne correspond.",
                            modifier = Modifier.padding(20.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp, fontWeight = FontWeight.Bold),
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentPickerSheet(
    agents: List<AgentInfo>,
    selected: String?,
    onDismiss: () -> Unit,
    onPick: (AgentInfo?) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
            Text(
                "Choisir un agent",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                item(key = "auto") {
                    AgentRow(
                        title = "Par défaut du serveur",
                        subtitle = "Laisse OpenCode choisir l'agent",
                        selected = selected.isNullOrBlank(),
                        onClick = { onPick(null) },
                    )
                }
                items(agents, key = { it.id }) { agent ->
                    AgentRow(
                        title = agent.label,
                        subtitle = agent.description ?: agent.mode ?: "",
                        selected = agent.id == selected,
                        onClick = { onPick(agent) },
                    )
                }
                if (agents.isEmpty()) {
                    item {
                        Text(
                            "Aucun agent exposé par ce serveur.",
                            modifier = Modifier.padding(20.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
