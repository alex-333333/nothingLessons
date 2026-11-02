package io.github.alex333333.bridge.pc

import io.github.alex333333.bridge.protocol.BridgeJson
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

/** The PC's identity and the paired phones with their tokens, kept in `devices.json`. */
internal class DeviceStore(dataDir: Path, private val random: SecureRandom = SecureRandom()) {
    @Serializable private data class Device(val deviceId: String, val name: String, val token: String)
    @Serializable private data class FileData(val pcId: String, val devices: List<Device> = emptyList())

    private val file = dataDir.resolve("devices.json")
    private var data: FileData

    init {
        val loaded = try {
            if (Files.exists(file)) BridgeJson.decodeFromString<FileData>(Files.readString(file)) else null
        } catch (_: Exception) {
            null // unreadable file: start with nobody paired (fail closed)
        }
        data = (loaded ?: FileData(UUID.randomUUID().toString())).let { d -> d.copy(devices = d.devices.filter { it.token.isNotBlank() }) }
        if (loaded == null) save()
    }

    val pcId: String @Synchronized get() = data.pcId

    /** The token of a paired device, or null. Never returns a blank token. */
    @Synchronized fun tokenFor(deviceId: String): String? = data.devices.firstOrNull { it.deviceId == deviceId }?.token?.takeIf { it.isNotBlank() }

    @Synchronized fun nameFor(deviceId: String): String? = data.devices.firstOrNull { it.deviceId == deviceId }?.name

    /** Pairs (or re-pairs) a device and returns its new secret token: 32 random bytes, base64url without padding. */
    @Synchronized fun pair(deviceId: String, name: String): String {
        val bytes = ByteArray(32).also { random.nextBytes(it) }
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        data = data.copy(devices = data.devices.filter { it.deviceId != deviceId } + Device(deviceId, name, token))
        save()
        return token
    }

    @Synchronized fun clear() {
        data = data.copy(devices = emptyList())
        save()
    }

    private fun save() = writeAtomically(file, BridgeJson.encodeToString(data).toByteArray(Charsets.UTF_8))
}
