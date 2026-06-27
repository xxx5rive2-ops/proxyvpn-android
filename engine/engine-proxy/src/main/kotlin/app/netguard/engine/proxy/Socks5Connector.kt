package app.netguard.engine.proxy

import app.netguard.domain.entity.ProxyAuthentication
import app.netguard.domain.entity.ProxyServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

/**
 * Socks5Connector — SOCKS5 client per RFC 1928 & RFC 1929.
 *
 * Uses blocking java.net.Socket on Dispatchers.IO (not NIO SocketChannel)
 * for simplicity and correctness. Performance-critical path uses
 * coroutines for structured concurrency without blocking the thread pool.
 *
 * SOCKS5 handshake state machine:
 *   GREETING → METHOD_SELECTED → [AUTH] → CONNECT_REQUEST → ESTABLISHED
 */
object Socks5Connector {

    private const val SOCKS5: Byte = 0x05
    private const val METHOD_NO_AUTH: Byte = 0x00
    private const val METHOD_USER_PASS: Byte = 0x02
    private const val METHOD_NO_ACCEPTABLE: Byte = 0xFF.toByte()
    private const val CMD_CONNECT: Byte = 0x01
    private const val ATYP_DOMAIN: Byte = 0x03
    private const val REPLY_SUCCESS: Byte = 0x00
    private const val AUTH_VERSION: Byte = 0x01
    private const val AUTH_SUCCESS: Byte = 0x00
    private const val CONNECT_TIMEOUT_MS = 10_000L

    /**
     * Establish a SOCKS5 tunnel through [server] to [targetHost]:[targetPort].
     * Returns a connected [Socket] ready for bidirectional relay.
     */
    suspend fun connect(
        server: ProxyServer,
        targetHost: String,
        targetPort: Int,
    ): Socket = withContext(Dispatchers.IO) {
        withTimeout(CONNECT_TIMEOUT_MS) {
            val socket = Socket()
            try {
                socket.connect(
                    java.net.InetSocketAddress(server.host, server.port),
                    CONNECT_TIMEOUT_MS.toInt()
                )
                socket.soTimeout = CONNECT_TIMEOUT_MS.toInt()

                val input  = socket.getInputStream()
                val output = socket.getOutputStream()

                // Phase 1: Method negotiation
                val method = negotiate(output, input, server.authentication)

                // Phase 2: Authentication
                if (method == METHOD_USER_PASS) {
                    val creds = server.authentication as? ProxyAuthentication.UserPassword
                        ?: throw Socks5Exception("Server requires username/password but none configured")
                    authenticate(output, input, creds)
                } else if (method != METHOD_NO_AUTH) {
                    throw Socks5Exception("Unacceptable auth method: 0x${method.toHex()}")
                }

                // Phase 3: CONNECT request
                sendConnect(output, input, targetHost, targetPort)

                Timber.d("SOCKS5 tunnel established: $targetHost:$targetPort via ${server.host}:${server.port}")
                socket
            } catch (e: Exception) {
                runCatching { socket.close() }
                throw e
            }
        }
    }

    private fun negotiate(out: OutputStream, inp: InputStream, auth: ProxyAuthentication?): Byte {
        val methods = if (auth is ProxyAuthentication.UserPassword)
            byteArrayOf(METHOD_NO_AUTH, METHOD_USER_PASS)
        else
            byteArrayOf(METHOD_NO_AUTH)

        out.write(byteArrayOf(SOCKS5, methods.size.toByte()) + methods)
        out.flush()

        val buf = ByteArray(2)
        inp.readFully(buf)
        if (buf[0] != SOCKS5) throw Socks5Exception("Wrong SOCKS version: ${buf[0]}")
        if (buf[1] == METHOD_NO_ACCEPTABLE) throw Socks5Exception("No acceptable auth methods")
        return buf[1]
    }

    private fun authenticate(out: OutputStream, inp: InputStream, creds: ProxyAuthentication.UserPassword) {
        val user = creds.username.toByteArray(Charsets.UTF_8)
        val pass = creds.password.toByteArray(Charsets.UTF_8)
        if (user.size > 255 || pass.size > 255)
            throw Socks5Exception("Credentials exceed 255 bytes")

        val request = byteArrayOf(AUTH_VERSION, user.size.toByte()) + user +
                      byteArrayOf(pass.size.toByte()) + pass
        try {
            out.write(request)
            out.flush()
        } finally {
            request.fill(0)
            pass.fill(0)
        }

        val resp = ByteArray(2)
        inp.readFully(resp)
        if (resp[1] != AUTH_SUCCESS)
            throw Socks5AuthException("Authentication failed (wrong credentials)")
    }

    private fun sendConnect(out: OutputStream, inp: InputStream, host: String, port: Int) {
        val hostBytes = host.toByteArray(Charsets.UTF_8)
        if (hostBytes.size > 255) throw Socks5Exception("Hostname too long")

        val req = byteArrayOf(SOCKS5, CMD_CONNECT, 0x00, ATYP_DOMAIN,
                              hostBytes.size.toByte()) +
                  hostBytes +
                  byteArrayOf((port shr 8).toByte(), (port and 0xFF).toByte())
        out.write(req)
        out.flush()

        // Response: VER REP RSV ATYP [ADDR] PORT
        val header = ByteArray(4)
        inp.readFully(header)
        if (header[0] != SOCKS5) throw Socks5Exception("Wrong version in response")
        if (header[1] != REPLY_SUCCESS) throw Socks5Exception("CONNECT failed: ${replyCodeToString(header[1])}")

        // Skip bound address
        when (header[3]) {
            0x01.toByte() -> inp.readFully(ByteArray(4 + 2))   // IPv4 + port
            0x03.toByte() -> {
                val len = inp.read()
                inp.readFully(ByteArray(len + 2))               // domain + port
            }
            0x04.toByte() -> inp.readFully(ByteArray(16 + 2))  // IPv6 + port
        }
    }

    private fun InputStream.readFully(buf: ByteArray) {
        var offset = 0
        while (offset < buf.size) {
            val n = read(buf, offset, buf.size - offset)
            if (n < 0) throw IOException("Connection closed")
            offset += n
        }
    }

    private fun replyCodeToString(code: Byte): String = when (code.toInt() and 0xFF) {
        0x01 -> "General failure"
        0x02 -> "Connection not allowed"
        0x03 -> "Network unreachable"
        0x04 -> "Host unreachable"
        0x05 -> "Connection refused"
        0x06 -> "TTL expired"
        0x07 -> "Command not supported"
        0x08 -> "Address type not supported"
        else -> "Unknown (0x${code.toHex()})"
    }

    private fun Byte.toHex(): String = "%02X".format(this)
}

class Socks5Exception(message: String, cause: Throwable? = null) : IOException(message, cause)
class Socks5AuthException(message: String) : Socks5Exception(message)
