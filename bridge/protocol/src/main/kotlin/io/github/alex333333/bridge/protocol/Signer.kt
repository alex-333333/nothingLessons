package io.github.alex333333.bridge.protocol

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

/** HMAC-SHA256 request signing. The signed path includes the query string. */
object Signer {
    fun sign(token: String, method: String, path: String, timeMillis: Long, bodyHash: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(token.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val digest = mac.doFinal("$method\n$path\n$timeMillis\n$bodyHash".toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    fun verify(
        token: String,
        method: String,
        path: String,
        timeMillis: Long,
        bodyHash: String,
        signature: String,
        nowMillis: Long,
        maxSkewMillis: Long = 60_000,
    ): Boolean {
        if (abs(nowMillis - timeMillis) > maxSkewMillis) return false
        val expected = sign(token, method, path, timeMillis, bodyHash)
        // Both sides are plain ASCII text, so a constant-time byte comparison is enough; garbage just fails.
        return MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), signature.toByteArray(Charsets.UTF_8))
    }

    fun sha256Hex(bytes: ByteArray): String = Fingerprint.sha256Hex(bytes)
}
