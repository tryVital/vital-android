package io.tryvital.client

import io.tryvital.client.jwt.JunctionTokenError
import io.tryvital.client.jwt.JunctionTokenErrorResponse
import io.tryvital.client.jwt.moshi
import org.junit.Test
import org.junit.Assert.*

class JWTAuthSerializationTest {
    @Test
    fun `Test JunctionTokenErrorResponse deserialization`() {
        val adapter = moshi.adapter(JunctionTokenErrorResponse::class.java)
        val response = adapter.fromJson("""
            {"detail":{"error_type":"invalid_token"}}
        """.trimIndent())

        assertEquals(
            response,
            JunctionTokenErrorResponse(
                detail = JunctionTokenError(errorType = JunctionTokenError.ErrorType.InvalidToken)
            )
        )
    }

    @Test
    fun `Test JunctionTokenErrorResponse deserialization_2`() {
        val adapter = moshi.adapter(JunctionTokenErrorResponse::class.java)
        val response = adapter.fromJson("""
            {"error": "invalid_request", "detail":{"error_type":"invalid_token"}}
        """.trimIndent())

        assertEquals(
            response,
            JunctionTokenErrorResponse(
                detail = JunctionTokenError(errorType = JunctionTokenError.ErrorType.InvalidToken)
            )
        )
    }

    @Test
    fun `Test JunctionTokenErrorResponse deserialization_3`() {
        val adapter = moshi.adapter(JunctionTokenErrorResponse::class.java)
        val response = adapter.fromJson("""
            {"error": "invalid_request", "detail":{"error_type":"invalid_token", "message": "doesn't matter"}}
        """.trimIndent())

        assertEquals(
            response,
            JunctionTokenErrorResponse(
                detail = JunctionTokenError(errorType = JunctionTokenError.ErrorType.InvalidToken)
            )
        )
    }
}
