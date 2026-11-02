package io.github.alex333333.bridge.protocol.client

import io.github.alex333333.bridge.protocol.NotPairedException
import java.io.IOException
import javax.net.ssl.SSLException

/** A short Russian explanation of a failed call, for the phone's screen and notifications. Never blank. */
fun errorText(e: Throwable): String = when {
    e is NotPairedException -> "Телефон не привязан к ПК"
    e is BridgeHttpException && e.status == 403 -> "Неверный или просроченный код"
    e is BridgeHttpException -> "ПК ответил ошибкой ${e.status}"
    e is SSLException -> "Сменился сертификат ПК: отвяжите ПК и привяжите заново"
    e is IOException -> "Нет связи с ПК"
    else -> e.message?.takeIf { it.isNotBlank() } ?: "Ошибка"
}
