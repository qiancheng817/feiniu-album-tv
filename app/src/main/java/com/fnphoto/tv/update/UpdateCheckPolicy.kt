package com.fnphoto.tv.update

object UpdateCheckPolicy {
    private const val ONE_DAY_MILLIS = 24L * 60L * 60L * 1000L

    fun shouldCheck(nowMillis: Long, lastCheckMillis: Long, force: Boolean): Boolean {
        if (force || lastCheckMillis <= 0L) {
            return true
        }
        return nowMillis - lastCheckMillis >= ONE_DAY_MILLIS
    }
}
