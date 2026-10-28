package io.github.alex333333.lessons.core

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

/** Stores [Config] as JSON so it fits in a single DataStore preference. */
object ConfigCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(config: Config): String = json.encodeToString(Config.serializer(), config)

    fun decode(text: String?): Config {
        if (text.isNullOrBlank()) return Config()
        val config = try {
            json.decodeFromString(Config.serializer(), text)
        } catch (e: SerializationException) {
            return Config()
        } catch (e: IllegalArgumentException) {
            return Config()
        }
        return config.copy(week = config.normalizedWeek())
    }
}

internal object LocalTimeSerializer : KSerializer<LocalTime> {
    private val format = DateTimeFormatter.ofPattern("HH:mm")

    override val descriptor = PrimitiveSerialDescriptor("LocalTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalTime) = encoder.encodeString(value.format(format))

    override fun deserialize(decoder: Decoder): LocalTime = LocalTime.parse(decoder.decodeString(), format)
}
