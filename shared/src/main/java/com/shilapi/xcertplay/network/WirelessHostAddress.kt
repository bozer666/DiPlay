package com.shilapi.xcertplay.network

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Lynk OSN adaptation: prefer IPv4 for manual APs.
 *
 * The iPhone joins the car hotspot via IPv4 DHCP and advertises/discovers
 * CarPlay services over IPv4 mDNS. Binding JmDNS and the AirPlay listener to
 * the interface's IPv6 link-local address breaks discovery entirely: the
 * phone shows the CarPlay prompt, but the head unit never discovers
 * _carplay-ctrl._tcp because iOS does not resolve the accessory's scoped
 * fe80:: address. IPv6 link-local remains as a fallback when the interface
 * has no usable IPv4 address.
 */
internal fun wirelessHostAddress(addresses: List<InetAddress>, interfaceIndex: Int): InetAddress? {
    addresses.firstOrNull {
        it is Inet4Address && !it.isLoopbackAddress && !it.isLinkLocalAddress &&
            !it.isAnyLocalAddress && !it.isMulticastAddress
    }?.let { return it }
    if (interfaceIndex > 0) {
        addresses.filterIsInstance<Inet6Address>().firstOrNull { it.isLinkLocalAddress }?.let {
            return Inet6Address.getByAddress(null, it.address, interfaceIndex)
        }
    }
    return null
}
