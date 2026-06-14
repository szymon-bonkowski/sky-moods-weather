package com.example.modernweather.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class TimedCacheTest {
    private val now = Instant.parse("2026-06-14T10:00:00Z")
    private val ttl = Duration.ofMinutes(15)
    private val clock = MutableClock(now)
    private val cache = TimedCache<String, String>(clock)

    @Test
    fun entryIsFreshUntilTtlExpires() {
        cache.put("warszawa:en:current", "sunny")

        clock.current = now.plus(ttl)
        assertTrue(cache.isFresh("warszawa:en:current", ttl))

        clock.current = now.plus(ttl).plusMillis(1)
        assertFalse(cache.isFresh("warszawa:en:current", ttl))
    }

    @Test
    fun nextRefreshInstantUsesEntryFetchTime() {
        cache.put("warszawa:en:current", "sunny")

        assertEquals(now.plus(ttl), cache.nextRefreshInstant("warszawa:en:current", ttl))
    }

    @Test
    fun staleEntryIsUsedWhenReloadFails() = runBlocking {
        cache.put("warszawa:en:current", "old")
        clock.current = now.plus(ttl).plusSeconds(1)

        val result = cache.getOrLoad("warszawa:en:current", ttl) {
            error("temporary network failure")
        }

        assertEquals("old", result)
    }

    @Test
    fun keyAndLanguageEntriesAreIsolated() {
        cache.put("warszawa:en:current", "english")
        cache.put("warszawa:pl:current", "polish")

        assertEquals("english", cache.getFresh("warszawa:en:current", ttl))
        assertEquals("polish", cache.getFresh("warszawa:pl:current", ttl))
        assertNull(cache.getFresh("krakow:en:current", ttl))
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNotConvertedToStaleFallback() = runBlocking<Unit> {
        cache.put("warszawa:en:current", "old")
        clock.current = now.plus(ttl).plusSeconds(1)

        cache.getOrLoad("warszawa:en:current", ttl) {
            throw CancellationException("cancelled")
        }
    }

    private class MutableClock(
        var current: Instant
    ) : Clock() {
        override fun instant(): Instant = current
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
    }
}
