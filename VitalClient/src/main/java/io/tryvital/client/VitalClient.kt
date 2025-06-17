package io.tryvital.client

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import io.tryvital.client.dependencies.Dependencies
import io.tryvital.client.jwt.VitalJWTAuth
import io.tryvital.client.jwt.VitalJWTAuthChangeReason
import io.tryvital.client.jwt.VitalJWTSignInError
import io.tryvital.client.jwt.VitalSignInToken
import io.tryvital.client.jwt.VitalSignInTokenClaims
import io.tryvital.client.services.*
import io.tryvital.client.utils.VitalLogger
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

const val VITAL_PERFS_FILE_NAME: String = "vital_health_connect_prefs"
const val VITAL_CLIENT_LOCAL_STORAGE: String = "vital_client_local_storage"
const val VITAL_ENCRYPTED_PERFS_FILE_NAME: String = "safe_vital_health_connect_prefs"

@Suppress("unused")
class VitalClient internal constructor(context: Context) {
    val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(
            VITAL_PERFS_FILE_NAME, MODE_PRIVATE
        )
    }

    val encryptedSharedPreferences: SharedPreferences by lazy {
        createLocalStorage(context.applicationContext)
    }

    private val configurationReader by lazy {
        SharedPreferencesConfigurationReader(encryptedSharedPreferences)
    }

    private val dependencies: Dependencies by lazy {
        Dependencies(context, configurationReader)
    }

    val activityService by lazy {
        ActivityService.create(dependencies.retrofit)
    }

    val bodyService by lazy {
        BodyService.create(dependencies.retrofit)
    }

    val linkService by lazy {
        LinkService.create(dependencies.retrofit)
    }

    val profileService by lazy {
        ProfileService.create(dependencies.retrofit)
    }

    val sleepService by lazy {
        SleepService.create(dependencies.retrofit)
    }

    val userService by lazy {
        UserService.create(dependencies.retrofit)
    }

    val vitalsService by lazy {
        TimeSeriesService.create(dependencies.retrofit)
    }

    @VitalPrivateApi
    val vitalPrivateService by lazy {
        VitalPrivateService.create(dependencies.retrofit)
    }

    val vitalLogger get() = dependencies.vitalLogger
    private val jwtAuth get() = dependencies.jwtAuth

    /** Moments which can materially change VitalClient.Companion.status */
    private val statusChanged = MutableSharedFlow<Unit>(extraBufferCapacity = Int.MAX_VALUE)

    val childSDKShouldReset: SharedFlow<Unit> get() = _childSDKShouldReset
    private val _childSDKShouldReset = MutableSharedFlow<Unit>(extraBufferCapacity = Int.MAX_VALUE)

    suspend fun signOut() {
        sharedPreferences.edit().clear().apply()
        encryptedSharedPreferences.edit().clear().apply()
        jwtAuth.signOut()
        statusChanged.emit(Unit)
        _childSDKShouldReset.emit(Unit)
    }

    private fun setConfiguration(strategy: VitalClientAuthStrategy) {
        encryptedSharedPreferences.edit()
            .putString(
                VITAL_ENCRYPTED_AUTH_STRATEGY_KEY,
                configurationMoshi.adapter(VitalClientAuthStrategy::class.java).toJson(strategy)
            )
            .apply()
        statusChanged.tryEmit(Unit)
    }

    private fun setUserId(userId: String) {
        // No-op if the SDK has been configured into JWT mode.
        if (configurationReader.authStrategy is VitalClientAuthStrategy.JWT) {
            return
        }

        encryptedSharedPreferences.edit().apply {
            putString(VITAL_ENCRYPTED_USER_ID_KEY, userId)
            apply()
        }

        statusChanged.tryEmit(Unit)
    }

    companion object {
        const val sdkVersion = "4.2.0-beta.1"

        private var sharedInstance: VitalClient? = null
        private val identifyMutex = Mutex()

        /**
         * The current status of the Vital SDK.
         * Note that this returns an empty status if [VitalClient.getOrCreate] has never been called
         * to initialize the [VitalClient].
         */
        val status: Set<Status>
            get() {
                val shared = synchronized(VitalClient) { sharedInstance } ?: return setOf()
                val authStrategy = shared.configurationReader.authStrategy ?: return setOf()
                val status = mutableSetOf(Status.Configured)

                when (authStrategy) {
                    is VitalClientAuthStrategy.APIKey -> {
                        if (shared.configurationReader.userId != null) {
                            status.add(Status.SignedIn)
                            status.add(Status.UseApiKey)
                        }
                    }
                    is VitalClientAuthStrategy.JWT -> {
                        if (shared.jwtAuth.currentUserId != null) {
                            status.add(Status.SignedIn)
                            status.add(Status.UseSignInToken)

                            if (shared.jwtAuth.pendingReauthentication) {
                                status.add(Status.PendingReauthentication)
                            }
                        }
                    }
                }

                return status
            }

        fun statusChanged(context: Context): Flow<Unit> {
            val client = getOrCreate(context)
            return client.statusChanged
        }

        fun statuses(context: Context): Flow<Set<Status>> {
            return statusChanged(context)
                .onStart { emit(Unit) }
                .map { this.status }
        }

        /**
         * The current user ID of the Vital SDK.
         * Note that this returns null if [VitalClient.getOrCreate] has never been called
         * to initialize the [VitalClient].
         */
        val currentUserId: String?
            get() {
                val shared = synchronized(VitalClient) { sharedInstance } ?: return null
                val authStrategy = shared.configurationReader.authStrategy ?: return null
                return when (authStrategy) {
                    is VitalClientAuthStrategy.APIKey -> shared.configurationReader.userId
                    is VitalClientAuthStrategy.JWT -> shared.jwtAuth.currentUserId
                }
            }

        /**
         * The currently identified External User ID of the Mobile SDK.
         * This has meaning only if you have migrated to [identifyExternalUser].
         *
         * Note that this returns null if [VitalClient.getOrCreate] has never been called
         * to initialize the [VitalClient].
         */
        val identifiedExternalUser: String?
            get() {
                val shared = synchronized(VitalClient) { sharedInstance } ?: return null
                return shared.sharedPreferences.getString(VitalClientPrefKeys.externalUserId, null)
            }

        fun checkUserId(): String {
            return currentUserId ?: throw IllegalStateException(
                "The SDK does not have a signed-in user, or is not configured with an API Key for evaluation."
            )
        }

        fun getOrCreate(context: Context): VitalClient = synchronized(VitalClient) {
            val appContext = context.applicationContext
            var instance = sharedInstance
            if (instance == null) {
                instance = VitalClient(appContext)
                sharedInstance = instance
                bind(instance, VitalJWTAuth.getInstance(appContext), context)
            }
            return instance
        }


        /**
         * Identify your user to the Vital Mobile SDK with an external user identifier from your system.
         *
         * This is _external_ with respect to the SDK. From your perspective, this would be your _internal_ user identifier.
         *
         * If the identified external user is not what the SDK has last seen, or has last successfully signed-in:
         * 1. The SDK calls your supplied [authenticate] lambda.
         * 2. Your lambda obtains a Vital Sign-In Token **from your backend service** and returns it.
         * 3. The SDK performs the following actions:
         *
         * | SDK Signed-In User | The supplied Sign-In Token | Outcome |
         * | ------ | ------ | ------ |
         * | User A | User B | Sign out user A, then Sign in user B |
         * | User A | User A | No-op |
         * | None | User A | Sign In user A |
         *
         * Your [authenticate] lambda can throw CancellationError to abort the identify operation.
         *
         * You should identify at regular and significant moments in your app user lifecycle to ensure that it stays in sync with
         * the Vital Mobile SDK user state. For example:
         *
         * 1. Identify your user after you signed in a new user.
         * 2. Identify your user again after you have reloaded user state from persistent storage (e.g. [SharedPreferences]) post app launch.
         *
         * You can query the current identified user through [identifiedExternalUser].
         *
         * ## Notes on migrating from [signIn]
         *
         * [identifyExternalUser] does not perform any action or [VitalClient.signOut] when the Sign-In Token you supplied belongs
         * to the already signed-in Vital User — regardless of whether the sign-in happened prior to or after the introduction of
         * [identifyExternalUser].
         *
         * Because of this behaviour, you can migrate by simply replacing [signIn] with [identifyExternalUser].
         * There is no precaution in SDK State — e.g., the Health SDK data sync state — being unintentionally reset.
         */
        suspend fun identifyExternalUser(
            context: Context,
            externalUserId: String,
            authenticate: suspend (externalUserId: String) -> AuthenticateRequest
        ): Unit = identifyMutex.withLock {
            // ^ Only one `identify` is allowed to run at any given time.

            // Make sure client has been setup & automaticConfiguration has been ran once
            val client = getOrCreate(context)

            val currentExternalUserId =
                client.sharedPreferences.getString(VitalClientPrefKeys.externalUserId, null)

            val logger = VitalLogger.getOrCreate()
            logger.info { "Identify: input=<$externalUserId)> current=<$currentExternalUserId)>" }

            if (currentExternalUserId == externalUserId) {
                return
            }

            val resolvedUserId = when (val request = authenticate(externalUserId)) {
                is AuthenticateRequest.APIKey -> {
                    logger.info { "Identify: authenticating with API Key" }

                    if (Status.Configured !in status){
                        client.setConfiguration(
                            VitalClientAuthStrategy.APIKey(request.key, request.environment, request.region)
                        )
                    }

                    if (Status.SignedIn in status) {
                        val existingUserId = currentUserId
                        if (existingUserId != null && existingUserId.lowercase() != request.userId.lowercase()) {
                            logger.info { "signing out current user $existingUserId" }
                            client.signOut()
                            client.setUserId(request.userId)
                        } else {
                            logger.info { "Identify: identified same user_id; no-op" }
                        }
                    } else {
                        client.setUserId(request.userId)
                    }

                    request.userId
                }

                is AuthenticateRequest.SignInToken -> {
                    logger.info { "Identify: authenticating with Sign In Token" }

                    val claims = try {
                        privateSignIn(context, client, request.rawToken)

                    } catch (err: VitalJWTSignInError) {

                        if (err.code != VitalJWTSignInError.Code.AlreadySignedIn) {
                            throw err
                        }

                        logger.info { "Identify: signing out current user" }

                        // Sign-out the current user, then sign-in again.
                        client.signOut()

                        privateSignIn(context, client, request.rawToken)
                    }

                    claims.userId
                }
            }

            client.sharedPreferences.edit()
                .putString(VitalClientPrefKeys.externalUserId, externalUserId)
                .apply()

            logger.info { "Identify: identified external user $externalUserId; user_id = $resolvedUserId" }
        }

        /**
         * Sign-in the SDK with a User JWT — no API Key is needed.
         *
         * In this mode, your app requests a Vital Sign-In Token **through your backend service**, typically at the same time when
         * your user sign-ins with your backend service. This allows your backend service to keep the API Key as a private secret.
         *
         * The environment and region is inferred from the User JWT. You need not specify them explicitly
         */
        @Deprecated(message="Use `identifyExternalUser` with `AuthenticationRequest.SignInToken`.")
        suspend fun signIn(context: Context, token: String) {
            privateSignIn(context, getOrCreate(context), token)
        }

        private suspend fun privateSignIn(context: Context, client: VitalClient, token: String): VitalSignInTokenClaims {
            val signInToken = VitalSignInToken.parse(token)
            val claims = signInToken.unverifiedClaims()
            val jwtAuth = VitalJWTAuth.getInstance(context)

            jwtAuth.signIn(signInToken)

            // Configure the SDK only if we have signed in successfully.
            client.setConfiguration(
                strategy = VitalClientAuthStrategy.JWT(claims.environment, claims.region),
            )

            return claims
        }

        /**
         * Configure the SDK in the API Key mode.
         *
         * API Key mode will continue to be supported, but discouraged for production usage.
         * Prefer to use Vital Sign-In Token in your production apps, which allows your API Keys
         * to be kept fully as server-side secrets.
         */
        @Deprecated(message="Use `identifyExternalUser` with `AuthenticationRequest.APIKey`.")
        fun configure(context: Context, region: Region, environment: Environment, apiKey: String) {
            getOrCreate(context).setConfiguration(
                VitalClientAuthStrategy.APIKey(apiKey, environment, region)
            )
        }

        /**
         * Set the current User ID (only for SDK using the API Key mode).
         *
         * API Key mode will continue to be supported, but discouraged for production usage.
         * Prefer to use Vital Sign-In Token in your production apps, which allows your API Keys
         * to be kept fully as server-side secrets.
         */
        @Deprecated(message="Use `identifyExternalUser` with `AuthenticationRequest.APIKey`.")
        fun setUserId(context: Context, userId: String) {
            getOrCreate(context).setUserId(userId)
        }

        /**
         * Control plane API calls which can be made via the API Key without having to configure the SDK.
         *
         * ### Warning
         * If you use Vital Sign-In Token, the API Key should be a server-side secret, and these calls generally
         * should be done by your backend services after authenticating the app user.
         * These control plane methods are only intended for early prototyping in Vital Sandbox, and customers sticking to the
         * API Key mode.
         */
        fun controlPlane(context: Context, environment: Environment, region: Region, apiKey: String): ControlPlaneService {
            val dependencies = Dependencies(
                context,
                StaticConfiguration(VitalClientAuthStrategy.APIKey(apiKey, environment, region))
            )

            return ControlPlaneService.create(dependencies.retrofit)
        }

        suspend fun debugForceTokenRefresh(context: Context) {
            VitalJWTAuth.getInstance(context).refreshToken()
        }

        /**
         * Must be called exactly once after Core SDK is initialized.
         */
        @OptIn(DelicateCoroutinesApi::class)
        private fun bind(client: VitalClient, jwtAuth: VitalJWTAuth, context: Context) {
            client.resetSharedPreferencesOnReinstallation(context)

            jwtAuth.statusChanged
                .filter { it == VitalJWTAuthChangeReason.UserNoLongerValid }
                .onEach {
                    client.signOut()
                }
                .launchIn(GlobalScope)


            statuses(context)
                .onEach { statuses ->
                    val logger = VitalLogger.getOrCreate()
                    logger.info { "status: ${statuses.joinToString(separator = ",") { it.name }}" }
                }
                .launchIn(GlobalScope)
        }
    }

    enum class Status {
        /**
         * The SDK has been configured, either through [VitalClient.Companion.configure] for the first time,
         * or `VitalClient` has restored the last auto-saved configuration upon creation through
         * [VitalClient.Companion.getOrCreate].
         */
        Configured,
        /**
         * The SDK has an active sign-in.
         */
        SignedIn,
        /**
         * The active sign-in was done through an explicitly set target User ID, paired with a Vital API Key.
         * (through [VitalClient.Companion.setUserId])
         */
        UseApiKey,
        /**
         * The active sign-in is done through a Vital Sign-In Token via [VitalClient.Companion.signIn].
         */
        UseSignInToken,
        /**
         * A Vital Sign-In Token sign-in session that is currently on hold, requiring re-authentication using
         * a new Vital Sign-In Token issued for the same user.
         *
         * This generally should not happen, as Vital's identity broker guarantees only to revoke auth
         * refresh tokens when a user is explicitly deleted, disabled or have their tokens explicitly
         * revoked.
         */
        PendingReauthentication;
    }
}

@SuppressLint("ApplySharedPref")
internal fun createLocalStorage(context: Context): SharedPreferences = synchronized(VitalClient) {
    val preferences = context.getSharedPreferences(
        VITAL_CLIENT_LOCAL_STORAGE, MODE_PRIVATE
    )

    // If an EncryptedSharedPreferences exists (created by an earlier SDK version),
    // migrate it to the new SharedPreferences.
    val oldPreferences = context.getSharedPreferences(VITAL_ENCRYPTED_PERFS_FILE_NAME, MODE_PRIVATE)
    if (!oldPreferences.getString("__androidx_security_crypto_encrypted_prefs_key_keyset__", "").isNullOrBlank()) {
        val logger = VitalLogger.getOrCreate()
        logger.logI("EncryptedSharedPrefs migration: detected")
        try {
            val oldDecryptedPreferences = getDeprecatedEncryptedSharedPreferences(context)
            preferences.edit().apply {
                for ((key, value) in oldDecryptedPreferences.all) {
                    @Suppress("UNCHECKED_CAST")
                    when (value) {
                        is String -> putString(key, value)
                        is Boolean -> putBoolean(key, value)
                        is Long -> putLong(key, value)
                        is Int -> putInt(key, value)
                        is Float -> putFloat(key, value)
                        is Set<*> -> putStringSet(key, value as Set<String>)
                    }
                }
                commit()
            }
            logger.logI("EncryptedSharedPrefs migration: completed")

        } catch (e: Throwable) {
            VitalLogger.getOrCreate().logE("EncryptedSharedPrefs migration: failed", e)
        }
        context.deleteSharedPreferences(VITAL_ENCRYPTED_PERFS_FILE_NAME)
        logger.logI("EncryptedSharedPrefs migration: deleted old file")
    }

    return preferences
}

internal fun getDeprecatedEncryptedSharedPreferences(context: Context) = EncryptedSharedPreferences.create(
    VITAL_ENCRYPTED_PERFS_FILE_NAME,
    MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
    context,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)
