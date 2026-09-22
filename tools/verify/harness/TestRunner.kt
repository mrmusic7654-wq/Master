/*
 * Offline test runner for the verification harness.
 *
 * Discovers classes containing methods annotated with `org.junit.Test` on the
 * runtime classpath and executes them, printing a JUnit-style summary. This
 * lets the project's JVM unit tests run in environments where the real
 * junit:junit artifact and Gradle are unavailable (see tools/verify/README.md).
 *
 * Exit code: 0 when every test passed, 1 otherwise.
 */
package mastercontrol.verify

import java.io.File
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier

private val TEST_ANNOTATION = "org.junit.Test"
private val BEFORE_ANNOTATION = "org.junit.Before"
private val AFTER_ANNOTATION = "org.junit.After"
private val IGNORE_ANNOTATION = "org.junit.Ignore"

fun main(args: Array<String>) {
    val roots = args.ifEmpty { arrayOf("build/offline-classes") }
    val classNames = roots.flatMap { root -> classNamesIn(File(root)) }.distinct().sorted()
    if (classNames.isEmpty()) {
        System.err.println("offline-tests: no classes found under ${roots.joinToString()}")
        kotlin.system.exitProcess(2)
    }

    var executed = 0
    var failed = 0
    var skipped = 0
    val failures = mutableListOf<String>()

    for (className in classNames) {
        val klass = try {
            Class.forName(className, false, Thread.currentThread().contextClassLoader)
        } catch (t: Throwable) {
            continue
        }
        if (klass.isInterface || Modifier.isAbstract(klass.modifiers)) continue
        if (klass.isAnnotationPresent(annotationClass(IGNORE_ANNOTATION))) {
            skipped += klass.declaredMethods.count { it.isTest() }
            continue
        }
        val tests = klass.declaredMethods.filter { it.isTest() }.sortedBy { it.name }
        if (tests.isEmpty()) continue

        println("\n── $className")
        for (method in tests) {
            if (method.isAnnotationPresent(annotationClass(IGNORE_ANNOTATION))) {
                skipped++
                println("   ○ skipped: ${method.name}")
                continue
            }
            executed++
            val started = System.nanoTime()
            val result = runTest(klass, method)
            val millis = (System.nanoTime() - started) / 1_000_000
            when (result) {
                null -> println("   ✓ ${method.name} (${millis}ms)")
                else -> {
                    failed++
                    println("   ✗ ${method.name} (${millis}ms) → ${describe(result)}")
                    failures += "$className.${method.name}: ${describe(result)}"
                }
            }
        }
    }

    println()
    println("════════════════════════════════════════════════")
    println(" offline tests: $executed run, $failed failed, $skipped skipped")
    println("════════════════════════════════════════════════")
    if (failures.isNotEmpty()) {
        println()
        failures.forEach { println(" FAILED  $it") }
        kotlin.system.exitProcess(1)
    }
}

private fun runTest(klass: Class<*>, method: Method): Throwable? = try {
    val instance = klass.getDeclaredConstructor().newInstance()
    klass.declaredMethods.filter { it.isAnnotationPresent(annotationClass(BEFORE_ANNOTATION)) }
        .forEach { it.apply { isAccessible = true }.invoke(instance) }
    method.apply { isAccessible = true }.invoke(instance)
    klass.declaredMethods.filter { it.isAnnotationPresent(annotationClass(AFTER_ANNOTATION)) }
        .forEach { it.apply { isAccessible = true }.invoke(instance) }
    null
} catch (e: InvocationTargetException) {
    e.targetException ?: e
} catch (e: Throwable) {
    e
}

private fun Method.isTest(): Boolean = isAnnotationPresent(annotationClass(TEST_ANNOTATION))

private fun annotationClass(fqName: String): Class<out Annotation> {
    @Suppress("UNCHECKED_CAST")
    return Class.forName(fqName) as Class<out Annotation>
}

private fun describe(t: Throwable): String {
    val message = t.message ?: t.javaClass.simpleName
    val location = t.stackTrace.firstOrNull { it.className.startsWith("com.mastercontrol") }
    return if (location == null) message else "$message (at ${location.className}:${location.lineNumber})"
}

private fun classNamesIn(root: File): List<String> {
    if (!root.exists()) return emptyList()
    return root.walkTopDown()
        .filter { it.isFile && it.name.endsWith(".class") && !it.name.contains('$') }
        .map { file ->
            file.relativeTo(root).path
                .removeSuffix(".class")
                .replace(File.separatorChar, '.')
        }
        .toList()
}
