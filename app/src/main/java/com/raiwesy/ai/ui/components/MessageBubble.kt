package com.raiwesy.ai.ui.components

import androidx.compose.animation.core.InfiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberInfiniteTransition
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.raiwesy.ai.data.ChatMessage
import com.raiwesy.ai.data.Role
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A single chat bubble (user = trailing/indigo, assistant = leading/surface)
 * with timestamp + copy/delete actions, typing dots and streaming cursor.
 */
@Composable
fun MessageBubble(
    message: ChatMessage,
    onCopy: (String) -> Unit,
    onDelete: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == Role.USER

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            AssistantAvatar()
            Spacer(Modifier.width(10.dp))
        }

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 330.dp)
                    .clip(
                        if (isUser)
                            RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
                        else
                            RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
                    )
                    .background(
                        if (isUser) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .padding(horizontal = 16.dp, vertical = 11.dp)
            ) {
                if (message.content.isEmpty()) {
                    if (message.streaming) {
                        TypingIndicator()
                    } else {
                        Spacer(Modifier.height(10.dp))
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
                        )
                        if (message.streaming) {
                            StreamingCursor()
                        }
                    }
                }
            }

            // Timestamp + actions row
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
            ) {
                Text(
                    text = formatTime(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = { onCopy(message.content) },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Kopyala",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
                IconButton(
                    onClick = { onDelete(message) },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Sil",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantAvatar() {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/** Three pulsing dots shown while the assistant is "thinking". */
@Composable
private fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        modifier = Modifier.padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = InfiniteRepeatable(
                    animation = tween(durationMillis = 650, delayMillis = index * 160),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$index"
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f * alpha)
                    )
            )
        }
    }
}

/** Blinking block cursor shown at the end of a streaming reply. */
@Composable
private fun StreamingCursor() {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 1f,
        animationSpec = InfiniteRepeatable(
            animation = tween(durationMillis = 500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )
    Box(
        modifier = Modifier
            .padding(start = 3.dp)
            .width(2.5.dp)
            .height(14.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
    )
}

private fun formatTime(millis: Long): String {
    return try {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
    } catch (t: Throwable) {
        ""
    }
}
