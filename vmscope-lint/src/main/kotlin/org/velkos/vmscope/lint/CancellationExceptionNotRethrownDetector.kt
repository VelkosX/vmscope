package org.velkos.vmscope.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClassType
import org.jetbrains.uast.UCatchClause
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UThrowExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

public class CancellationExceptionNotRethrownDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCatchClause::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitCatchClause(node: UCatchClause) {
                val evaluator = context.evaluator
                val caughtCex = node.types.any { type ->
                    val cls = (type as? PsiClassType)?.resolve() ?: return@any false
                    evaluator.extendsClass(cls, CANCELLATION_JAVA, strict = false) ||
                        evaluator.extendsClass(cls, CANCELLATION_KOTLIN, strict = false)
                }
                if (!caughtCex) return

                val parameters = node.parameters
                val paramName = parameters.firstOrNull()?.name ?: return

                if (bodyRethrowsParameter(node, paramName)) return

                val body = node.body
                val fix = LintFix.create()
                    .name("Rethrow $paramName")
                    .replace()
                    .range(context.getLocation(body))
                    .text("{")
                    .with("{ throw $paramName;")
                    .build()

                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "`CancellationException` must be rethrown to preserve coroutine cancellation",
                    fix,
                )
            }
        }

    private fun bodyRethrowsParameter(node: UCatchClause, paramName: String): Boolean {
        val body = node.body
        var found = false
        body.accept(object : AbstractUastVisitor() {
            override fun visitThrowExpression(node: UThrowExpression): Boolean {
                val thrown = node.thrownExpression
                val identifier = (thrown as? UReferenceExpression)?.resolvedName
                if (identifier == paramName) found = true
                return super.visitThrowExpression(node)
            }
        })
        return found
    }

    public companion object {
        private const val CANCELLATION_JAVA = "java.util.concurrent.CancellationException"
        private const val CANCELLATION_KOTLIN = "kotlin.coroutines.cancellation.CancellationException"

        @JvmField
        public val ISSUE: Issue = Issue.create(
            id = "CancellationExceptionNotRethrown",
            briefDescription = "CancellationException caught but not rethrown",
            explanation = """
                Catching `CancellationException` (or a subtype like `TimeoutCancellationException`) \
                without rethrowing it suppresses structured-concurrency cancellation. The coroutine \
                machinery relies on this exception propagating to cancel parents and siblings.

                Always rethrow:
                ```
                try {
                    doWork()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    // handle
                }
                ```
            """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                CancellationExceptionNotRethrownDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
