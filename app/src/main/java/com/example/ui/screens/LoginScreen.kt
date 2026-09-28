package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.auth.AuthError
import com.example.data.auth.AuthRepository
import com.example.data.auth.AuthResult
import com.example.data.auth.RegistrationRequest
import com.example.data.model.UserProfile
import com.example.data.risk.RelocationPlanner
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmergencyRedContainer
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianContainer
import com.example.ui.theme.ObsidianContainerHigh
import com.example.ui.theme.ObsidianContainerLow
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.OnEmergencyRedContainer
import com.example.ui.theme.OnNeonEmerald
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.TacticalOutline
import com.example.ui.theme.TacticalOutlineVariant

/**
 * Login / Registration gate shown before the relief network is entered.
 *
 * ONE authentication system for both modes: the same brand header, the same
 * field styling, error panel and primary action — so Registration reads as the
 * same product as Sign in, not as a separate screen.
 *
 * AUTH CONTRACT: [onLogin] receives the credentials. [onRegister] receives a
 * [RegistrationRequest] carrying those credentials PLUS the citizen profile the
 * user typed on the same form — the SAME [UserProfile] schema the Profile tab
 * renders. `AuthRepository.register` creates the account and stores that profile
 * in one step, so every value entered here becomes that account's stored profile
 * and nothing is dropped, defaulted or replaced by a sample identity.
 *
 * REGISTRATION FIELD POLICY (mirrors the Profile editor exactly):
 *  - REQUIRED: email, password (min length), confirm password, full name.
 *  - OPTIONAL, and labelled as such: citizen ID, phone, blood group, medical
 *    tag, medical notes, household members + details, vulnerable-member flags
 *    and the medical-support flag. Any of these may be left empty and filled in
 *    later from Profile — the EMPTY value is what gets stored.
 *
 * LAYOUT NOTES: one scrollable surface with `imePadding()` and
 * `navigationBarsPadding()`, so the keyboard can never cover the submit button
 * or the error panel. Every field has a real label, the password fields share a
 * visibility toggle, and the primary button carries explicit enabled/disabled
 * colours. No loading spinner exists on purpose: authentication and profile
 * storage are local and complete synchronously, so a spinner would be theatre.
 * Red stays reserved for errors and SOS — the CTA is brand green.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoginScreen(
  onLogin: (email: String, password: String, staySignedIn: Boolean) -> AuthResult,
  onRegister: (RegistrationRequest) -> AuthResult,
  modifier: Modifier = Modifier
) {
  var isRegisterMode by rememberSaveable { mutableStateOf(false) }
  var email by rememberSaveable { mutableStateOf("") }
  var password by rememberSaveable { mutableStateOf("") }
  var confirmPassword by rememberSaveable { mutableStateOf("") }
  var staySignedIn by rememberSaveable { mutableStateOf(true) }
  var showPassword by rememberSaveable { mutableStateOf(false) }
  var errorRes by rememberSaveable { mutableStateOf<Int?>(null) }

  // ---- Registration profile state — the SAME fields the Profile tab owns ----
  // Held here only until the account is created; registration then stores them
  // as one UserProfile. Nothing is pre-filled with an example identity.
  var fullName by rememberSaveable { mutableStateOf("") }
  var citizenId by rememberSaveable { mutableStateOf("") }
  var phone by rememberSaveable { mutableStateOf("") }
  var bloodGroup by rememberSaveable { mutableStateOf("") }
  var medicalTag by rememberSaveable { mutableStateOf("") }
  var medicalNotes by rememberSaveable { mutableStateOf("") }
  var dependentsCount by rememberSaveable { mutableStateOf(0) }
  var dependentsDetail by rememberSaveable { mutableStateOf("") }
  // A List (not a Set) so rememberSaveable can bundle the value directly.
  var vulnerableIds by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
  var needsMedicalSupport by rememberSaveable { mutableStateOf(false) }

  val errorText = errorRes?.let { stringResource(it) }
  val focusManager = LocalFocusManager.current
  val emailFocus = remember { FocusRequester() }
  // Open the keyboard where the user actually starts work.
  LaunchedEffect(Unit) { runCatching { emailFocus.requestFocus() } }

  /** Leaves no credentials from the previous mode behind. */
  fun resetTransientSecrets() {
    password = ""
    confirmPassword = ""
    errorRes = null
  }

  /**
   * ONE submit path for both modes. Registration validates the required fields
   * first, then hands the complete profile to the caller so the account and its
   * citizen profile are created together.
   */
  fun submit() {
    focusManager.clearFocus()
    errorRes = if (isRegisterMode) {
      when {
        fullName.isBlank() -> R.string.register_error_name
        password.length < AuthRepository.MIN_PASSWORD_LENGTH -> R.string.register_error_password
        password != confirmPassword -> R.string.login_passwords_do_not_match
        email.isBlank() -> R.string.login_error_invalid_email
        else -> authErrorMessageRes(
          onRegister(
            RegistrationRequest(
              email = email.trim(),
              password = password,
              staySignedIn = staySignedIn,
              // Exactly what the user typed: blank optional fields stay blank.
              profile = UserProfile(
                fullName = fullName.trim(),
                citizenId = citizenId.trim(),
                phone = phone.trim(),
                bloodGroup = bloodGroup,
                medicalTag = medicalTag.trim(),
                medicalNotes = medicalNotes.trim(),
                dependentsCount = dependentsCount,
                dependentsDetail = dependentsDetail.trim(),
                vulnerableCategoryIds = vulnerableIds.toSet(),
                needsMedicalSupport = needsMedicalSupport
              )
            )
          )
        )
      }
    } else {
      authErrorMessageRes(onLogin(email.trim(), password, staySignedIn))
    }
  }

  val canSubmit = if (isRegisterMode) {
    email.isNotBlank() && fullName.isNotBlank() &&
      password.isNotBlank() && confirmPassword.isNotBlank()
  } else {
    email.isNotBlank() && password.isNotBlank()
  }
  val hasError = errorRes != null

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
      BrandLockup()

      Spacer(Modifier.height(22.dp))

      // ---- One form card, so both modes read as the same system ----------
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(20.dp))
          .background(ObsidianContainerLow)
          .border(1.dp, TacticalOutlineVariant, RoundedCornerShape(20.dp))
          .padding(20.dp)
      ) {
        AuthFormHeading(
          isRegisterMode = isRegisterMode,
          onBackToSignIn = {
            isRegisterMode = false
            resetTransientSecrets()
          }
        )

        Spacer(Modifier.height(18.dp))

        if (isRegisterMode) {
          // ================= ACCOUNT — required ============================
          AuthSectionHeader(
            title = stringResource(R.string.register_section_account),
            hint = stringResource(R.string.register_section_account_hint)
          )

          // Email is the account identifier the Profile screen shows as
          // "Signed in as".
          AuthField(
            value = email,
            onValueChange = { email = it; errorRes = null },
            label = stringResource(R.string.login_email),
            testTag = "login_email_field",
            isError = hasError,
            keyboardType = KeyboardType.Email,
            keyboardActions = KeyboardActions(
              onNext = { focusManager.moveFocus(FocusDirection.Down) }
            ),
            modifier = Modifier.focusRequester(emailFocus)
          )
          Spacer(Modifier.height(14.dp))

          PasswordField(
            value = password,
            onValueChange = { password = it; errorRes = null },
            label = stringResource(
              R.string.login_password_min,
              AuthRepository.MIN_PASSWORD_LENGTH
            ),
            testTag = "login_password_field",
            isError = hasError,
            showPassword = showPassword,
            onToggleShow = { showPassword = !showPassword },
            onImeAction = { focusManager.moveFocus(FocusDirection.Down) }
          )
          Spacer(Modifier.height(14.dp))

          PasswordField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it; errorRes = null },
            label = stringResource(R.string.login_confirm_password),
            testTag = "login_confirm_field",
            isError = hasError,
            showPassword = showPassword,
            onToggleShow = { showPassword = !showPassword },
            onImeAction = { focusManager.moveFocus(FocusDirection.Down) }
          )
          Spacer(Modifier.height(14.dp))

          // Required: a citizen record with no name cannot be relayed to a
          // rescue team, so this is the one profile field registration demands.
          AuthField(
            value = fullName,
            onValueChange = { fullName = it; errorRes = null },
            label = stringResource(R.string.register_full_name),
            testTag = "register_full_name_field",
            isError = hasError,
            keyboardActions = KeyboardActions(
              onNext = { focusManager.moveFocus(FocusDirection.Down) }
            )
          )
        } else {
          // ================= SIGN IN =======================================
          AuthField(
            value = email,
            onValueChange = { email = it; errorRes = null },
            label = stringResource(R.string.login_email),
            testTag = "login_email_field",
            isError = hasError,
            keyboardType = KeyboardType.Email,
            keyboardActions = KeyboardActions(
              onNext = { focusManager.moveFocus(FocusDirection.Down) }
            ),
            modifier = Modifier.focusRequester(emailFocus)
          )
          Spacer(Modifier.height(14.dp))

          PasswordField(
            value = password,
            onValueChange = { password = it; errorRes = null },
            label = stringResource(
              R.string.login_password_min,
              AuthRepository.MIN_PASSWORD_LENGTH
            ),
            testTag = "login_password_field",
            isError = hasError,
            showPassword = showPassword,
            onToggleShow = { showPassword = !showPassword },
            onImeAction = { focusManager.clearFocus() }
          )
        }

        StaySignedInRow(
          checked = staySignedIn,
          onCheckedChange = { staySignedIn = it }
        )

        if (isRegisterMode) {
          // ================= PROFILE — every field optional ================
          // Same fields, same order and same controls as the Profile editor,
          // so a value typed here is editable in exactly the same place later.
          Spacer(Modifier.height(22.dp))
          AuthSectionHeader(
            title = stringResource(R.string.register_section_profile),
            hint = stringResource(R.string.register_section_profile_hint)
          )

          AuthField(
            value = citizenId,
            onValueChange = { citizenId = it },
            label = stringResource(R.string.register_citizen_id_optional),
            testTag = "register_citizen_id_field",
            isError = false,
            leadingIcon = Icons.Default.Person
          )
          Spacer(Modifier.height(14.dp))

          AuthField(
            value = phone,
            onValueChange = { phone = it },
            label = stringResource(R.string.gobag_phone),
            testTag = "register_phone_field",
            isError = false,
            keyboardType = KeyboardType.Phone,
            leadingIcon = Icons.Default.Call
          )
          Spacer(Modifier.height(16.dp))

          // Blood group: the SAME eight groups the Profile editor offers.
          // Tapping the selected chip clears it, so "not provided" stays a real
          // choice instead of a default the user cannot undo.
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = stringResource(R.string.edit_profile_blood_group),
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = TacticalOnSurfaceVariant,
              letterSpacing = 0.5.sp
            )
            FlowRow(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              BLOOD_GROUPS.forEach { group ->
                BloodGroupChip(
                  label = group,
                  selected = bloodGroup == group,
                  testTag = "register_blood_group_$group",
                  onClick = { bloodGroup = if (bloodGroup == group) "" else group }
                )
              }
            }
          }
          Spacer(Modifier.height(14.dp))

          AuthField(
            value = medicalTag,
            onValueChange = { medicalTag = it },
            label = stringResource(R.string.edit_profile_medical_tag),
            testTag = "register_medical_tag_field",
            isError = false
          )
          Spacer(Modifier.height(14.dp))

          AuthField(
            value = medicalNotes,
            onValueChange = { medicalNotes = it },
            label = stringResource(R.string.edit_profile_medical_notes),
            testTag = "register_medical_notes_field",
            isError = false,
            singleLine = false
          )
          Spacer(Modifier.height(16.dp))

          Text(
            text = stringResource(R.string.register_dependents_optional),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = TacticalOnSurfaceVariant,
            letterSpacing = 0.5.sp
          )
          Spacer(Modifier.height(6.dp))
          DependentStepper(
            count = dependentsCount,
            onDecrease = { if (dependentsCount > 0) dependentsCount-- },
            onIncrease = { dependentsCount++ }
          )
          Spacer(Modifier.height(14.dp))

          AuthField(
            value = dependentsDetail,
            onValueChange = { dependentsDetail = it },
            label = stringResource(R.string.edit_profile_dependents_detail),
            testTag = "register_dependents_detail_field",
            isError = false
          )
          Spacer(Modifier.height(18.dp))

          Text(
            text = stringResource(R.string.profile_evacuation_needs_label),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = TacticalOnSurfaceVariant,
            letterSpacing = 0.5.sp
          )
          Text(
            text = stringResource(R.string.profile_evacuation_needs_description),
            fontSize = 10.sp,
            color = TacticalOnSurfaceVariant,
            lineHeight = 14.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
          )

          // The vulnerable-member set and the medical-support flag are real
          // inputs to the shelter ranking / relocation engine, so they are
          // collected here from the same source of truth the editor uses.
          RelocationPlanner.VULNERABLE_CATEGORIES.forEach { category ->
            CheckRow(
              checked = category.id in vulnerableIds,
              onCheckedChange = { checked ->
                vulnerableIds = if (checked) {
                  (vulnerableIds + category.id).distinct()
                } else {
                  vulnerableIds - category.id
                }
              },
              title = category.label,
              subtitle = category.supportNeeds,
              testTag = "register_vulnerable_${category.id}"
            )
          }
          CheckRow(
            checked = needsMedicalSupport,
            onCheckedChange = { needsMedicalSupport = it },
            title = stringResource(R.string.edit_profile_need_medical),
            subtitle = stringResource(R.string.edit_profile_need_medical_sub),
            testTag = "register_need_medical_checkbox"
          )

          Spacer(Modifier.height(12.dp))
          Text(
            text = stringResource(R.string.register_privacy_note),
            fontSize = 10.sp,
            lineHeight = 14.sp,
            color = TacticalOnSurfaceVariant
          )
        }

        // ---- Error panel: heading + icon + message -------------------------
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
            Spacer(Modifier.width(8.dp))
            Column {
              Text(
                // Registration has its own heading so a failure never reads as
                // a sign-in failure.
                text = stringResource(
                  if (isRegisterMode) R.string.register_error_title
                  else R.string.login_error_title
                ),
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
        // disabled colours are explicit, so an incomplete form never shows a
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
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
          )
        }

        TextButton(
          onClick = {
            isRegisterMode = !isRegisterMode
            // Never carry a typed password or a stale error between the two
            // forms: a half-typed registration must not become a sign-in retry.
            resetTransientSecrets()
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

      // ---- Demo account hint (sign-in only): the whole card is the target --
      if (!isRegisterMode) {
        Spacer(Modifier.height(16.dp))
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

      Spacer(Modifier.height(16.dp))
    }
  }
}

// ---------------------------------------------------------------------------
// SHARED AUTH PRIMITIVES — one visual language for Login and Registration
// ---------------------------------------------------------------------------

/** The eight blood groups the Profile editor also offers (language-neutral). */
private val BLOOD_GROUPS = listOf("A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-")

/**
 * Brand lockup: the official Vippatti Sarana emblem, the brand name and the
 * tagline. Reuses the app's own artwork — the same logo the launcher, splash
 * screen and onboarding carry — instead of a new or generic mark.
 */
@Composable
private fun BrandLockup() {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Image(
      painter = painterResource(id = R.drawable.onboarding_hero_logo),
      contentDescription = stringResource(R.string.login_a11y_logo),
      contentScale = ContentScale.Fit,
      modifier = Modifier
        .size(112.dp)
        .shadow(6.dp, CircleShape)
        .clip(CircleShape)
        .background(Color.White)
        .testTag("login_brand_logo")
    )
    Spacer(Modifier.height(14.dp))
    Text(
      text = stringResource(R.string.login_title),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = TacticalOnSurface,
      textAlign = TextAlign.Center
    )
    Text(
      text = stringResource(R.string.login_brand_tagline),
      style = MaterialTheme.typography.bodySmall,
      color = TacticalOnSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 2.dp, start = 16.dp, end = 16.dp)
    )
  }
}

/**
 * Mode heading, identical in both forms so the two screens read as one system.
 * Registration additionally carries a "back to sign in" action beside the
 * heading, so leaving the long form never requires scrolling to the bottom.
 */
@Composable
private fun AuthFormHeading(
  isRegisterMode: Boolean,
  onBackToSignIn: () -> Unit
) {
  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = if (isRegisterMode) stringResource(R.string.register_title)
        else stringResource(R.string.login_welcome_title),
        style = MaterialTheme.typography.titleLarge,
        color = TacticalOnSurface
      )
      Text(
        text = if (isRegisterMode) stringResource(R.string.register_subtitle)
        else stringResource(R.string.login_welcome_subtitle),
        style = MaterialTheme.typography.bodySmall,
        color = TacticalOnSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
      )
    }
    if (isRegisterMode) {
      TextButton(
        onClick = onBackToSignIn,
        modifier = Modifier.testTag("register_back_to_login_button")
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = null,
          tint = NeonEmerald,
          modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
          text = stringResource(R.string.register_back_to_login),
          style = MaterialTheme.typography.bodySmall,
          color = NeonEmerald
        )
      }
    }
  }
}

/**
 * Section label: what the block contains, plus whether it is required or may be
 * completed later. Registered users can therefore see at a glance which fields
 * they may skip.
 */
@Composable
private fun AuthSectionHeader(title: String, hint: String) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Text(
      text = title,
      fontSize = 11.sp,
      fontWeight = FontWeight.Black,
      color = NeonEmerald,
      letterSpacing = 0.8.sp
    )
    Text(
      text = hint,
      fontSize = 10.sp,
      lineHeight = 14.sp,
      color = TacticalOnSurfaceVariant,
      modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
    )
  }
}

/**
 * One labelled text field with the app's full M3 colour set: a visible
 * focused/unfocused border, a readable floating label and a real error state.
 * Every field on both forms renders through this, so Login and Registration
 * are visually identical.
 */
@Composable
private fun AuthField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  testTag: String,
  isError: Boolean,
  modifier: Modifier = Modifier,
  keyboardType: KeyboardType = KeyboardType.Text,
  imeAction: ImeAction = ImeAction.Next,
  visualTransformation: VisualTransformation = VisualTransformation.None,
  keyboardActions: KeyboardActions = KeyboardActions.Default,
  leadingIcon: ImageVector? = null,
  singleLine: Boolean = true,
  trailingIcon: (@Composable () -> Unit)? = null
) {
  // Declared with an explicit type so the lambda below is inferred as a
  // composable lambda (the M3 `leadingIcon` slot requires @Composable).
  val leading: (@Composable () -> Unit)? = leadingIcon?.let { icon ->
    {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = TacticalOnSurfaceVariant,
        modifier = Modifier.size(18.dp)
      )
    }
  }

  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    label = { Text(label, fontSize = 12.sp) },
    singleLine = singleLine,
    maxLines = if (singleLine) 1 else 3,
    isError = isError,
    visualTransformation = visualTransformation,
    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
    keyboardActions = keyboardActions,
    leadingIcon = leading,
    trailingIcon = trailingIcon,
    colors = OutlinedTextFieldDefaults.colors(
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
    ),
    shape = RoundedCornerShape(12.dp),
    modifier = modifier
      .fillMaxWidth()
      .testTag(testTag)
  )
}

/**
 * Password field with a real show/hide control. Both password fields on the
 * registration form obey the SAME toggle, so the two entries can never be
 * compared while rendered under different transformations.
 */
@Composable
private fun PasswordField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  testTag: String,
  isError: Boolean,
  showPassword: Boolean,
  onToggleShow: () -> Unit,
  onImeAction: () -> Unit,
  modifier: Modifier = Modifier
) {
  AuthField(
    value = value,
    onValueChange = onValueChange,
    label = label,
    testTag = testTag,
    isError = isError,
    modifier = modifier,
    keyboardType = KeyboardType.Password,
    visualTransformation =
      if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
    keyboardActions = KeyboardActions(
      onNext = { onImeAction() },
      onDone = { onImeAction() }
    ),
    trailingIcon = {
      IconButton(onClick = onToggleShow) {
        Icon(
          imageVector =
            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
          contentDescription = stringResource(
            if (showPassword) R.string.login_hide_password else R.string.login_show_password
          ),
          tint = TacticalOnSurfaceVariant
        )
      }
    }
  )
}

/**
 * "Stay signed in" — an explicit opt-in that controls whether the session
 * survives an app restart. Shared by both forms.
 */
@Composable
private fun StaySignedInRow(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 10.dp)
  ) {
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
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
}

/**
 * Household-size stepper — the same control (and the same bounds) as the
 * Profile editor, so the count typed at registration stays editable later.
 */
@Composable
private fun DependentStepper(
  count: Int,
  onDecrease: () -> Unit,
  onIncrease: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(ObsidianContainer)
      .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
      .padding(horizontal = 6.dp, vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    IconButton(
      onClick = onDecrease,
      enabled = count > 0,
      modifier = Modifier
        .size(40.dp)
        .clip(CircleShape)
        .testTag("dependents_decrease_button")
    ) {
      Icon(
        imageVector = Icons.Default.Remove,
        contentDescription = stringResource(R.string.edit_profile_remove_dependent),
        tint = EmergencyRed,
        modifier = Modifier.size(18.dp)
      )
    }
    Text(
      text = "$count",
      fontSize = 15.sp,
      fontWeight = FontWeight.Bold,
      color = TacticalOnSurface,
      modifier = Modifier.testTag("register_dependents_count_value")
    )
    IconButton(
      onClick = onIncrease,
      modifier = Modifier
        .size(40.dp)
        .clip(CircleShape)
        .testTag("dependents_increase_button")
    ) {
      Icon(
        imageVector = Icons.Default.Add,
        contentDescription = stringResource(R.string.edit_profile_add_dependent),
        tint = NeonEmerald,
        modifier = Modifier.size(18.dp)
      )
    }
  }
}

/** One blood-group chip: selection shown by fill, border AND a check icon. */
@Composable
private fun BloodGroupChip(
  label: String,
  selected: Boolean,
  testTag: String,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .clip(RoundedCornerShape(10.dp))
      .background(if (selected) EmergencyRedContainer else ObsidianContainerLow)
      .border(
        width = 1.dp,
        color = if (selected) EmergencyRed else TacticalOutlineVariant,
        shape = RoundedCornerShape(10.dp)
      )
      .clickable(onClick = onClick)
      .heightIn(min = 40.dp)
      .padding(horizontal = 12.dp, vertical = 8.dp)
      .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    if (selected) {
      Icon(
        imageVector = Icons.Default.Check,
        contentDescription = null,
        tint = EmergencyRed,
        modifier = Modifier.size(13.dp)
      )
    }
    Text(
      text = label,
      fontSize = 12.sp,
      fontWeight = FontWeight.Bold,
      color = if (selected) EmergencyRed else TacticalOnSurface
    )
  }
}

/**
 * A checkable requirement row (vulnerable member / medical support). The whole
 * row is the target and the checkbox carries the state, so the meaning is never
 * conveyed by colour alone.
 */
@Composable
private fun CheckRow(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  title: String,
  subtitle: String,
  testTag: String
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 4.dp)
      .clip(RoundedCornerShape(10.dp))
      .background(ObsidianContainerLow)
      .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
      .clickable { onCheckedChange(!checked) }
      .padding(horizontal = 4.dp)
      .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Checkbox(
      checked = checked,
      onCheckedChange = onCheckedChange,
      colors = CheckboxDefaults.colors(
        checkedColor = TacticalCyan,
        uncheckedColor = TacticalOutlineVariant
      )
    )
    Column(modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)) {
      Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = TacticalOnSurface
      )
      Text(
        text = subtitle,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        color = TacticalOnSurfaceVariant
      )
    }
  }
}

/**
 * Maps an auth failure to the string resource for its message.
 *
 * Returns a resource id rather than resolved text so this stays a pure mapping
 * function that the @Composable submit() handler can call; the auth behaviour
 * itself is untouched.
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
