package io.github.alex333333.bridge.protocol.client

import io.github.alex333333.bridge.protocol.Fingerprint
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * TLS for a PC that has a self-signed certificate valid only for "localhost": the certificate is trusted by its
 * SHA-256 fingerprint (of the leaf certificate's DER) and by nothing else, and host names are not checked.
 */
internal class PinnedTls private constructor(
    val socketFactory: SSLSocketFactory,
    val trustManager: X509TrustManager,
    val hostnameVerifier: HostnameVerifier,
) {
    companion object {
        /** Trusts exactly the certificate whose fingerprint is [fingerprintHex]. */
        fun pinned(fingerprintHex: String): PinnedTls {
            val expected = normalize(fingerprintHex)
            val trust = object : X509TrustManager {
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                    val leaf = chain?.firstOrNull() ?: throw CertificateException("The PC sent no certificate")
                    if (!matches(leaf, expected)) throw CertificateException("The PC certificate is not the one that was paired")
                }

                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
                    throw CertificateException("No client certificates")

                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            // The verifier looks at the certificate of the live session itself, so it says yes only for a pin match,
            // also on a reused connection.
            val verifier = HostnameVerifier { _, session -> sessionMatches(session, expected) }
            return build(trust, verifier)
        }

        /**
         * Accepts any certificate. Used only for hello() and pair() of a client that has no fingerprint yet; the
         * fingerprint is then read from the handshake of the response and becomes the pin.
         */
        fun acceptAny(): PinnedTls {
            val trust = object : X509TrustManager {
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                    if (chain.isNullOrEmpty()) throw CertificateException("The PC sent no certificate")
                }

                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
                    throw CertificateException("No client certificates")

                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            return build(trust, HostnameVerifier { _, _ -> true })
        }

        fun fingerprintOf(certificate: Certificate): String = Fingerprint.sha256Hex(certificate.encoded)

        private fun build(trust: X509TrustManager, verifier: HostnameVerifier): PinnedTls {
            val context = SSLContext.getInstance("TLS")
            context.init(null, arrayOf(trust), SecureRandom())
            return PinnedTls(context.socketFactory, trust, verifier)
        }

        private fun normalize(hex: String): String = hex.filter { it != ':' && !it.isWhitespace() }.lowercase()

        private fun matches(certificate: Certificate, expected: String): Boolean =
            MessageDigest.isEqual(fingerprintOf(certificate).toByteArray(), expected.toByteArray())

        private fun sessionMatches(session: SSLSession, expected: String): Boolean = try {
            val leaf = session.peerCertificates.firstOrNull()
            leaf != null && matches(leaf, expected)
        } catch (_: SSLPeerUnverifiedException) {
            false
        }
    }
}
