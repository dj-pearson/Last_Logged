package com.pearsonmedia.lastlogged.service

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.pearsonmedia.lastlogged.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleSignInService @Inject constructor() {

    companion object {
        private const val TAG = "GoogleSignInService"
    }

    val isConfigured: Boolean
        get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    /**
     * Launches the Credential Manager Google ID flow. Returns the ID token on
     * success or throws [GoogleSignInException] on any failure. Must be called
     * from an Activity context so the Credential Manager can present UI.
     */
    suspend fun getGoogleIdToken(activityContext: Context): String {
        if (!isConfigured) {
            throw GoogleSignInException("Google Sign-In is not configured")
        }

        val option = GetGoogleIdOption.Builder()
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        val credentialManager = CredentialManager.create(activityContext)

        val response = try {
            credentialManager.getCredential(activityContext, request)
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Credential request failed: ${e.message}")
            throw GoogleSignInException(e.message ?: "Credential request failed", e)
        }

        val credential = response.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw GoogleSignInException("Unexpected credential type: ${credential.type}")
        }

        return try {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (e: GoogleIdTokenParsingException) {
            throw GoogleSignInException("Failed to parse Google ID token", e)
        }
    }
}

class GoogleSignInException(message: String, cause: Throwable? = null) : Exception(message, cause)
