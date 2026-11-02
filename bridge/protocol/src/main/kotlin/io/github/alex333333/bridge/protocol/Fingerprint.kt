package io.github.alex333333.bridge.protocol

import java.security.MessageDigest

object Fingerprint {
    fun sha256Hex(certDer: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(certDer).joinToString("") { "%02x".format(it) }

    /** "a3f109bc..." -> "A3F1-09BC": the first eight hex characters, for showing to the user. */
    fun short(hex: String): String {
        val head = hex.take(8).uppercase()
        return "${head.take(4)}-${head.drop(4)}"
    }
}
