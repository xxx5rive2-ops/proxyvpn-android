package app.netguard.engine.proxy

import app.netguard.domain.entity.ProxyAuthentication
import app.netguard.domain.entity.ProxyServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Socks5Connector — SOCKS5 client (RFC 1928 + RFC 1929).
 * Blocking I/O on Dispatchers.IO, wrapped in coroutine.
 */
object Socks5Connector {

    private const val VER: Byte    = 5
    private const val NOAUTH: Byte = 0
    private const val UPAUTH: Byte = 2
    private const val NOACCEPT: Byte = 0xFF.toByte()
    private const val CONNECT: Byte = 1
    private const val DOMAIN: Byte  = 3
    private const val OK: Byte      = 0
    private const val AUTHVER: Byte = 1

    suspend fun connect(server: ProxyServer, host: String, port: Int): Socket =
        withContext(Dispatchers.IO) {
            val sock = Socket()
            try {
                sock.connect(InetSocketAddress(server.host, server.port), 10_000)
                sock.soTimeout = 10_000
                val inp = sock.getInputStream()
                val out = sock.getOutputStream()

                // Greeting
                val hasAuth = server.authentication is ProxyAuthentication.UserPassword
                out.write(byteArrayOf(VER, if (hasAuth) 2 else 1, NOAUTH))
                if (hasAuth) out.write(byteArrayOf(UPAUTH))
                out.flush()

                val greetResp = inp.readExactly(2)
                check(greetResp[0] == VER) { "Wrong SOCKS version: ${greetResp[0]}" }
                check(greetResp[1] != NOACCEPT) { "No acceptable auth methods" }

                // Auth if needed
                if (greetResp[1] == UPAUTH) {
                    val creds = server.authentication as ProxyAuthentication.UserPassword
                    val user = creds.username.toByteArray(Charsets.UTF_8)
                    val pass = creds.password.toByteArray(Charsets.UTF_8)
                    check(user.size <= 255 && pass.size <= 255) { "Credentials too long" }
                    out.write(byteArrayOf(AUTHVER, user.size.toByte()))
                    out.write(user)
                    out.write(byteArrayOf(pass.size.toByte()))
                    out.write(pass)
                    out.flush()
                    pass.fill(0.toByte())
                    val authResp = inp.readExactly(2)
                    check(authResp[1] == OK) { "SOCKS5 auth rejected" }
                }

                // Connect request
                val hostBytes = host.toByteArray(Charsets.UTF_8)
                check(hostBytes.size <= 255) { "Hostname too long" }
                out.write(byteArrayOf(VER, CONNECT, 0, DOMAIN, hostBytes.size.toByte()))
                out.write(hostBytes)
                out.write(byteArrayOf((port ushr 8).toByte(), (port and 0xFF).toByte()))
                out.flush()

                val resp = inp.readExactly(4)
                check(resp[0] == VER) { "Wrong version in connect response" }
                check(resp[1] == OK)  { "CONNECT failed: code=${resp[1].toInt() and 0xFF}" }

                // Discard bound address
                when (resp[3]) {
                    1.toByte()  -> inp.readExactly(6)
                    3.toByte()  -> inp.readExactly(inp.read() + 2)
                    4.toByte()  -> inp.readExactly(18)
                }

                Timber.d("SOCKS5 connected → $host:$port via ${server.host}:${server.port}")
                sock
            } catch (e: Exception) {
                runCatching { sock.close() }
                throw Socks5Exception("SOCKS5 connect failed: ${e.message}", e)
            }
        }

    private fun InputStream.readExactly(n: Int): ByteArray {
        val buf = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = read(buf, off, n - off)
            if (r < 0) throw IOException("Stream closed after $off of $n bytes")
            off += r
        }
        return buf
    }
}

class Socks5Exception(msg: String, cause: Throwable? = null) : IOException(msg, cause)
