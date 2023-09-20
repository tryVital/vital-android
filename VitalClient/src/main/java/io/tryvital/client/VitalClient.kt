package io.tryvital.client

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import io.tryvital.client.dependencies.Dependencies
import io.tryvital.client.jwt.VitalJWTAuth
import io.tryvital.client.jwt.VitalSignInToken
import io.tryvital.client.services.*
import io.tryvital.client.utils.VitalLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            vitalLogger.logE(
                "Failed to decrypt shared preferences, creating new encrypted shared preferences", e
            )
            context.deleteSharedPreferences(VITAL_ENCRYPTED_PERFS_FILE_NAME)
            return@lazy createEncryptedSharedPreferences(context)
        }
    }

    private val configurationReader = SharedPreferencesConfigurationReader(encryptedSharedPreferences)

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

    val summaryService by lazy {
        SummaryService.create(dependencies.retrofit)
    }

    val vitalsService by lazy {
        VitalsService.create(dependencies.retrofit)
    }

    val vitalLogger get() = dependencies.vitalLogger
    private val jwtAuth get() = dependencies.jwtAuth

    fun cleanUp() {
        sharedPreferences.edit().clear().apply()
        encryptedSharedPreferences.edit().clear().apply()
        jwtAuth.signOut()
    }

    @Deprecated(
        message = "Use VitalClient.configure (API Key) or VitalClient.signIn (Vital Sign-In Token) instead.",
        replaceWith = ReplaceWith("VitalClient.configure(context, region, environment, apiKey)"),
    )
    fun configure(region: Region, environment: Environment, apiKey: String) {
        setConfiguration(
            VitalClientAuthStrategy.APIKey(apiKey, environment, region)
        )
    }

    private fun setConfiguration(strategy: VitalClientAuthStrategy) {
        encryptedSharedPreferences.edit()
            .putString(
                VITAL_ENCRYPTED_AUTH_STRATEGY_KEY,
                configurationMoshi.adapter(VitalClientAuthStrategy::class.java).toJson(strategy)
            )
            .apply()
    }

    @Deprecated(
        message = "Use VitalClient.setUserId instead.",
        replaceWith = ReplaceWith("VitalClient.setUserId(context, userId)"),
    )
    fun setUserId(userId: String) {
        // No-op if the SDK has been configured into JWT mode.
        if (configurationReader.authStrategy is VitalClientAuthStrategy.JWT) {
            return
        }

        encryptedSharedPreferences.edit().apply {
            putString(VITAL_ENCRYPTED_USER_ID_KEY, userId)
            apply()
        }
    }

    @Deprecated(
        message = "Use VitalClient.status instead.",
        replaceWith = ReplaceWith("VitalClient.Status.SignedIn in VitalClient.status"),
    )
    fun hasUserId(): Boolean {
        return encryptedSharedPreferences.getString(VITAL_ENCRYPTED_USER_ID_KEY, null) != null
    }

    fun checkUserId(): String {
        return currentUserId ?: throw IllegalStateException(
            "You need to call setUserId before you can read the health data"
        )
    }

    companion object {
        private var sharedInstance: VitalClient? = null
        private var reauthHandler: ReauthenticationHandler? = null
        private var reauthenticationMonitor: Job? = null

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
                            status.add(Status.UseApiKey)
                            status.add(Status.SignedIn)
                        }
                    }
                    is VitalClientAuthStrategy.JWT -> {
                        if (shared.jwtAuth.currentUserId != null) {
                            status.add(Status.UseSignInToken)
                            status.add(Status.SignedIn)
                        }
                    }
                }

                return status
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

        fun getOrCreate(context: Context): VitalClient = synchronized(VitalClient) {
            var instance = sharedInstance
            if (instance == null) {
                instance = VitalClient(context)
                sharedInstance = instance
            }
            instance = VitalClient(context)
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


        /**
         * Observe reauthentication requests, and respond to these requests by asynchronously fetching
         * a new Vital Sign-In Token through your backend service.
         *
         * There are two scenarios where a reauthentication request may arise:
         *
         * **Migration from API Key mode**:
         *
         * An existing user in API Key mode has launched your app for the first time, after the app was upgraded to a new release that
         * has adopted Vital Sign-In Token.
         *
         * After you setup `observeReauthenticationRequest`, the Vital SDK would automatically trigger it once to migrate the
         * said user from API Key mode to Vital Sign-In Token mode.
         *
         * **Refresh Token invalidation**:
         *
         * Typically, reauthentication request would not arise due to refresh token invalidation.
         *
         * Vital's identity broker guarantees that the underlying refresh token is not invalidated, unless the user is disabled or deleted, or
         * Vital explicitly revokes the refresh tokens (which we typically would not do so).
         *
         * However, Vital still recommends setting up `observeReauthenticationRequest`, so that
         * the SDK can recover in event of a necessitated token revocation (announced by Vital, or requested by you).
         *
         * ### Warning
         * The supplied `reauthHandler` is retained until the process is terminated, or until you explicitly clear it.
         *
         * ### Precondition
         * The SDK has been configured.
         */
        fun observeReauthenticationRequest(
            context: Context,
            handler: ReauthenticationHandler?
        ) {
            if (Status.Configured !in status) {
                throw VitalClientUnconfigured()
            }

            val appContext = context.applicationContext
            synchronized(VitalClient) {
                this.reauthHandler = handler

                when {
                    this.reauthenticationMonitor == null && handler != null -> {
                        // Start a reauth monitor
                        this.reauthenticationMonitor = CoroutineScope(Dispatchers.Default)
                            .launch { reauthenticationMonitor(appContext) }
                    }

                    this.reauthenticationMonitor != null && handler == null -> {
                        // Stop the reauth monitor.
                        this.reauthenticationMonitor!!.cancel()
                        this.reauthenticationMonitor = null
                    }

                    else -> {}
                }
            }
        }

        suspend fun debugForceTokenRefresh(context: Context) {
            VitalJWTAuth.getInstance(context).refreshToken()
        }

        private suspend fun reauthenticationMonitor(appContext: Context) {
            // Check if we are in API Key mode
            // Perform a one-off migration to Vital Sign-In Token if needed.
            val shared = getOrCreate(appContext)
            val jwtAuth = VitalJWTAuth.getInstance(appContext)

            suspend fun tryToReauthenticate(logContext: String) {
                val handler = synchronized(VitalClient) { reauthHandler }
                val userId = currentUserId

                if (handler != null && userId != null) {
                    try {
                        VitalLogger.getOrCreate().logI("reauth[$logContext] started")

                        val signInToken = handler.getSignInToken(userId)
                        this.signIn(appContext, signInToken)

                        withContext(Dispatchers.Main) {
                            handler.onReauthenticationSuccess()
                            VitalLogger.getOrCreate().logI("reauth[$logContext] completed")
                        }
                    } catch (e: Throwable) {
                        withContext(Dispatchers.Main) {
                            handler.onReauthenticationFailure(e)
                            VitalLogger.getOrCreate().logE("reauth[$logContext] failed", e)
                        }
                    }
                }
            }

            if (shared.configurationReader.authStrategy is VitalClientAuthStrategy.APIKey) {
                tryToReauthenticate(logContext = "api-key-migration")
            }

            if (jwtAuth.needsReauthentication) {
                tryToReauthenticate(logContext = "app-launch")
            }

            // Observe reauthentication requests from VitalJWTAuth.
            jwtAuth.reauthenticationRequest
                .filter { jwtAuth.needsReauthentication }
                .onEach { tryToReauthenticate(logContext = "on-demand") }
                .collect()
        }
    }

    enum class Status {
        Configured, SignedIn, UseApiKey, UseSignInToken;
    }

    interface ReauthenticationHandler {
        suspend fun getSignInToken(vitalUserId: String): String

        fun onReauthenticationSuccess()
        fun onReauthenticationFailure(error: Throwable)
    }
}

fun createEncryptedSharedPreferences(context: Context) = EncryptedSharedPreferences.create(
    VITAL_ENCRYPTED_PERFS_FILE_NAME,
    MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
    context,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)
