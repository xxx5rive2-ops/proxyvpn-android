package app.netguard.engine.vpn

import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * IpPacketParser — parses raw IP packets from TUN interface.
 *
 * Supports both IPv4 (RFC 791) and IPv6 (RFC 8200).
 * Zero-copy: operates on ByteBuffer slices, no data copying.
 *
 * IP header layout (IPv4):
 *  0      4      8     16     24     31
 *  ┌──────┬──────┬──────────────────┐
 *  │  VER │  IHL │   TOS  │  LEN   │  offset 0
 *  ├──────────────┬─────────────────┤
 *  │     ID       │ FLAGS │ FRAG   │  offset 4
 *  ├──────┬───────┴────────────────-┤
 *  │  TTL │ PROTO │    CHECKSUM     │  offset 8
 *  ├──────────────────────────────┤
 *  │           SRC IP              │  offset 12
 *  ├──────────────────────────────┤
 *  │           DST IP              │  offset 16
 *  └──────────────────────────────┘
 */
object IpPacketParser {

    private const val IP_VERSION_4 = 4
    private const val IP_VERSION_6 = 6
    private const val PROTO_TCP = 6
    private const val PROTO_UDP = 17

    /**
     * Parse a raw IP packet from [buffer].
     * @return [ParsedPacket] or null if packet is malformed/unsupported.
     */
    fun parse(buffer: ByteBuffer): ParsedPacket? {
        if (buffer.remaining() < 20) return null // Minimum IPv4 header size

        val versionIhl = buffer.get(0).toInt() and 0xFF
        val version = versionIhl shr 4

        return when (version) {
            IP_VERSION_4 -> parseIPv4(buffer)
            IP_VERSION_6 -> parseIPv6(buffer)
            else -> null
        }
    }

    private fun parseIPv4(buffer: ByteBuffer): ParsedPacket? {
        val ihl = (buffer.get(0).toInt() and 0x0F) * 4
        if (ihl < 20 || buffer.remaining() < ihl) return null

        val totalLength = buffer.getShort(2).toInt() and 0xFFFF
        val protocol = buffer.get(9).toInt() and 0xFF
        val srcIp = ByteArray(4).also { buffer.position(12); buffer.get(it) }
        val dstIp = ByteArray(4).also { buffer.position(16); buffer.get(it) }
        buffer.position(0)

        val transport = when (protocol) {
            PROTO_TCP -> parseTcpHeader(buffer, ihl) ?: return null
            PROTO_UDP -> parseUdpHeader(buffer, ihl) ?: return null
            else -> return null // ICMP etc. — pass through as DIRECT
        }

        return ParsedPacket(
            version = 4,
            protocol = if (protocol == PROTO_TCP) TransportProto.TCP else TransportProto.UDP,
            srcAddress = InetAddress.getByAddress(srcIp),
            dstAddress = InetAddress.getByAddress(dstIp),
            srcPort = transport.first,
            dstPort = transport.second,
            headerLength = ihl,
            totalLength = totalLength,
            rawBuffer = buffer,
        )
    }

    private fun parseIPv6(buffer: ByteBuffer): ParsedPacket? {
        if (buffer.remaining() < 40) return null // IPv6 fixed header

        val nextHeader = buffer.get(6).toInt() and 0xFF
        val payloadLength = buffer.getShort(4).toInt() and 0xFFFF
        val srcIp = ByteArray(16).also { buffer.position(8); buffer.get(it) }
        val dstIp = ByteArray(16).also { buffer.position(24); buffer.get(it) }
        buffer.position(0)

        val transport = when (nextHeader) {
            PROTO_TCP -> parseTcpHeader(buffer, 40) ?: return null
            PROTO_UDP -> parseUdpHeader(buffer, 40) ?: return null
            else -> return null
        }

        return ParsedPacket(
            version = 6,
            protocol = if (nextHeader == PROTO_TCP) TransportProto.TCP else TransportProto.UDP,
            srcAddress = InetAddress.getByAddress(srcIp),
            dstAddress = InetAddress.getByAddress(dstIp),
            srcPort = transport.first,
            dstPort = transport.second,
            headerLength = 40,
            totalLength = 40 + payloadLength,
            rawBuffer = buffer,
        )
    }

    private fun parseTcpHeader(buffer: ByteBuffer, offset: Int): Pair<Int, Int>? {
        if (buffer.remaining() < offset + 4) return null
        val srcPort = buffer.getShort(offset).toInt() and 0xFFFF
        val dstPort = buffer.getShort(offset + 2).toInt() and 0xFFFF
        return srcPort to dstPort
    }

    private fun parseUdpHeader(buffer: ByteBuffer, offset: Int): Pair<Int, Int>? {
        if (buffer.remaining() < offset + 4) return null
        val srcPort = buffer.getShort(offset).toInt() and 0xFFFF
        val dstPort = buffer.getShort(offset + 2).toInt() and 0xFFFF
        return srcPort to dstPort
    }
}

data class ParsedPacket(
    val version: Int,
    val protocol: TransportProto,
    val srcAddress: InetAddress,
    val dstAddress: InetAddress,
    val srcPort: Int,
    val dstPort: Int,
    val headerLength: Int,
    val totalLength: Int,
    val rawBuffer: ByteBuffer,
) {
    val srcAddressString: String get() = srcAddress.hostAddress ?: ""
    val dstAddressString: String get() = dstAddress.hostAddress ?: ""
    val isIPv4: Boolean get() = version == 4
    val isIPv6: Boolean get() = version == 6
}

enum class TransportProto { TCP, UDP }
