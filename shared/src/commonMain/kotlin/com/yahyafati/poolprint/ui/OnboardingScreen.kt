package com.yahyafati.poolprint.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.yahyafati.poolprint.core.validation.ValidationError
import org.jetbrains.compose.resources.stringResource
import poolprint.shared.generated.resources.*

@Composable
fun OnboardingScreen(model: OnboardingModel, onFinished: () -> Unit) {
    val state by model.state.collectAsState()
    ScreenColumn {
        when (state.step) {
            OnboardingStep.Welcome -> {
                Text(stringResource(Res.string.onboarding_welcome_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(Res.string.onboarding_welcome_body))
                Button(onClick = model::start) { Text(stringResource(Res.string.onboarding_start)) }
            }

            OnboardingStep.Username -> {
                Text(stringResource(Res.string.onboarding_username_title), style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(
                    value = state.usernameInput,
                    onValueChange = model::onUsernameChange,
                    label = { Text(stringResource(Res.string.onboarding_username_label)) },
                    singleLine = true,
                    isError = state.usernameError != null,
                    supportingText = state.usernameError?.let { { Text(usernameErrorText(it)) } },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                NavRow(onBack = model::back) {
                    Button(onClick = model::submitUsername) { Text(stringResource(Res.string.action_next)) }
                }
            }

            OnboardingStep.Key -> {
                Text(stringResource(Res.string.onboarding_key_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(Res.string.onboarding_key_body))
                state.publicKey?.let { KeyInstructions(it) }
                OutlinedButton(onClick = model::usePassword, enabled = state.publicKey != null) {
                    Text(stringResource(Res.string.onboarding_key_use_password))
                }
                NavRow(onBack = model::back) {
                    Button(onClick = model::continueToConnect, enabled = state.publicKey != null) {
                        Text(stringResource(Res.string.onboarding_key_added))
                    }
                }
            }

            OnboardingStep.PasswordSetup -> PasswordSetupStep(model, state)

            OnboardingStep.Connect, OnboardingStep.Done -> ConnectStep(model, state, onFinished)
        }
    }
}

@Composable
private fun usernameErrorText(error: ValidationError): String = when (error) {
    ValidationError.UsernameEmpty -> stringResource(Res.string.validation_username_empty)
    else -> stringResource(Res.string.validation_username_invalid)
}

/** The three shell lines from docs/ux.md with the public key filled in, plus Copy and Share. */
@Composable
fun KeyInstructions(publicKey: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    fun copy(text: String) { scope.launch { clipboard.setText(text) } }
    val sharer = rememberTextSharer()
    val command = "mkdir -p ~/.ssh && chmod 700 ~/.ssh\n" +
        "echo '$publicKey' >> ~/.ssh/authorized_keys\n" +
        "chmod 600 ~/.ssh/authorized_keys"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.onboarding_key_public_label), style = MaterialTheme.typography.labelLarge)
        SelectionContainer { Text(publicKey, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { copy(publicKey) }) { Text(stringResource(Res.string.action_copy)) }
            sharer?.let { share -> OutlinedButton(onClick = { share(publicKey) }) { Text(stringResource(Res.string.action_share)) } }
        }
        SelectionContainer { Text(command, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
        OutlinedButton(onClick = { copy(command) }) { Text(stringResource(Res.string.onboarding_key_copy_command)) }
    }
}

/**
 * The one-time password key install (D-20). The secure field can't be copied from; its text is handed to the model
 * as a CharArray and cleared from the field straight away.
 */
@Composable
private fun PasswordSetupStep(model: OnboardingModel, state: OnboardingState) {
    val password = rememberTextFieldState()
    val busy = state.install == ConnectState.Running || state.install is ConnectState.AskTrust
    fun submit() {
        val text = password.text
        if (busy || text.isEmpty()) return
        val chars = CharArray(text.length) { text[it] }
        password.clearText()
        model.installWithPassword(chars)
    }
    Text(stringResource(Res.string.onboarding_password_title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(Res.string.onboarding_password_body))
    Text(stringResource(Res.string.onboarding_connect_vpn_note), style = MaterialTheme.typography.bodyMedium)
    OutlinedSecureTextField(
        state = password,
        label = { Text(stringResource(Res.string.onboarding_password_label)) },
        enabled = !busy,
        onKeyboardAction = { submit() },
        modifier = Modifier.fillMaxWidth(),
    )
    when (val install = state.install) {
        ConnectState.Running -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.padding(end = 4.dp))
            Text(stringResource(Res.string.onboarding_password_running))
        }
        is ConnectState.AskTrust -> TrustHostKeyCard(install.key, onTrust = model::trustHostKey, onDecline = model::declineHostKey)
        is ConnectState.Failed -> FailureCard(install.failure, onRetry = null)
        else -> Unit
    }
    NavRow(onBack = model::back) {
        Button(onClick = { submit() }, enabled = !busy && password.text.isNotEmpty()) {
            Text(stringResource(Res.string.onboarding_password_submit))
        }
    }
}

@Composable
private fun ConnectStep(model: OnboardingModel, state: OnboardingState, onFinished: () -> Unit) {
    Text(stringResource(Res.string.onboarding_connect_title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(Res.string.onboarding_connect_vpn_note), style = MaterialTheme.typography.bodyMedium)
    when (val connect = state.connect) {
        ConnectState.Idle -> Unit
        ConnectState.Running -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.padding(end = 4.dp))
            Text(stringResource(Res.string.onboarding_connect_running))
        }
        ConnectState.Success -> Text(stringResource(Res.string.onboarding_connect_success), color = MaterialTheme.colorScheme.primary)
        is ConnectState.AskTrust -> TrustHostKeyCard(connect.key, onTrust = model::trustHostKey, onDecline = model::declineHostKey)
        is ConnectState.Failed -> FailureCard(connect.failure, onRetry = model::testConnection, onShowKey = model::back)
    }
    NavRow(onBack = model::back) {
        if (state.connect == ConnectState.Success) {
            Button(onClick = { model.finish(); onFinished() }) { Text(stringResource(Res.string.onboarding_connect_finish)) }
        } else {
            Button(onClick = model::testConnection, enabled = state.connect != ConnectState.Running && state.connect !is ConnectState.AskTrust) {
                Text(stringResource(Res.string.onboarding_connect_test))
            }
        }
    }
}

@Composable
private fun NavRow(onBack: () -> Unit, primary: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = onBack) { Text(stringResource(Res.string.action_back)) }
        primary()
    }
}
