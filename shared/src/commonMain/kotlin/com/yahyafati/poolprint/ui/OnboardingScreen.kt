package com.yahyafati.poolprint.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yahyafati.poolprint.core.validation.ValidationError
import com.yahyafati.poolprint.service.FailureKind
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import poolprint.shared.generated.resources.*

@Composable
fun OnboardingScreen(model: OnboardingModel, onFinished: () -> Unit) {
    val state by model.state.collectAsState()
    when (state.step) {
        OnboardingStep.Welcome -> WelcomeStep(model)
        OnboardingStep.Username -> UsernameStep(model, state)
        OnboardingStep.Key -> KeyStep(model, state)
        OnboardingStep.PasswordSetup -> PasswordSetupStep(model, state)
        OnboardingStep.Connect, OnboardingStep.Done -> ConnectStep(model, state, onFinished)
    }
}

@Composable
private fun WelcomeStep(model: OnboardingModel) {
    val c = PoolTheme.colors
    val t = PoolTheme.type
    PhoneScreen(
        topBar = { StepHeader(1, onBack = null) },
        bottomBar = { BlockButton(stringResource(Res.string.onboarding_start), model::start, trailing = "→") },
    ) {
        Column(Modifier.gutter().padding(top = 48.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            AppMark()
            Text(stringResource(Res.string.onboarding_welcome_title), style = t.display)
            Text(stringResource(Res.string.onboarding_welcome_body), style = t.lead, color = c.onVariant)
        }
        Column(Modifier.gutter().padding(top = 32.dp)) {
            Rule()
            PointRow(
                1,
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(stringResource(Res.string.onboarding_welcome_point1_lead)) }
                    append(" ")
                    append(stringResource(Res.string.onboarding_welcome_point1))
                },
            )
            Rule(strong = false)
            PointRow(2, buildAnnotatedString { append(stringResource(Res.string.onboarding_welcome_point2)) })
            Rule(strong = false)
            PointRow(3, buildAnnotatedString { append(stringResource(Res.string.onboarding_welcome_point3)) })
            Rule(strong = false)
        }
    }
}

@Composable
private fun PointRow(number: Int, text: androidx.compose.ui.text.AnnotatedString) {
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(number.toString(), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.width(20.dp))
        Text(text, style = PoolTheme.type.body)
    }
}

@Composable
private fun StepTitle(title: String, body: String?, top: Int = 48) {
    Column(Modifier.gutter().padding(top = top.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = PoolTheme.type.headline)
        body?.let { Text(it, style = PoolTheme.type.body, color = PoolTheme.colors.onVariant) }
    }
}

@Composable
private fun UsernameStep(model: OnboardingModel, state: OnboardingState) {
    PhoneScreen(
        topBar = { StepHeader(2, onBack = model::back) },
        bottomBar = { BlockButton(stringResource(Res.string.onboarding_continue), model::submitUsername, trailing = "→") },
    ) {
        StepTitle(stringResource(Res.string.onboarding_username_title), stringResource(Res.string.onboarding_username_body))
        PoolTextField(
            value = state.usernameInput,
            onValueChange = model::onUsernameChange,
            label = stringResource(Res.string.onboarding_username_label),
            helper = stringResource(Res.string.onboarding_username_helper),
            error = state.usernameError?.let { usernameErrorText(it) },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { model.submitUsername() }),
            modifier = Modifier.gutter().padding(top = 32.dp),
        )
    }
}

@Composable
private fun usernameErrorText(error: ValidationError): String = when (error) {
    ValidationError.UsernameEmpty -> stringResource(Res.string.validation_username_empty)
    else -> stringResource(Res.string.validation_username_invalid)
}

/** The shell lines from docs/ux.md with the public key filled in. */
internal fun keyInstallCommand(publicKey: String): String =
    "mkdir -p ~/.ssh && chmod 700 ~/.ssh\n" +
        "echo '$publicKey' >> ~/.ssh/authorized_keys\n" +
        "chmod 600 ~/.ssh/authorized_keys"

@Composable
private fun KeyStep(model: OnboardingModel, state: OnboardingState) {
    val c = PoolTheme.colors
    val t = PoolTheme.type
    val ready = state.publicKey != null
    PhoneScreen(
        topBar = { StepHeader(3, onBack = model::back) },
        bottomBar = {
            BlockButton(
                stringResource(Res.string.onboarding_key_added),
                onClick = { model.continueToConnect(); model.testConnection() },
                trailing = "→",
                enabled = ready,
            )
            TextAction(stringResource(Res.string.onboarding_key_use_password), model::usePassword, color = c.onVariant, enabled = ready)
        },
    ) {
        StepTitle(stringResource(Res.string.onboarding_key_title), stringResource(Res.string.onboarding_key_body), top = 28)
        state.publicKey?.let { KeyInstructions(it, Modifier.gutter().padding(top = 20.dp)) }
    }
}

/** The three numbered steps of adding the key, the snippet with Copy and Send to…, and the bare public key on demand. */
@Composable
private fun KeyInstructions(publicKey: String, modifier: Modifier = Modifier) {
    val c = PoolTheme.colors
    val t = PoolTheme.type
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val sharer = rememberTextSharer()
    val command = keyInstallCommand(publicKey)
    var showKey by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Rule()
        Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StepNumber(1, filled = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(Res.string.onboarding_key_copy_title), style = t.rowTitle, modifier = Modifier.padding(top = 3.dp))
                MonoBox(command, style = t.monoSmall.copy(fontSize = 11.sp, lineHeight = 18.sp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BlockButton(
                        stringResource(Res.string.onboarding_key_copy_command),
                        onClick = { scope.launch { clipboard.setText(command) } },
                        modifier = Modifier.weight(1f),
                        height = 44.dp,
                        icon = PoolIcons.Copy,
                    )
                    sharer?.let { share ->
                        BlockButton(
                            stringResource(Res.string.action_send_to),
                            onClick = { share(command) },
                            modifier = Modifier.weight(1f),
                            kind = ButtonKind.Outline,
                            height = 44.dp,
                            icon = PoolIcons.Share,
                        )
                    }
                }
            }
        }
        Rule(strong = false)
        Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StepNumber(2, filled = false)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(stringResource(Res.string.onboarding_key_paste_lead)) }
                    append(" ")
                    append(stringResource(Res.string.onboarding_key_paste))
                },
                style = t.bodySmall,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Rule(strong = false)
        Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StepNumber(3, filled = false)
            Text(stringResource(Res.string.onboarding_key_test), style = t.bodySmall, modifier = Modifier.padding(top = 3.dp))
        }
        Rule(strong = false)
        ListRow(minHeight = 48.dp, onClick = { showKey = !showKey }, trailing = { Text(if (showKey) "▴" else "▾", style = t.action, color = c.primary) }) {
            Text(stringResource(Res.string.onboarding_key_show_public), style = t.action, color = c.primary, modifier = Modifier.weight(1f))
        }
        if (showKey) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MonoBox(publicKey)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BlockButton(
                        stringResource(Res.string.action_copy),
                        onClick = { scope.launch { clipboard.setText(publicKey) } },
                        modifier = Modifier.weight(1f),
                        kind = ButtonKind.Outline,
                        height = 44.dp,
                    )
                    sharer?.let { share ->
                        BlockButton(
                            stringResource(Res.string.action_share),
                            onClick = { share(publicKey) },
                            modifier = Modifier.weight(1f),
                            kind = ButtonKind.Outline,
                            height = 44.dp,
                        )
                    }
                }
                Text(stringResource(Res.string.onboarding_key_other_device), style = t.caption, color = c.onVariant)
            }
        }
    }
}

/**
 * The one-time password key install (D-20). The secure field can't be copied from; its text is handed to the model
 * as a CharArray and cleared from the field straight away.
 */
@Composable
private fun PasswordSetupStep(model: OnboardingModel, state: OnboardingState) {
    val install = state.install
    if (install is ConnectState.AskTrust) {
        TrustServerScreen(install.key, onTrust = model::trustHostKey, onCancel = model::declineHostKey, header = { StepHeader(3, onBack = model::back) })
        return
    }
    if (install is ConnectState.Failed && install.failure.kind == FailureKind.HostKeyChanged) {
        HostKeyChangedScreen(install.failure, onClose = model::declineHostKey)
        return
    }
    val c = PoolTheme.colors
    val t = PoolTheme.type
    val password = rememberTextFieldState()
    val busy = install == ConnectState.Running
    fun submit() {
        val text = password.text
        if (busy || text.isEmpty()) return
        val chars = CharArray(text.length) { text[it] }
        password.clearText()
        model.installWithPassword(chars)
    }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    PhoneScreen(
        topBar = { StepHeader(3, onBack = model::back) },
        bottomBar = { BlockButton(stringResource(Res.string.onboarding_password_submit), { submit() }, trailing = "→", enabled = !busy && password.text.isNotEmpty()) },
    ) {
        StepTitle(stringResource(Res.string.onboarding_password_title), stringResource(Res.string.onboarding_password_body), top = 28)
        Column(Modifier.gutter().padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(Res.string.onboarding_password_label), style = t.label, color = if (focused) c.primary else c.onVariant)
            BasicSecureTextField(
                state = password,
                enabled = !busy,
                textStyle = t.mono.copy(fontSize = 18.sp, color = c.on),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                onKeyboardAction = { submit() },
                interactionSource = interaction,
                cursorBrush = SolidColor(c.primary),
                decorator = TextFieldDecorator { inner -> FieldBox(focused = focused, isError = install is ConnectState.Failed) { inner() } },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(Res.string.onboarding_connect_vpn_note), style = t.caption, color = c.onVariant)
        }
        when (install) {
            ConnectState.Running -> Column(Modifier.gutter().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BarProgress(null)
                Text(stringResource(Res.string.onboarding_password_running), style = t.bodySmall, color = c.onVariant)
            }
            is ConnectState.Failed -> FailureCard(install.failure, onRetry = null, modifier = Modifier.gutter().padding(top = 24.dp))
            else -> Unit
        }
    }
}

@Composable
private fun ConnectStep(model: OnboardingModel, state: OnboardingState, onFinished: () -> Unit) {
    val c = PoolTheme.colors
    val t = PoolTheme.type
    val header: @Composable () -> Unit = { StepHeader(4, onBack = model::back) }
    when (val connect = state.connect) {
        is ConnectState.AskTrust ->
            TrustServerScreen(connect.key, onTrust = model::trustHostKey, onCancel = model::declineHostKey, header = header)

        ConnectState.Success -> PhoneScreen(
            bottomBar = { BlockButton(stringResource(Res.string.onboarding_connect_finish), { model.finish(); onFinished() }, trailing = "→") },
        ) {
            Column(Modifier.gutter().padding(top = 96.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                SuccessMark()
                Text(stringResource(Res.string.onboarding_connect_success), style = t.display)
                Text(
                    buildAnnotatedString {
                        append(stringResource(Res.string.onboarding_connect_success_as))
                        append(" ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = c.on)) { append(state.usernameInput) }
                        append(".")
                    },
                    style = t.lead,
                    color = c.onVariant,
                )
            }
            Column(Modifier.gutter().padding(top = 32.dp)) {
                Rule()
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(stringResource(Res.string.onboarding_connect_next_lead)) }
                        append(" ")
                        append(stringResource(Res.string.onboarding_connect_next))
                    },
                    style = t.bodySmall,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }

        is ConnectState.Failed -> if (connect.failure.kind == FailureKind.HostKeyChanged) {
            HostKeyChangedScreen(connect.failure, onClose = model::declineHostKey)
        } else {
            PhoneScreen(
                topBar = header,
                bottomBar = { BlockButton(stringResource(Res.string.onboarding_connect_test_again), model::testConnection, trailing = "↻") },
            ) {
                StepTitle(stringResource(Res.string.onboarding_connect_failed_title), null)
                FailureCard(
                    connect.failure,
                    onRetry = model::testConnection,
                    onShowKey = model::back,
                    retryLabel = stringResource(Res.string.onboarding_connect_test_again),
                    showKeyLabel = stringResource(Res.string.onboarding_connect_show_snippet),
                    modifier = Modifier.gutter().padding(top = 24.dp),
                )
            }
        }

        ConnectState.Running, ConnectState.Idle -> {
            val running = connect == ConnectState.Running
            PhoneScreen(
                topBar = header,
                bottomBar = {
                    if (running) {
                        BlockButton(stringResource(Res.string.action_cancel), model::cancelTest, kind = ButtonKind.Outline)
                    } else {
                        BlockButton(stringResource(Res.string.onboarding_connect_test), model::testConnection, trailing = "→")
                    }
                },
            ) {
                StepTitle(
                    stringResource(if (running) Res.string.onboarding_connect_running else Res.string.onboarding_connect_title),
                    stringResource(Res.string.onboarding_connect_vpn_note),
                )
                if (running) BarProgress(null, Modifier.gutter().padding(top = 24.dp))
            }
        }
    }
}
