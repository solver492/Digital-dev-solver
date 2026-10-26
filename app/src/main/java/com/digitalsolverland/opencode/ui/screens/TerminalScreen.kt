package com.digitalsolverland.opencode.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digitalsolverland.opencode.runtime.LocalServer
import kotlinx.coroutines.launch

/**
 * Console intégrée : exécute des commandes dans la Debian embarquée (proot).
 * Ce n'est pas un émulateur de terminal complet (pas de PTY interactif), mais
 * toutes les commandes non interactives fonctionnent (apt, git, ls, opencode…).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    var command by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("Bienvenue dans la console Debian embarquée.\nTapez une commande puis ↵.\n") }
    var running by remember { mutableStateOf(false) }

    fun run(cmd: String) {
        val trimmed = cmd.trim()
        if (trimmed.isEmpty() || running) return
        running = true
        output += "\n$ " + trimmed + "\n"
        command = ""
        focus.clearFocus()
        scope.launch {
            val result = LocalServer.exec(context, trimmed)
            output += result + "\n"
            if (output.length > 120_000) output = output.takeLast(100_000)
            running = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Terminal") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("uname -a", "ls -la /root", "opencode --version", "apt-get update").forEach { preset ->
                    AssistChip(
                        onClick = { run(preset) },
                        label = { Text(preset, fontSize = 11.sp) },
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(10.dp),
            ) {
                Text(
                    text = output,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("commande…") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { run(command) }),
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = { run(command) },
                    enabled = !running && command.isNotBlank(),
                ) {
                    if (running) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Exécuter")
                    }
                }
            }

            Text(
                "Les fichiers de /root/workspace sont partagés avec le serveur OpenCode.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}
