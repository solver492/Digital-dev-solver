package com.digitalsolverland.opencode.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.digitalsolverland.opencode.data.model.Session
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    sessions: List<Session>,
    onSessionClick: (Session) -> Unit,
    onCreateSession: () -> Unit,
    onDeleteSession: (Session) -> Unit,
    onSettingsClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf<Session?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OpenCode") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Text("⚙️")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateSession,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Session")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No sessions yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Tap + to create a new session",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sessions.sortedByDescending { it.time?.updated ?: it.time?.created ?: 0 }) { session ->
                    SessionItem(
                        session = session,
                        onClick = { onSessionClick(session) },
                        onMenuClick = { showMenu = session }
                    )
                }
            }
        }
    }
    
    showMenu?.let { session ->
        AlertDialog(
            onDismissRequest = { showMenu = null },
            title = { Text("Session Options") },
            text = { Text(session.title ?: "Untitled Session") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteSession(session)
                        showMenu = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMenu = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SessionItem(
    session: Session,
    onClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title ?: "Untitled Session",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                session.time?.updated?.let { timestamp ->
                    val date = Date(timestamp)
                    val formatter = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                    Text(
                        text = formatter.format(date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                session.summary?.let { summary ->
                    if (summary.files > 0) {
                        Text(
                            text = "${summary.files} files • +${summary.additions} -${summary.deletions}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.MoreVert, contentDescription = "More")
            }
        }
    }
}
