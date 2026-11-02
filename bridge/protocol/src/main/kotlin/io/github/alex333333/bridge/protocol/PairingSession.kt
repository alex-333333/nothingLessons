package io.github.alex333333.bridge.protocol

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Random

/** A one-shot pairing code: valid once, for [ttlMillis], and locked after [maxAttempts] wrong tries. */
class PairingSession(
    val code: String,
    private val now: () -> Long,
    private val ttlMillis: Long = 120_000,
    private val maxAttempts: Int = 5,
) {
    private val createdAt = now()
    private var failures = 0
    private var used = false

    @Synchronized
    fun tryCode(input: String): Boolean {
        if (used || failures >= maxAttempts || now() - createdAt > ttlMillis) return false
        val ok = MessageDigest.isEqual(code.toByteArray(Charsets.UTF_8), input.toByteArray(Charsets.UTF_8))
        if (ok) used = true else failures++
        return ok
    }

    companion object {
        /** Exactly six digits; leading zeros are allowed. */
        fun newCode(random: Random = SecureRandom()): String = random.nextInt(1_000_000).toString().padStart(6, '0')
    }
}
