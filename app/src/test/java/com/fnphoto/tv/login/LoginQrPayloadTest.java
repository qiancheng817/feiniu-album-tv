package com.fnphoto.tv.login;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.Assert.assertEquals;

public class LoginQrPayloadTest {
    @Test
    public void buildUri_matchesOfficialScanLoginFormat() {
        assertEquals(
            "fn://com.trim.tv/trim.media-center?platform=AndroidTV&osver=36&clientName=飞牛TV&code=18da853cf2c34bfc8664d975794ec943&event=scanLogin&deviceName=客厅电视",
            LoginQrPayload.buildUri("18da853cf2c34bfc8664d975794ec943", 36, "客厅电视")
        );
    }

    @Test
    public void buildEncodedPayload_isBase64OfOfficialUri() {
        String payload = LoginQrPayload.buildEncodedPayload("18da853cf2c34bfc8664d975794ec943", 36, "客厅电视");

        assertEquals(
            "fn://com.trim.tv/trim.media-center?platform=AndroidTV&osver=36&clientName=飞牛TV&code=18da853cf2c34bfc8664d975794ec943&event=scanLogin&deviceName=客厅电视",
            new String(Base64.getDecoder().decode(payload), StandardCharsets.UTF_8)
        );
    }
}
