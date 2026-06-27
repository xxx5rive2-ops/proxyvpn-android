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
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Socks5Connector — SOCKS5 client per RFC 1928 & RFC 1929.
 *
 * Uses blocking java.net.Socket on Dispatchers.IO for correctness.
 * Coroutines provide structured concurrency and timeout control.
 */
object Socks5Connector {

    private const val SOCKS5: Byte       = 0x05
    private const val NO_AUTH: Byte      = 0x00
    private const val USER_PASS: Byte    = 0x02
    private const val NO_ACCEPTABLE: Byte = 0xFF.toByte()
    private const val CMD_CONNECT: Byte  = 0x01
    private const val ATYP_DOMAIN: Byte  = 0x03
    private const val REPLY_OK: Byte     = 0x00
    private const val AUTH_VER: Byte     = 0x01
    private const val AUTH_OK: Byte      = 0x00
    private const val TIMEOUT_MS         = 10_000L

    /**
     * Establish a SOCKS5 tunnel through [server] to [host]:[port].
     * Returns a connected [Socket] ready for bidirectional data relay.
     */
    suspend fun connect(server: ProxyServer, host: String, port: Int): Socket =
        withContext(Dispatchers.IO) {
            withTimeout(TIMEOUT_MS) {
                val sock = Socket()
                try {
                    sock.connect(InetSocketAddress(server.host, server.port), TIMEOUT_MS.toInt())
                    sock.soTimeout = TIMEOUT_MS.toInt()
                    val inp = sock.getInputStream()
                    val out = sock.getOutputStream()
                    val method = greet(out, inp, server.authentication)
                    when (method) {
                        USER_PASS -> auth(out, inp,
                            server.authentication as? ProxyAuthentication.UserPassword
                                ?: throw Socks5Exception("No credentials for auth"))
                        NO_AUTH  -> Unit
                        else     -> throw Socks5Exception("Unacceptable auth: 0x${method.hex()}")
                    }
                    connect(out, inp, host, port)
                    Timber.d("SOCKS5 tunnel: $host:$port via ${server.host}:${server.port}")
                    sock
                } catch (e: Exception) {
                    runCatching { sock.close() }
                    throw e
                }
            }
        }

    // ── SOCKS5 phases ────────────────────────────────────────────

    private fun greet(out: OutputStream, inp: InputStream, authInfo: ProxyAuthentication?): Byte {
        val methods = if (authInfo is ProxyAuthentication.UserPassword)
            byteArrayOf(NO_AUTH, USER_PASS) else byteArrayOf(NO_AUTH)
        out.write(byteArrayOf(SOCKS5, methods.size.toByte()))
        out.write(methods)
        out.flush()
        val buf = inp.readN(2)
        if (buf[0] != SOCKS5)       throw Socks5Exception("Wrong SOCKS version: ${buf[0]}")
        if (buf[1] == NO_ACCEPTABLE) throw Socks5Exception("No acceptable auth methods")
        return buf[1]
    }

    private fun auth(out: OutputStream, inp: InputStream, creds: ProxyAuthentication.UserPassword) {
        val user = creds.username.toByteArray(Charsets.UTF_8)
        val pass = creds.password.toByteArray(Charsets.UTF_8)
        if (user.size > 255 || pass.size > 255) throw Socks5Exception("Credentials exceed 255 bytes")
        try {
            out.write(byteArrayOf(AUTH_VER, user.size.toByte()))
            out.write(user)
            out.write(byteArrayOf(pass.size.toByte()))
            out.write(pass)
            out.flush()
            val resp = inp.readN(2)
            if (resp[1] != AUTH_OK) throw Socks5AuthException("Authentication rejected")
        } finally {
            pass.fill(0.toByte())
        }
    }

    private fun connect(out: OutputStream, inp: InputStream, host: String, port: Int) {
        val hostBytes = host.toByteArray(Charsets.UTF_8)
        if (hostBytes.size > 255) throw Socks5Exception("Hostname too long: ${hostBytes.size} bytes")
        out.write(byteArrayOf(SOCKS5, CMD_CONNECT, 0x00, ATYP_DOMAIN, hostBytes.size.toByte()))
        out.write(hostBytes)
        out.write(byteArrayOf((port ushr 8).toByte(), (port and 0xFF).toByte()))
        out.flush()

        val header = inp.readN(4)
        if (header[0] != SOCKS5)   throw Socks5Exception("Wrong SOCKS version in response")
        if (header[1] != REPLY_OK) throw Socks5Exception("CONNECT failed: ${replyDesc(header[1])}")

        // Skip bound address
        when (header[3]) {
            0x01.toByte() -> inp.readN(4 + 2)        // IPv4 (4 bytes) + port (2)
            0x03.toByte() -> inp.readN(inp.read() + 2) // domain length + port
            0x04.toByte() -> inp.readN(16 + 2)       // IPv6 (16 bytes) + port
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    private fun InputStream.readN(n: Int): ByteArray {
        val buf = ByteArray(n)
        var offset = 0
        while (offset < n) {
            val read = read(buf, offset, n - offset)
            if (read < 0) throw IOException("Connection closed unexpectedly")
            offset += read
        }
        return buf
    }

    private fun replyDesc(code: Byte): String = when (code.toInt() and 0xFF) {
        0x01 -> "General SOCKS server failure"
        0x02 -> "Connection not allowed by ruleset"
        0x03 -> "Network unreachable"
        0x04 -> "Host unreachable"
        0x05 -> "Connection refused"
        0x06 -> "TTL expired"
        0x07 -> "Command not supported"
        0x08 -> "Address type not supported"
        else -> "Unknown (0x${code.hex()})"
    }

    private fun Byte.hex(): String = "%02X".format(this)
}

class Socks5Exception(message: String, cause: Throwable? = null) : IOException(message, cause)
class Socks5AuthException(message: String) : Socks5Exception(message)
