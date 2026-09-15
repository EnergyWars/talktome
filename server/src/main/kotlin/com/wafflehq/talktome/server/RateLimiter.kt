package com.wafflehq.talktome.server

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

class RateLimiter(
    private val capacity: Int = 30,
    private val refillPerSecond: Double = 1.0,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Bucket(var tokens: Double, var lastRefillMillis: Long)

    private val buckets = ConcurrentHashMap<String, Bucket>()

    @Synchronized
    fun tryConsume(key: String): Boolean {
        val now = clock()
        val bucket = buckets.getOrPut(key) { Bucket(capacity.toDouble(), now) }
        val elapsedSeconds = (now - bucket.lastRefillMillis) / 1000.0
        bucket.tokens = min(capacity.toDouble(), bucket.tokens + elapsedSeconds * refillPerSecond)
        bucket.lastRefillMillis = now
        if (bucket.tokens < 1.0) return false
        bucket.tokens -= 1.0
        return true
    }
}
