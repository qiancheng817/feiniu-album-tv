package com.fnphoto.tv.api

internal fun interface FnRequestSigner {
    fun sign(path: String, method: String, data: String?): String

    companion object {
        val Default = FnRequestSigner(FnAuthUtils::generateAuthX)
    }
}
