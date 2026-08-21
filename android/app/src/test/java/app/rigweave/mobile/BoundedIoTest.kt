package app.rigweave.mobile

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class BoundedIoTest {
    @Test fun boundedReadStopsAtTheLimit() {
        assertArrayEquals(byteArrayOf(1, 2), ByteArrayInputStream(byteArrayOf(1, 2, 3)).readBoundedBytes(2))
    }

    @Test fun exactReadRejectsTruncatedStreams() {
        assertThrows(IllegalArgumentException::class.java) {
            ByteArrayInputStream(byteArrayOf(1)).readExactBytes(2)
        }
    }

    @Test fun boundedReadMakesProgressWhenAStreamReturnsZero() {
        val delegate = ByteArrayInputStream(byteArrayOf(4, 5, 6))
        var first = true
        val stream = object : InputStream() {
            override fun read(): Int = delegate.read()
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (first) { first = false; return 0 }
                return delegate.read(buffer, offset, length)
            }
        }
        assertArrayEquals(byteArrayOf(4, 5, 6), stream.readBoundedBytes(3))
    }
}
