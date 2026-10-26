package com.digitalsolverland.opencode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ----------------------------- Parsing ----------------------------- */

private sealed interface MdBlock {
    data class TextBlock(val text: String) : MdBlock
    data class CodeBlock(val code: String, val language: String?) : MdBlock
}

private fun splitBlocks(raw: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val buffer = StringBuilder()
    var code: StringBuilder? = null
    var language: String? = null

    fun flushText() {
        if (buffer.isNotBlank()) blocks += MdBlock.TextBlock(buffer.toString().trim('\n'))
        buffer.clear()
    }

    raw.lines().forEach { line ->
        val fence = line.trimStart()
        if (code == null && fence.startsWith("```")) {
            flushText()
            code = StringBuilder()
            language = fence.removePrefix("```").trim().ifBlank { null }
        } else if (code != null && fence.startsWith("```")) {
            blocks += MdBlock.CodeBlock(code.toString().trimEnd('\n'), language)
            code = null
            language = null
        } else if (code != null) {
            code.appendLine(line)
        } else {
            buffer.appendLine(line)
        }
    }
    val dangling = code
    if (dangling != null) {
        // Streaming text often opens a fence before closing it: still show it.
        blocks += MdBlock.CodeBlock(dangling.toString().trimEnd('\n'), language)
    } else {
        flushText()
    }
    return blocks
}

/** Handles the inline subset LLMs actually emit: **bold**, *italic*, `code`. */
private fun inlineMarkdown(text: String, codeColor: Color, accent: Color): AnnotatedString =
    buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("```", i) -> i += 3
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end == -1) {
                        append(text.substring(i)); i = text.length
                    } else {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    }
                }
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end == -1) {
                        append(text.substring(i)); i = text.length
                    } else {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = codeColor,
                                color = accent,
                                fontSize = 13.sp,
                            )
                        ) { append(text.substring(i + 1, end)) }
                        i = end + 1
                    }
                }
                text.startsWith("__", i) -> {
                    val end = text.indexOf("__", i + 2)
                    if (end == -1) {
                        append(text.substring(i)); i = text.length
                    } else {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    }
                }
                else -> {
                    append(text[i]); i++
                }
            }
        }
    }

/* --------------------------- Composables --------------------------- */

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val blocks = remember(text) { splitBlocks(text) }
    val inlineCodeBg = MaterialTheme.colorScheme.surfaceVariant
    val accent = MaterialTheme.colorScheme.primary

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.TextBlock -> {
                    if (block.text.isNotBlank()) {
                        SelectionContainer {
                            Text(
                                text = inlineMarkdown(block.text, inlineCodeBg, accent),
                                color = color,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    lineHeight = 21.sp,
                                    fontSize = 14.5.sp,
                                ),
                            )
                        }
                    }
                }
                is MdBlock.CodeBlock -> CodeBlockView(block.code, block.language)
            }
        }
    }
}

@Composable
fun CodeBlockView(code: String, language: String?) {
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                RoundedCornerShape(10.dp),
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = language?.lowercase() ?: "code",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { clipboard.setText(AnnotatedString(code)) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copier",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
        SelectionContainer {
            Text(
                text = code,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
