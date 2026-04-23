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
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCatchClause
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UThrowExpression
import org.jetbrains.uast.getContainingUMethod
import org.jetbrains.uast.visitor.AbstractUastVisitor

public class CatchingThrowableInCoroutineDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCatchClause::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitCatchClause(node: UCatchClause) {
                val evaluator = context.evaluator

                val caughtBroadly = node.types.any { type ->
                    val cls = (type as? PsiClassType)?.resolve() ?: return@any false
                    val qn = cls.qualifiedName
                    qn == THROWABLE_FQN ||
                        qn == EXCEPTION_FQN ||
                        qn == RUNTIME_EXCEPTION_FQN
                }
                if (!caughtBroadly) return

                if (!isInsideCoroutineContext(node)) return

                val paramName = node.parameters.firstOrNull()?.name ?: return

                if (bodyHasRethrowOfParameter(node, paramName) ||
                    bodyHasCancellationGuard(node, paramName)
                ) return

                val body = node.body
                val fix = LintFix.create()
                    .name("Add CancellationException guard")
                    .replace()
                    .range(context.getLocation(body))
                    .text("{")
                    .with("{ if ($paramName is kotlin.coroutines.cancellation.CancellationException) throw $paramName;")
                    .build()

                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Catching `Throwable`/`Exception` in a coroutine context swallows " +
                        "`CancellationException`. Add `if ($paramName is CancellationException) throw $paramName` " +
                        "at the top of the catch block.",
                    fix,
                )
            }
        }

    private fun isInsideCoroutineContext(node: UCatchClause): Boolean {
        // Case 1: enclosing Kotlin function is `suspend`.
        val method = node.getContainingUMethod()
        val ktFun = method?.sourcePsi as? KtNamedFunction
        if (ktFun?.hasModifier(KtTokens.SUSPEND_KEYWORD) == true) return true

        // Case 2: enclosing lambda is passed to a known coroutine builder.
        var cur: UElement? = node.uastParent
        while (cur != null) {
            if (cur is ULambdaExpression) {
                val parent = cur.uastParent
                if (parent is UCallExpression && isCoroutineBuilder(parent)) return true
            }
            cur = cur.uastParent
        }
        return false
    }

    private fun isCoroutineBuilder(call: UCallExpression): Boolean {
        val name = call.methodName ?: call.methodIdentifier?.name ?: return false
        if (name !in BUILDER_NAMES) return false
        // Name match is enough for common practice; a stricter FQN check would require resolving,
        // which lint does transparently when deriving `methodName`. Users who name a local
        // function `launch` get a false positive — acceptable for a Warning-severity rule.
        return true
    }

    private fun bodyHasRethrowOfParameter(node: UCatchClause, paramName: String): Boolean {
        var found = false
        node.body.accept(object : AbstractUastVisitor() {
            override fun visitThrowExpression(node: UThrowExpression): Boolean {
                val thrown = node.thrownExpression
                if ((thrown as? UReferenceExpression)?.resolvedName == paramName) found = true
                return super.visitThrowExpression(node)
            }
        })
        return found
    }

    private fun bodyHasCancellationGuard(node: UCatchClause, paramName: String): Boolean {
        // Any `throw <paramName>` is enough — unconditional rethrow is fine, and the typical
        // cancellation guard `if (x is CancellationException) throw x` also contains one.
        return bodyHasRethrowOfParameter(node, paramName)
    }

    public companion object {
        private const val THROWABLE_FQN = "java.lang.Throwable"
        private const val EXCEPTION_FQN = "java.lang.Exception"
        private const val RUNTIME_EXCEPTION_FQN = "java.lang.RuntimeException"

        private val BUILDER_NAMES = setOf(
            "launch",
            "async",
            "runBlocking",
            "withContext",
            "withTimeout",
            "withTimeoutOrNull",
            "coroutineScope",
            "supervisorScope",
            "flow",
            "channelFlow",
            "callbackFlow",
            "produce",
            "actor",
        )

        @JvmField
        public val ISSUE: Issue = Issue.create(
            id = "CatchingThrowableInCoroutine",
            briefDescription = "Unguarded broad catch in coroutine",
            explanation = """
                Catching `Throwable` or `Exception` inside a coroutine context (a suspend \
                function, a coroutine builder like `launch`/`async`, or a scope like \
                `coroutineScope {}`) will also catch `CancellationException`, which breaks \
                cooperative cancellation.

                Always guard:
                ```
                try {
                    doWork()
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    // handle / log
                }
                ```
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                CatchingThrowableInCoroutineDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
