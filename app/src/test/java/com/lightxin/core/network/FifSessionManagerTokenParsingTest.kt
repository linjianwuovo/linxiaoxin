package com.linxin.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FifSessionManagerTokenParsingTest {

    @Test
    fun `extract memberId from fif jwt token`() {
        val token = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJyZWFsTmFtZSI6IuekuuS-i-WQjOWtpiIsInNjaG9vbElkIjoiMjgxMTAwMDIyNjAwMDAwMDAwMSIsImlzcyI6ImZpZmFjIiwiaWQiOiIzNDAyMDMwMDAwMDAxMDAwMDAxIiwiZXhwIjoxNzc3NDYyNDQyLCJ1c2VybmFtZSI6ImFpaXRleGFtcGxlMDAwMCIsIm1lbWJlcklkIjoiYTFiMmMzZDRlNWY2MDcxODI5M2E0YjVjNmQ3ZThmOTAifQ.FAKE_SIGNATURE_NOT_A_REAL_TOKEN"

        assertEquals(
            "a1b2c3d4e5f60718293a4b5c6d7e8f90",
            extractMemberUserIdFromFifToken(token),
        )
    }

    @Test
    fun `return null when token payload has no memberId`() {
        val token = "header.eyJ1c2VybmFtZSI6ImFpaXQifQ.signature"

        assertNull(extractMemberUserIdFromFifToken(token))
    }
}
