package com.example.data.auth

import com.example.data.model.UserProfile

/**
 * One Registration-form submission: the account credentials PLUS the citizen
 * profile the user typed on the same form.
 *
 * Both halves travel together on purpose, so account creation stays atomic:
 * [AuthRepository.register] stores the credential and, only on success, that
 * account's profile. The Profile tab can therefore never open on a
 * half-created user, and a rejected registration leaves no orphan citizen
 * record behind.
 *
 * The profile uses the ONE existing [UserProfile] schema — the same object the
 * Profile tab renders and the profile editor writes back.
 */
data class RegistrationRequest(
  val email: String,
  val password: String,
  val staySignedIn: Boolean,
  val profile: UserProfile
)
