package io.github.alex333333.bridge.protocol

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable

/** The one PC a phone is paired with, as the phone stores it. */
@Serializable
data class PcRecord(
    val name: String,
    val host: String,
    val port: Int,
    val token: String,
    val deviceId: String,
    val fingerprintHex: String,
    val macs: List<String>,
) {
    fun toJson(): String = BridgeJson.encodeToString(serializer(), this)

    companion object {
        /** Null for missing or damaged text. */
        fun fromJson(text: String?): PcRecord? {
            if (text.isNullOrBlank()) return null
            return try {
                BridgeJson.decodeFromString(serializer(), text)
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
