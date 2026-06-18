package ru.inetcheck.ping

// VPN is kept at the end of the enum strictly for ordinal-stable decoding of
// history rows written by a brief earlier build that treated VPN as its own
// lane. Going forward VPN is a modifier (HistoryRepository.Entry.viaVpn), not
// a transport — runtime code only ever attributes checks to WIFI / MOBILE.
enum class NetworkType { WIFI, MOBILE, OTHER, NONE, VPN }
