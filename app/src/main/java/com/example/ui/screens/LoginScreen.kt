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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import androidx.compose.ui.res.stringResource
import com.example.data.auth.AuthError
import com.example.data.auth.AuthRepository
import com.example.data.auth.AuthResult
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmergencyRedBright
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianContainer
import com.example.ui.theme.ObsidianContainerLow
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.TacticalOutlineVariant

/**
 * Login / create-account gate shown before the relief network is entered.
 * Latest-news-style branding on top, email + password below, with a demo
 * account hint (seeded by [AuthRepository.seedDemoAccount]) so the app stays
 * immediately usable offline.
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

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(ObsidianSurface)
      .statusBarsPadding()
      .verticalScroll(rememberScrollState())
      .padding(24.dp)
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Spacer(Modifier.height(48.dp))

      // Branding lockup.
      Box(
        modifier = Modifier
          .size(72.dp)
          .background(EmergencyRed.copy(alpha = 0.15f), CircleShape)
          .border(2.dp, EmergencyRed, CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Text(stringResource(R.string.login_sos_badge), fontSize = 20.sp, fontWeight = FontWeight.Black, color = EmergencyRed)
      }
      Spacer(Modifier.height(16.dp))
      Text(
        stringResource(R.string.login_title),
        fontSize = 18.sp,
        fontWeight = FontWeight.Black,
        color = EmergencyRedBright,
        letterSpacing = 1.sp
      )
      Text(
        stringResource(R.string.login_subtitle),
        fontSize = 12.sp,
        color = TacticalOnSurfaceVariant
      )
      Spacer(Modifier.height(32.dp))

      Text(
        if (isRegisterMode) stringResource(R.string.login_tab_create) else stringResource(R.string.login_tab_signin),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = TacticalOnSurface
      )
      Spacer(Modifier.height(16.dp))

      OutlinedTextField(
        value = email,
        onValueChange = { email = it; errorRes = null },
        label = { Text(stringResource(R.string.login_email), fontSize = 13.sp, color = TacticalOnSurfaceVariant) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("login_email_field")
      )

      Spacer(Modifier.height(12.dp))

      OutlinedTextField(
        value = password,
        onValueChange = { password = it; errorRes = null },
        label = {
          Text(
            stringResource(R.string.login_password_min, AuthRepository.MIN_PASSWORD_LENGTH),
            fontSize = 13.sp,
            color = TacticalOnSurfaceVariant
          )
        },
        singleLine = true,
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
          IconButton(onClick = { showPassword = !showPassword }) {
            Icon(
              imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
              contentDescription = stringResource(if (showPassword) R.string.login_hide_password else R.string.login_show_password),
              tint = TacticalOnSurfaceVariant
            )
          }
        },
        modifier = Modifier
          .fillMaxWidth()
          .testTag("login_password_field")
      )

      if (isRegisterMode) {
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
          value = confirmPassword,
          onValueChange = { confirmPassword = it; errorRes = null },
          label = { Text(stringResource(R.string.login_confirm_password), fontSize = 13.sp, color = TacticalOnSurfaceVariant) },
          singleLine = true,
          visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("login_confirm_field")
        )
      }

      Spacer(Modifier.height(8.dp))

      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
      ) {
        Switch(
          checked = staySignedIn,
          onCheckedChange = { staySignedIn = it },
          colors = SwitchDefaults.colors(checkedTrackColor = NeonEmerald),
          modifier = Modifier.testTag("login_stay_signed_in")
        )
        Spacer(Modifier.size(8.dp))
        Text(
          stringResource(R.string.login_stay_signed_in),
          fontSize = 12.sp,
          color = TacticalOnSurfaceVariant
        )
      }

      Spacer(Modifier.height(16.dp))

      errorText?.let {
        Text(
          text = it,
          color = EmergencyRed,
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianContainerLow, RoundedCornerShape(8.dp))
            .padding(10.dp)
            .testTag("login_error_text")
        )
        Spacer(Modifier.height(12.dp))
      }

      Button(
        onClick = { submit() },
        enabled = email.isNotBlank() && password.isNotBlank(),
        colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 50.dp)
          .testTag("login_submit_button")
      ) {
        Text(
          if (isRegisterMode) stringResource(R.string.login_create_account) else stringResource(R.string.login_sign_in),
          fontWeight = FontWeight.Black,
          color = Color.White
        )
      }

      TextButton(
        onClick = { isRegisterMode = !isRegisterMode; errorRes = null; password = ""; confirmPassword = "" },
        modifier = Modifier.testTag("login_toggle_mode_button")
      ) {
        Text(
          if (isRegisterMode) stringResource(R.string.login_have_account) else stringResource(R.string.login_new_to_network),
          fontSize = 12.sp,
          color = TacticalCyan
        )
      }

      Spacer(Modifier.height(16.dp))

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(ObsidianContainer, RoundedCornerShape(12.dp))
          .border(1.dp, TacticalOutlineVariant, RoundedCornerShape(12.dp))
          .padding(14.dp)
      ) {
        Text(
          stringResource(R.string.login_demo_account),
          fontSize = 10.sp,
          fontWeight = FontWeight.Black,
          color = NeonEmerald,
          letterSpacing = 0.8.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
          "${AuthRepository.DEMO_EMAIL}  /  ${AuthRepository.DEMO_PASSWORD}",
          fontSize = 12.sp,
          color = TacticalOnSurfaceVariant,
          modifier = Modifier
            .clickable {
              email = AuthRepository.DEMO_EMAIL
              password = AuthRepository.DEMO_PASSWORD
              errorRes = null
            }
            .padding(vertical = 4.dp)
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
