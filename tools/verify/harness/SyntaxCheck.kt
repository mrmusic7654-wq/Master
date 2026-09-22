/*
 * Offline Kotlin syntax verification for the whole repository.
 *
 * Parses every `.kt` file with the real Kotlin front-end parser (PSI) and
 * reports syntax errors. This does NOT type-check (that requires the Android
 * SDK, Compose, Room, Hilt and Google Maven artifacts — see BUILD.md); it
 * catches the class of defects that are otherwise invisible until a full Gradle
 * build: unbalanced braces/parentheses, stray tokens, malformed declarations.
 *
 * Usage: java -cp kotlin-compiler.jar:kotlin-stdlib.jar:out mastercontrol.verify.SyntaxCheckKt <root...>
 * Exit code: 0 when no file has a syntax error, 1 otherwise.
 */
@file:OptIn(
    org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class,
    org.jetbrains.kotlin.CoreEnvironmentDeprecation::class,
    org.jetbrains.kotlin.config.CompilerConfiguration.Internals::class,
)

package mastercontrol.verify

import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.cli.FrontendConfigurationKeys
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.idea.KotlinLanguage
import java.io.File

private val IGNORED_PATH_SEGMENTS = listOf(
    "/third_party/",
    "/build/",
    "/.git/",
    "/tools/verify/stubs/",
    "/tools/verify/harness/",
)

fun main(args: Array<String>) {
    val roots = args.ifEmpty { arrayOf(".") }
    val files = roots
        .map { File(it) }
        .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
        .filter { file -> IGNORED_PATH_SEGMENTS.none { seg -> file.absolutePath.replace('\\', '/').contains(seg) } }
        .sortedBy { it.path }

    if (files.isEmpty()) {
        System.err.println("syntax-check: no Kotlin sources found under ${roots.joinToString()}")
        kotlin.system.exitProcess(2)
    }

    val disposable = Disposer.newDisposable()
    val configuration = CompilerConfiguration().apply {
        // K2 requires an extension storage to exist before a core environment is created.
        put(FrontendConfigurationKeys.EXTENSIONS_STORAGE, CompilerPluginRegistrar.ExtensionStorage())
    }
    val environment = KotlinCoreEnvironment.createForProduction(
        disposable,
        configuration,
        EnvironmentConfigFiles.JVM_CONFIG_FILES,
    )
    val factory = PsiFileFactory.getInstance(environment.project)

    var broken = 0
    var checked = 0
    for (file in files) {
        checked++
        val text = try {
            file.readText()
        } catch (t: Throwable) {
            println("READ-ERROR ${file.path}: ${t.message}")
            broken++
            continue
        }
        val psi = factory.createFileFromText(file.name, KotlinLanguage.INSTANCE, text)
        val errors = PsiTreeUtil.findChildrenOfType(psi, PsiErrorElement::class.java)
        if (errors.isNotEmpty()) {
            broken++
            println("SYNTAX ${file.path}")
            errors.take(6).forEach { e ->
                val offset = e.textOffset
                val line = text.substring(0, minOf(offset, text.length)).count { it == '\n' } + 1
                println("   line $line: ${e.errorDescription}")
            }
            if (errors.size > 6) println("   … ${errors.size - 6} more")
        }
    }
    Disposer.dispose(disposable)

    println()
    println("════════════════════════════════════════════════")
    println(" syntax-check: $checked files parsed, $broken with syntax errors")
    println("════════════════════════════════════════════════")
    if (broken > 0) kotlin.system.exitProcess(1)
}
