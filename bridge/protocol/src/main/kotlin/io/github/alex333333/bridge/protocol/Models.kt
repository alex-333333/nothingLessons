package io.github.alex333333.bridge.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The one Json instance used for every message on the wire. */
val BridgeJson = Json {
    ignoreUnknownKeys = true
    classDiscriminator = "type"
}

@Serializable data class Hello(val pcId: String, val name: String, val app: String = "bridge", val version: Int = 1)

@Serializable data class PairRequest(val code: String, val deviceId: String, val deviceName: String)

@Serializable data class PairResponse(val token: String, val pcName: String, val macs: List<String>)

@Serializable data class Note(val id: String, val text: String, val updatedAt: Long)

@Serializable data class ClipText(val text: String)

@Serializable data class FileInfo(val id: String, val name: String, val size: Long)

@Serializable
sealed interface InputEvent {
    @Serializable data class MouseMove(val dx: Int, val dy: Int) : InputEvent
    @Serializable data class Click(val button: MouseButton) : InputEvent
    @Serializable data class Scroll(val dy: Int) : InputEvent
    @Serializable data class TypeText(val text: String) : InputEvent
    @Serializable data class PressKey(val key: Key) : InputEvent
    @Serializable data class Media(val action: MediaAction) : InputEvent
    @Serializable data class Power(val action: PowerAction, val confirmed: Boolean = false) : InputEvent
}

enum class MouseButton { Left, Right, Middle }

enum class Key { Enter, Backspace, Escape, Tab, Left, Right, Up, Down }

enum class MediaAction { VolumeUp, VolumeDown, Mute, PlayPause, Next, Previous }

enum class PowerAction { Lock, Shutdown }

@Serializable
sealed interface ServerEvent {
    @Serializable data object OutboxChanged : ServerEvent
    @Serializable data object NotesChanged : ServerEvent
    @Serializable data object Unpaired : ServerEvent
}
