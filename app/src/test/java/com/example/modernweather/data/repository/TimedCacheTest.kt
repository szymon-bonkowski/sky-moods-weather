package com.example.modernweather.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
        val failureAt = now.plus(ttl).plusSeconds(1)
        clock.current = failureAt

        val result = cache.getOrLoad("warszawa:en:current", ttl) {
            error("temporary network failure")
        }

        assertEquals("old", result)
        assertFalse(cache.isFresh("warszawa:en:current", ttl))
        assertEquals(
            failureAt.plus(Duration.ofMinutes(1)),
            cache.nextRefreshInstant("warszawa:en:current", ttl)
        )
    }

    @Test
    fun staleEntryInsideBackoffReturnsWithoutReloading() = runBlocking {
        cache.put("warszawa:en:current", "old")
        clock.current = now.plus(ttl).plusSeconds(1)
        cache.getOrLoad("warszawa:en:current", ttl) {
            error("temporary network failure")
        }

        var loaderCalls = 0
        clock.current = clock.current.plusSeconds(30)
        val result = cache.getOrLoad("warszawa:en:current", ttl) {
            loaderCalls++
            "new"
        }

        assertEquals("old", result)
        assertEquals(0, loaderCalls)
    }

    @Test
    fun repeatedFailuresUseExponentialBackoffCappedAtFifteenMinutes() = runBlocking {
        cache.put("warszawa:en:current", "old")
        clock.current = now.plus(ttl).plusSeconds(1)

        cache.getOrLoad("warszawa:en:current", ttl) { error("failure 1") }
        assertEquals(clock.current.plus(Duration.ofMinutes(1)), cache.nextRefreshInstant("warszawa:en:current", ttl))

        clock.current = cache.nextRefreshInstant("warszawa:en:current", ttl)!!.plusMillis(1)
        cache.getOrLoad("warszawa:en:current", ttl) { error("failure 2") }
        assertEquals(clock.current.plus(Duration.ofMinutes(2)), cache.nextRefreshInstant("warszawa:en:current", ttl))

        clock.current = cache.nextRefreshInstant("warszawa:en:current", ttl)!!.plusMillis(1)
        cache.getOrLoad("warszawa:en:current", ttl) { error("failure 3") }
        assertEquals(clock.current.plus(Duration.ofMinutes(4)), cache.nextRefreshInstant("warszawa:en:current", ttl))

        clock.current = cache.nextRefreshInstant("warszawa:en:current", ttl)!!.plusMillis(1)
        cache.getOrLoad("warszawa:en:current", ttl) { error("failure 4") }
        assertEquals(clock.current.plus(Duration.ofMinutes(8)), cache.nextRefreshInstant("warszawa:en:current", ttl))

        clock.current = cache.nextRefreshInstant("warszawa:en:current", ttl)!!.plusMillis(1)
        cache.getOrLoad("warszawa:en:current", ttl) { error("failure 5") }
        assertEquals(clock.current.plus(Duration.ofMinutes(15)), cache.nextRefreshInstant("warszawa:en:current", ttl))

        clock.current = cache.nextRefreshInstant("warszawa:en:current", ttl)!!.plusMillis(1)
        cache.getOrLoad("warszawa:en:current", ttl) { error("failure 6") }
        assertEquals(clock.current.plus(Duration.ofMinutes(15)), cache.nextRefreshInstant("warszawa:en:current", ttl))
    }

    @Test
    fun successfulReloadResetsRetryState() = runBlocking {
        cache.put("warszawa:en:current", "old")
        clock.current = now.plus(ttl).plusSeconds(1)
        cache.getOrLoad("warszawa:en:current", ttl) {
            error("temporary network failure")
        }

        clock.current = cache.nextRefreshInstant("warszawa:en:current", ttl)!!.plusMillis(1)
        val reloadedAt = clock.current
        val result = cache.getOrLoad("warszawa:en:current", ttl) {
            "new"
        }

        assertEquals("new", result)
        assertTrue(cache.isFresh("warszawa:en:current", ttl))
        assertEquals(reloadedAt.plus(ttl), cache.nextRefreshInstant("warszawa:en:current", ttl))
    }

    @Test
    fun keyAndLanguageEntriesAreIsolated() {
        cache.put("warszawa:en:current", "english")
        cache.put("warszawa:pl:current", "polish")

        assertEquals("english", cache.getFresh("warszawa:en:current", ttl))
        assertEquals("polish", cache.getFresh("warszawa:pl:current", ttl))
        assertNull(cache.getFresh("krakow:en:current", ttl))
    }

    @Test
    fun cancellationIsNotConvertedToStaleFallback() = runBlocking<Unit> {
        cache.put("warszawa:en:current", "old")
        clock.current = now.plus(ttl).plusSeconds(1)

        try {
            cache.getOrLoad("warszawa:en:current", ttl) {
                throw CancellationException("cancelled")
            }
            fail("Expected cancellation to be rethrown")
        } catch (_: CancellationException) {
            assertEquals(now.plus(ttl), cache.nextRefreshInstant("warszawa:en:current", ttl))
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
