package io.github.alex333333.bridge.protocol

/** Turns untrusted file names into safe Windows/Android file names. */
object SafeName {
    private const val MAX_LENGTH = 120
    private const val FALLBACK = "file"
    private val FORBIDDEN = "<>:\"|?*".toSet()
    private val RESERVED = buildSet {
        addAll(listOf("CON", "PRN", "AUX", "NUL"))
        for (i in 1..9) { add("COM$i"); add("LPT$i") }
    }

    fun of(raw: String): String {
        var name = raw.substringAfterLast('/').substringAfterLast('\\')
        name = name.map { if (it < ' ' || it in FORBIDDEN) '_' else it }.joinToString("")
        name = name.trim().trimEnd('.', ' ')
        if (name.isEmpty()) return FALLBACK
        if (name.substringBefore('.').uppercase() in RESERVED) name = "_$name"
        if (name.length > MAX_LENGTH) name = shorten(name)
        return name.trimEnd('.', ' ').ifEmpty { FALLBACK }
    }

    /** "a.txt" -> "a (1).txt", then "a (2).txt", until [exists] says the name is free. */
    fun unique(name: String, exists: (String) -> Boolean): String {
        if (!exists(name)) return name
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (true) {
            val candidate = "$stem ($i)$ext"
            if (!exists(candidate)) return candidate
            i++
        }
    }

    private fun shorten(name: String): String {
        val dot = name.lastIndexOf('.')
        val ext = if (dot > 0 && name.length - dot < MAX_LENGTH / 2) name.substring(dot) else ""
        var keep = MAX_LENGTH - ext.length
        // Do not cut a surrogate pair (emoji) in half.
        if (keep > 0 && Character.isHighSurrogate(name[keep - 1])) keep--
        return name.substring(0, keep) + ext
    }
}
