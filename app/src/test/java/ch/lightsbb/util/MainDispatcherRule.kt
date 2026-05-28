package ch.lightsbb.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit4 [org.junit.rules.TestRule] that replaces [Dispatchers.Main] with a
 * [TestDispatcher] for the duration of each test.
 *
 * ## Why this is needed
 * [androidx.lifecycle.ViewModel.viewModelScope] launches coroutines on [Dispatchers.Main].
 * In a JVM unit test there is no Android Looper, so the real Main dispatcher does not exist
 * and any coroutine that touches it will throw [IllegalStateException]. Swapping it with a
 * [TestDispatcher] lets ViewModel coroutines run under controlled test-clock conditions.
 *
 * ## Sharing the dispatcher with `runTest`
 * Tests that call `runTest` should pass [testDispatcher] explicitly:
 * ```kotlin
 * @get:Rule val rule = MainDispatcherRule()
 *
 * @Test fun example() = runTest(rule.testDispatcher) {
 *     // viewModelScope coroutines and the runTest block share the same scheduler,
 *     // so advanceTimeBy() / advanceUntilIdle() control both.
 * }
 * ```
 * If a different [TestDispatcher] is passed to `runTest`, the two schedulers are independent
 * and time-control calls inside `runTest` will not advance ViewModel coroutines.
 *
 * @param testDispatcher The dispatcher to install as [Dispatchers.Main]. Defaults to
 *   [StandardTestDispatcher], which requires explicit time advancement via
 *   [kotlinx.coroutines.test.TestCoroutineScheduler.advanceUntilIdle] or
 *   [kotlinx.coroutines.test.TestCoroutineScheduler.advanceTimeBy].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {

    /** Installs [testDispatcher] as [Dispatchers.Main] before each test method runs. */
    override fun starting(description: Description?) = Dispatchers.setMain(testDispatcher)

    /** Restores the original [Dispatchers.Main] after each test method completes. */
    override fun finished(description: Description?) = Dispatchers.resetMain()
}
