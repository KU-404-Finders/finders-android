package com.ku.lostandfound

import com.ku.lostandfound.network.*
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Real loopback sockets + the production OkHttp call factory, not a mocked interceptor chain. */
class DedicatedWithdrawalRedirectRegressionTest : StabilityFixture() {
    private data class Recorded(val method: String, val path: String, val authorization: String?)
    private class WireServer(val reply: (Recorded) -> Pair<Int, String?>) : AutoCloseable {
        private val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        val port get() = server.localPort
        val requests = CopyOnWriteArrayList<Recorded>()
        private val failure = AtomicReference<Throwable?>()
        @Volatile private var closed = false
        fun url(path: String, host: String = "127.0.0.1") = "http://$host:$port$path"
        private val worker = Thread({
            try {
                while (!closed) server.accept().use { socket ->
                    socket.soTimeout = 5_000
                    val reader = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                    val first = reader.readLine() ?: return@use
                    val parts = first.split(' ')
                    var authorization: String? = null
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        if (line.startsWith("Authorization:", ignoreCase = true)) authorization = line.substringAfter(':').trim()
                    }
                    val recorded = Recorded(parts[0], parts[1], authorization)
                    requests += recorded
                    val (status, location) = reply(recorded)
                    val headers = "HTTP/1.1 $status Test\r\n" +
                        (location?.let { "Location: $it\r\n" } ?: "") +
                        "Content-Length: 0\r\nConnection: close\r\n\r\n"
                    socket.getOutputStream().apply { write(headers.toByteArray(Charsets.ISO_8859_1)); flush() }
                }
            } catch (e: SocketException) { if (!closed) failure.set(e) }
            catch (e: Throwable) { failure.set(e) }
        }, "withdrawal-loopback-server").apply { isDaemon = true; start() }
        override fun close() {
            closed = true; server.close(); worker.join(5_000)
            assertFalse("Loopback server did not stop", worker.isAlive)
            assertNull(failure.get())
        }
    }
    private fun request(url: String, kind: WithdrawalTokenKind?) = Request.Builder().url(url).apply {
        if (kind != null) {
            delete()
            header("Authorization", "Bearer withdrawal-only")
            tag(WithdrawalRequest::class.java, WithdrawalRequest(TokenManager.sessionGeneration(), kind))
        }
    }.build()
    private fun execute(request: Request): Int {
        val call = RetrofitClient.newCall(request)
        call.timeout().timeout(5, TimeUnit.SECONDS)
        return call.execute().use { it.code }
    }

    @Test fun dedicatedSameHostRedirectDoesNotForwardCredentialToAnotherPath() {
        val location = AtomicReference("")
        WireServer { if (it.path == "/api/v1/auth/withdraw") 302 to location.get() else 200 to null }.use { server ->
            location.set(server.url("/ordinary-api"))
            assertEquals(302, execute(request(server.url("/api/v1/auth/withdraw"), WithdrawalTokenKind.DEDICATED)))
            assertEquals(1, server.requests.size)
            assertEquals(Recorded("DELETE", "/api/v1/auth/withdraw", "Bearer withdrawal-only"), server.requests.single())
        }
    }
    @Test fun dedicatedOtherHostRedirectMakesNoFollowUpRequest() {
        WireServer { 200 to null }.use { destination ->
            WireServer { 302 to destination.url("/ordinary-api", "localhost") }.use { source ->
                assertEquals(302, execute(request(source.url("/api/v1/auth/withdraw"), WithdrawalTokenKind.DEDICATED)))
                assertEquals(1, source.requests.size)
                assertTrue(destination.requests.isEmpty())
            }
        }
    }
    @Test fun dedicatedRedirectToHttpsMakesNoTlsFollowUp() {
        WireServer { 200 to null }.use { destination ->
            WireServer { 302 to "https://localhost:${destination.port}/ordinary-api" }.use { source ->
                assertEquals(302, execute(request(source.url("/api/v1/auth/withdraw"), WithdrawalTokenKind.DEDICATED)))
                assertEquals(1, source.requests.size)
                assertTrue(destination.requests.isEmpty())
            }
        }
    }
    @Test fun dedicatedOtherRedirectStatusCodesAlsoStopAtOriginalUrl() {
        for (status in listOf(301, 303, 307, 308)) {
            val location = AtomicReference("")
            WireServer { if (it.path == "/api/v1/auth/withdraw") status to location.get() else 200 to null }.use { source ->
                location.set(source.url("/ordinary-api"))
                assertEquals(status, execute(request(source.url("/api/v1/auth/withdraw"), WithdrawalTokenKind.DEDICATED)))
                assertEquals(1, source.requests.size)
            }
        }
    }
    @Test fun ordinaryApiKeepsExistingRedirectBehavior() {
        val location = AtomicReference("")
        WireServer { if (it.path == "/start") 302 to location.get() else 200 to null }.use { server ->
            location.set(server.url("/end"))
            assertEquals(200, execute(request(server.url("/start"), null)))
            assertEquals(listOf("/start", "/end"), server.requests.map { it.path })
            assertTrue(server.requests.all { it.authorization == "Bearer a" })
        }
    }
    @Test fun sessionWithdrawalKeepsExistingRedirectBehavior() {
        val location = AtomicReference("")
        WireServer { if (it.path == "/api/v1/auth/withdraw") 302 to location.get() else 200 to null }.use { server ->
            location.set(server.url("/end"))
            assertEquals(200, execute(request(server.url("/api/v1/auth/withdraw"), WithdrawalTokenKind.SESSION)))
            assertEquals(listOf("/api/v1/auth/withdraw", "/end"), server.requests.map { it.path })
            assertTrue(server.requests.all { it.authorization == "Bearer a" })
        }
    }
}
