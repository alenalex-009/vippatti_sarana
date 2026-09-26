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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.auth.AuthError
import com.example.data.auth.AuthRepository
import com.example.data.auth.AuthResult
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmergencyRedContainer
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianContainer
import com.example.ui.theme.ObsidianContainerHigh
import com.example.ui.theme.ObsidianContainerLow
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.OnEmergencyRedContainer
import com.example.ui.theme.OnNeonEmerald
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.TacticalOutline
import com.example.ui.theme.TacticalOutlineVariant

/**
 * Login / create-account gate shown before the relief network is entered.
 *
 * VISUAL REDESIGN (this sprint) - layout, hierarchy and contrast only. The
 * authentication contract is untouched: [onLogin] / [onRegister] still receive
 * the same (email, password, staySignedIn) triple, `submit()` still runs the
 * same password-match check and the same auth-error mapping, and the demo
 * account is still seeded by [AuthRepository.seedDemoAccount].
 *
 * Defects fixed here:
 *  - The primary button used `containerColor = EmergencyRed` with no disabled
 *    colour and a hard-coded white label, so an empty form rendered white text
 *    on a washed-out disabled red. It is now brand green (red stays reserved
 *    for errors and SOS) with an explicit, readable disabled state.
 *  - Fields now carry full M3 colours: a visible focused/unfocused border and
 *    a readable floating label, plus error states, instead of the default
 *    washed-out outline.
 *  - The screen is one scrollable card so the form reads as a single group.
 *  - `imePadding()` + `navigationBarsPadding()`: the keyboard previously covered
 *    the submit button and the error message on short screens.
 *  - The error is a proper alert panel with a heading and an icon, rather than a
 *    bare red sentence floating above the button.
 *  - Three `FontWeight.Black` call-sites on one screen reduced to a single
 *    strong CTA, per the "visually heavy text" request.
 */

@Composable
fun LoginScreen(
  onLogin: (email: String, password: String, staySignedIn: Boolean) -> AuthResult,
  onRegister: (email: String, password: String, staySignedIn: Boolean) -> AuthResult,
  modifier: Modifier = Modifier
) {
  var isRegisterMode by rememberSaveable { mutableStateOf(false) }
  var email by rememberSaveable { mutableStateOf("") }
  var password by rememberSaveable { mutableStateOf("") }
  var confirmPassword by rememberSaveable { mutableStateOf("") }
  var staySignedIn by rememberSaveable { mutableStateOf(true) }
  var showPassword by rememberSaveable { mutableStateOf(false) }
  var errorRes by rememberSaveable { mutableStateOf<Int?>(null) }
  val errorText = errorRes?.let { stringResource(it) }

  // UNCHANGED AUTH BEHAVIOUR: same validation, same callbacks, same mapping.
  fun submit() {
    errorRes = if (isRegisterMode) {
      if (password != confirmPassword) {
        R.string.login_passwords_do_not_match
      } else {
        authErrorMessageRes(onRegister(email.trim(), password, staySignedIn))
      }
    } else {
      authErrorMessageRes(onLogin(email.trim(), password, staySignedIn))
    }
  }

  val focusManager = LocalFocusManager.current
  val emailFocus = remember { FocusRequester() }
  // Open the keyboard where the user actually starts work.
  LaunchedEffect(Unit) { runCatching { emailFocus.requestFocus() } }

  val canSubmit = email.isNotBlank() && password.isNotBlank()
  val fieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = NeonEmerald,
    unfocusedBorderColor = TacticalOutline,
    focusedContainerColor = ObsidianContainerLow,
    unfocusedContainerColor = ObsidianContainerLow,
    focusedTextColor = TacticalOnSurface,
    unfocusedTextColor = TacticalOnSurface,
    cursorColor = NeonEmerald,
    focusedLabelColor = NeonEmerald,
    unfocusedLabelColor = TacticalOnSurfaceVariant,
    errorBorderColor = EmergencyRed,
    errorLabelColor = EmergencyRed,
    errorCursorColor = EmergencyRed
  )

  // One scrollable surface. imePadding keeps the submit button and the error
  // panel reachable while the keyboard is up (they were previously covered).
  Surface(modifier = modifier.fillMaxSize(), color = ObsidianSurface) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .imePadding()
        .navigationBarsPadding()
        .statusBarsPadding()
        .padding(horizontal = 20.dp, vertical = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Spacer(Modifier.height(8.dp))

      // ---- Brand lockup -------------------------------------------------
      Box(
        modifier = Modifier
          .size(72.dp)
          .clip(RoundedCornerShape(22.dp))
          .background(NeonEmerald),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.Shield,
          contentDescription = stringResource(R.string.login_a11y_logo),
          tint = Color.White,
          modifier = Modifier.size(36.dp)
        )
      }
      Spacer(Modifier.height(14.dp))
      Text(
        text = stringResource(R.string.login_title),
        style = MaterialTheme.typography.titleMedium,
        color = TacticalOnSurface
      )
      Text(
        text = stringResource(R.string.login_brand_tagline),
        style = MaterialTheme.typography.bodySmall,
        color = TacticalOnSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 2.dp, start = 16.dp, end = 16.dp)
      )

      Spacer(Modifier.height(24.dp))

      // ---- One form card, so the form reads as a single group -----------
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(20.dp))
          .background(ObsidianContainerLow)
          .border(1.dp, TacticalOutlineVariant, RoundedCornerShape(20.dp))
          .padding(20.dp)
      ) {
        Text(
          text = if (isRegisterMode) stringResource(R.string.login_create_welcome_title)
          else stringResource(R.string.login_welcome_title),
          style = MaterialTheme.typography.titleLarge,
          color = TacticalOnSurface
        )
        Text(
          text = if (isRegisterMode) stringResource(R.string.login_create_welcome_subtitle)
          else stringResource(R.string.login_welcome_subtitle),
          style = MaterialTheme.typography.bodySmall,
          color = TacticalOnSurfaceVariant,
          modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
          value = email,
          onValueChange = { email = it; errorRes = null },
          label = { Text(stringResource(R.string.login_email)) },
          singleLine = true,
          isError = errorRes != null,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
          keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
          colors = fieldColors,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .focusRequester(emailFocus)
            .testTag("login_email_field")
        )

        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
          value = password,
          onValueChange = { password = it; errorRes = null },
          label = {
            Text(stringResource(R.string.login_password_min, AuthRepository.MIN_PASSWORD_LENGTH))
          },
          singleLine = true,
          isError = errorRes != null,
          visualTransformation =
            if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
          keyboardActions = KeyboardActions(
            onNext = {
              if (isRegisterMode) focusManager.moveFocus(FocusDirection.Down)
              else focusManager.clearFocus()
            }
          ),
          trailingIcon = {
            IconButton(onClick = { showPassword = !showPassword }) {
              Icon(
                imageVector =
                  if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = stringResource(
                  if (showPassword) R.string.login_hide_password else R.string.login_show_password
                ),
                tint = TacticalOnSurfaceVariant
            )
          }
        },
        colors = fieldColors,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("login_password_field")
      )

        if (isRegisterMode) {
          Spacer(Modifier.height(14.dp))
          OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it; errorRes = null },
            label = { Text(stringResource(R.string.login_confirm_password)) },
            singleLine = true,
            isError = errorRes != null,
            visualTransformation =
              if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            colors = fieldColors,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("login_confirm_field")
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
        ) {
          Switch(
            checked = staySignedIn,
            onCheckedChange = { staySignedIn = it },
            colors = SwitchDefaults.colors(
              checkedThumbColor = Color.White,
              checkedTrackColor = NeonEmerald,
              uncheckedThumbColor = TacticalOnSurfaceVariant,
              uncheckedTrackColor = ObsidianContainer,
              uncheckedBorderColor = TacticalOutline
            ),
            modifier = Modifier.testTag("login_stay_signed_in")
          )
          Spacer(Modifier.size(10.dp))
          Text(
            text = stringResource(R.string.login_stay_signed_in),
            style = MaterialTheme.typography.bodyMedium,
            color = TacticalOnSurfaceVariant
          )
        }

        // ---- Error panel: heading + icon + message ------------------------
        if (errorText != null) {
          Spacer(Modifier.height(16.dp))
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(EmergencyRedContainer)
              .padding(12.dp)
              .testTag("login_error_text"),
            verticalAlignment = Alignment.Top
          ) {
            Icon(
              imageVector = Icons.Default.ErrorOutline,
              contentDescription = null,
              tint = EmergencyRed,
              modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.size(10.dp))
            Column {
              Text(
                text = stringResource(R.string.login_error_title),
                style = MaterialTheme.typography.titleSmall,
                color = EmergencyRed
              )
              Spacer(Modifier.height(2.dp))
              Text(
                text = errorText,
                style = MaterialTheme.typography.bodySmall,
                color = OnEmergencyRedContainer
              )
            }
          }
        }

        Spacer(Modifier.height(20.dp))

        // ---- Primary CTA ---------------------------------------------------
        // Brand green, not red: red is reserved for errors and SOS. The
        // disabled colours are explicit, so an empty form no longer shows a
        // white label on a washed-out container.
        Button(
          onClick = { submit() },
          enabled = canSubmit,
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = NeonEmerald,
            contentColor = OnNeonEmerald,
            disabledContainerColor = ObsidianContainerHigh,
            disabledContentColor = TacticalOnSurfaceVariant
          ),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .testTag("login_submit_button")
        ) {
          Text(
            text = if (isRegisterMode) stringResource(R.string.login_create_account)
            else stringResource(R.string.login_sign_in),
            style = MaterialTheme.typography.titleSmall
          )
        }

        TextButton(
          onClick = {
            isRegisterMode = !isRegisterMode
            errorRes = null
            password = ""
            confirmPassword = ""
          },
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .testTag("login_toggle_mode_button")
        ) {
          Text(
            text = if (isRegisterMode) stringResource(R.string.login_have_account)
            else stringResource(R.string.login_new_to_network),
            style = MaterialTheme.typography.bodySmall,
            color = NeonEmerald
          )
        }
      }

      Spacer(Modifier.height(16.dp))

      // ---- Demo account hint: the whole card is the tap target -----------
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(16.dp))
          .background(ObsidianContainer)
          .border(1.dp, TacticalOutlineVariant, RoundedCornerShape(16.dp))
          .clickable {
            email = AuthRepository.DEMO_EMAIL
            password = AuthRepository.DEMO_PASSWORD
            errorRes = null
          }
          .padding(14.dp)
          .testTag("login_demo_account")
      ) {
        Text(
          text = stringResource(R.string.login_demo_account),
          style = MaterialTheme.typography.labelMedium,
          color = NeonEmerald
        )
        Spacer(Modifier.height(4.dp))
        Text(
          text = stringResource(R.string.login_demo_hint),
          style = MaterialTheme.typography.bodySmall,
          color = TacticalOnSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Text(
          text = "${AuthRepository.DEMO_EMAIL}  /  ${AuthRepository.DEMO_PASSWORD}",
          style = MaterialTheme.typography.bodyMedium,
          color = TacticalOnSurface
        )
      }
    }
  }
}

/**
 * Maps an auth failure to the string resource for its message.
 *
 * Returns a resource id rather than resolved text so this stays a pure
 * mapping function that the @Composable submit() handler can call; the
 * auth behaviour itself is untouched.
 */
private fun authErrorMessageRes(result: AuthResult): Int? {
  if (result.ok) return null
  return when (result.error) {
    AuthError.INVALID_EMAIL -> R.string.login_error_invalid_email
    AuthError.WEAK_PASSWORD -> R.string.login_error_password_short
    AuthError.EMAIL_TAKEN -> R.string.login_error_account_exists
    AuthError.ACCOUNT_NOT_FOUND -> R.string.login_error_no_account
    AuthError.WRONG_CREDENTIALS -> R.string.login_error_incorrect
    null -> R.string.login_error_generic
  }
}
