@file:OptIn(ExperimentalMaterial3Api::class)

package com.raiwesy.ai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.raiwesy.ai.BuildConfig
import com.raiwesy.ai.R
import com.raiwesy.ai.core.util.AppearanceMode

/**
 * Settings bottom sheet:
 *  - NVIDIA API key management (encrypted on device),
 *  - appearance (System / Light / Dark),
 *  - chat history clearing,
 *  - about / version info.
 */
@Composable
fun SettingsSheet(
    onDismiss: () -> Unit,
    hasEmbeddedKey: Boolean,
    appearanceMode: AppearanceMode,
    onSaveKey: (String) -> Unit,
    onAppearanceMode: (AppearanceMode) -> Unit,
    onClearChat: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var keyInput by rememberSaveable { mutableStateOf("") }
    var keyVisible by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            // ---------------- API key ----------------
            SectionTitle(stringResource(R.string.section_api_key))
            Text(
                text = if (hasEmbeddedKey) {
                    stringResource(R.string.api_key_embedded_info)
                } else {
                    stringResource(R.string.api_key_info)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.api_key_label)) },
                placeholder = { Text("API anahtarınız…") },
                singleLine = true,
                visualTransformation = if (keyVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(
                            imageVector = if (keyVisible) {
                                Icons.Outlined.VisibilityOff
                            } else {
                                Icons.Outlined.Visibility
                            },
                            contentDescription = stringResource(R.string.toggle_key_visibility)
                        )
                    }
                },
                supportingText = { Text(stringResource(R.string.api_key_supporting)) }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { keyInput = ""; onSaveKey("") }) {
                    Text(stringResource(R.string.api_key_clear))
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onSaveKey(keyInput) }) {
                    Text(stringResource(R.string.save))
                }
            }

            Spacer(Modifier.height(24.dp))

            // ---------------- Appearance ----------------
            SectionTitle(stringResource(R.string.section_appearance))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppearanceChip(
                    mode = AppearanceMode.SYSTEM,
                    icon = Icons.Outlined.PhoneAndroid,
                    label = stringResource(R.string.appearance_system),
                    selectedMode = appearanceMode,
                    onSelect = onAppearanceMode
                )
                Spacer(Modifier.width(10.dp))
                AppearanceChip(
                    mode = AppearanceMode.LIGHT,
                    icon = Icons.Outlined.LightMode,
                    label = stringResource(R.string.appearance_light),
                    selectedMode = appearanceMode,
                    onSelect = onAppearanceMode
                )
                Spacer(Modifier.width(10.dp))
                AppearanceChip(
                    mode = AppearanceMode.DARK,
                    icon = Icons.Outlined.DarkMode,
                    label = stringResource(R.string.appearance_dark),
                    selectedMode = appearanceMode,
                    onSelect = onAppearanceMode
                )
            }

            Spacer(Modifier.height(24.dp))

            // ---------------- Chat ----------------
            SectionTitle(stringResource(R.string.section_chat))
            TextButton(
                onClick = onClearChat,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteForever,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.clear_chat))
            }

            Spacer(Modifier.height(20.dp))

            // ---------------- About ----------------
            SectionTitle(stringResource(R.string.section_about))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(
                        R.string.about_text,
                        BuildConfig.VERSION_NAME
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AppearanceChip(
    mode: AppearanceMode,
    icon: ImageVector,
    label: String,
    selectedMode: AppearanceMode,
    onSelect: (AppearanceMode) -> Unit
) {
    FilterChip(
        selected = selectedMode == mode,
        onClick = { onSelect(mode) },
        label = { Text(label) },
        leadingIcon = {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}
