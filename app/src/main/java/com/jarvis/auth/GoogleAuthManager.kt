package com.jarvis.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.jarvis.BuildConfig

data class GoogleAccount(
    val email: String,
    val displayName: String?,
    val photoUrl: String?,
)

class AuthException(message: String) : Exception(message)

/**
 * Google Sign-In through Credential Manager (the modern replacement for the
 * deprecated GoogleSignInClient).
 *
 * Requirements for a real sign-in:
 *   1. an OAuth "Web application" client id in gradle.properties
 *      (jarvisGoogleWebClientId), and
 *   2. the SHA-1 of the signing key registered on that client.
 *
 * If the id is missing the app says so plainly and offers limited mode instead
 * of pretending the user is verified.
 */
class GoogleAuthManager(private val context: Context) {

    fun isConfigured(): Boolean = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    /**
     * @param activityContext must be an Activity context: Credential Manager
     *        shows its own bottom sheet on top of it.
     */
    suspend fun signIn(activityContext: Context): GoogleAccount {
        if (!isConfigured()) {
            throw AuthException(
                "Google Sign-In is not configured in this build. Add jarvisGoogleWebClientId " +
                    "to gradle.properties, or continue in limited mode."
            )
        }

        val credentialManager = CredentialManager.create(context)
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        return try {
            val response = credentialManager.getCredential(
                context = activityContext,
                request = request,
            )
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleId = try {
                    GoogleIdTokenCredential.createFrom(credential.data)
                } catch (e: GoogleIdTokenParsingException) {
                    throw AuthException("Google returned an unreadable account token.")
                }
                GoogleAccount(
                    email = googleId.id,
                    displayName = googleId.displayName,
                    photoUrl = googleId.profilePictureUri?.toString(),
                )
            } else {
                throw AuthException("Unexpected credential type from Google.")
            }
        } catch (e: GetCredentialException) {
            throw AuthException(friendlyMessage(e))
        }
    }

    suspend fun signOut() {
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (t: Throwable) {
            // Not fatal: the local session is cleared regardless.
        }
    }

    private fun friendlyMessage(e: GetCredentialException): String = when (e) {
        is NoCredentialException ->
            "No Google account was found on this phone. Add one in Android Settings › Accounts, " +
                "or continue in limited mode."
        is GetCredentialCancellationException ->
            "Sign-in was cancelled."
        else ->
            "Google sign-in failed: ${e.message ?: e.type}"
    }
}
