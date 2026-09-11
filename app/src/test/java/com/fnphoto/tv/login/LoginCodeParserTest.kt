package com.fnphoto.tv.login

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoginCodeParserTest {
    @Test
    fun parseGenerateResponse_readsLoginCode() {
        val response = LoginCodeParser.parseGenerateResponse(
            """{"msg":"","code":0,"data":{"code":"ebbf44be44d943edae725f421da9265e"}}"""
        )

        assertEquals("ebbf44be44d943edae725f421da9265e", response.code)
    }

    @Test
    fun parseStatusResponse_treatsInvalidAsWaiting() {
        val status = LoginCodeParser.parseStatusResponse(
            """{"msg":"","code":0,"data":{"status":"Invalid"}}"""
        )

        assertFalse(status.isAuthenticated)
        assertEquals("Invalid", status.status)
    }

    @Test
    fun parseStatusResponse_readsSessionFieldsWhenScanSucceeds() {
        val status = LoginCodeParser.parseStatusResponse(
            """{"msg":"","code":0,"data":{"status":"Success","token":"token-1","secret":"secret-1","backId":"back-1"}}"""
        )

        assertTrue(status.isAuthenticated)
        assertEquals("token-1", status.token)
        assertEquals("secret-1", status.secret)
        assertEquals("back-1", status.backId)
    }

    @Test
    fun parseStatusResponse_acceptsOfficialQrSuccessWithTokenOnly() {
        val status = LoginCodeParser.parseStatusResponse(
            """{"msg":"","code":0,"data":{"status":"Success","token":"token-1"}}"""
        )

        assertTrue(status.isAuthenticated)
        assertEquals("token-1", status.token)
        assertEquals("", status.secret)
        assertEquals("", status.backId)
    }
}
