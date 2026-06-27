package app.netguard.engine.vpn

import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * IpPacketParser — parses raw IP packets from the TUN interface.
 *
 * Handles IPv4 (RFC 791) and IPv6 (RFC 8200) headers.
 * Extracts source/destination IP, ports, and protocol for rule evaluation.
 *
 * Thread safety: stateless, safe for concurrent use.
 */
object IpPacketParser {

    private const val PROTO_TCP = 6
    private const val PROTO_UDP = 17

    /**
     * Parse raw bytes from TUN fd into a [ParsedPacket].
     * Returns null for malformed, too-short, or unsupported packets.
     */
    fun parse(buffer: ByteBuffer): ParsedPacket? {
        if (buffer.remaining() < 20) return null
        return when ((buffer.get(0).toInt() and 0xFF) ushr 4) {
            4    -> parseIPv4(buffer)
            6    -> parseIPv6(buffer)
            else -> null
        }
    }

    private fun parseIPv4(buf: ByteBuffer): ParsedPacket? {
        val ihl      = (buf.get(0).toInt() and 0x0F) * 4
        if (ihl < 20 || buf.remaining() < ihl) return null
        val totalLen = buf.getShort(2).toInt() and 0xFFFF
        val proto    = buf.get(9).toInt() and 0xFF
        val srcIp    = buf.getBytes(12, 4)
        val dstIp    = buf.getBytes(16, 4)
        val transport = parsePorts(buf, ihl, proto) ?: return null
        return ParsedPacket(
            version      = 4,
            protocol     = transport.proto,
            srcAddress   = InetAddress.getByAddress(srcIp),
            dstAddress   = InetAddress.getByAddress(dstIp),
            srcPort      = transport.srcPort,
            dstPort      = transport.dstPort,
            headerLength = ihl,
            totalLength  = totalLen,
        )
    }

    private fun parseIPv6(buf: ByteBuffer): ParsedPacket? {
        if (buf.remaining() < 40) return null
        val nextHeader = buf.get(6).toInt() and 0xFF
        val payloadLen = buf.getShort(4).toInt() and 0xFFFF
        val srcIp      = buf.getBytes(8, 16)
        val dstIp      = buf.getBytes(24, 16)
        val transport  = parsePorts(buf, 40, nextHeader) ?: return null
        return ParsedPacket(
            version      = 6,
            protocol     = transport.proto,
            srcAddress   = InetAddress.getByAddress(srcIp),
            dstAddress   = InetAddress.getByAddress(dstIp),
            srcPort      = transport.srcPort,
            dstPort      = transport.dstPort,
            headerLength = 40,
            totalLength  = 40 + payloadLen,
        )
    }

    private data class TransportInfo(val proto: TransportProto, val srcPort: Int, val dstPort: Int)

    private fun parsePorts(buf: ByteBuffer, offset: Int, protoNum: Int): TransportInfo? {
        if (buf.remaining() < offset + 4) return null
        val srcPort = buf.getShort(offset).toInt() and 0xFFFF
        val dstPort = buf.getShort(offset + 2).toInt() and 0xFFFF
        val proto   = when (protoNum) {
            PROTO_TCP -> TransportProto.TCP
            PROTO_UDP -> TransportProto.UDP
            else      -> return null
        }
        return TransportInfo(proto, srcPort, dstPort)
    }

    /** Read [len] bytes starting at absolute [pos] without moving buffer position. */
    private fun ByteBuffer.getBytes(pos: Int, len: Int): ByteArray {
        val arr = ByteArray(len)
        // Use duplicate to avoid changing position of the original buffer
        val dup = duplicate()
        dup.position(pos)
        dup.get(arr)
        return arr
    }
}

data class ParsedPacket(
    val version      : Int,
    val protocol     : TransportProto,
    val srcAddress   : InetAddress,
    val dstAddress   : InetAddress,
    val srcPort      : Int,
    val dstPort      : Int,
    val headerLength : Int,
    val totalLength  : Int,
) {
    val srcAddressString: String get() = srcAddress.hostAddress ?: ""
    val dstAddressString: String get() = dstAddress.hostAddress ?: ""
    val isIPv4: Boolean get() = version == 4
    val isIPv6: Boolean get() = version == 6
}

enum class TransportProto { TCP, UDP }
