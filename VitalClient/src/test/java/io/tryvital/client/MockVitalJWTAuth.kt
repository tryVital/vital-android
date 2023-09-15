package io.tryvital.client

import io.tryvital.client.jwt.AbstractVitalJWTAuth

class MockVitalJWTAuth: AbstractVitalJWTAuth {
    override suspend fun <Result> withAccessToken(action: suspend (String) -> Result): Result {
        TODO("Not yet implemented")
    }

    override suspend fun refreshToken() {
        TODO("Not yet implemented")
    }
}
