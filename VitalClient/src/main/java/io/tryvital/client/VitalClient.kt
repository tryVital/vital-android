package io.tryvital.client

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import io.tryvital.client.dependencies.Dependencies
import io.tryvital.client.jwt.VitalJWTAuth
import io.tryvital.client.jwt.VitalJWTAuthChangeReason
import io.tryvital.client.jwt.VitalSignInToken
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

const val VITAL_PERFS_FILE_NAME: String = "vital_health_connect_prefs"
const val VITAL_ENCRYPTED_PERFS_FILE_NAME: String = "safe_vital_health_connect_prefs"

@Suppress("unused")
class VitalClient internal constructor(context: Context) {
    val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(
            VITAL_PERFS_FILE_NAME, Context.MODE_PRIVATE
        )
    }

    val encryptedSharedPreferences: SharedPreferences by lazy {
        try {
            createEncryptedSharedPreferences(context)
        } catch (e: Exception) {
            VitalLogger.getOrCreate().logE(
                "Failed to decrypt shared preferences, creating new encrypted shared preferences", e
            )
            context.deleteSharedPreferences(VITAL_ENCRYPTED_PERFS_FILE_NAME)
            return@lazy createEncryptedSharedPreferences(context)
        }
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
        const val sdkVersion = "3.0.2"

        private var sharedInstance: VitalClient? = null

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
         * Sign-in the SDK with a User JWT — no API Key is needed.
         *
         * In this mode, your app requests a Vital Sign-In Token **through your backend service**, typically at the same time when
         * your user sign-ins with your backend service. This allows your backend service to keep the API Key as a private secret.
         *
         * The environment and region is inferred from the User JWT. You need not specify them explicitly
         */
        suspend fun signIn(context: Context, token: String) {
            val signInToken = VitalSignInToken.parse(token)
            val claims = signInToken.unverifiedClaims()
            val jwtAuth = VitalJWTAuth.getInstance(context)

            jwtAuth.signIn(signInToken)

            // Configure the SDK only if we have signed in successfully.
            val shared = getOrCreate(context)
            shared.setConfiguration(
                strategy = VitalClientAuthStrategy.JWT(claims.environment, claims.region),
            )
        }

        /**
         * Configure the SDK in the API Key mode.
         *
         * API Key mode will continue to be supported, but discouraged for production usage.
         * Prefer to use Vital Sign-In Token in your production apps, which allows your API Keys
         * to be kept fully as server-side secrets.
         */
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
        fun setUserId(context: Context, userId: String) {
            @Suppress("DEPRECATION")
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

fun createEncryptedSharedPreferences(context: Context) = EncryptedSharedPreferences.create(
    VITAL_ENCRYPTED_PERFS_FILE_NAME,
    MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
    context,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)
