package io.github.alex333333.bridge.pc

import io.github.alex333333.bridge.protocol.Fingerprint
import io.ktor.network.tls.certificates.KeyType
import io.ktor.network.tls.certificates.buildKeyStore
import io.ktor.network.tls.extensions.HashAlgorithm
import io.ktor.network.tls.extensions.SignatureAlgorithm
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import javax.security.auth.x500.X500Principal

/**
 * The PC's self-signed RSA 2048 certificate, created on first start and kept in `bridge.p12`. Phones pin its
 * SHA-256 fingerprint when they pair, so the host name inside the certificate is irrelevant.
 */
internal class TlsIdentity private constructor(val keyStore: KeyStore, val fingerprintHex: String) {
    val alias: String get() = ALIAS
    /** The file is only a convenience store next to the app data; it cannot be protected better than the user profile. */
    val password: CharArray get() = PASSWORD.toCharArray()

    companion object {
        private const val ALIAS = "bridge"
        private const val PASSWORD = "bridge-local"
        private const val FILE = "bridge.p12"

        fun loadOrCreate(dataDir: Path): TlsIdentity {
            Files.createDirectories(dataDir)
            val file = dataDir.resolve(FILE)
            if (Files.exists(file)) {
                try {
                    return from(KeyStore.getInstance("PKCS12").also { ks -> Files.newInputStream(file).use { ks.load(it, PASSWORD.toCharArray()) } })
                } catch (_: Exception) {
                    // Unreadable: fall through and make a new one (phones must pair again).
                }
            }
            val identity = from(generate())
            val out = ByteArrayOutputStream()
            identity.keyStore.store(out, PASSWORD.toCharArray())
            writeAtomically(file, out.toByteArray())
            return identity
        }

        private fun generate(): KeyStore {
            val generated = buildKeyStore {
                certificate(ALIAS) {
                    hash = HashAlgorithm.SHA256
                    sign = SignatureAlgorithm.RSA
                    keySizeInBits = 2048
                    keyType = KeyType.Server
                    password = PASSWORD
                    daysValid = 3650
                    subject = X500Principal("CN=Bridge")
                    domains = listOf("localhost")
                }
            }
            // Repack as a real PKCS#12 store (the builder's in-memory store is JKS).
            val key = generated.getKey(ALIAS, PASSWORD.toCharArray())
            val chain = generated.getCertificateChain(ALIAS)
            return KeyStore.getInstance("PKCS12").also {
                it.load(null, null)
                it.setKeyEntry(ALIAS, key, PASSWORD.toCharArray(), chain)
            }
        }

        private fun from(keyStore: KeyStore): TlsIdentity {
            requireNotNull(keyStore.getKey(ALIAS, PASSWORD.toCharArray())) { "No private key in the store" }
            val cert = requireNotNull(keyStore.getCertificate(ALIAS)) { "No certificate in the store" }
            return TlsIdentity(keyStore, Fingerprint.sha256Hex(cert.encoded))
        }
    }
}
