package io.github.alex333333.bridge.protocol

/** The name a shared file gets on the PC: the file name if there is one, else the last URI segment, else "file". */
fun displayName(fileName: String?, uriLastSegment: String?): String {
    val raw = listOf(fileName, uriLastSegment).firstOrNull { !it.isNullOrBlank() } ?: "file"
    return SafeName.of(raw)
}
