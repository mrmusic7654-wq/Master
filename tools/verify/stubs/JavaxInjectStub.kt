/*
 * Minimal javax.inject stub used ONLY by the offline verification harness
 * (tools/verify/run-offline-checks.sh). Not part of any Gradle source set:
 * the real artifact is resolved by Gradle from Maven Central.
 */
package javax.inject

@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.CLASS,
)
annotation class Inject

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class Singleton

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.ANNOTATION_CLASS)
annotation class Qualifier

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Named(val value: String = "")

fun interface Provider<T> {
    fun get(): T
}
