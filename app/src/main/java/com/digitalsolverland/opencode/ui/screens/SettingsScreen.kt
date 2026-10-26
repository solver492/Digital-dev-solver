package com.digitalsolverland.opencode.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    serverUrl: String,
    serverPassword: String,
    isConnected: Boolean,
    isConnecting: Boolean,
    lastError: String?,
    onSave: (String, String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenLocalServer: () -> Unit,
    onOpenLogs: () -> Unit,
    onBack: () -> Unit,
) {
    var url by remember { mutableStateOf(serverUrl) }
    var password by remember { mutableStateOf(serverPassword) }
    var showPassword by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Connexion") },
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
            StatusCard(isConnected = isConnected, isConnecting = isConnecting, lastError = lastError)

            SectionCard(title = "Où tourne le serveur ?") {
                Text(
                    "Trois façons d'utiliser OpenCode : directement sur ce téléphone, depuis votre ordinateur " +
                        "en Wi-Fi, ou via un serveur distant.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onOpenLocalServer,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Sur ce téléphone (autonome)")
                }
                Text(
                    "Aucun PC nécessaire : l'app installe une distribution Linux et lance OpenCode elle-même.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = onOpenLogs,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Ouvrir le journal")
                }
            }

            SectionCard(title = "Serveur OpenCode") {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Adresse") },
                    placeholder = { Text("http://192.168.1.20:4096") },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Mot de passe du serveur") },
                    placeholder = { Text("affiché par opencode serve") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPassword) "Masquer" else "Afficher",
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = if (showPassword) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onSave(url.trim(), password)
                            onConnect()
                        },
                        enabled = !isConnecting && url.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (isConnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(15.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (isConnecting) "Connexion…" else "Se connecter")
                    }
                    if (isConnected) {
                        OutlinedButton(
                            onClick = onDisconnect,
                            shape = RoundedCornerShape(12.dp),
                        ) { Text("Déconnecter") }
                    }
                }
            }

            SectionCard(title = "Démarrer le serveur sur votre ordinateur") {
                Text(
                    "Exécutez OpenCode en mode serveur, puis notez le mot de passe affiché :",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val command = "opencode serve --hostname 0.0.0.0 --port 4096"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = command,
                        modifier = Modifier.weight(1f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    IconButton(onClick = { clipboard.setText(AnnotatedString(command)) }) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copier",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    "Sur le même Wi-Fi, utilisez l'IP locale de votre ordinateur (ex. 192.168.1.20). " +
                        "Le serveur imprime « server password … » : collez-le ci-dessus.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard(title = "Bon à savoir") {
                InfoLine("Toutes les capacités d'OpenCode sont accessibles : sessions, agents, outils, modèles locaux ou cloud.")
                InfoLine("Les modèles gratuits de votre configuration apparaissent avec le badge GRATUIT dans le sélecteur.")
                InfoLine("Le mot de passe est chiffré sur l'appareil (EncryptedSharedPreferences) et n'est jamais journalisé.")
            }

            Text(
                "OpenCode Mobile 2.1 · client natif API v2",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun StatusCard(isConnected: Boolean, isConnecting: Boolean, lastError: String?) {
    val color = when {
        isConnecting -> MaterialTheme.colorScheme.tertiary
        isConnected -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
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
            if (isConnected) Icons.Default.CheckCircle else Icons.Default.Lock,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = when {
                    isConnecting -> "Connexion en cours…"
                    isConnected -> "Connecté au serveur"
                    else -> "Non connecté"
                },
                style = MaterialTheme.typography.labelLarge,
                color = color,
            )
            if (!isConnected && !lastError.isNullOrBlank()) {
                Text(
                    text = lastError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}

@Composable
private fun InfoLine(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(4.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
