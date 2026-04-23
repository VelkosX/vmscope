package org.velkos.vmscope.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UClass

public class MissingVmScopeConfigProviderDetector : Detector(), SourceCodeScanner {

    private var sawApplication = false
    private var sawProvider = false
    private var firstAppLocation: Location? = null

    override fun beforeCheckRootProject(context: Context) {
        sawApplication = false
        sawProvider = false
        firstAppLocation = null
    }

    override fun getApplicableUastTypes(): List<Class<out org.jetbrains.uast.UElement>> =
        listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitClass(node: UClass) {
                val evaluator = context.evaluator
                val isApp = evaluator.inheritsFrom(node, APPLICATION_FQN, strict = false)
                if (!isApp) return
                // Ignore the Application class itself — we're looking at user subclasses.
                val qn = node.qualifiedName ?: return
                if (qn == APPLICATION_FQN) return

                sawApplication = true
                if (firstAppLocation == null) {
                    firstAppLocation = context.getNameLocation(node)
                }
                if (
                    evaluator.inheritsFrom(node, PROVIDER_FQN_DOT, strict = false) ||
                    evaluator.inheritsFrom(node, PROVIDER_FQN_DOLLAR, strict = false)
                ) {
                    sawProvider = true
                }
            }
        }

    override fun afterCheckRootProject(context: Context) {
        if (sawApplication && !sawProvider) {
            val location = firstAppLocation ?: Location.create(context.project.dir)
            context.report(ISSUE, location, MESSAGE)
        }
    }

    public companion object {
        private const val APPLICATION_FQN = "android.app.Application"
        private const val PROVIDER_FQN_DOT =
            "org.velkos.vmscope.VmScopeConfig.Provider"
        private const val PROVIDER_FQN_DOLLAR =
            "org.velkos.vmscope.VmScopeConfig\$Provider"

        private val MESSAGE = """
            This module declares an `Application` subclass that does not implement \
            `VmScopeConfig.Provider`. vmScope will install an empty configuration, and any \
            uncaught exception in a `vmScope` coroutine will crash release builds.

            Implement the provider on your Application:
            ```
            class MyApp : Application(), VmScopeConfig.Provider {
                override val vmScopeConfiguration = vmScopeConfig {
                    onUnhandledException { e -> reportToCrashlytics(e) }
                }
            }
            ```

            If you instead initialize vmScope manually (via `VmScope.install(...)`), suppress \
            this check on the Application class with `@Suppress("MissingVmScopeConfigProvider")`.
        """.trimIndent().replace("\\\n", "")

        @JvmField
        public val ISSUE: Issue = Issue.create(
            id = "MissingVmScopeConfigProvider",
            briefDescription = "Application does not implement VmScopeConfig.Provider",
            explanation = MESSAGE,
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.FATAL,
            implementation = Implementation(
                MissingVmScopeConfigProviderDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
