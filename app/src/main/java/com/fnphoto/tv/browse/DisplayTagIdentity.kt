package com.fnphoto.tv.browse

import java.nio.charset.StandardCharsets

internal object DisplayTagIdentity {
    fun fromRaw(rawName: String): String {
        val encoded = rawName
            .toByteArray(StandardCharsets.UTF_8)
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return "tag:$encoded"
    }
}
