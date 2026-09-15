package com.wafflehq.talktome.server

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RateLimiterTest {

    @Test
    fun `allows requests up to capacity`() {
        val limiter = RateLimiter(capacity = 3, refillPerSecond = 0.0)

        assertTrue(limiter.tryConsume("device-a"))
        assertTrue(limiter.tryConsume("device-a"))
        assertTrue(limiter.tryConsume("device-a"))
    }

    @Test
    fun `blocks requests once capacity is exhausted`() {
        val limiter = RateLimiter(capacity = 1, refillPerSecond = 0.0)

        assertTrue(limiter.tryConsume("device-a"))
        assertFalse(limiter.tryConsume("device-a"))
    }

    @Test
    fun `keys are tracked independently`() {
        val limiter = RateLimiter(capacity = 1, refillPerSecond = 0.0)

        assertTrue(limiter.tryConsume("device-a"))
        assertTrue(limiter.tryConsume("device-b"))
    }

    @Test
    fun `refills tokens over time`() {
        var now = 0L
        val limiter = RateLimiter(capacity = 1, refillPerSecond = 1.0, clock = { now })

        assertTrue(limiter.tryConsume("device-a"))
        assertFalse(limiter.tryConsume("device-a"))

        now += 1_000L
        assertTrue(limiter.tryConsume("device-a"))
    }

    @Test
    fun `does not exceed capacity when refilling`() {
        var now = 0L
        val limiter = RateLimiter(capacity = 2, refillPerSecond = 1.0, clock = { now })

        now += 10_000L
        assertTrue(limiter.tryConsume("device-a"))
        assertTrue(limiter.tryConsume("device-a"))
        assertFalse(limiter.tryConsume("device-a"))
    }
}
