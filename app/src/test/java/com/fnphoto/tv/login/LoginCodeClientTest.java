package com.fnphoto.tv.login;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LoginCodeClientTest {
    @Test
    public void statusPath_usesOfficialLoginCodePath() {
        assertEquals(
                "/v/api/v1/logincode/df4608d7ebc64698a40b417c4e21f389",
                LoginCodeClient.statusPath("df4608d7ebc64698a40b417c4e21f389")
        );
    }
}
