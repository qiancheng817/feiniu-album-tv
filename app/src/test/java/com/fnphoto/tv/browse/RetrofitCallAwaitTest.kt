package com.fnphoto.tv.browse

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okio.Timeout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RetrofitCallAwaitTest {
    @Test
    fun awaitCancellableResponse_cancelsUnderlyingCallAndPropagatesCancellation() = runBlocking {
        val call = DelayedCall<String>()
        val result = async {
            call.awaitCancellableResponse()
        }
        call.enqueued.await()

        result.cancel()

        assertFailsWith<CancellationException> { result.await() }
        assertTrue(call.isCanceled)
    }

    private class DelayedCall<T> : Call<T> {
        val enqueued = CompletableDeferred<Unit>()
        private var canceled = false

        override fun enqueue(callback: Callback<T>) {
            enqueued.complete(Unit)
        }

        override fun cancel() {
            canceled = true
        }

        override fun isCanceled(): Boolean = canceled

        override fun isExecuted(): Boolean = enqueued.isCompleted

        override fun clone(): Call<T> = DelayedCall()

        override fun request(): Request = Request.Builder()
            .url("https://example.invalid/p/api/v2/search/results")
            .build()

        override fun timeout(): Timeout = Timeout.NONE

        override fun execute(): Response<T> {
            throw IOException("execute must not be used")
        }
    }
}
