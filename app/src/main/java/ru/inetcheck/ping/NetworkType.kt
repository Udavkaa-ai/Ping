package ru.inetcheck.ping

// VPN appended at the end so history rows encoded with the older WIFI/MOBILE/
// OTHER/NONE ordinals still decode correctly via NetworkType.values()[ordinal].
enum class NetworkType { WIFI, MOBILE, OTHER, NONE, VPN }
