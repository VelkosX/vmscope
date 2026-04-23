package org.velkos.vmscope

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * Common-test helper: install a [TestDispatcher] as Dispatchers.Main before the test, and clean
 * up afterward. Each platform runs this identically.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherSetup(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) {
    fun install() {
        Dispatchers.setMain(dispatcher)
    }

    fun tearDown() {
        Dispatchers.resetMain()
    }
}
