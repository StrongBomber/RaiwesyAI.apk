package com.raiwesy.ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sparkles
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.raiwesy.ai.R

/**
 * Welcome screen shown when the chat history is empty:
 * brand mark, greeting and tappable suggestion chips.
 */
@Composable
fun EmptyState(
    onSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val suggestions = stringArrayResource(R.array.suggestions)

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(38.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.empty_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.empty_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 36.dp)
        )

        Spacer(Modifier.height(26.dp))
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            suggestions.forEach { suggestion ->
                SuggestionChip(
                    onClick = { onSuggestion(suggestion) },
                    label = { Text(suggestion) },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}
