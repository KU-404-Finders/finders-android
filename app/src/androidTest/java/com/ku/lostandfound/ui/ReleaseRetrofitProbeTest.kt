package com.ku.lostandfound.ui

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dalvik.system.DexClassLoader
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.Properties
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Opt-in fixture for an already-built APK. The isolated loader runs its original R8 DEX,
 * without adding test keep rules. A synthetic OkHttp response stops every call before network I/O.
 */
@RunWith(AndroidJUnit4::class)
class ReleaseRetrofitProbeTest {
    @Test fun releaseWireConversion() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val mode = InstrumentationRegistry.getArguments().getString("releaseProbe")
        assumeTrue(mode == "before" || mode == "after")
        val directory = File(instrumentation.targetContext.filesDir, "release-probe")
        val symbols = Properties().apply { File(directory, "symbols.properties").inputStream().use(::load) }
        val apk = File(directory, "release.apk")
        check(apk.setReadOnly() || !apk.canWrite())
        val loader = DexClassLoader(apk.path, instrumentation.targetContext.codeCacheDir.path, null,
            ClassLoader.getSystemClassLoader().parent)
        Probe(loader, symbols, instrumentation.targetContext).run(mode == "before")
    }

    private class Probe(val loader: ClassLoader, val symbols: Properties, val context: android.content.Context) {
        private val network = "com.ku.lostandfound.network."
        private fun type(name: String) = loader.loadClass(checkNotNull(symbols.getProperty(name)) { name })
        private fun name(owner: String, member: String) =
            checkNotNull(symbols.getProperty("$owner@$member")) { "$owner#$member" }
        private fun methodName(owner: String, member: String) = name(owner, "$member()")
        private fun field(owner: String, member: String) =
            type(owner).getDeclaredField(name(owner, member)).apply { isAccessible = true }
        private fun value(owner: String, member: String, instance: Any? = null): Any? =
            field(owner, member).get(instance)
        private fun dtoField(instance: Any, member: String): Any? =
            instance.javaClass.getDeclaredField(member).apply { isAccessible = true }.get(instance)
        private val clientOwner = network + "RetrofitClient"
        private val calls = AtomicInteger()
        private var code = 200
        private var json = ""
        // R8 inlines Buffer's empty constructor. Its two fields start at JVM defaults.
        private fun allocate(type: Class<*>): Any {
            val unsafeType = Class.forName("sun.misc.Unsafe")
            val unsafe = unsafeType.declaredFields.first { it.type == unsafeType }
                .apply { isAccessible = true }.get(null)
            return checkNotNull(unsafeType.getMethod("allocateInstance", Class::class.java).invoke(unsafe, type))
        }
        private fun syntheticResponse(chain: Any): Any {
            calls.incrementAndGet()
            val bufferType = type("okio.Buffer")
            val buffer = allocate(bufferType)
            bufferType.getDeclaredMethod(methodName("okio.Buffer", "writeUtf8"), String::class.java)
                .apply { isAccessible = true }.invoke(buffer, json)
            val bodyConstructor = type("okhttp3.internal.http.RealResponseBody").declaredConstructors
                .single { it.parameterCount == 3 || it.parameterCount == 4 }.apply { isAccessible = true }
            val length = json.toByteArray().size.toLong()
            val body = if (bodyConstructor.parameterCount == 4)
                bodyConstructor.newInstance(null, length, buffer, 1.toByte())
            else bodyConstructor.newInstance(null, length, buffer)
            val builderName = "okhttp3.Response\$Builder"
            val builder = type(builderName).getDeclaredConstructor().newInstance()
            field(builderName, "request").set(builder, value("okhttp3.internal.http.RealInterceptorChain", "request", chain))
            field(builderName, "protocol").set(builder, value("okhttp3.Protocol", "HTTP_1_1"))
            field(builderName, "code").set(builder, code)
            field(builderName, "message").set(builder, "fixture")
            field(builderName, "body").set(builder, body)
            return type(builderName).getDeclaredMethod(methodName(builderName, "build"))
                .apply { isAccessible = true }.invoke(builder)!!
        }

        private fun call(api: String, service: String, endpoint: String, vararg args: Any): Any {
            val fullService = network + service
            val method = type(fullService).declaredMethods.single { it.name == methodName(fullService, endpoint) }
                .apply { isAccessible = true }
            val continuation = "kotlin.coroutines.Continuation"
            val results = ArrayBlockingQueue<Any>(1)
            val callback = Proxy.newProxyInstance(loader, arrayOf(type(continuation))) { _, invoked, inputs ->
                when (invoked.name) {
                    methodName(continuation, "getContext") -> value("kotlin.coroutines.EmptyCoroutineContext", "INSTANCE")
                    methodName(continuation, "resumeWith") -> { results.add(inputs[0]); null }
                    else -> error(invoked.name)
                }
            }
            val input = when {
                args.isEmpty() || method.parameterTypes[0].isPrimitive -> args
                else -> arrayOf(method.parameterTypes[0].getDeclaredConstructor(*Array(args.size) { String::class.java })
                    .apply { isAccessible = true }.newInstance(*args))
            }
            val immediate = try {
                method.invoke(value(clientOwner, api), *input, callback)
            } catch (failure: InvocationTargetException) { throw failure.targetException }
            val result = if (type("retrofit2.Response").isInstance(immediate)) immediate
                else checkNotNull(results.poll(15, TimeUnit.SECONDS)) { "No result for $endpoint" }
            if (type("kotlin.Result\$Failure").isInstance(result)) {
                throw value("kotlin.Result\$Failure", "exception", result) as Throwable
            }
            return result!!
        }

        fun run(before: Boolean) {
            val interceptor = Proxy.newProxyInstance(loader, arrayOf(type("okhttp3.Interceptor"))) { _, _, arguments ->
                try {
                    syntheticResponse(arguments[0])
                } catch (failure: Exception) {
                    throw java.io.IOException("Cannot build synthetic response", failure)
                }
            }
            field("okhttp3.OkHttpClient", "interceptors").set(value(clientOwner, "okHttpClient"), listOf(interceptor))
            fun login() = call("authApi", "AuthApiService", "login", "fixture@example.invalid", "Fixture1")
            fun reset(operation: String, vararg args: String) =
                call("passwordResetApi", "PasswordResetApiService", operation, *args)
            fun body(response: Any) = checkNotNull(value("retrofit2.Response", "body", response))
            json = """{"success":true,"message":"ok","data":{"accessToken":"fixture-a","refreshToken":"fixture-r"}}"""
            if (before) {
                fun failsAsAbstract(operation: () -> Any) {
                    val error = runCatching(operation).exceptionOrNull() ?: error("Expected Gson failure")
                    assertTrue(error.toString(), type("com.google.gson.JsonIOException").isInstance(error))
                    assertTrue(error.message.orEmpty(), error.message.orEmpty().contains("Abstract"))
                    Log.i("ReleaseRetrofitProbe", error.toString() + " at " + error.stackTrace.take(8).joinToString())
                }
                failsAsAbstract(::login)
                json = """{"success":true,"message":"sent"}"""
                failsAsAbstract { reset("requestReset", "fixture@example.invalid") }
                assertEquals(2, calls.get())
                return
            }
            val loginData = checkNotNull(dtoField(body(login()), "data"))
            assertEquals("fixture-a", dtoField(loginData, "accessToken"))
            for (status in listOf(400, 401, 403, 429)) {
                code = status
                json = """{"success":false,"message":"fixture","errors":{"email":"invalid"}}"""
                val response = login()
                assertNull(value("retrofit2.Response", "body", response))
                val raw = value("retrofit2.Response", "rawResponse", response)
                assertEquals(status, value("okhttp3.Response", "code", raw))
            }
            code = 200
            json = """{"success":true,"message":"sent"}"""
            assertEquals(true, dtoField(body(reset("requestReset", "fixture@example.invalid")), "success"))
            json = """{"success":true,"data":{"resetToken":"fixture-reset","expiresInSeconds":120}}"""
            val verified = body(reset("verifyCode", "fixture@example.invalid", "123456"))
            assertEquals("fixture-reset", dtoField(checkNotNull(dtoField(verified, "data")), "resetToken"))
            json = """{"success":true,"message":"changed"}"""
            assertEquals(true, dtoField(body(reset("confirmReset", "fixture-reset", "Fixture1", "Fixture1")), "success"))
            json = """{"success":true,"data":[{"id":7,"creatorId":1,"recipientId":2,"createdAt":"fixture","unreadMessageCount":4}]}"""
            val envelope = body(call("chatApi", "ChatApiService", "getRooms", 0))
            assertEquals(4, dtoField((dtoField(envelope, "data") as List<*>).single()!!, "unreadMessageCount"))
            assertEquals(9, calls.get())

            val tokens = network + "TokenManager"
            val fixturePrefs = context.getSharedPreferences("release-probe-fixture", 0)
            try {
                fixturePrefs.edit().putString("access_token", "fixture-old-a")
                    .putString("refresh_token", "fixture-old-r").commit()
                field(tokens, "prefs").set(null, fixturePrefs)
                field("okhttp3.OkHttpClient", "interceptors").set(value(clientOwner, "refreshClient"), listOf(interceptor))
                val generation = type(tokens).getDeclaredMethod(methodName(tokens, "sessionGeneration"))
                    .apply { isAccessible = true }.invoke(value(tokens, "INSTANCE"))
                json = """{"success":true,"data":{"accessToken":"fixture-new-a","refreshToken":"fixture-new-r"}}"""
                val refresh = type(clientOwner).getDeclaredMethod(methodName(clientOwner, "refreshTokensBlocking"),
                    Long::class.javaPrimitiveType, String::class.java).apply { isAccessible = true }
                assertEquals("fixture-new-a", refresh.invoke(value(clientOwner, "INSTANCE"), generation, "fixture-old-a"))
                assertEquals("fixture-new-r", fixturePrefs.getString("refresh_token", null))
                assertEquals(10, calls.get())
            } finally {
                fixturePrefs.edit().clear().commit()
            }
        }
    }
}
