package com.digitalsolverland.opencode.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digitalsolverland.opencode.runtime.LocalPhase
import com.digitalsolverland.opencode.runtime.LocalServer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalServerScreen(
    onBack: () -> Unit,
    onInstall: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onUseLocalServer: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenTerminal: () -> Unit,
) {
    val context = LocalContext.current
    val phase by LocalServer.phase.collectAsState()
    val progress by LocalServer.progress.collectAsState()
    val message by LocalServer.message.collectAsState()
    val port by LocalServer.port.collectAsState()
    val installed by LocalServer.installed.collectAsState()

    LaunchedEffect(Unit) { LocalServer.refresh(context) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Serveur local") },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PhaseCard(phase = phase, message = message, port = port, installed = installed, progress = progress)

            if (phase == LocalPhase.INSTALLING) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp)),
                )
            }

            when {
                !installed -> {
                    Button(
                        onClick = onInstall,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Installer le runtime Linux (≈180 Mo)")
                    }
                    Text(
                        "Téléchargement unique : Debian 12 arm64 + le binaire OpenCode 2.1.4. " +
                            "Comptez ~500 Mo d'espace libre et quelques minutes. Wi-Fi recommandé.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                phase == LocalPhase.RUNNING -> {
                    Button(
                        onClick = onUseLocalServer,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Utiliser ce serveur")
                    }
                    OutlinedButton(
                        onClick = onStop,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Arrêter le serveur")
                    }
                }

                else -> {
                    Button(
                        onClick = onStart,
                        enabled = phase != LocalPhase.STARTING && phase != LocalPhase.INSTALLING,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (phase == LocalPhase.STARTING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (phase == LocalPhase.STARTING) "Démarrage…" else "Démarrer le serveur")
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onOpenTerminal,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    enabled = installed,
                ) {
                    Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Terminal")
                }
                OutlinedButton(
                    onClick = onOpenLogs,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.Article, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Journal")
                }
            }

            SectionCard("Environnement embarqué") {
                KeyValue("Distribution", "Debian 12 (bookworm) arm64")
                KeyValue("Moteur", "OpenCode 2.1.4 (binaire glibc)")
                KeyValue("Isolation", "proot · rootfs privé à l'app")
                KeyValue("Port local", port.toString())
                KeyValue("Espace utilisé", "≈ 500 Mo")
                if (installed) {
                    KeyValue("Réinstallation", "supprimez les données de l'app")
                }
            }

            SectionCard("Comment ça marche") {
                Text(
                    "L'app télécharge une mini-distribution Debian, puis lance le vrai serveur OpenCode " +
                        "à l'intérieur via proot. Aucun PC, aucun compte root, aucune modification système : " +
                        "tout reste dans le stockage privé de l'application.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Le serveur écoute uniquement sur 127.0.0.1 : il n'est pas accessible depuis le réseau.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                "Astuce : gardez l'app en mode « Serveur local » dans Connexion pour un redémarrage automatique.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun PhaseCard(
    phase: LocalPhase,
    message: String,
    port: Int,
    installed: Boolean,
    progress: Float,
) {
    val (color, label) = when (phase) {
        LocalPhase.RUNNING -> MaterialTheme.colorScheme.tertiary to "Serveur actif"
        LocalPhase.STARTING -> MaterialTheme.colorScheme.tertiary to "Démarrage"
        LocalPhase.INSTALLING -> MaterialTheme.colorScheme.tertiary to "Installation"
        LocalPhase.ERROR -> MaterialTheme.colorScheme.error to "Erreur"
        LocalPhase.STOPPED -> MaterialTheme.colorScheme.primary to "Prêt"
        LocalPhase.NOT_INSTALLED -> MaterialTheme.colorScheme.onSurfaceVariant to "Non installé"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when (phase) {
                LocalPhase.RUNNING, LocalPhase.STOPPED -> Icons.Default.CheckCircle
                LocalPhase.ERROR -> Icons.Default.Error
                LocalPhase.INSTALLING -> Icons.Default.CloudDownload
                else -> Icons.Default.PlayArrow
            },
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelLarge, color = color)
            Text(
                message.ifBlank {
                    when {
                        !installed -> "Le runtime Linux n'est pas encore installé."
                        else -> "Serveur arrêté sur le port $port."
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
            )
            if (phase == LocalPhase.RUNNING) {
                Text(
                    "http://127.0.0.1:$port",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
