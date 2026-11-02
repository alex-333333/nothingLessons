package io.github.alex333333.bridge.protocol

import java.net.Inet6Address
import java.net.InetAddress

/** True for loopback, link-local and private addresses (RFC 1918 and IPv6 unique-local fc00::/7). */
fun isLocalAddress(address: InetAddress): Boolean {
    if (address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress) return true
    return address is Inet6Address && (address.address[0].toInt() and 0xFE) == 0xFC
}
