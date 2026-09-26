package com.boss.cameraguard

import android.app.Activity
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.NoCredentialException
import com.boss.cameraguard.data.DriveDiagnosticStore
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Thrown for a Google sign-in failure that already carries a short, user-safe message.
 * [cancelled] is true when the person dismissed the account picker themselves (not a real
 * error) so the UI can show a lighter "try again" message instead of an error banner.
 */
class GoogleSignInUiException(
    message: String,
    val cancelled: Boolean,
    cause: Throwable? = null
) : Exception(message, cause)

/** Single authentication gateway for CameraGuard. */
object CameraGuardAuthManager {
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    private const val TAG = "CameraGuardAuth"

    /**
     * A person cannot see a Credential Manager bottom sheet appear and deliberately dismiss it
     * faster than this. A "cancellation" reported quicker than this is treated as "no UI was ever
     * shown" (a provider/account availability problem in disguise), not a real user cancel - see
     * the long comment in [signInWithGoogle].
     */
    private const val FAST_CANCEL_THRESHOLD_MILLIS = 1000L

    data class GoogleSignInResult(
        val user: FirebaseUser,
        val linkedExistingGuest: Boolean
    )

    /**
     * Full diagnostic dump for a failed Google sign-in / Firebase link attempt. Never logs the
     * Google ID token, the Firebase credential, or any other secret - only classifications and
     * account state that help tell "no Google account on this device", "user cancelled",
     * "Credential Manager provider unavailable" and "real Firebase/config error" apart.
     */
    private fun logAuthFailure(stage: String, error: Throwable) {
        val user = auth.currentUser
        val credentialManagerType = (error as? GetCredentialException)?.type
        val firebaseErrorCode = (error as? com.google.firebase.auth.FirebaseAuthException)?.errorCode
        val apiStatusCode = (error as? ApiException)?.statusCode
        val message = "stage=$stage " +
            "exceptionClass=${error::class.java.name} " +
            "message=${error.message} " +
            "credentialManagerErrorType=$credentialManagerType " +
            "apiExceptionStatusCode=$apiStatusCode " +
            "firebaseErrorCode=$firebaseErrorCode " +
            "currentUid=${user?.uid} " +
            "currentUserIsAnonymous=${user?.isAnonymous} " +
            "providerIds=${user?.providerData?.map { it.providerId }}"
        Log.e(TAG, message, error)
        // Also written to the in-app drive-diagnostic log (Settings -> Export Drive
        // Diagnostics) so this is retrievable from the phone alone, with no PC/adb needed.
        DriveDiagnosticStore.log("AUTH_GOOGLE_FAIL", message)
    }

    /** Lightweight non-error trace of the sign-in flow, for the same on-device export. */
    private fun logAuthTrace(message: String) {
        DriveDiagnosticStore.log("AUTH_GOOGLE_TRACE", message)
    }

    fun currentUser(): FirebaseUser? = auth.currentUser

    /** Only use this for an explicit account switch after community presence is removed. */
    fun signOut() { auth.signOut() }

    suspend fun refreshUser(): FirebaseUser {
        val user = auth.currentUser ?: error("No account is signed in.")
        user.reload().awaitResult()
        return auth.currentUser ?: error("Your session expired. Please sign in again.")
    }

    suspend fun updateDisplayName(name: String): FirebaseUser {
        val clean = name.trim()
        require(clean.isNotEmpty() && clean.length <= 40) { "Display name must be 1–40 characters." }
        val user = auth.currentUser ?: error("No account is signed in.")
        require(!user.isAnonymous) { "Connect an account before editing your profile." }
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(clean).build()).awaitResult()
        // Keep the existing communityEnabled field intact; never replace /users/{uid}.
        val profile = FirebaseDatabase.getInstance().reference.child("users").child(user.uid)
        val snapshot = profile.get().awaitResult()
        if (snapshot.exists()) profile.child("displayName").setValue(clean).awaitResult()
        return refreshUser()
    }

    private fun isLinked(user: FirebaseUser, provider: String): Boolean =
        user.providerData.any { it.providerId == provider }

    private fun safeLinkFailure(provider: String, error: FirebaseAuthUserCollisionException): Nothing =
        throw IllegalStateException("This $provider is already attached to another account. Your current CameraGuard account was not merged or replaced.", error)


    fun ensureGuestSession(onSuccess: (FirebaseUser) -> Unit, onFailure: (Exception) -> Unit) {
        auth.currentUser?.let(onSuccess) ?: auth.signInAnonymously()
            .addOnSuccessListener { result ->
                result.user?.let(onSuccess) ?: onFailure(IllegalStateException("Firebase returned no user"))
            }
            .addOnFailureListener(onFailure)
    }

    /** For repository code that already runs off the main thread. */
    fun ensureAuthenticatedBlocking(): FirebaseUser {
        auth.currentUser?.let { return it }
        return Tasks.await(auth.signInAnonymously()).user
            ?: error("Firebase returned no user")
    }

    private const val LEGACY_GOOGLE_SIGN_IN_REQUEST = 9047

    private data class PendingLegacyGoogleSignIn(
        val continuation: kotlinx.coroutines.CancellableContinuation<GoogleSignInResult>,
        val before: FirebaseUser?
    )

    @Volatile
    private var pendingLegacyGoogleSignIn: PendingLegacyGoogleSignIn? = null

    private fun completeGoogleFirebaseSignIn(
        before: FirebaseUser?,
        firebaseCredential: com.google.firebase.auth.AuthCredential,
        onSuccess: (GoogleSignInResult) -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        val wasGuest = before?.isAnonymous == true
        if (before != null && (before.isAnonymous || !isLinked(before, "google.com"))) {
            before.linkWithCredential(firebaseCredential)
                .addOnSuccessListener { result ->
                    val user = result.user
                    if (user != null) onSuccess(GoogleSignInResult(user, wasGuest))
                    else onFailure(IllegalStateException("Firebase returned no linked user"))
                }
                .addOnFailureListener { error ->
                    if (error is FirebaseAuthUserCollisionException) {
                        // The selected Google account already owns a Firebase account. This is
                        // expected after reinstalling/testing CameraGuard: the fresh anonymous
                        // guest cannot be linked to an already-linked Google identity. Instead
                        // of trapping the rider on the Join screen, switch the Firebase session
                        // to that existing Google account. Local app settings remain on-device;
                        // Community membership/profile will be (re)created immediately after
                        // this call by CommunityHostScreen.
                        auth.signInWithCredential(firebaseCredential)
                            .addOnSuccessListener { signInResult ->
                                val user = signInResult.user
                                if (user != null) onSuccess(GoogleSignInResult(user, false))
                                else onFailure(IllegalStateException("Firebase returned no Google user after account switch"))
                            }
                            .addOnFailureListener { switchError ->
                                logAuthFailure("link_collision_switch", switchError)
                                onFailure(switchError)
                            }
                    } else {
                        logAuthFailure("link_with_credential", error)
                        onFailure(error)
                    }
                }
        } else if (before != null) {
            onSuccess(GoogleSignInResult(before, false))
        } else {
            auth.signInWithCredential(firebaseCredential)
                .addOnSuccessListener { result ->
                    val user = result.user
                    if (user != null) onSuccess(GoogleSignInResult(user, false))
                    else onFailure(IllegalStateException("Firebase returned no signed-in user"))
                }
                .addOnFailureListener { error ->
                    logAuthFailure("sign_in_with_credential", error)
                    onFailure(error)
                }
        }
    }

    private suspend fun legacyGoogleSignIn(activity: Activity, serverClientId: String, before: FirebaseUser?): GoogleSignInResult =
        suspendCancellableCoroutine { continuation ->
            if (pendingLegacyGoogleSignIn != null) {
                continuation.resumeWithException(IllegalStateException("Google sign-in is already in progress."))
                return@suspendCancellableCoroutine
            }
            pendingLegacyGoogleSignIn = PendingLegacyGoogleSignIn(continuation, before)
            continuation.invokeOnCancellation {
                if (pendingLegacyGoogleSignIn?.continuation === continuation) pendingLegacyGoogleSignIn = null
            }

            val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestIdToken(serverClientId)
                .build()
            val client = GoogleSignIn.getClient(activity, options)
            logAuthTrace("legacy_google_sign_in launching picker")
            // NOTE: this used to call client.signOut() and wait for it to complete before
            // launching signInIntent(). signInIntent() already always shows the account
            // chooser on its own (it is not the silent one-tap path), so the extra signOut()
            // added nothing functionally - but it did add an async round-trip through Play
            // Services immediately before starting the picker activity. On some Play Services
            // builds that round-trip can leave the auth session in a state where the picker
            // that follows immediately returns RESULT_CANCELED with ApiException status 16
            // (CANCELED), which surfaced to riders as the unhelpful "[16] Account reauth
            // failed". Launching signInIntent() directly removes that race entirely while
            // still prompting the rider to choose an account every time.
            try {
                @Suppress("DEPRECATION")
                activity.startActivityForResult(client.signInIntent, LEGACY_GOOGLE_SIGN_IN_REQUEST)
            } catch (t: Throwable) {
                logAuthFailure("legacy_google_sign_in_launch", t)
                val pending = pendingLegacyGoogleSignIn
                pendingLegacyGoogleSignIn = null
                if (pending?.continuation?.isActive == true) pending.continuation.resumeWithException(t)
            }
        }

    /**
     * Called by MainActivity for the legacy Google Sign-In fallback. [resultCode] is the real
     * Android `Activity.RESULT_*` code from `onActivityResult` - this is the single most useful
     * diagnostic signal available here: `RESULT_CANCELED` means the OS/picker itself was
     * dismissed (a genuine cancel, whether by the rider or the picker having nothing to show),
     * while `RESULT_OK` means the picker activity finished normally (the rider selected
     * something) and whatever went wrong happened afterward, while turning that selection into a
     * token - i.e. a real bug, not a cancel, however the Play Services status code reads.
     */
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?): Boolean {
        if (requestCode != LEGACY_GOOGLE_SIGN_IN_REQUEST) return false
        val pending = pendingLegacyGoogleSignIn ?: return true
        pendingLegacyGoogleSignIn = null
        val resultCodeName = when (resultCode) {
            android.app.Activity.RESULT_OK -> "RESULT_OK"
            android.app.Activity.RESULT_CANCELED -> "RESULT_CANCELED"
            else -> "OTHER($resultCode)"
        }
        logAuthTrace("legacy_google_sign_in activity_result resultCode=$resultCodeName hasData=${data != null}")
        try {
            @Suppress("DEPRECATION")
            val account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
            val idToken = account.idToken ?: throw IllegalStateException("Google returned no ID token.")
            logAuthTrace("legacy_google_sign_in account resolved idTokenPresent=true, linking to Firebase")
            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            completeGoogleFirebaseSignIn(
                pending.before, firebaseCredential,
                onSuccess = { result -> if (pending.continuation.isActive) pending.continuation.resume(result) },
                onFailure = { error -> if (pending.continuation.isActive) pending.continuation.resumeWithException(error) }
            )
        } catch (t: Throwable) {
            logAuthFailure("legacy_google_sign_in_result (androidResultCode=$resultCodeName)", t)
            val isRealCancel = resultCode == android.app.Activity.RESULT_CANCELED
            val friendly: Throwable = if (t is ApiException &&
                t.statusCode == com.google.android.gms.common.api.CommonStatusCodes.CANCELED &&
                isRealCancel
            ) {
                GoogleSignInUiException("Sign-in was cancelled.", cancelled = true, cause = t)
            } else if (t is ApiException && t.statusCode == com.google.android.gms.common.api.CommonStatusCodes.CANCELED) {
                // The Android activity result said OK (a real account was picked) but Play
                // Services still reported CANCELED turning that pick into a token. This is not a
                // rider cancelling anything - see the doc comment above.
                GoogleSignInUiException(
                    "Google couldn't finish signing you in with that account. Please try again - " +
                        "if this keeps happening, check Settings > Export Drive Diagnostics for detail.",
                    cancelled = false,
                    cause = t
                )
            } else {
                t
            }
            if (pending.continuation.isActive) pending.continuation.resumeWithException(friendly)
        }
        return true
    }

    suspend fun signInWithGoogle(activity: Activity): GoogleSignInResult {
        val serverClientId = activity.getString(R.string.default_web_client_id)
        require(serverClientId.isNotBlank()) { "Google OAuth web client ID is missing" }
        val before = auth.currentUser
        val credentialManager = CredentialManager.create(activity)
        logAuthTrace(
            "signInWithGoogle start currentUid=${before?.uid} isAnonymous=${before?.isAnonymous} " +
                "sdkInt=${android.os.Build.VERSION.SDK_INT} manufacturer=${android.os.Build.MANUFACTURER} model=${android.os.Build.MODEL}"
        )

        suspend fun requestGoogleCredential(useExplicitButtonOption: Boolean): androidx.credentials.Credential {
            val option = if (useExplicitButtonOption) {
                GetSignInWithGoogleOption.Builder(serverClientId).build()
            } else {
                GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(serverClientId)
                    .setAutoSelectEnabled(false)
                    .build()
            }
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            return credentialManager.getCredential(activity, request).credential
        }

        suspend fun toFirebaseResult(credential: androidx.credentials.Credential): GoogleSignInResult {
            require(credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                "Unexpected credential type: ${credential.type}"
            }
            val googleToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            val firebaseCredential = GoogleAuthProvider.getCredential(googleToken, null)
            return suspendCancellableCoroutine { continuation ->
                completeGoogleFirebaseSignIn(
                    before, firebaseCredential,
                    onSuccess = { if (continuation.isActive) continuation.resume(it) },
                    onFailure = { if (continuation.isActive) continuation.resumeWithException(it) }
                )
            }
        }

        // A real cancellation means the rider actually saw the account picker/bottom sheet and
        // dismissed it - that is not a "no working sign-in path" situation, so it must not
        // trigger the legacy fallback (which would just pop a second, unrelated picker right
        // after they backed out of the first).
        //
        // However, on some devices/Play-services builds Credential Manager reports
        // GetCredentialCancellationException even when NO UI was ever shown - e.g. when it has
        // zero eligible providers/accounts to display, some OEM builds "cancel" instead of
        // throwing NoCredentialException. A person cannot physically see a bottom sheet appear
        // and dismiss it in a few hundred milliseconds, so elapsed time is used to tell the two
        // apart: a "cancellation" that came back faster than a human could have acted on it is
        // treated the same as "this path did not work" (falls through to the next attempt /
        // legacy fallback) instead of being taken at face value and stopping the flow dead, which
        // is what produced a rider getting stuck on "Sign-in was cancelled" without ever seeing a
        // picker.
        //
        // Every other Credential Manager failure (no provider/account available, the Credential
        // Manager provider itself misbehaving, an interrupted request, ...) is treated as "this
        // path did not work" and falls back to the legacy Google Sign-In API, which is kept as a
        // real fallback for every Android version - including 16 - because Credential Manager's
        // Play Services provider is not guaranteed to be present/working on every device, while
        // legacy GoogleSignIn's account-chooser flow does not depend on that provider at all.
        fun wasGenuineUserCancellation(elapsedMillis: Long) = elapsedMillis >= FAST_CANCEL_THRESHOLD_MILLIS

        logAuthTrace("credential_manager_button_option requesting")
        val firstAttemptStart = android.os.SystemClock.elapsedRealtime()
        try {
            val result = toFirebaseResult(requestGoogleCredential(useExplicitButtonOption = true))
            logAuthTrace("credential_manager_button_option succeeded uid=${result.user.uid}")
            return result
        } catch (first: GetCredentialException) {
            val firstElapsed = android.os.SystemClock.elapsedRealtime() - firstAttemptStart
            logAuthFailure("credential_manager_button_option (elapsedMs=$firstElapsed)", first)
            if (first is GetCredentialCancellationException && wasGenuineUserCancellation(firstElapsed)) {
                throw GoogleSignInUiException("Sign-in was cancelled.", cancelled = true, cause = first)
            }

            // Recent Play-services builds can leave the Credential Manager provider in a stale
            // re-auth state. Clear provider session state and retry with the general Google ID
            // option, explicitly allowing any account on the device.
            runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
            logAuthTrace("credential_manager_id_option requesting")
            val secondAttemptStart = android.os.SystemClock.elapsedRealtime()
            try {
                val result = toFirebaseResult(requestGoogleCredential(useExplicitButtonOption = false))
                logAuthTrace("credential_manager_id_option succeeded uid=${result.user.uid}")
                return result
            } catch (second: GetCredentialException) {
                val secondElapsed = android.os.SystemClock.elapsedRealtime() - secondAttemptStart
                logAuthFailure("credential_manager_id_option (elapsedMs=$secondElapsed)", second)
                if (second is GetCredentialCancellationException && wasGenuineUserCancellation(secondElapsed)) {
                    throw GoogleSignInUiException("Sign-in was cancelled.", cancelled = true, cause = second)
                }

                val shouldFallBackToLegacy = second is NoCredentialException ||
                    second is GetCredentialProviderConfigurationException ||
                    second is GetCredentialUnknownException ||
                    second is GetCredentialInterruptedException ||
                    second is GetCredentialCancellationException
                if (shouldFallBackToLegacy) {
                    try {
                        val result = legacyGoogleSignIn(activity, serverClientId, before)
                        logAuthTrace("legacy_google_sign_in succeeded uid=${result.user.uid}")
                        return result
                    } catch (legacy: Exception) {
                        if (legacy is GoogleSignInUiException) throw legacy
                        logAuthFailure("legacy_google_sign_in_fallback", legacy)
                        if (legacy is ApiException && legacy.statusCode == com.google.android.gms.common.api.CommonStatusCodes.DEVELOPER_ERROR) {
                            throw GoogleSignInUiException(
                                "Google sign-in isn't configured correctly for this app build (DEVELOPER_ERROR / status 10). " +
                                    "This does not necessarily mean the SHA fingerprint is wrong - check Logcat for the full detail.",
                                cancelled = false,
                                cause = legacy
                            )
                        }
                        throw GoogleSignInUiException(
                            legacy.message?.takeIf { it.isNotBlank() } ?: "Google sign-in failed. Please try again.",
                            cancelled = false,
                            cause = legacy
                        )
                    }
                }

                val detail = second.message?.takeIf { it.isNotBlank() }
                    ?: first.message?.takeIf { it.isNotBlank() }
                    ?: "Google account selection is unavailable on this device."
                throw GoogleSignInUiException(detail, cancelled = false, cause = second)
            }
        }
    }


    data class EmailAuthResult(
        val user: FirebaseUser,
        val linkedExistingGuest: Boolean,
        val verificationEmailSent: Boolean
    )

    suspend fun createOrLinkEmailAccount(email: String, password: String): EmailAuthResult {
        val normalizedEmail = email.trim().lowercase()
        require(normalizedEmail.isNotBlank()) { "Enter your email address." }
        require(password.length >= 6) { "Password must be at least 6 characters." }
        val before = auth.currentUser
        val credential = EmailAuthProvider.getCredential(normalizedEmail, password)
        val result = if (before != null) {
            require(!isLinked(before, "password")) { "An email/password method is already connected to this account." }
            try { before.linkWithCredential(credential).awaitResult() }
            catch (collision: FirebaseAuthUserCollisionException) { safeLinkFailure("email address", collision) }
        } else auth.createUserWithEmailAndPassword(normalizedEmail, password).awaitResult()
        val user = result.user ?: error("Firebase returned no email user")
        user.sendEmailVerification().awaitResult()
        return EmailAuthResult(user, before?.isAnonymous == true, true)
    }

    suspend fun signInWithEmail(email: String, password: String): EmailAuthResult {
        val normalizedEmail = email.trim().lowercase()
        require(normalizedEmail.isNotBlank()) { "Enter your email address." }
        require(password.isNotBlank()) { "Enter your password." }
        require(auth.currentUser?.isAnonymous != false) {
            "Sign out safely before switching to a different account. To add an email, use Connect Email."
        }
        val result = auth.signInWithEmailAndPassword(normalizedEmail, password).awaitResult()
        return EmailAuthResult(result.user ?: error("Firebase returned no signed-in user"), false, false)
    }

    suspend fun sendPasswordReset(email: String) {
        val normalizedEmail = email.trim().lowercase()
        require(normalizedEmail.isNotBlank()) { "Enter your email address first." }
        auth.sendPasswordResetEmail(normalizedEmail).awaitResult()
    }

    suspend fun resendVerificationEmail() {
        val user = auth.currentUser ?: error("No CameraGuard account is signed in.")
        require(!user.isAnonymous) { "Connect a permanent account first." }
        if (!user.isEmailVerified) user.sendEmailVerification().awaitResult()
    }


    data class PhoneAuthResult(
        val user: FirebaseUser,
        val linkedExistingGuest: Boolean
    )

    interface PhoneVerificationListener {
        fun onCodeSent(verificationId: String, resendToken: PhoneAuthProvider.ForceResendingToken)
        fun onVerificationCompleted(result: PhoneAuthResult)
        fun onVerificationFailed(exception: Exception)
    }

    fun startPhoneVerification(
        activity: Activity,
        phoneNumber: String,
        resendToken: PhoneAuthProvider.ForceResendingToken? = null,
        listener: PhoneVerificationListener
    ) {
        val normalized = phoneNumber.trim().replace(" ", "").replace("-", "")
        require(normalized.startsWith("+") && normalized.length >= 8) {
            "Enter a valid phone number with country code, for example +393331234567."
        }
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // Android can sometimes verify the SMS automatically. Keep the same guest-linking rule.
                credentialToPhoneAccount(credential)
                    .addOnSuccessListener { listener.onVerificationCompleted(it) }
                    .addOnFailureListener { listener.onVerificationFailed(it as? Exception ?: Exception(it)) }
            }

            override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                listener.onVerificationFailed(e)
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                listener.onCodeSent(verificationId, token)
            }
        }
        val builder = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(normalized)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
        resendToken?.let { builder.setForceResendingToken(it) }
        PhoneAuthProvider.verifyPhoneNumber(builder.build())
    }

    suspend fun verifyPhoneCode(verificationId: String, smsCode: String): PhoneAuthResult {
        require(verificationId.isNotBlank()) { "Request an SMS code first." }
        require(smsCode.trim().length == 6 && smsCode.trim().all(Char::isDigit)) { "Enter the 6-digit SMS code." }
        return credentialToPhoneAccountSuspend(PhoneAuthProvider.getCredential(verificationId, smsCode.trim()))
    }

    private fun credentialToPhoneAccount(credential: PhoneAuthCredential): com.google.android.gms.tasks.Task<PhoneAuthResult> {
        val source = com.google.android.gms.tasks.TaskCompletionSource<PhoneAuthResult>()
        val before = auth.currentUser
        val task = if (before != null && !isLinked(before, "phone")) before.linkWithCredential(credential)
            else if (before != null) {
                source.setException(IllegalStateException("A mobile number is already connected to this account."))
                return source.task
            } else auth.signInWithCredential(credential)
        task.addOnSuccessListener { result ->
            val user = result.user
            if (user == null) source.setException(IllegalStateException("Firebase returned no phone user"))
            else source.setResult(PhoneAuthResult(user, before?.isAnonymous == true))
        }.addOnFailureListener { e ->
            if (e is FirebaseAuthUserCollisionException && before != null) {
                source.setException(IllegalStateException(
                    "This mobile number belongs to another account. Your current account was not merged or replaced.", e
                ))
            } else source.setException(e)
        }
        return source.task
    }

    private suspend fun credentialToPhoneAccountSuspend(credential: PhoneAuthCredential): PhoneAuthResult =
        credentialToPhoneAccount(credential).awaitResult()

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitResult(): T =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
            addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
            addOnCanceledListener { continuation.cancel() }
        }
}
