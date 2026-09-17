package com.manhwaread.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manhwaread.core.common.AppError

private val FieldPadding = 8.dp
private val ContentPadding = 16.dp
private val ProgressSize = 20.dp

// Точка входа раздела «Настройки» (подключается в NavHost приложения).
@Composable
fun SettingsRoute(
    onBack: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        actions = SettingsActions(
            onProviderSelected = viewModel::onProviderSelected,
            onBaseUrlChange = viewModel::onBaseUrlChange,
            onModelChange = viewModel::onModelChange,
            onApiKeyChange = viewModel::onApiKeyChange,
            onSave = viewModel::save,
            onTestConnection = viewModel::testConnection,
        ),
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.settings_back),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(ContentPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(FieldPadding),
        ) {
            Text(
                text = stringResource(R.string.settings_section_translation),
                style = MaterialTheme.typography.titleMedium,
            )
            ProviderDropdown(
                state = state,
                onProviderSelected = actions.onProviderSelected,
            )
            val provider = state.selectedProvider
            if (provider?.requiresBaseUrl == true) {
                OutlinedTextField(
                    value = state.baseUrl,
                    onValueChange = actions.onBaseUrlChange,
                    label = { Text(stringResource(R.string.settings_base_url_label)) },
                    placeholder = { Text(BASE_URL_PLACEHOLDER) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (provider?.defaultModel != null) {
                OutlinedTextField(
                    value = state.model,
                    onValueChange = actions.onModelChange,
                    label = { Text(stringResource(R.string.settings_model_label)) },
                    placeholder = { Text(provider.defaultModel.orEmpty()) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = state.apiKeyInput,
                onValueChange = actions.onApiKeyChange,
                label = { Text(stringResource(R.string.settings_api_key_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                supportingText = {
                    if (state.hasSavedApiKey) {
                        Text(stringResource(R.string.settings_api_key_saved))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(FieldPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = actions.onSave, enabled = state.canSave) {
                    Text(stringResource(R.string.settings_save))
                }
                OutlinedButton(
                    onClick = actions.onTestConnection,
                    enabled = state.canSave && !state.isBusy,
                ) {
                    Text(stringResource(R.string.settings_test))
                }
                if (state.isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(ProgressSize))
                }
            }
            state.message?.let { message -> MessageText(message) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderDropdown(
    state: SettingsUiState,
    onProviderSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = state.selectedProvider?.displayName
                ?: stringResource(R.string.settings_provider_none),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_provider_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            // Пункт «Не выбран» — сброс провайдера.
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_provider_none)) },
                onClick = {
                    expanded = false
                    onProviderSelected("")
                },
            )
            for (descriptor in state.providers) {
                DropdownMenuItem(
                    text = { Text(descriptor.displayName) },
                    onClick = {
                        expanded = false
                        onProviderSelected(descriptor.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun MessageText(message: SettingsMessage) {
    val text = when (message) {
        SettingsMessage.Saved -> stringResource(R.string.settings_saved)
        is SettingsMessage.TestSuccess ->
            stringResource(R.string.settings_test_success, message.providerDisplayName)
        SettingsMessage.ConfigurationInvalid -> stringResource(R.string.settings_configuration_invalid)
        is SettingsMessage.ProviderError -> providerErrorText(message.error)
    }
    val isError = message is SettingsMessage.ConfigurationInvalid || message is SettingsMessage.ProviderError
    val color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Text(text = text, color = color, style = MaterialTheme.typography.bodyMedium)
}

// Доменная ошибка провайдера → локализованный текст.
@Composable
private fun providerErrorText(error: AppError): String =
    when (error) {
        is AppError.Network -> stringResource(R.string.settings_error_network)
        AppError.ProviderAuth -> stringResource(R.string.settings_error_auth)
        AppError.ProviderQuota -> stringResource(R.string.settings_error_quota)
        is AppError.RateLimited -> stringResource(R.string.settings_error_rate_limited)
        is AppError.ProviderBadResponse -> stringResource(R.string.settings_error_bad_response, error.reason)
        else -> stringResource(R.string.settings_error_unknown)
    }

private const val BASE_URL_PLACEHOLDER = "https://api.openai.com/v1"
