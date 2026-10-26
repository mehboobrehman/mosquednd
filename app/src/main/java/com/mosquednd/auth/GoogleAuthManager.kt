package com.mosquednd.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * GoogleAuthManager handles Google Sign-In using AndroidX Credential Manager and
 * Google ID Token credential provider with the specified Web Client ID.
 */
object GoogleAuthManager {
    private const val TAG = "GoogleAuthManager"
    const val CLIENT_ID = "370852286562-pcl1top4ur9m2qaq2l29bal32i7dr1kd.apps.googleusercontent.com"

    private const val PREFS_NAME = "google_auth_prefs"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USER_PHOTO_URL = "user_photo_url"
    private const val KEY_ID_TOKEN = "id_token"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isSignedin(context: Context): Boolean {
        return !getPrefs(context).getString(KEY_USER_EMAIL, null).isNullOrEmpty()
    }

    fun getSignedInUser(context: Context): GoogleUser? {
        val prefs = getPrefs(context)
        val email = prefs.getString(KEY_USER_EMAIL, null) ?: return null
        val name = prefs.getString(KEY_USER_NAME, null)
        val photoUrl = prefs.getString(KEY_USER_PHOTO_URL, null)
        val idToken = prefs.getString(KEY_ID_TOKEN, null)
        return GoogleUser(email, name, photoUrl, idToken)
    }

    fun saveUserSession(context: Context, email: String, name: String?, photoUrl: String?, idToken: String?) {
        getPrefs(context).edit()
            .putString(KEY_USER_EMAIL, email)
            .putString(KEY_USER_NAME, name)
            .putString(KEY_USER_PHOTO_URL, photoUrl)
            .putString(KEY_ID_TOKEN, idToken)
            .apply()
    }

    fun signOut(context: Context) {
        getPrefs(context).edit().clear().apply()
        Log.i(TAG, "User signed out successfully")
    }

    /**
     * Initiates Google Sign-In via Credential Manager.
     * Returns GoogleUser on success, or throws an exception on failure/cancellation.
     */
    suspend fun signIn(context: Context): GoogleUser = withContext(Dispatchers.IO) {
        val credentialManager = CredentialManager.create(context)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        try {
            val result = credentialManager.getCredential(
                context = context,
                request = request
            )
            return@withContext handleCredentialResponse(context, result)
        } catch (e: GetCredentialException) {
            Log.e(TAG, "GetCredentialException during Google Sign-In", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected exception during Google Sign-In", e)
            throw e
        }
    }

    private fun handleCredentialResponse(context: Context, result: GetCredentialResponse): GoogleUser {
        val credential = result.credential
        when (credential) {
            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        val email = googleIdTokenCredential.id
                        val name = googleIdTokenCredential.displayName
                        val photoUrl = googleIdTokenCredential.profilePictureUri?.toString()
                        val idToken = googleIdTokenCredential.idToken

                        Log.i(TAG, "Google Sign-In successful for email: $email")
                        saveUserSession(context, email, name, photoUrl, idToken)
                        return GoogleUser(email, name, photoUrl, idToken)
                    } catch (e: GoogleIdTokenParsingException) {
                        Log.e(TAG, "Received an invalid google id token response", e)
                        throw IllegalArgumentException("Invalid Google ID token", e)
                    }
                } else {
                    throw IllegalStateException("Unexpected custom credential type: ${credential.type}")
                }
            }
            else -> {
                throw IllegalStateException("Unexpected credential type: ${credential::class.java.name}")
            }
        }
    }
}

data class GoogleUser(
    val email: String,
    val name: String?,
    val photoUrl: String?,
    val idToken: String?
)
