package com.mastercontrol.app.domain.util

import com.mastercontrol.app.domain.util.ValidationResult.Error
import com.mastercontrol.app.domain.util.ValidationResult.Success
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialValidationTest {

    @Test
    fun `api id accepts numeric input only`() {
        assertTrue(CredentialValidation.validateApiId("123456") is Success)
        assertTrue(CredentialValidation.validateApiId(" 123456 ") is Success)
        assertTrue(CredentialValidation.validateApiId("abc") is Error)
        assertTrue(CredentialValidation.validateApiId("12.5") is Error)
        assertTrue(CredentialValidation.validateApiId("") is Error)
        assertTrue(CredentialValidation.validateApiId("-5") is Error)
        assertTrue(CredentialValidation.validateApiId("0") is Error)
    }

    @Test
    fun `api hash requires hexadecimal 16-64 chars`() {
        assertTrue(CredentialValidation.validateApiHash("0123456789abcdef0123456789abcdef") is Success)
        assertTrue(CredentialValidation.validateApiHash("ABCDEF0123456789ABCDEF0123456789") is Success)
        assertTrue(CredentialValidation.validateApiHash("short") is Error)
        assertTrue(CredentialValidation.validateApiHash("zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz") is Error)
        assertTrue(CredentialValidation.validateApiHash("") is Error)
    }

    @Test
    fun `phone validation is international-format oriented`() {
        assertTrue(CredentialValidation.validatePhoneNumber("+15551234567") is Success)
        assertTrue(CredentialValidation.validatePhoneNumber("15551234567") is Success)
        assertTrue(CredentialValidation.validatePhoneNumber("123") is Error)
        assertTrue(CredentialValidation.validatePhoneNumber("+99999999999999999") is Error)
        assertTrue(CredentialValidation.validatePhoneNumber("") is Error)
    }

    @Test
    fun `code and pin validation`() {
        assertTrue(CredentialValidation.validateCode("12345") is Success)
        assertTrue(CredentialValidation.validateCode("12") is Error)
        assertTrue(CredentialValidation.validatePin("1234") is Success)
        assertTrue(CredentialValidation.validatePin("12ab") is Error)
        assertTrue(CredentialValidation.validatePin("123") is Error)
    }
}
