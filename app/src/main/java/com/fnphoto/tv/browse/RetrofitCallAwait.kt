package com.fnphoto.tv.browse

import kotlinx.coroutines.suspendCancellableCoroutine
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal suspend fun <T> Call<T>.awaitCancellableResponse(): Response<T> {
    return suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation {
            cancel()
        }
        enqueue(object : Callback<T> {
            override fun onResponse(call: Call<T>, response: Response<T>) {
                if (continuation.isActive) {
                    continuation.resume(response)
                }
            }

            override fun onFailure(call: Call<T>, throwable: Throwable) {
                if (continuation.isActive) {
                    continuation.resumeWithException(throwable)
                }
            }
        })
    }
}
