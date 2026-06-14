package com.example.modernweather.data.repository

import kotlinx.coroutines.CancellationException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

internal class TimedCache<K, T>(
    private val clock: Clock
) {
    private data class Entry<T>(
        val value: T,
        val fetchedAt: Instant
    )

    private val entries = ConcurrentHashMap<K, Entry<T>>()

    fun getFresh(key: K, ttl: Duration): T? {
        val entry = entries[key] ?: return null
        return entry.value.takeIf { entry.isFresh(clock.instant(), ttl) }
    }

    fun getStale(key: K): T? {
        return entries[key]?.value
    }

    fun isFresh(key: K, ttl: Duration): Boolean {
        return entries[key]?.isFresh(clock.instant(), ttl) == true
    }

    fun nextRefreshInstant(key: K, ttl: Duration): Instant? {
        return entries[key]?.fetchedAt?.plus(ttl)
    }

    fun put(key: K, value: T, fetchedAt: Instant = clock.instant()) {
        entries[key] = Entry(value, fetchedAt)
    }

    suspend fun getOrLoad(
        key: K,
        ttl: Duration,
        loader: suspend () -> T
    ): T {
        getFresh(key, ttl)?.let { return it }
        val stale = getStale(key)

        return runCatching { loader() }
            .onSuccess { value -> put(key, value) }
            .getOrElse { error ->
                if (error is CancellationException) {
                    throw error
                }
                stale ?: throw error
            }
    }

    private fun Entry<T>.isFresh(now: Instant, ttl: Duration): Boolean {
        return !fetchedAt.plus(ttl).isBefore(now)
    }
}
