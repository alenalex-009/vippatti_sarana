package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmergencyRedContainer
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.OnEmergencyRedContainer
import com.example.ui.theme.OnNeonEmerald
import com.example.ui.theme.ObsidianContainerHigh
import com.example.ui.theme.ObsidianContainerLow
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.TacticalOutlineVariant
import com.example.viewmodel.AuthUiState

/**
 * ============================================================================
 * VIPPATTI SARANA — AUTH SCREENS (login + signup)
 * ============================================================================
 *
 * One design language with the rest of the app: obsidian surfaces, NEON
 * EMERALD for the safe/action color, EmergencyRed reserved for danger.
 * (The previous version imported an indigo "fintech" palette that matched
 * nothing else in the app — that is part of why it read as messy.)
 *
 * Structure of BOTH screens (identical rhythm, so switching feels free):
 *
 *   [ brand crest + wordmark ]          AuthBrand
 *   [ title / subtitle ]                AuthHeading
 *   [ card: fields -> inline errors
 *         -> server error panel
 *         -> primary button ]           AuthCard
 *   [ switch-mode link ]                AuthSwitchLink
 *   [ privacy / network note ]          AuthFootnote
 *
 * States it renders, all driven by [AuthUiState] (no screen-local truth):
 *  - submitting  : button shows a spinner, fields lock
 *  - rate limited: button counts down, fields lock
 *  - field error : inline, under the offending field
 *  - server error: panel above the button, verbatim backend advice
 *  - PENDING EMAIL CONFIRMATION (signup, Supabase "confirm email" ON):
 *    a dedicated success-shaped panel that tells the user to open their
 *    inbox — the app does NOT open, because no session exists yet.
 *
 * Accessibility: labels are real (not placeholder-only), error text is read
 * with the field state, touch targets >= 48 dp, IME actions chain fields.
 */

// --------------------------------------------------------------------- LOGIN

@Composable
fun LoginScreen(
  uiState: AuthUiState,
  onLogin: (email: String, password: String) -> Unit,
  onGoToSignup: () -> Unit,
  modifier: Modifier = Modifier
) {
  var email by rememberSaveable { mutableStateOf("") }
  var password by rememberSaveable { mutableStateOf("") }
  var showPassword by rememberSaveable { mutableStateOf(false) }

  val focusManager = LocalFocusManager.current
  val emailFocus = remember { FocusRequester() }
  LaunchedEffect(Unit) { runCatching { emailFocus.requestFocus() } }

  val busy = uiState.isSubmitting || uiState.isRateLimited

  AuthScaffold {
    AuthBrand(signup = false)
    AuthHeading(
      title = stringResource(R.string.login_welcome_title),
      subtitle = stringResource(R.string.login_welcome_subtitle)
    )
    AuthCard {
      AuthField(
        label = stringResource(R.string.login_email),
        value = email,
        placeholder = stringResource(R.string.login_email_hint),
        leadingIcon = Icons.Default.Email,
        onValueChange = { email = it },
        isError = uiState.loginFieldError != null,
        enabled = !busy,
        testTag = "login_email_field",
        keyboardType = KeyboardType.Email,
        imeAction = ImeAction.Next,
        modifier = Modifier.focusRequester(emailFocus),
        onNext = { focusManager.moveFocus(FocusDirection.Down) }
      )
      Spacer(Modifier.height(14.dp))
      AuthField(
        label = stringResource(R.string.login_password),
        value = password,
        placeholder = stringResource(R.string.login_password_field_hint),
        leadingIcon = Icons.Default.Lock,
        onValueChange = { password = it },
        isError = uiState.loginFieldError != null,
        enabled = !busy,
        testTag = "login_password_field",
        keyboardType = KeyboardType.Password,
        imeAction = ImeAction.Done,
        visualTransformation =
          if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        trailing = {
          AuthToggleVisibility(
            shown = showPassword,
            onToggle = { showPassword = !showPassword }
          )
        },
        onNext = {
          focusManager.clearFocus()
          if (!busy) onLogin(email.trim(), password)
        }
      )
      uiState.loginFieldError?.let { res ->
        Spacer(Modifier.height(8.dp))
        AuthInlineError(stringResource(res))
      }
      uiState.loginError?.let { message ->
        Spacer(Modifier.height(12.dp))
        AuthErrorPanel(uiState.withCooldown(message))
      }
      Spacer(Modifier.height(20.dp))
      AuthPrimaryButton(
        text = when {
          uiState.isRateLimited && uiState.cooldownSeconds > 0 ->
            "${stringResource(R.string.login_sign_in_action)} (${uiState.cooldownSeconds}s)"
          uiState.isSubmitting -> stringResource(R.string.login_sign_in_action)
          else -> stringResource(R.string.login_sign_in_action)
        },
        loading = uiState.isSubmitting,
        enabled = email.isNotBlank() && password.isNotBlank() && !busy,
        testTag = "login_submit_button",
        onClick = {
          focusManager.clearFocus()
          onLogin(email.trim(), password)
        }
      )
    }
    AuthSwitchLink(
      text = stringResource(R.string.login_new_to_network),
      testTag = "login_goto_signup_button",
      enabled = !busy,
      onClick = onGoToSignup
    )
    AuthFootnote(stringResource(R.string.login_backend_note))
  }
}

// -------------------------------------------------------------------- SIGNUP

@Composable
fun SignupScreen(
  uiState: AuthUiState,
  onSignup: (fullName: String, email: String, password: String, confirmPassword: String) -> Unit,
  onBackToLogin: () -> Unit,
  modifier: Modifier = Modifier
) {
  var fullName by rememberSaveable { mutableStateOf("") }
  var email by rememberSaveable { mutableStateOf("") }
  var password by rememberSaveable { mutableStateOf("") }
  var confirmPassword by rememberSaveable { mutableStateOf("") }
  var showPassword by rememberSaveable { mutableStateOf(false) }

  val focusManager = LocalFocusManager.current
  val nameFocus = remember { FocusRequester() }
  LaunchedEffect(Unit) { runCatching { nameFocus.requestFocus() } }

  val busy = uiState.isSubmitting || uiState.isRateLimited

  // EMAIL CONFIRMATION PENDING (Supabase projects with "Confirm email" ON):
  // /signup returned a user WITHOUT a session. The honest state is "your
  // inbox has a link" — there is nothing to sign into yet and no fake
  // session to walk into.
  if (uiState.pendingConfirmationEmail != null) {
    AuthScaffold(modifier = modifier) {
      AuthBrand(signup = true)
      AuthPendingConfirmationPanel(
        email = uiState.pendingConfirmationEmail!!,
        acknowledged = onBackToLogin
      )
      AuthFootnote(stringResource(R.string.register_privacy_note))
    }
    return
  }

  AuthScaffold(modifier = modifier) {
    AuthBrand(signup = true)
    AuthHeading(
      title = stringResource(R.string.login_create_welcome_title),
      subtitle = stringResource(R.string.register_welcome_subtitle)
    )
    AuthCard {
      AuthField(
        label = stringResource(R.string.register_full_name),
        value = fullName,
        placeholder = stringResource(R.string.register_name_hint),
        leadingIcon = Icons.Default.Person,
        onValueChange = { fullName = it },
        isError = uiState.signupFieldError != null,
        enabled = !busy,
        testTag = "signup_name_field",
        keyboardType = KeyboardType.Text,
        imeAction = ImeAction.Next,
        modifier = Modifier.focusRequester(nameFocus),
        onNext = { focusManager.moveFocus(FocusDirection.Down) }
      )
      Spacer(Modifier.height(14.dp))
      AuthField(
        label = stringResource(R.string.login_email),
        value = email,
        placeholder = stringResource(R.string.login_email_hint),
        leadingIcon = Icons.Default.Email,
        onValueChange = { email = it },
        isError = uiState.signupFieldError != null,
        enabled = !busy,
        testTag = "signup_email_field",
        keyboardType = KeyboardType.Email,
        imeAction = ImeAction.Next,
        onNext = { focusManager.moveFocus(FocusDirection.Down) }
      )
      Spacer(Modifier.height(14.dp))
      AuthField(
        label = stringResource(R.string.login_password),
        value = password,
        placeholder = stringResource(R.string.login_password_hint),
        leadingIcon = Icons.Default.Lock,
        onValueChange = { password = it },
        isError = uiState.signupFieldError != null,
        enabled = !busy,
        testTag = "login_password_field",
        keyboardType = KeyboardType.Password,
        imeAction = ImeAction.Next,
        visualTransformation =
          if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        trailing = {
          AuthToggleVisibility(shown = showPassword, onToggle = { showPassword = !showPassword })
        },
        onNext = { focusManager.moveFocus(FocusDirection.Down) }
      )
      Spacer(Modifier.height(14.dp))
      AuthField(
        label = stringResource(R.string.login_confirm_password),
        value = confirmPassword,
        placeholder = stringResource(R.string.register_confirm_hint),
        leadingIcon = Icons.Default.Lock,
        onValueChange = { confirmPassword = it },
        isError = uiState.signupFieldError != null,
        enabled = !busy,
        testTag = "signup_confirm_password_field",
        keyboardType = KeyboardType.Password,
        imeAction = ImeAction.Done,
        visualTransformation =
          if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        onNext = {
          focusManager.clearFocus()
          if (!busy) onSignup(fullName.trim(), email.trim(), password, confirmPassword)
        }
      )
      uiState.signupFieldError?.let { res ->
        Spacer(Modifier.height(8.dp))
        AuthInlineError(stringResource(res))
      }
      uiState.signupError?.let { message ->
        Spacer(Modifier.height(12.dp))
        AuthErrorPanel(uiState.withCooldown(message))
      }
      Spacer(Modifier.height(20.dp))
      AuthPrimaryButton(
        text = when {
          uiState.isRateLimited && uiState.cooldownSeconds > 0 ->
            "${stringResource(R.string.register_create_account_action)} (${uiState.cooldownSeconds}s)"
          else -> stringResource(R.string.register_create_account_action)
        },
        loading = uiState.isSubmitting,
        enabled = fullName.isNotBlank() && email.isNotBlank() &&
          password.isNotBlank() && confirmPassword.isNotBlank() && !busy,
        testTag = "signup_submit_button",
        onClick = {
          focusManager.clearFocus()
          onSignup(fullName.trim(), email.trim(), password, confirmPassword)
        }
      )
    }
    AuthSwitchLink(
      text = stringResource(R.string.login_have_account),
      testTag = "signup_back_to_login_button",
      enabled = !busy,
      onClick = onBackToLogin
    )
    AuthFootnote(stringResource(R.string.register_privacy_note))
  }
}

/** Rate-limit messages carry the live countdown the VM maintains. */
private fun AuthUiState.withCooldown(message: String): String =
  if (isRateLimited && cooldownSeconds > 0) "$message ($cooldownSeconds s)" else message

// ---------------------------------------------------------------- SCAFFOLD

/**
 * The shared auth page frame: scrollable, IME-safe, edge-safe; content is a
 * centered single column with a fixed max width so tablets stay readable.
 */
@Composable
private fun AuthScaffold(
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit
) {
  Surface(modifier = modifier.fillMaxSize(), color = ObsidianSurface) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .imePadding()
        .navigationBarsPadding()
        .statusBarsPadding()
        .padding(horizontal = 24.dp, vertical = 20.dp)
        .widthIn(max = 460.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) { content() }
  }
}

// ---------------------------------------------------------------- BRAND

/**
 * The app's crest: a shield in the brand emerald with the wordmark.
 * Replaces the old indigo cartoon avatar — this is an emergency app.
 */
@Composable
private fun AuthBrand(signup: Boolean) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Box(
      modifier = Modifier
        .size(88.dp)
        .clip(CircleShape)
        .background(ObsidianContainerHigh)
        .border(1.5.dp, NeonEmerald.copy(alpha = 0.55f), CircleShape),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = Icons.Default.CheckCircle,
        contentDescription = stringResource(R.string.login_a11y_logo),
        tint = NeonEmerald,
        modifier = Modifier
          .size(44.dp)
          .testTag("auth_brand_logo")
      )
    }
    Spacer(Modifier.height(12.dp))
    Text(
      text = stringResource(R.string.login_title),
      style = TextStyle(fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp, fontSize = 17.sp),
      color = TacticalOnSurface
    )
    Text(
      text = stringResource(R.string.login_subtitle),
      style = MaterialTheme.typography.bodySmall,
      color = TacticalOnSurfaceVariant
    )
    Spacer(Modifier.height(20.dp))
  }
}

// ---------------------------------------------------------------- HEADING

@Composable
private fun AuthHeading(title: String, subtitle: String) {
  Text(
    text = title,
    style = MaterialTheme.typography.headlineMedium,
    fontWeight = FontWeight.Bold,
    color = TacticalOnSurface,
    textAlign = TextAlign.Center
  )
  Spacer(Modifier.height(6.dp))
  Text(
    text = subtitle,
    style = MaterialTheme.typography.bodyMedium,
    color = TacticalOnSurfaceVariant,
    textAlign = TextAlign.Center,
    modifier = Modifier.padding(horizontal = 8.dp)
  )
  Spacer(Modifier.height(20.dp))
}

// ---------------------------------------------------------------- CARD

@Composable
private fun AuthCard(content: @Composable () -> Unit) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(20.dp))
      .background(ObsidianContainerLow)
      .border(1.dp, TacticalOutlineVariant, RoundedCornerShape(20.dp))
      .padding(20.dp)
  ) { content() }
}

// ---------------------------------------------------------------- FIELD

@Composable
private fun AuthField(
  label: String,
  value: String,
  placeholder: String,
  leadingIcon: ImageVector,
  onValueChange: (String) -> Unit,
  isError: Boolean,
  enabled: Boolean,
  testTag: String,
  keyboardType: KeyboardType,
  imeAction: ImeAction,
  modifier: Modifier = Modifier,
  visualTransformation: VisualTransformation = VisualTransformation.None,
  trailing: (@Composable () -> Unit)? = null,
  onNext: () -> Unit
) {
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier
      .fillMaxWidth()
      .heightIn(min = 56.dp)
      .testTag(testTag),
    enabled = enabled,
    label = { Text(label) },
    placeholder = {
      Text(placeholder, color = TacticalOnSurfaceVariant.copy(alpha = 0.55f))
    },
    leadingIcon = {
      Icon(leadingIcon, contentDescription = null, tint = TacticalOnSurfaceVariant)
    },
    trailingIcon = trailing,
    isError = isError,
    singleLine = true,
    shape = RoundedCornerShape(14.dp),
    keyboardOptions = KeyboardOptions(
      keyboardType = keyboardType,
      imeAction = imeAction
    ),
    keyboardActions = KeyboardActions(
      onNext = { onNext() },
      onDone = { onNext() }
    ),
    colors = OutlinedTextFieldDefaults.colors(
      focusedBorderColor = NeonEmerald,
      unfocusedBorderColor = TacticalOutlineVariant,
      focusedLabelColor = NeonEmerald,
      cursorColor = NeonEmerald,
      focusedTrailingIconColor = TacticalOnSurface,
      unfocusedTextColor = TacticalOnSurface,
      focusedTextColor = TacticalOnSurface
    )
  )
}

@Composable
private fun AuthToggleVisibility(shown: Boolean, onToggle: () -> Unit) {
  Box(
    modifier = Modifier
      .size(width = 48.dp, height = 48.dp)
      .clip(CircleShape)
      .clickable(onClick = onToggle),
    contentAlignment = Alignment.Center
  ) {
    Icon(
      imageVector = if (shown) Icons.Default.VisibilityOff else Icons.Default.Visibility,
      contentDescription = stringResource(
        if (shown) R.string.login_hide_password else R.string.login_show_password
      ),
      tint = TacticalOnSurfaceVariant
    )
  }
}

// ---------------------------------------------------------------- ERRORS

/** One-line validation hint directly under the field group. */
@Composable
private fun AuthInlineError(message: String) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(
      Icons.Default.ErrorOutline,
      contentDescription = null,
      tint = EmergencyRed,
      modifier = Modifier.size(16.dp)
    )
    Spacer(Modifier.width(6.dp))
    Text(
      text = message,
      style = MaterialTheme.typography.bodySmall,
      color = EmergencyRed
    )
  }
}

/** Server/network failure panel: calm, specific, actionable. */
@Composable
private fun AuthErrorPanel(message: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .background(EmergencyRedContainer)
      .padding(12.dp),
    verticalAlignment = Alignment.Top
  ) {
    Icon(
      Icons.Default.ErrorOutline,
      contentDescription = stringResource(R.string.login_error_title),
      tint = OnEmergencyRedContainer,
      modifier = Modifier.size(20.dp)
    )
    Spacer(Modifier.width(10.dp))
    Text(
      text = message,
      style = MaterialTheme.typography.bodySmall,
      color = OnEmergencyRedContainer,
      modifier = Modifier.weight(1f)
    )
  }
}

// ---------------------------------------------------------------- PENDING

/**
 * Signup completed but Supabase requires email confirmation: show the inbox
 * instruction as a POSITIVE state (emerald), never as an error, and give a
 * single clear action back to the sign-in form.
 */
@Composable
private fun AuthPendingConfirmationPanel(email: String, acknowledged: () -> Unit) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(20.dp))
      .background(ObsidianContainerLow)
      .border(1.5.dp, NeonEmerald.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
      .padding(22.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Icon(
      Icons.Default.Email,
      contentDescription = null,
      tint = NeonEmerald,
      modifier = Modifier.size(40.dp)
    )
    Spacer(Modifier.height(12.dp))
    Text(
      text = stringResource(R.string.login_pending_title),
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
      color = TacticalOnSurface,
      textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(8.dp))
    Text(
      text = stringResource(R.string.login_pending_body, email),
      style = MaterialTheme.typography.bodyMedium,
      color = TacticalOnSurfaceVariant,
      textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(20.dp))
    AuthPrimaryButton(
      text = stringResource(R.string.login_pending_action),
      loading = false,
      enabled = true,
      testTag = "signup_pending_ack_button",
      onClick = acknowledged
    )
  }
  Spacer(Modifier.height(16.dp))
}

// ---------------------------------------------------------------- BUTTON

@Composable
private fun AuthPrimaryButton(
  text: String,
  loading: Boolean,
  enabled: Boolean,
  testTag: String,
  onClick: () -> Unit
) {
  val bg = if (enabled) NeonEmerald else NeonEmerald.copy(alpha = 0.25f)
  val fg = if (enabled) OnNeonEmerald else OnNeonEmerald.copy(alpha = 0.5f)
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(54.dp)
      .clip(RoundedCornerShape(14.dp))
      .background(bg)
      .testTag(testTag)
      .then(
        if (enabled && !loading) Modifier.clickable(onClick = onClick) else Modifier
      ),
    contentAlignment = Alignment.Center
  ) {
    if (loading) {
      CircularProgressIndicator(
        color = OnNeonEmerald,
        strokeWidth = 2.5.dp,
        modifier = Modifier.size(24.dp)
      )
    } else {
      Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = fg
      )
    }
  }
}

// ---------------------------------------------------------------- SWITCH

/** The mode-switch affordance: one full translated sentence, tappable. */
@Composable
private fun AuthSwitchLink(text: String, testTag: String, enabled: Boolean, onClick: () -> Unit) {
  Spacer(Modifier.height(18.dp))
  Text(
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    color = if (enabled) NeonEmerald else NeonEmerald.copy(alpha = 0.4f),
    fontWeight = FontWeight.SemiBold,
    textAlign = TextAlign.Center,
    modifier = Modifier
      .clip(RoundedCornerShape(12.dp))
      .testTag(testTag)
      .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
      .padding(horizontal = 12.dp, vertical = 10.dp)
  )
}

@Composable
private fun AuthFootnote(text: String) {
  Spacer(Modifier.height(10.dp))
  Text(
    text = text,
    fontSize = 11.sp,
    lineHeight = 15.sp,
    color = TacticalOnSurfaceVariant,
    textAlign = TextAlign.Center,
    modifier = Modifier.padding(horizontal = 16.dp)
  )
}
