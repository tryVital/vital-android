package io.tryvital.client.jwt

import android.content.Context
import android.content.SharedPreferences
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.tryvital.client.Environment
import io.tryvital.client.Region
import io.tryvital.client.createEncryptedSharedPreferences
import io.tryvital.client.utils.VitalLogger
import kotlinx.coroutines.flow.MutableStateFlow
import okio.ByteString.Companion.decodeBase64
import java.lang.IllegalArgumentException
import java.time.Instant
import java.util.Date

const val VITAL_JWT_AUTH_PREFERENCES: String = "vital_jwt_auth_prefs"
const val AUTH_RECORD_KEY = "auth_record"

private val moshi by lazy {
    Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
}

@Suppress("MemberVisibilityCanBePrivate")
class VitalJWTSignInError(val code: Code): Throwable("VitalJWTSignInError: $code") {
    @JvmInline value class Code(val rawValue: String) {
        companion object {
            val AlreadySignedIn = Code("alreadySignedIn")
            val InvalidSignInToken = Code("invalidSignInToken")
        }
    }
}

@Suppress("MemberVisibilityCanBePrivate")
class VitalJWTAuthError(val code: Code): Throwable("VitalJWTAuthError: $code") {
    @JvmInline value class Code(val rawValue: String) {
        companion object {
            /// There is no active SDK sign-in.
            val NotSignedIn = Code("notSignedIn")

            /// The user is no longer valid, and the SDK has been reset automatically.
            val InvalidUser = Code("invalidUser")

            /// The refresh token is invalid, and the user must be signed in again with a new Vital Sign-In Token.
            val NeedsReauthentication = Code("needsReauthentication")
        }
    }
}

data class VitalJWTAuthUserContext(val userId: String, val teamId: String)

internal class VitalJWTAuthNeedsRefresh: Throwable()

internal class VitalJWTAuth(
    val preferences: SharedPreferences
) {
    companion object {
        private var shared: VitalJWTAuth? = null

        fun getInstance(context: Context): VitalJWTAuth = synchronized(VitalJWTAuth) {
            val shared = this.shared
            if (shared != null) {
                return shared
            }

            val sharedPreferences = try {
                createEncryptedSharedPreferences(context)
            } catch (e: Exception) {
                VitalLogger.getOrCreate().logE(
                    "Failed to decrypt VitalJWTAuth preferences, re-creating it", e
                )
                context.deleteSharedPreferences(VITAL_JWT_AUTH_PREFERENCES)
                createEncryptedSharedPreferences(context)
            }

            this.shared = VitalJWTAuth(
                preferences = sharedPreferences
            )
            return this.shared!!
        }
    }

    val currentUserId: String? get() = getRecord()?.userId

    private val isRefreshing = MutableStateFlow(true)
    private var cachedRecord: VitalJWTAuthRecord? = null


    suspend fun signIn(signInToken: VitalSignInToken): Unit = TODO()

    suspend fun signOut(): Unit = TODO()

    fun userContext(): VitalJWTAuthUserContext {
        val record = getRecord() ?: throw VitalJWTAuthError(code = VitalJWTAuthError.Code.NotSignedIn)
        return VitalJWTAuthUserContext(userId = record.userId, teamId = record.teamId)
    }

    /// If the action encounters 401 Unauthorized, throw `VitalJWTAuthNeedsRefresh` to initiate
    /// the token refresh flow.
    suspend fun <Result> withAccessToken(action: suspend (String) -> Result): Result {
        /// When token refresh flow is ongoing, the ParkingLot is enabled and the call will suspend
        /// until the flow completes.
        /// Otherwise, the call will return immediately when the ParkingLot is disabled.

        TODO()
    }

    /// Start a token refresh flow, or wait for the started flow to complete.
    suspend fun refreshToken(): Unit = TODO()

    private fun getRecord(): VitalJWTAuthRecord? = synchronized(this) {
        val cachedRecord = this.cachedRecord
        if (cachedRecord != null) {
            return cachedRecord
        }

        // Backfill from SharedPreferences
        val recordJson = preferences.getString(AUTH_RECORD_KEY, null) ?: return null

        try {
            val adapter = moshi.adapter(VitalJWTAuthRecord::class.java)
            val record = adapter.fromJson(recordJson)!!
            this.cachedRecord = record
            return record
        } catch (e: Throwable) {
            VitalLogger.getOrCreate().logE("auto signout: failed to decode keychain auth record", e)
            setRecord(null)
            return null
        }
    }

    private fun setRecord(record: VitalJWTAuthRecord?): Unit = synchronized(this) {
        preferences
            .edit()
            .apply {
                if (record != null) {
                    val adapter = moshi.adapter(VitalJWTAuthRecord::class.java)
                    putString(AUTH_RECORD_KEY, adapter.toJson(record))
                } else {
                    remove(AUTH_RECORD_KEY)
                }
            }
            .apply()
        this.cachedRecord = record
    }
}

private data class VitalJWTAuthRecord(
    val environment: Environment,
    val userId: String,
    val teamId: String,
    val gcipTenantId: String,
    val publicApiKey: String,
    val accessToken: String,
    val refreshToken: String,
    val expiry: Date,
    val pendingReauthentication: Boolean = false,
) {
    fun isExpired(now: Date = Date.from(Instant.now())) = expiry.before(now)
}

private data class FirebaseTokenRefreshResponse(
    @Json(name = "expires_in")
    val expiresIn: String,
    @Json(name = "refresh_token")
    val refreshToken: String,
    @Json(name = "id_token")
    val idToken: String,
)

private data class FirebaseTokenRefreshErrorResponse(
    val error: FirebaseTokenRefreshError
)

private data class FirebaseTokenRefreshError(
    val message: String,
    val status: String,
) {
    val isInvalidUser: Boolean
        get() = arrayOf("USER_DISABLED", "USER_NOT_FOUND").contains(message)

    val needsReauthentication: Boolean
        get() = arrayOf("TOKEN_EXPIRED", "INVALID_REFRESH_TOKEN").contains(message)
}

private data class FirebaseTokenExchangeRequest(
    val returnSecureToken: Boolean = true,
    val token: String,
    val tenantId: String,
)

private data class FirebaseTokenExchangeResponse(
    val expiresIn: String,
    val refreshToken: String,
    val idToken: String,
)

internal data class VitalSignInToken(
    @Json(name = "public_key")
    val publicKey: String,

    @Json(name = "user_token")
    val userToken: String,
) {
    companion object {
        fun parse(token: String): VitalSignInToken {
            val unwrappedData = token.decodeBase64() ?: throw IllegalArgumentException("token is not valid base64 blob")
            return moshi.adapter(VitalSignInToken::class.java).fromJson(unwrappedData.utf8())!!
        }
    }

    fun unverifiedClaims(): VitalSignInTokenClaims {
        val components = userToken.split(".")
        if (components.size != 3) {
            throw IllegalArgumentException("malformed JWT [0]")
        }

        val rawHeader = padBase64(components[0]).decodeBase64()
        val rawClaims = padBase64(components[1]).decodeBase64()

        if (rawHeader == null || rawClaims == null) {
            throw IllegalArgumentException("malformed JWT [1]")
        }

        val headers = moshi.adapter(Map::class.java).fromJson(rawHeader.utf8())!!

        if (headers["alg"] != "RS256" || headers["typ"] != "JWT") {
            throw IllegalArgumentException("malformed JWT [2]")
        }

        return moshi.adapter(VitalSignInTokenClaims::class.java).fromJson(rawClaims.utf8())!!
    }
}

private fun padBase64(string: String): String {
    val pad = string.length % 4
    return if (pad == 0) {
        string
    } else {
        string + "=".repeat(4 - pad)
    }
}

internal data class VitalSignInTokenClaims(
    val userId: String,
    val teamId: String,
    val gcipTenantId: String,
    val environment: Environment,
    val region: Region,
) {
    companion object {
        fun parse(rawToken: String): VitalSignInTokenClaims {
            val rawClaims = moshi.adapter(RawClaims::class.java).fromJson(rawToken)!!

            // e.g. id-signer-{env}-{region}@vital-id-{env}-{region}.iam.gserviceaccount.com
            val pattern = Regex("^id-signer-([a-z]+)-([a-z]+)@vital-id-[a-z]+-[a-z]+.iam.gserviceaccount.com$")
            val match = pattern.matchEntire(rawClaims.issuer) ?: throw IllegalArgumentException("invalid issuer")

            val environment = Environment.valueOf(match.groupValues[1])
            val region = Region.valueOf(match.groupValues[2])

            return VitalSignInTokenClaims(
                userId = rawClaims.userId,
                teamId = rawClaims.claims.teamId,
                gcipTenantId = rawClaims.gcipTenantId,
                environment = environment,
                region = region,
            )
        }
    }

    private data class RawClaims(
        @Json(name = "iss")
        val issuer: String,
        @Json(name = "uid")
        val userId: String,
        @Json(name = "claims")
        val claims: RawInnerClaims,
        @Json(name = "tenant_id")
        val gcipTenantId: String,
    )

    private data class RawInnerClaims(
        @Json(name = "vital_team_id")
        val teamId: String
    )
}
