package org.velkos.vmscope.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

public class VmScopeIssueRegistry : IssueRegistry() {
    override val issues: List<Issue> = listOf(
        MissingVmScopeConfigProviderDetector.ISSUE,
        CancellationExceptionNotRethrownDetector.ISSUE,
        CatchingThrowableInCoroutineDetector.ISSUE,
        UseVmScopeDetector.ISSUE,
    )

    override val api: Int = CURRENT_API
    override val minApi: Int = 8

    override val vendor: Vendor = Vendor(
        vendorName = "vmscope",
        feedbackUrl = "https://github.com/VelkosX/vmscope/issues",
        contact = "https://github.com/VelkosX/vmscope",
    )
}
