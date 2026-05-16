package com.example.mytodoapp.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.example.mytodoapp.data.TodoDatabase
import com.example.mytodoapp.sync.SyncManager
import com.example.mytodoapp.utils.PreferenceManager
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

enum class AuthState {
    LOADING,
    UNAUTHENTICATED,
    GUEST,
    AUTHENTICATED
}

class AuthManager(
    private val applicationContext: Context,
    private val syncManager: SyncManager,
    private val preferenceManager: PreferenceManager
) {
    private val auth = FirebaseAuth.getInstance()
    private val credentialManager = CredentialManager.create(applicationContext)

    private val _authState = MutableStateFlow(AuthState.LOADING)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _currentUser = MutableStateFlow(auth.currentUser)
    val currentUserFlow: StateFlow<com.google.firebase.auth.FirebaseUser?> = _currentUser.asStateFlow()

    init {
        // Initialize immediately
        updateAuthState(auth.currentUser)
        
        auth.addAuthStateListener { firebaseAuth ->
            updateAuthState(firebaseAuth.currentUser)
        }
    }

    private fun updateAuthState(user: com.google.firebase.auth.FirebaseUser?) {
        _authState.value = when {
            user == null -> AuthState.UNAUTHENTICATED
            user.isAnonymous -> AuthState.GUEST
            else -> AuthState.AUTHENTICATED
        }
        _currentUser.value = user
    }

    val currentUser get() = auth.currentUser

    suspend fun signInAnonymously(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (auth.currentUser == null) {
                auth.signInAnonymously().await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun signInWithGoogle(activityContext: Context): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Generate Nonce
            val rawNonce = UUID.randomUUID().toString()
            val bytes = rawNonce.toByteArray()
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(bytes)
            val hashedNonce = digest.fold("") { str, it -> str + "%02x".format(it) }

            // 2. Get Web Client ID from google-services.json
            val webClientIdId = applicationContext.resources.getIdentifier("default_web_client_id", "string", applicationContext.packageName)
            if (webClientIdId == 0) throw Exception("Web Client ID not found. Ensure google-services.json is configured.")
            val webClientId = applicationContext.getString(webClientIdId)
            
            // 3. Build Google ID Option
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setNonce(hashedNonce)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            // 4. Request Credential
            val result = credentialManager.getCredential(context = activityContext, request = request)
            val credential = result.credential

            if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                throw Exception("Unexpected credential type")
            }

            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val authCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)

            val currentUser = auth.currentUser

            // 5. Link or Sign In
            if (currentUser != null && currentUser.isAnonymous) {
                // We securely upgrade the anonymous account to a Google account
                try {
                    currentUser.linkWithCredential(authCredential).await()
                } catch (e: Exception) {
                    // If linking fails (e.g., Google account already exists), fallback to signing in
                    auth.signInWithCredential(authCredential).await()
                    // Clean up the orphaned anonymous account
                    try { currentUser.delete().await() } catch (ignored: Exception) {}
                    
                    // Force re-migration so that local guest data is pushed to the newly signed-in Google account
                    syncManager.migrateLocalDataToCloud()
                }
            } else {
                auth.signInWithCredential(authCredential).await()
            }
            
            // Signal that we should prioritize remote settings from this Google account
            preferenceManager.setForceRemoteSettings(true)

            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            syncManager.stopRealtimeSync()

            // Clear Room Database completely to prevent the next user from seeing this user's data
            val db = TodoDatabase.getDatabase(applicationContext)
            db.todoDao().deleteAllGroups()
            db.todoDao().deleteAllTasks()

            // Clear Preferences (Theme, deviceId, etc)
            preferenceManager.clearAll()

            // Clear Google Credentials
            credentialManager.clearCredentialState(ClearCredentialStateRequest())

            // Sign out of Firebase
            auth.signOut()

            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
