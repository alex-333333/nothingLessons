package io.github.alex333333.bridge.protocol

/** Wire constants shared by the PC server and every client. */
object Api {
    const val PORT = 48750
    const val SERVICE_TYPE = "_bridge._tcp." // for NsdManager; JmDNS appends "local."

    const val HELLO = "/hello"
    const val PAIR = "/pair"
    const val NOTES = "/notes"
    const val CLIPBOARD = "/clipboard"
    const val FILES = "/files"
    const val OUTBOX = "/outbox"
    const val WS = "/ws"

    const val HDR_DEVICE = "X-Bridge-Device"
    const val HDR_TIME = "X-Bridge-Time"
    const val HDR_SIG = "X-Bridge-Sig"

    /** Stands in for the body hash of streamed files. */
    const val UNSIGNED_BODY = "UNSIGNED-PAYLOAD"
}
