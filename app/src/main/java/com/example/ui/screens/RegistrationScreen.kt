package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
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

/** Eight blood groups the Profile editor offers (language-neutral). */
private val REGISTRATION_BLOOD_GROUPS = listOf("A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-")

/**
 * Standalone Registration screen.
 *
 * Collects the ONE existing UserProfile schema plus account credentials and
 * hands both to onRegister as a RegistrationRequest, so
 * AuthRepository.register(request) creates the account AND stores the typed
 * profile in one step through the existing account/profile data layer.
 *
 * REQUIRED: email, password (min length), confirm password, full name.
 * OPTIONAL: every other UserProfile field; blanks stay blank and can be
 * completed later from Profile.
 *
 * Standalone on purpose: LoginScreen untouched. Wire later with the same
 * authRepository::register lambda used today.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RegistrationScreen(
  onRegister: (RegistrationRequest) -> AuthResult,
  onBackToSignIn: () -> Unit,
  modifier: Modifier = Modifier
) {
  var email by rememberSaveable { mutableStateOf("") }
  var password by rememberSaveable { mutableStateOf("") }
  var confirmPassword by rememberSaveable { mutableStateOf("") }
  var staySignedIn by rememberSaveable { mutableStateOf(false) }
  var showPassword by rememberSaveable { mutableStateOf(false) }
  var errorRes by rememberSaveable { mutableStateOf<Int?>(null) }
  var fullName by rememberSaveable { mutableStateOf("") }
  var citizenId by rememberSaveable { mutableStateOf("") }
  var phone by rememberSaveable { mutableStateOf("") }
  var bloodGroup by rememberSaveable { mutableStateOf("") }
  var medicalTag by rememberSaveable { mutableStateOf("") }
  var medicalNotes by rememberSaveable { mutableStateOf("") }
  var dependentsCount by rememberSaveable { mutableStateOf(0) }
  var dependentsDetail by rememberSaveable { mutableStateOf("") }
  var vulnerableIds by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
  var needsMedicalSupport by rememberSaveable { mutableStateOf(false) }

  val errorText = errorRes?.let { stringResource(it) }
  val focusManager = LocalFocusManager.current

  fun submit() {
    focusManager.clearFocus()
    errorRes = when {
      fullName.isBlank() -> R.string.register_error_name
      password.length < AuthRepository.MIN_PASSWORD_LENGTH -> R.string.register_error_password
      password != confirmPassword -> R.string.login_passwords_do_not_match
      email.isBlank() -> R.string.login_error_invalid_email
      else -> registerAuthErrorMessageRes(
        onRegister(
          RegistrationRequest(
            email = email.trim(),
            password = password,
            staySignedIn = staySignedIn,
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
  }

  val canSubmit = email.isNotBlank() && fullName.isNotBlank() &&
    password.isNotBlank() && confirmPassword.isNotBlank()
  val hasError = errorRes != null

  Surface(modifier = modifier.fillMaxSize(), color = ObsidianSurface) {
    Column(
      modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .imePadding().navigationBarsPadding().statusBarsPadding()
        .padding(horizontal = 20.dp, vertical = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      RegistrationBrandHeader()
      Spacer(Modifier.height(22.dp))
      Column(
        modifier = Modifier.fillMaxWidth()
          .clip(RoundedCornerShape(20.dp)).background(ObsidianContainerLow)
          .border(1.dp, TacticalOutlineVariant, RoundedCornerShape(20.dp))
          .padding(20.dp)
      ) {
        RegistrationHeading(onBackToSignIn = onBackToSignIn)
        Spacer(Modifier.height(18.dp))
        RegistrationSectionHeader(
          title = stringResource(R.string.register_section_account),
          hint = stringResource(R.string.register_section_account_hint)
        )
        RegisterField(
          value = email, onValueChange = { email = it; errorRes = null },
          label = stringResource(R.string.login_email),
          testTag = "register_email_field", isError = hasError,
          keyboardType = KeyboardType.Email,
          keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) }
          )
        )
        Spacer(Modifier.height(14.dp))
        RegisterPasswordField(
          value = password, onValueChange = { password = it; errorRes = null },
          label = stringResource(R.string.login_password_min, AuthRepository.MIN_PASSWORD_LENGTH),
          testTag = "register_password_field", isError = hasError,
          showPassword = showPassword, onToggleShow = { showPassword = !showPassword },
          onImeAction = { focusManager.moveFocus(FocusDirection.Down) }
        )
        Spacer(Modifier.height(14.dp))
        RegisterPasswordField(
          value = confirmPassword, onValueChange = { confirmPassword = it; errorRes = null },
          label = stringResource(R.string.login_confirm_password),
          testTag = "register_confirm_field", isError = hasError,
          showPassword = showPassword, onToggleShow = { showPassword = !showPassword },
          onImeAction = { focusManager.moveFocus(FocusDirection.Down) }
        )
        Spacer(Modifier.height(14.dp))
        RegisterField(
          value = fullName, onValueChange = { fullName = it; errorRes = null },
          label = stringResource(R.string.register_full_name),
          testTag = "register_full_name_field", isError = hasError,
          keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) }
          )
        )
        Spacer(Modifier.height(22.dp))
        RegistrationSectionHeader(
          title = stringResource(R.string.register_section_profile),
          hint = stringResource(R.string.register_section_profile_hint)
        )
        RegisterField(
          value = citizenId, onValueChange = { citizenId = it },
          label = stringResource(R.string.register_citizen_id_optional),
          testTag = "register_citizen_id_field", isError = false,
          leadingIcon = Icons.Default.Person
        )
        Spacer(Modifier.height(14.dp))
        RegisterField(
          value = phone, onValueChange = { phone = it },
          label = stringResource(R.string.gobag_phone),
          testTag = "register_phone_field", isError = false,
          keyboardType = KeyboardType.Phone, leadingIcon = Icons.Default.Call
        )
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = stringResource(R.string.edit_profile_blood_group),
            fontSize = 10.sp, fontWeight = FontWeight.Bold,
            color = TacticalOnSurfaceVariant, letterSpacing = 0.5.sp
          )
          FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            REGISTRATION_BLOOD_GROUPS.forEach { group ->
              RegisterBloodChip(
                label = group, selected = bloodGroup == group,
                testTag = "register_blood_group_$group",
                onClick = { bloodGroup = if (bloodGroup == group) "" else group }
              )
            }
          }
        }
        Spacer(Modifier.height(14.dp))
        RegisterField(
          value = medicalTag, onValueChange = { medicalTag = it },
          label = stringResource(R.string.edit_profile_medical_tag),
          testTag = "register_medical_tag_field", isError = false
        )
        Spacer(Modifier.height(14.dp))
        RegisterField(
          value = medicalNotes, onValueChange = { medicalNotes = it },
          label = stringResource(R.string.edit_profile_medical_notes),
          testTag = "register_medical_notes_field", isError = false,
          singleLine = false
        )
        Spacer(Modifier.height(16.dp))
        Text(
          text = stringResource(R.string.register_dependents_optional),
          fontSize = 10.sp, fontWeight = FontWeight.Bold,
          color = TacticalOnSurfaceVariant, letterSpacing = 0.5.sp
        )
        Spacer(Modifier.height(6.dp))
        RegisterDependentStepper(
          count = dependentsCount,
          onDecrease = { if (dependentsCount > 0) dependentsCount-- },
          onIncrease = { dependentsCount++ }
        )
        Spacer(Modifier.height(14.dp))
        RegisterField(
          value = dependentsDetail, onValueChange = { dependentsDetail = it },
          label = stringResource(R.string.edit_profile_dependents_detail),
          testTag = "register_dependents_detail_field", isError = false
        )
        Spacer(Modifier.height(18.dp))
        Text(
          text = stringResource(R.string.profile_evacuation_needs_label),
          fontSize = 10.sp, fontWeight = FontWeight.Bold,
          color = TacticalOnSurfaceVariant, letterSpacing = 0.5.sp
        )
        Text(
          text = stringResource(R.string.profile_evacuation_needs_description),
          fontSize = 10.sp, color = TacticalOnSurfaceVariant, lineHeight = 14.sp,
          modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
        )
        RelocationPlanner.VULNERABLE_CATEGORIES.forEach { category ->
          RegisterCheckRow(
            checked = category.id in vulnerableIds,
            onCheckedChange = { checked ->
              vulnerableIds = if (checked) (vulnerableIds + category.id).distinct()
              else vulnerableIds - category.id
            },
            title = category.label, subtitle = category.supportNeeds,
            testTag = "register_vulnerable_${category.id}"
          )
        }
        RegisterCheckRow(
          checked = needsMedicalSupport,
          onCheckedChange = { needsMedicalSupport = it },
          title = stringResource(R.string.edit_profile_need_medical),
          subtitle = stringResource(R.string.edit_profile_need_medical_sub),
          testTag = "register_need_medical_checkbox"
        )
        Spacer(Modifier.height(12.dp))
        Text(
          text = stringResource(R.string.register_privacy_note),
          fontSize = 10.sp, lineHeight = 14.sp, color = TacticalOnSurfaceVariant
        )

        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        ) {
          Switch(
            checked = staySignedIn, onCheckedChange = { staySignedIn = it },
            colors = SwitchDefaults.colors(
              checkedThumbColor = Color.White, checkedTrackColor = NeonEmerald,
              uncheckedThumbColor = TacticalOnSurfaceVariant,
              uncheckedTrackColor = ObsidianContainer,
              uncheckedBorderColor = TacticalOutline
            ),
            modifier = Modifier.testTag("register_stay_signed_in")
          )
          Spacer(Modifier.size(10.dp))
          Text(
            text = stringResource(R.string.login_stay_signed_in),
            style = MaterialTheme.typography.bodyMedium,
            color = TacticalOnSurfaceVariant
          )
        }
        if (errorText != null) {
          Spacer(Modifier.height(16.dp))
          Row(
            modifier = Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(12.dp)).background(EmergencyRedContainer)
              .padding(12.dp).testTag("register_error_text"),
            verticalAlignment = Alignment.Top
          ) {
            Icon(
              imageVector = Icons.Default.ErrorOutline, contentDescription = null,
              tint = EmergencyRed, modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column {
              Text(
                text = stringResource(R.string.register_error_title),
                style = MaterialTheme.typography.titleSmall, color = EmergencyRed
              )
              Spacer(Modifier.height(2.dp))
              Text(
                text = errorText, style = MaterialTheme.typography.bodySmall,
                color = OnEmergencyRedContainer
              )
            }
          }
        }
        Spacer(Modifier.height(20.dp))
        Button(
          onClick = { submit() }, enabled = canSubmit,
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = NeonEmerald, contentColor = OnNeonEmerald,
            disabledContainerColor = ObsidianContainerHigh,
            disabledContentColor = TacticalOnSurfaceVariant
          ),
          modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .testTag("register_submit_button")
        ) {
          Text(
            text = stringResource(R.string.login_create_account),
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
          )
        }
        TextButton(
          onClick = onBackToSignIn,
          modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            .testTag("register_back_to_login_button")
        ) {
          Text(
            text = stringResource(R.string.register_back_to_login),
            style = MaterialTheme.typography.bodySmall, color = NeonEmerald
          )
        }
      }
      Spacer(Modifier.height(16.dp))
    }
  }
}


/** Logo header: existing Vippatti Sarana emblem + brand name + tagline. */
@Composable
private fun RegistrationBrandHeader() {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Image(
      painter = painterResource(id = R.drawable.onboarding_hero_logo),
      contentDescription = stringResource(R.string.login_a11y_logo),
      contentScale = ContentScale.Fit,
      modifier = Modifier.size(112.dp).shadow(6.dp, CircleShape)
        .clip(CircleShape).background(Color.White)
        .testTag("register_brand_logo")
    )
    Spacer(Modifier.height(14.dp))
    Text(
      text = stringResource(R.string.login_title),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold, color = TacticalOnSurface,
      textAlign = TextAlign.Center
    )
    Text(
      text = stringResource(R.string.login_brand_tagline),
      style = MaterialTheme.typography.bodySmall,
      color = TacticalOnSurfaceVariant, textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 2.dp, start = 16.dp, end = 16.dp)
    )
  }
}

/** Title row with a top-level back-to-sign-in action. */
@Composable
private fun RegistrationHeading(onBackToSignIn: () -> Unit) {
  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = stringResource(R.string.register_title),
        style = MaterialTheme.typography.titleLarge, color = TacticalOnSurface
      )
      Text(
        text = stringResource(R.string.register_subtitle),
        style = MaterialTheme.typography.bodySmall,
        color = TacticalOnSurfaceVariant, modifier = Modifier.padding(top = 4.dp)
      )
    }
    TextButton(
      onClick = onBackToSignIn,
      modifier = Modifier.testTag("register_back_to_login_top_button")
    ) {
      Icon(
        imageVector = Icons.Default.Close, contentDescription = null,
        tint = NeonEmerald, modifier = Modifier.size(16.dp)
      )
      Spacer(Modifier.width(4.dp))
      Text(
        text = stringResource(R.string.register_back_to_login),
        style = MaterialTheme.typography.bodySmall, color = NeonEmerald
      )
    }
  }
}

/** Section label: block purpose plus required/optional hint. */
@Composable
private fun RegistrationSectionHeader(title: String, hint: String) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Text(
      text = title, fontSize = 11.sp, fontWeight = FontWeight.Black,
      color = NeonEmerald, letterSpacing = 0.8.sp
    )
    Text(
      text = hint, fontSize = 10.sp, lineHeight = 14.sp,
      color = TacticalOnSurfaceVariant,
      modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
    )
  }
}

/** Labelled text field in registration styling. */
@Composable
private fun RegisterField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  testTag: String,
  isError: Boolean,
  modifier: Modifier = Modifier,
  keyboardType: KeyboardType = KeyboardType.Text,
  imeAction: ImeAction = ImeAction.Next,
  keyboardActions: KeyboardActions = KeyboardActions.Default,
  leadingIcon: ImageVector? = null,
  singleLine: Boolean = true
) {
  val leading: (@Composable () -> Unit)? = leadingIcon?.let { icon ->
    {
      Icon(
        imageVector = icon, contentDescription = null,
        tint = TacticalOnSurfaceVariant, modifier = Modifier.size(18.dp)
      )
    }
  }
  OutlinedTextField(
    value = value, onValueChange = onValueChange,
    label = { Text(label, fontSize = 12.sp) },
    singleLine = singleLine, maxLines = if (singleLine) 1 else 3,
    isError = isError,
    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
    keyboardActions = keyboardActions, leadingIcon = leading,
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
    modifier = modifier.fillMaxWidth().testTag(testTag)
  )
}

/** Password field with a show/hide control. */
@Composable
private fun RegisterPasswordField(
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
  OutlinedTextField(
    value = value, onValueChange = onValueChange,
    label = { Text(label, fontSize = 12.sp) },
    singleLine = true, maxLines = 1, isError = isError,
    visualTransformation =
      if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
    keyboardOptions = KeyboardOptions(
      keyboardType = KeyboardType.Password, imeAction = ImeAction.Next
    ),
    keyboardActions = KeyboardActions(
      onNext = { onImeAction() }, onDone = { onImeAction() }
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
    },
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
    modifier = modifier.fillMaxWidth().testTag(testTag)
  )
}

/** Household-size stepper, same bounds as the Profile editor. */
@Composable
private fun RegisterDependentStepper(
  count: Int,
  onDecrease: () -> Unit,
  onIncrease: () -> Unit
) {
  Row(
    modifier = Modifier.fillMaxWidth()
      .clip(RoundedCornerShape(12.dp)).background(ObsidianContainer)
      .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
      .padding(horizontal = 6.dp, vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    IconButton(
      onClick = onDecrease, enabled = count > 0,
      modifier = Modifier.size(40.dp).clip(CircleShape)
        .testTag("register_dependents_decrease_button")
    ) {
      Icon(
        imageVector = Icons.Default.Remove,
        contentDescription = stringResource(R.string.edit_profile_remove_dependent),
        tint = EmergencyRed, modifier = Modifier.size(18.dp)
      )
    }
    Text(
      text = "$count", fontSize = 15.sp, fontWeight = FontWeight.Bold,
      color = TacticalOnSurface,
      modifier = Modifier.testTag("register_dependents_count_value")
    )
    IconButton(
      onClick = onIncrease,
      modifier = Modifier.size(40.dp).clip(CircleShape)
        .testTag("register_dependents_increase_button")
    ) {
      Icon(
        imageVector = Icons.Default.Add,
        contentDescription = stringResource(R.string.edit_profile_add_dependent),
        tint = NeonEmerald, modifier = Modifier.size(18.dp)
      )
    }
  }
}

/** Blood-group chip: selection shown by fill, border AND a check icon. */
@Composable
private fun RegisterBloodChip(
  label: String,
  selected: Boolean,
  testTag: String,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier.clip(RoundedCornerShape(10.dp))
      .background(if (selected) EmergencyRedContainer else ObsidianContainerLow)
      .border(
        width = 1.dp,
        color = if (selected) EmergencyRed else TacticalOutlineVariant,
        shape = RoundedCornerShape(10.dp)
      )
      .clickable(onClick = onClick).heightIn(min = 40.dp)
      .padding(horizontal = 12.dp, vertical = 8.dp).testTag(testTag),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    if (selected) {
      Icon(
        imageVector = Icons.Default.Check, contentDescription = null,
        tint = EmergencyRed, modifier = Modifier.size(13.dp)
      )
    }
    Text(
      text = label, fontSize = 12.sp, fontWeight = FontWeight.Bold,
      color = if (selected) EmergencyRed else TacticalOnSurface
    )
  }
}

/** Checkable requirement row (vulnerable member / medical support). */
@Composable
private fun RegisterCheckRow(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  title: String,
  subtitle: String,
  testTag: String
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
      .clip(RoundedCornerShape(10.dp)).background(ObsidianContainerLow)
      .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
      .clickable { onCheckedChange(!checked) }.padding(horizontal = 4.dp)
      .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Checkbox(
      checked = checked, onCheckedChange = onCheckedChange,
      colors = CheckboxDefaults.colors(
        checkedColor = TacticalCyan,
        uncheckedColor = TacticalOutlineVariant
      )
    )
    Column(modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)) {
      Text(
        text = title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        color = TacticalOnSurface
      )
      Text(
        text = subtitle, fontSize = 10.sp, lineHeight = 14.sp,
        color = TacticalOnSurfaceVariant
      )
    }
  }
}

/** Maps a registration failure to its message string resource. */
private fun registerAuthErrorMessageRes(result: AuthResult): Int? {
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
