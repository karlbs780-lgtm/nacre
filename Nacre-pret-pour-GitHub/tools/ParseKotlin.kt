import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.io.File

/** Syntax-only PSI validation. This is NOT Android type-checking or an APK build. */
fun main(args: Array<String>) {
    val disposable = Disposer.newDisposable()
    try {
        val environment = KotlinCoreEnvironment.createForProduction(disposable, CompilerConfiguration(), EnvironmentConfigFiles.JVM_CONFIG_FILES)
        val factory = KtPsiFactory(environment.project, false)
        val files = File(args.first()).walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "kts") }.toList()
        var errors = 0
        files.forEach { file ->
            val psi = factory.createFile(file.name, file.readText())
            PsiTreeUtil.collectElementsOfType(psi, PsiErrorElement::class.java).forEach {
                System.err.println("${file.path}:${it.textOffset}: ${it.errorDescription}"); errors++
            }
        }
        println("Kotlin PSI: ${files.size} files; $errors syntax error(s). No Android/API type-check performed.")
        check(errors == 0)
    } finally { Disposer.dispose(disposable) }
}
