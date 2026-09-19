@file:OptIn(ExperimentalMaterial3Api::class)

package com.raiwesy.ai.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raiwesy.ai.BuildConfig
import com.raiwesy.ai.R
import com.raiwesy.ai.ui.components.Composer
import com.raiwesy.ai.ui.components.EmptyState
import com.raiwesy.ai.ui.components.ErrorBanner
import com.raiwesy.ai.ui.components.MessageBubble
import com.raiwesy.ai.ui.components.NoKeyBanner
import com.raiwesy.ai.ui.components.OfflineBanner
import com.raiwesy.ai.ui.components.SettingsSheet

/**
 * Main chat screen (the "View" of the MVVM architecture).
 * Pure Compose: renders [ChatUiState] and forwards user intents to the
 * [ChatViewModel]. Holds no business logic.
 */
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current

    var text by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showCrashDialog by remember { mutableStateOf(viewModel.consumeCrashMark()) }

    // ---------------- transient snackbar ----------------
    val snackbar = state.snackbar
    LaunchedEffect(snackbar?.id) {
        snackbar ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(snackbar.message)
    }

    // ---------------- auto-scroll ----------------
    val messageCount = state.messages.size
    val streamingLength = state.messages.lastOrNull { it.streaming }?.content?.length ?: 0

    LaunchedEffect(messageCount) {
        if (messageCount > 0) {
            listState.animateScrollToItem(messageCount - 1)
        }
    }
    LaunchedEffect(streamingLength) {
        if (streamingLength == 0) return@LaunchedEffect
        val nearBottom = listState.layoutInfo.visibleItemsInfo
            .any { it.index >= messageCount - 2 }
        if (nearBottom && messageCount > 0) {
            listState.scrollToItem(messageCount - 1)
        }
    }

    fun copyToClipboard(value: String) {
        if (value.isBlank()) return
        clipboardManager.setText(AnnotatedString(value))
        viewModel.showMessage("Kopyalandı.")
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(
                                        if (state.isOnline) {
                                            Color(0xFF2E7D32)
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        },
                                        CircleShape
                                    )
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = stringResource(R.string.model_badge, BuildConfig.MODEL),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.settings)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            // ---------------- status banners ----------------
            AnimatedVisibility(
                visible = !state.isOnline,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                OfflineBanner()
            }
            AnimatedVisibility(
                visible = !state.apiKeyConfigured,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                NoKeyBanner(onOpenSettings = { showSettings = true })
            }

            // ---------------- message list / empty state ----------------
            if (messageCount == 0) {
                EmptyState(onSuggestion = { suggestion -> text = suggestion })
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        end = 8.dp,
                        top = 8.dp,
                        bottom = 8.dp
                    )
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            onCopy = { copyToClipboard(it) },
                            onDelete = { viewModel.requestDelete(it) }
                        )
                    }
                }
            }

            // ---------------- error banner + composer ----------------
            AnimatedVisibility(
                visible = state.error != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                state.error?.let { errorMessage ->
                    ErrorBanner(
                        message = errorMessage,
                        onRetry = { viewModel.retry() },
                        onDismiss = { viewModel.dismissError() }
                    )
                }
            }
            Composer(
                text = text,
                onTextChange = { text = it },
                canSend = state.isOnline && state.apiKeyConfigured && text.isNotBlank(),
                isStreaming = state.isStreaming,
                onSend = {
                    if (text.isNotBlank()) {
                        viewModel.send(text)
                        text = ""
                    }
                },
                onStop = { viewModel.stop() }
            )
            Spacer(Modifier.height(2.dp))
        }
    }

    // ---------------- dialogs ----------------
    state.messageToDelete?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text(stringResource(R.string.delete_message_title)) },
            text = { Text(stringResource(R.string.delete_message_text)) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDelete() }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (state.clearChatRequested) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelClearChat() },
            title = { Text(stringResource(R.string.clear_chat_title)) },
            text = { Text(stringResource(R.string.clear_chat_text)) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmClearChat() }) {
                    Text(stringResource(R.string.clear_chat_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelClearChat() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showCrashDialog) {
        AlertDialog(
            onDismissRequest = { showCrashDialog = false },
            title = { Text(stringResource(R.string.crash_dialog_title)) },
            text = { Text(stringResource(R.string.crash_dialog_text)) },
            confirmButton = {
                TextButton(onClick = { showCrashDialog = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    // ---------------- settings sheet ----------------
    if (showSettings) {
        SettingsSheet(
            onDismiss = { showSettings = false },
            hasEmbeddedKey = remember { viewModel.hasEmbeddedKey() },
            appearanceMode = state.themeMode,
            onSaveKey = { viewModel.saveApiKey(it) },
            onAppearanceMode = { viewModel.setAppearanceMode(it) },
            onClearChat = { viewModel.requestClearChat() }
        )
    }
}
