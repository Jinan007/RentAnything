package com.clippost.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** What the Connect card on screen shows. */
sealed interface AuthState {
    data object Disconnected : AuthState
    data object Connecting : AuthState
    data object Connected : AuthState
    data class Failed(val message: String) : AuthState
}

/** The answer from Google when we ask for permission to upload. */
sealed interface AuthResult {
    /** We already have permission: here is a fresh access token (valid about 1 hour). */
    data class Token(val value: String) : AuthResult

    /** The user has to approve first: launch this consent screen. */
    data class NeedsConsent(val pendingIntent: PendingIntent) : AuthResult

    data class Failed(val message: String) : AuthResult
}

/**
 * Talks to Google's Authorization API (play-services-auth).
 * No password and no client secret: Google identifies the app by its package name and signing SHA-1,
 * which you register once as an "Android" OAuth client in Google Cloud.
 */
object YouTubeAuth {

    private const val UPLOAD_SCOPE = "https://www.googleapis.com/auth/youtube.upload"

    /** Lets ClipPost post a comment on your own video (step 9b). */
    private const val COMMENT_SCOPE = "https://www.googleapis.com/auth/youtube.force-ssl"

    const val CANCELLED_MESSAGE =
        "Sign-in did not finish. If Google showed \"Access blocked\" or an error, add your Gmail as a " +
            "Test user in Google Cloud (Google Auth Platform > Audience), then tap Connect again."

    private fun request(): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(UPLOAD_SCOPE), Scope(COMMENT_SCOPE)))
            .build()

    /**
     * Asks Google for an access token.
     * If the user already approved the app, this works silently (no screen) and returns a fresh token,
     * so call it again before every upload instead of storing the token.
     */
    suspend fun authorize(context: Context): AuthResult = suspendCancellableCoroutine { cont ->
        Identity.getAuthorizationClient(context.applicationContext)
            .authorize(request())
            .addOnSuccessListener { result ->
                val pending = result.pendingIntent
                val token = result.accessToken
                when {
                    result.hasResolution() && pending != null -> cont.resume(AuthResult.NeedsConsent(pending))
                    token != null -> cont.resume(AuthResult.Token(token))
                    else -> cont.resume(AuthResult.Failed("Google did not return an access token."))
                }
            }
            .addOnFailureListener { e -> cont.resume(AuthResult.Failed(explain(e))) }
    }

    /** Reads the answer after the user finished the consent screen. */
    fun fromConsentResult(context: Context, data: Intent?): AuthResult {
        if (data == null) return AuthResult.Failed(CANCELLED_MESSAGE)
        return try {
            val result = Identity.getAuthorizationClient(context.applicationContext)
                .getAuthorizationResultFromIntent(data)
            result.accessToken?.let { AuthResult.Token(it) }
                ?: AuthResult.Failed("Google did not return an access token.")
        } catch (e: ApiException) {
            AuthResult.Failed(explain(e))
        }
    }

    private fun explain(e: Exception): String {
        if (e is ApiException) {
            return when (e.statusCode) {
                CommonStatusCodes.DEVELOPER_ERROR ->
                    "Google rejected this app (error 10). In Google Cloud, check that the Android OAuth client " +
                        "uses package com.clippost.app and the exact SHA-1 of this APK. " +
                        "New clients can take a few minutes to start working."

                CommonStatusCodes.NETWORK_ERROR -> "No internet connection. Check your network and try again."
                else -> "Google sign-in failed (code ${e.statusCode}). ${e.message.orEmpty()}".trim()
            }
        }
        return e.message ?: "Google sign-in failed."
    }
}
