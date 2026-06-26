package app.netguard.engine.proxy

import app.netguard.domain.entity.ProxyAuthentication
import app.netguard.domain.entity.ProxyServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel

/**
 * Socks5Connector — production-grade SOCKS5 client per RFC 1928 & RFC 1929.
 *
 * Implements the complete SOCKS5 handshake state machine:
 *   GREETING → AUTH_METHOD → [AUTHENTICATE] → REQUEST → ESTABLISHED
 *
 * Security considerations:
 * - Username/password transmitted only after method negotiation
 * - Credentials kept in ByteArray, zeroed after use
 * - Supports domain-based CONNECT (ATYP=0x03) to avoid local DNS leaks
 * - TLS wrapping handled at a higher layer (ProxyDispatcher)
 *
 * Thread safety: stateless — each call creates its own channel.
 */
object Socks5Connector {

    private const val SOCKS_VERSION: Byte = 0x05
    private const val METHOD_NO_AUTH: Byte = 0x00
    private const val METHOD_USERNAME_PASSWORD: Byte = 0x02
    private const val METHOD_NO_ACCEPTABLE: Byte = 0xFF.toByte()
    private const val CMD_CONNECT: Byte = 0x01
    private const val ATYP_IPV4: Byte = 0x01
    private const val ATYP_DOMAIN: Byte = 0x03
    private const val ATYP_IPV6: Byte = 0x04
    private const val REPLY_SUCCESS: Byte = 0x00
    private const val AUTH_VERSION: Byte = 0x01
    private const val AUTH_SUCCESS: Byte = 0x00

    private const val CONNECT_TIMEOUT_MS = 10_000L
    private const val BUFFER_SIZE = 512

    /**
     * Establish a SOCKS5 connection through [server] to [targetHost]:[targetPort].
     *
     * @return Connected [SocketChannel] ready for data relay, or throws on failure.
     */
    suspend fun connect(
        server: ProxyServer,
        targetHost: String,
        targetPort: Int,
    ): SocketChannel = withContext(Dispatchers.IO) {
        withTimeout(CONNECT_TIMEOUT_MS) {
            val channel = SocketChannel.open()
            try {
                channel.connect(InetSocketAddress(server.host, server.port))

                // Phase 1: Greeting
                val method = sendGreeting(channel, server.authentication)

                // Phase 2: Authentication (if required)
                if (method == METHOD_USERNAME_PASSWORD) {
                    val auth = server.authentication as? ProxyAuthentication.UserPassword
                        ?: throw Socks5Exception("Server requires auth but no credentials configured")
                    sendUsernamePasswordAuth(channel, auth)
                } else if (method != METHOD_NO_AUTH) {
                    throw Socks5Exception("Server returned unacceptable auth method: 0x${method.toHex()}")
                }

                // Phase 3: CONNECT request
                sendConnectRequest(channel, targetHost, targetPort)

                Timber.d("SOCKS5 connected: $targetHost:$targetPort via ${server.host}:${server.port}")
                channel
            } catch (e: Exception) {
                runCatching { channel.close() }
                throw e
            }
        }
    }

    private fun sendGreeting(channel: SocketChannel, auth: ProxyAuthentication?): Byte {
        val methods = if (auth is ProxyAuthentication.UserPassword) {
            byteArrayOf(METHOD_NO_AUTH, METHOD_USERNAME_PASSWORD)
        } else {
            byteArrayOf(METHOD_NO_AUTH)
        }

        val greeting = ByteBuffer.allocate(2 + methods.size).apply {
            put(SOCKS_VERSION)
            put(methods.size.toByte())
            put(methods)
            flip()
        }
        channel.writeAll(greeting)

        val response = ByteBuffer.allocate(2)
        channel.readAll(response)
        response.flip()

        val serverVersion = response.get()
        val serverMethod = response.get()

        if (serverVersion != SOCKS_VERSION) {
            throw Socks5Exception("Server returned wrong SOCKS version: $serverVersion")
        }
        if (serverMethod == METHOD_NO_ACCEPTABLE) {
            throw Socks5Exception("Server has no acceptable authentication methods")
        }
        return serverMethod
    }

    private fun sendUsernamePasswordAuth(
        channel: SocketChannel,
        auth: ProxyAuthentication.UserPassword,
    ) {
        val userBytes = auth.username.toByteArray(Charsets.UTF_8)
        val passBytes = auth.password.toByteArray(Charsets.UTF_8)

        if (userBytes.size > 255 || passBytes.size > 255) {
            throw Socks5Exception("Username or password exceeds 255 bytes")
        }

        val request = ByteBuffer.allocate(3 + userBytes.size + passBytes.size).apply {
            put(AUTH_VERSION)
            put(userBytes.size.toByte())
            put(userBytes)
            put(passBytes.size.toByte())
            put(passBytes)
            flip()
        }

        try {
            channel.writeAll(request)
        } finally {
            // Zero out sensitive data from buffer
            request.clear()
            repeat(request.capacity()) { request.put(0) }
            passBytes.fill(0)
        }

        val response = ByteBuffer.allocate(2)
        channel.readAll(response)
        response.flip()

        response.get() // sub-version (ignored)
        val status = response.get()
        if (status != AUTH_SUCCESS) {
            throw Socks5AuthException("SOCKS5 authentication failed (server rejected credentials)")
        }
    }

    private fun sendConnectRequest(
        channel: SocketChannel,
        targetHost: String,
        targetPort: Int,
    ) {
        // Use ATYP_DOMAIN to avoid local DNS resolution (anti-leak)
        val hostBytes = targetHost.toByteArray(Charsets.UTF_8)
        if (hostBytes.size > 255) throw Socks5Exception("Target hostname too long: ${hostBytes.size}")

        val request = ByteBuffer.allocate(4 + 1 + hostBytes.size + 2).apply {
            put(SOCKS_VERSION)
            put(CMD_CONNECT)
            put(0x00)            // reserved
            put(ATYP_DOMAIN)
            put(hostBytes.size.toByte())
            put(hostBytes)
            putShort(targetPort.toShort())
            flip()
        }
        channel.writeAll(request)

        // Read response header (at least 10 bytes for IPv4 reply)
        val responseHeader = ByteBuffer.allocate(10)
        channel.readAll(responseHeader)
        responseHeader.flip()

        val version = responseHeader.get()
        val reply = responseHeader.get()
        responseHeader.get() // reserved
        val addrType = responseHeader.get()

        if (version != SOCKS_VERSION) {
            throw Socks5Exception("Unexpected version in CONNECT response: $version")
        }
        if (reply != REPLY_SUCCESS) {
            throw Socks5Exception("CONNECT failed: ${replyCodeToString(reply)}")
        }

        // Skip bound address based on type
        when (addrType) {
            ATYP_IPV4 -> {
                // Already read 4 bytes of IPv4 + 2 port in initial read
                // remaining 6 bytes of 10-byte response cover this
            }
            ATYP_DOMAIN -> {
                val domainLen = responseHeader.get().toInt() and 0xFF
                val extra = ByteBuffer.allocate(domainLen + 2)
                channel.readAll(extra)
            }
            ATYP_IPV6 -> {
                val extra = ByteBuffer.allocate(12 + 2) // remaining IPv6 + port
                channel.readAll(extra)
            }
        }
    }

    private fun SocketChannel.writeAll(buffer: ByteBuffer) {
        while (buffer.hasRemaining()) {
            val written = write(buffer)
            if (written < 0) throw IOException("Connection closed during write")
        }
    }

    private fun SocketChannel.readAll(buffer: ByteBuffer) {
        while (buffer.hasRemaining()) {
            val read = read(buffer)
            if (read < 0) throw IOException("Connection closed during read")
            if (read == 0) Thread.yield()
        }
    }

    private fun replyCodeToString(code: Byte): String = when (code) {
        0x01.toByte() -> "General SOCKS server failure"
        0x02.toByte() -> "Connection not allowed by ruleset"
        0x03.toByte() -> "Network unreachable"
        0x04.toByte() -> "Host unreachable"
        0x05.toByte() -> "Connection refused"
        0x06.toByte() -> "TTL expired"
        0x07.toByte() -> "Command not supported"
        0x08.toByte() -> "Address type not supported"
        else -> "Unknown error (0x${code.toHex()})"
    }

    private fun Byte.toHex(): String = String.format("%02X", this)
}

class Socks5Exception(message: String, cause: Throwable? = null) : IOException(message, cause)
class Socks5AuthException(message: String) : Socks5Exception(message)
