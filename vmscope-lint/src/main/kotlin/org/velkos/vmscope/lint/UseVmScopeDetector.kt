package org.velkos.vmscope.lint

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.getContainingUFile
import org.jetbrains.uast.visitor.AbstractUastVisitor

public class UseVmScopeDetector : Detector(), SourceCodeScanner {

    override fun getApplicableReferenceNames(): List<String> = listOf(REFERENCE_NAME)

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement,
    ) {
        val method = referenced as? PsiMethod ?: return
        // Kotlin extension-property getters compile to `getViewModelScope` on the file class, and
        // the first parameter is the receiver. Identify the extension by receiver type rather
        // than containing file name (which varies between AGP's view of the jar and stubbed tests).
        if (method.name != "getViewModelScope") return
        val receiverType = method.parameterList.parameters.firstOrNull()?.type?.canonicalText
        if (receiverType != VIEW_MODEL_FQN) return

        val replaceFix = LintFix.create()
            .name("Replace with vmScope")
            .replace()
            .range(context.getLocation(reference))
            .text(REFERENCE_NAME)
            .with(REPLACEMENT)
            .imports(VM_SCOPE_FQN)
            .build()

        // If this is the last `viewModelScope` reference in the file, also strip its now-unused
        // import. Fixing earlier references in a file with multiple refs leaves the import alone
        // so the remaining unfixed references still resolve.
        val fix = importRemovalFix(context, reference)
            ?.let { removeImport ->
                LintFix.create()
                    .name("Replace with vmScope")
                    .composite(replaceFix, removeImport)
            }
            ?: replaceFix

        context.report(
            ISSUE,
            reference,
            context.getLocation(reference),
            "Prefer `vmScope` so uncaught exceptions are routed through `VmScopeConfig`",
            fix,
        )
    }

    private fun importRemovalFix(
        context: JavaContext,
        reference: UReferenceExpression,
    ): LintFix? {
        val file = reference.getContainingUFile() ?: return null
        if (countMatchingReferences(file) > 1) return null

        val importStmt = file.imports.firstOrNull { importStmt ->
            importStmt.asSourceString().contains(VIEW_MODEL_SCOPE_FQN)
        } ?: return null

        return LintFix.create()
            .replace()
            .range(context.getLocation(importStmt))
            .with("")
            .build()
    }

    private fun countMatchingReferences(file: org.jetbrains.uast.UFile): Int {
        var count = 0
        file.accept(object : AbstractUastVisitor() {
            override fun visitSimpleNameReferenceExpression(
                node: USimpleNameReferenceExpression,
            ): Boolean {
                if (node.identifier == REFERENCE_NAME) {
                    val resolved = node.resolve() as? PsiMethod
                    if (resolved?.name == "getViewModelScope" &&
                        resolved.parameterList.parameters.firstOrNull()?.type?.canonicalText == VIEW_MODEL_FQN
                    ) {
                        count++
                    }
                }
                return super.visitSimpleNameReferenceExpression(node)
            }
        })
        return count
    }

    public companion object {
        private const val REFERENCE_NAME = "viewModelScope"
        private const val REPLACEMENT = "vmScope"
        private const val VIEW_MODEL_FQN = "androidx.lifecycle.ViewModel"
        private const val VIEW_MODEL_SCOPE_FQN = "androidx.lifecycle.viewModelScope"
        private const val VM_SCOPE_FQN = "org.velkos.vmscope.vmScope"

        @JvmField
        public val ISSUE: Issue = Issue.create(
            id = "UseVmScope",
            briefDescription = "Prefer vmScope over viewModelScope",
            explanation = """
                This module uses vmscope. Prefer `vmScope` over `viewModelScope` so uncaught \
                exceptions in ViewModel coroutines flow through your configured handler rather \
                than crashing silently or inconsistently.

                The quick fix replaces the reference, adds the required import, and (when this \
                is the last `viewModelScope` reference in the file) removes the now-unused \
                `androidx.lifecycle.viewModelScope` import.
            """,
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = Implementation(
                UseVmScopeDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
