package app.netguard.engine.vpn

import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue

/**
 * PacketBuffer — zero-copy buffer pool for TUN packet processing.
 *
 * Pre-allocates a fixed pool of direct ByteBuffers to avoid GC pressure
 * in the hot packet-processing path. Every packet read from the TUN fd
 * acquires a buffer, is processed, then released back to the pool.
 *
 * Design:
 * - Direct ByteBuffers (off-heap) avoid Java GC entirely
 * - Pool size 256 × 1500 bytes = ~384 KB pre-allocated
 * - ArrayBlockingQueue is thread-safe without explicit locks
 * - acquire() blocks if pool is empty (back-pressure mechanism)
 *
 * Thread safety: fully thread-safe — pool is backed by ArrayBlockingQueue.
 */
class PacketBuffer(
    poolSize: Int = DEFAULT_POOL_SIZE,
    val mtu: Int = DEFAULT_MTU,
) {
    private val pool = ArrayBlockingQueue<ByteBuffer>(poolSize)

    init {
        repeat(poolSize) {
            pool.offer(ByteBuffer.allocateDirect(mtu))
        }
    }

    /**
     * Acquire a buffer from the pool.
     * Blocks if pool is exhausted (acts as back-pressure).
     * Caller MUST call [release] when done.
     */
    fun acquire(): ByteBuffer {
        val buf = pool.take() // blocks if empty
        buf.clear()
        return buf
    }

    /**
     * Release a buffer back to the pool.
     * Must be called exactly once per [acquire].
     */
    fun release(buffer: ByteBuffer) {
        buffer.clear()
        pool.offer(buffer)
    }

    /** Current number of available buffers */
    val available: Int get() = pool.size

    companion object {
        const val DEFAULT_POOL_SIZE = 256
        const val DEFAULT_MTU = 1500
    }
}
