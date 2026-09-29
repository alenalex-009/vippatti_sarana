package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.auth.AuthApiResult
import com.example.data.auth.AuthError
import com.example.data.auth.AuthRepository
import com.example.data.auth.AuthResult
import com.example.data.auth.AuthValidator
import com.example.data.auth.BackendUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The complete authentication state machine the UI renders. */
data class AuthUiState(
  /** App-start session check in progress (shows the splash-ish busy state). */
  val isRestoring: Boolean = false,
  /** A login/signup network round-trip is in progress (button spinner). */
  val isSubmitting: Boolean = false,
  /** Rate-limit cooldown active (prevents spamming Supabase email endpoints). */
  val isRateLimited: Boolean = false,
  /** Remaining seconds in rate-limit cooldown. */
  val cooldownSeconds: Int = 0,
  /** Login form field error (client-side validation). */
  val loginFieldError: Int? = null,
  /** Signup form field error (client-side validation). */
  val signupFieldError: Int? = null,
  /** Server/network error for the login form (shown in the error panel). */
  val loginError: String? = null,
  /** Server/network error for the signup form (shown in the error panel). */
  val signupError: String? = null,
  /** The authenticated user's REAL account (from /me via the repository). */
  val user: BackendUser? = null,
  /** Email of an account awaiting email confirmation (signup without a
   * session). Non-null means the UI must show the 'check your inbox' state
   * and must NOT enter the app. */
  val pendingConfirmationEmail: String? = null
) {
  val isAuthenticated: Boolean get() = user != null
}

/**
 * ViewModel for the login/signup gate + session restoration + logout.
 *
 * Layering (per the architecture rule): Login/Signup UI -> AuthViewModel ->
 * AuthRepository -> AuthApiService -> FastAPI backend -> PostgreSQL.
 * No Composable talks to the network.
 *
 * App start: [restoreSession] validates the stored tokens against the
 * backend; a valid session opens the main app, otherwise Login is shown.
 */
class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

  private val _uiState = MutableStateFlow(AuthUiState(isRestoring = true))
  val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

  fun hasStoredSession(): Boolean = repository.hasStoredSession()

  /**
   * App-startup session check. [onResult] receives true when the app should
   * open straight into the main experience.
   */
  fun restoreSession(onResult: (Boolean) -> Unit) {
    if (!repository.hasStoredSession()) {
      _uiState.update { it.copy(isRestoring = false) }
      onResult(false)
      return
    }
    _uiState.update { it.copy(isRestoring = true) }
    viewModelScope.launch {
      val user = repository.restoreSession()
      _uiState.update {
        it.copy(isRestoring = false, user = user)
      }
      onResult(user != null)
    }
  }

  fun login(email: String, password: String, onSuccess: () -> Unit) {
    // Client-side validation first: instant field errors, no network spam.
    val cleanEmail = AuthValidator.normalizeEmail(email)
    _uiState.update {
      it.copy(
        isSubmitting = true,
        loginFieldError = when {
          cleanEmail == null -> com.example.R.string.login_error_invalid_email
          password.isBlank() -> com.example.R.string.login_error_empty_password
          else -> null
        },
        loginError = null
      )
    }
    if (_uiState.value.loginFieldError != null) {
      _uiState.update { it.copy(isSubmitting = false) }
      return
    }

    viewModelScope.launch {
      val result = repository.login(cleanEmail!!, password)
      if (result.ok) {
        _uiState.update {
          it.copy(isSubmitting = false, user = BackendUser(
            id = "",
            fullName = result.fullName,
            email = result.email,
            createdAt = "",
            updatedAt = ""
          ))
        }
        // Refresh the REAL identity from /me right away.
        refreshCurrentUser()
        onSuccess()
      } else {
        _uiState.update {
          it.copy(isSubmitting = false, loginError = result.errorMessage ?: genericError)
        }
        if (result.error == AuthError.RATE_LIMITED) {
          startCooldown(60)
        }
      }
    }
  }

  fun signup(fullName: String, email: String, password: String, confirmPassword: String, onSuccess: () -> Unit) {
    val cleanEmail = AuthValidator.normalizeEmail(email)
    _uiState.update {
      it.copy(
        isSubmitting = true,
        pendingConfirmationEmail = null,
        signupFieldError = when {
          fullName.isBlank() -> com.example.R.string.register_error_name
          cleanEmail == null -> com.example.R.string.login_error_invalid_email
          password.length < AuthValidator.MIN_PASSWORD_LENGTH -> com.example.R.string.register_error_password
          password != confirmPassword -> com.example.R.string.login_passwords_do_not_match
          else -> null
        },
        signupError = null
      )
    }
    if (_uiState.value.signupFieldError != null) {
      _uiState.update { it.copy(isSubmitting = false) }
      return
    }

    viewModelScope.launch {
      val result = repository.register(fullName, cleanEmail!!, password)
      if (result.ok) {
        _uiState.update {
          it.copy(isSubmitting = false, user = BackendUser(
            id = "",
            fullName = result.fullName,
            email = result.email,
            createdAt = "",
            updatedAt = ""
          ))
        }
        refreshCurrentUser()
        onSuccess()
      } else if (result.pendingConfirmation) {
        // NO fake session: show the inbox state, do not call onSuccess.
        _uiState.update {
          it.copy(
            isSubmitting = false,
            pendingConfirmationEmail = result.email,
            signupError = result.errorMessage
          )
        }
      } else {
        _uiState.update {
          it.copy(isSubmitting = false, signupError = result.errorMessage ?: genericError)
        }
        if (result.error == AuthError.RATE_LIMITED) {
          startCooldown(60)
        }
      }
    }
  }

  private var cooldownJob: kotlinx.coroutines.Job? = null

  private fun startCooldown(seconds: Int) {
    cooldownJob?.cancel()
    cooldownJob = viewModelScope.launch {
      for (remaining in seconds downTo 1) {
        _uiState.update { it.copy(isRateLimited = true, cooldownSeconds = remaining) }
        kotlinx.coroutines.delay(1000L)
      }
      _uiState.update { it.copy(isRateLimited = false, cooldownSeconds = 0) }
    }
  }

  /**
   * Logout: revoke server-side, clear the encrypted local session and drop
   * back to the Login screen with a clean state.
   */
  fun logout(onDone: () -> Unit) {
    viewModelScope.launch {
      repository.logout()
      _uiState.update { AuthUiState(isRestoring = false, user = null) }
      onDone()
    }
  }

  /** Fetch the authenticated user's real data from /me into the state. */
  fun refreshCurrentUser() {
    viewModelScope.launch {
      // Only the /me leg of restoration: read the real identity WITHOUT
      // triggering a refresh-token rotation (which would fail a failed-login
      // flow and overwrite a just-set error). On /me failure the cached user
      // from the successful login stays on screen.
      val access = repository.hasStoredSession()
      if (!access) return@launch
      when (val me = repository.meOrNull()) {
        is AuthApiResult.Success -> _uiState.update { it.copy(user = me.user) }
        else -> Unit
      }
    }
  }

  fun clearErrors() {
    _uiState.update {
      it.copy(
        loginError = null, signupError = null,
        loginFieldError = null, signupFieldError = null,
        pendingConfirmationEmail = null
      )
    }
  }

  private val genericError = "Something went wrong. Please try again."

  companion object {
    fun factory(repository: AuthRepository) = object : ViewModelProvider.Factory {
      @Suppress("UNCHECKED_CAST")
      override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AuthViewModel(repository) as T
    }
  }
}

/** Small helper to keep the update calls readable. */
private inline fun MutableStateFlow<AuthUiState>.update(
  transform: (AuthUiState) -> AuthUiState
) {
  value = transform(value)
}
