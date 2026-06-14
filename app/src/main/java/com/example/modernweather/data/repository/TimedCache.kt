package com.example.modernweather.data.repository

import kotlinx.coroutines.CancellationException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

internal class TimedCache<K, T>(
    private val clock: Clock,
    private val retryPolicy: RetryBackoffPolicy = RetryBackoffPolicy()
) {
    data class RetryBackoffPolicy(
        val initialDelay: Duration = Duration.ofMinutes(1),
        val maxDelay: Duration = Duration.ofMinutes(15),
        val multiplier: Long = 2L
    )

    private companion object {
        const val MAX_TRACKED_FAILED_REFRESH_COUNT = 32
    }

    private data class Entry<T>(
        val value: T,
        val fetchedAt: Instant,
        val lastAttemptAt: Instant? = null,
        val failedRefreshCount: Int = 0
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
        val entry = entries[key] ?: return null
        val freshnessInstant = entry.fetchedAt.plus(ttl)
        val retryInstant = entry.nextRetryInstant()
        return listOfNotNull(freshnessInstant, retryInstant).maxOrNull()
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
        if (stale != null && isRetryBackoffActive(key, ttl)) {
            return stale
        }

        return runCatching { loader() }
            .onSuccess { value -> put(key, value) }
            .getOrElse { error ->
                if (error is CancellationException) {
                    throw error
                }
                recordFailedAttempt(key)
                stale ?: throw error
            }
    }

    private fun Entry<T>.isFresh(now: Instant, ttl: Duration): Boolean {
        return !fetchedAt.plus(ttl).isBefore(now)
    }

    private fun isRetryBackoffActive(key: K, ttl: Duration): Boolean {
        val entry = entries[key] ?: return false
        if (entry.isFresh(clock.instant(), ttl)) {
            return false
        }
        val nextRetryInstant = entry.nextRetryInstant() ?: return false
        return clock.instant().isBefore(nextRetryInstant)
    }

    private fun recordFailedAttempt(key: K) {
        val entry = entries[key] ?: return
        entries[key] = entry.copy(
            lastAttemptAt = clock.instant(),
            failedRefreshCount = (entry.failedRefreshCount + 1).coerceAtMost(MAX_TRACKED_FAILED_REFRESH_COUNT)
        )
    }

    private fun Entry<T>.nextRetryInstant(): Instant? {
        val attemptAt = lastAttemptAt ?: return null
        if (failedRefreshCount <= 0) {
            return null
        }
        return attemptAt.plus(retryDelay(failedRefreshCount))
    }

    private fun retryDelay(failedRefreshCount: Int): Duration {
        var delay = retryPolicy.initialDelay
        repeat((failedRefreshCount - 1).coerceAtLeast(0)) {
            val nextDelay = delay.multipliedBy(retryPolicy.multiplier)
            delay = if (nextDelay > retryPolicy.maxDelay) retryPolicy.maxDelay else nextDelay
        }
        return if (delay > retryPolicy.maxDelay) retryPolicy.maxDelay else delay
    }
}
