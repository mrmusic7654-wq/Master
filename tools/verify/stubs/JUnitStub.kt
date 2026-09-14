/*
 * Minimal JUnit 4 stub used ONLY by the offline verification harness
 * (tools/verify/run-offline-checks.sh) together with
 * tools/verify/harness/TestRunner.kt.
 *
 * The authoritative build uses the real junit:junit:4.13.2 artifact from
 * Maven Central via Gradle. Keeping the same package/type names lets the real
 * test sources compile and run unmodified in both environments.
 */
package org.junit

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Test(val expected: kotlin.reflect.KClass<out Throwable> = None::class, val timeout: Long = 0L)

class None private constructor() : Throwable()

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Before

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class After

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class Ignore(val value: String = "")

object Assert {

    @JvmStatic
    fun assertTrue(message: String?, condition: Boolean) {
        if (!condition) throw AssertionError(message ?: "expected true but was false")
    }

    @JvmStatic
    fun assertTrue(condition: Boolean) = assertTrue(null, condition)

    @JvmStatic
    fun assertFalse(message: String?, condition: Boolean) {
        if (condition) throw AssertionError(message ?: "expected false but was true")
    }

    @JvmStatic
    fun assertFalse(condition: Boolean) = assertFalse(null, condition)

    @JvmStatic
    fun assertEquals(message: String?, expected: Any?, actual: Any?) {
        if (expected == null && actual == null) return
        if (expected != null && expected == actual) return
        throw AssertionError("${message?.let { "$it — " } ?: ""}expected:<$expected> but was:<$actual>")
    }

    @JvmStatic
    fun assertEquals(expected: Any?, actual: Any?) = assertEquals(null, expected, actual)

    @JvmStatic
    fun assertEquals(expected: Long, actual: Long) {
        if (expected != actual) throw AssertionError("expected:<$expected> but was:<$actual>")
    }

    @JvmStatic
    fun assertEquals(expected: Int, actual: Int) {
        if (expected != actual) throw AssertionError("expected:<$expected> but was:<$actual>")
    }

    @JvmStatic
    fun assertEquals(expected: Double, actual: Double, delta: Double) {
        if (kotlin.math.abs(expected - actual) > delta) {
            throw AssertionError("expected:<$expected> but was:<$actual> (delta $delta)")
        }
    }

    @JvmStatic
    fun assertEquals(expected: Float, actual: Float, delta: Float) {
        if (kotlin.math.abs(expected - actual) > delta) {
            throw AssertionError("expected:<$expected> but was:<$actual> (delta $delta)")
        }
    }

    @JvmStatic
    fun assertNotEquals(message: String?, unexpected: Any?, actual: Any?) {
        if (unexpected == actual) throw AssertionError(message ?: "values should be different but both were <$actual>")
    }

    @JvmStatic
    fun assertNotEquals(unexpected: Any?, actual: Any?) = assertNotEquals(null, unexpected, actual)

    @JvmStatic
    fun assertNull(message: String?, value: Any?) {
        if (value != null) throw AssertionError(message ?: "expected null but was <$value>")
    }

    @JvmStatic
    fun assertNull(value: Any?) = assertNull(null, value)

    @JvmStatic
    fun assertNotNull(message: String?, value: Any?) {
        if (value == null) throw AssertionError(message ?: "expected non-null value")
    }

    @JvmStatic
    fun assertNotNull(value: Any?) = assertNotNull(null, value)

    @JvmStatic
    fun assertSame(expected: Any?, actual: Any?) {
        if (expected !== actual) throw AssertionError("expected the same instance")
    }

    @JvmStatic
    fun assertArrayEquals(expected: Array<*>?, actual: Array<*>?) {
        if (!expected.contentEquals(actual)) {
            throw AssertionError("expected:<${expected?.contentToString()}> but was:<${actual?.contentToString()}>")
        }
    }

    @JvmStatic
    fun fail(message: String? = null): Nothing = throw AssertionError(message ?: "failed")
}
