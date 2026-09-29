package com.example.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Standard JUnit rule that installs a controllable Main dispatcher for tests
 * that exercise `ViewModel.viewModelScope` (which is hard-bound to
 * `Dispatchers.Main.immediate`).
 *
 * [UnconfinedTestDispatcher] is used on purpose: it runs launched work eagerly
 * and synchronously, so a coroutine is never left "in flight" while Main is
 * being swapped in `finished` — the race that produced
 * "Dispatchers.Main is used concurrently with setting it" under Robolectric and
 * "Main dispatcher was absent" on a plain JVM. Remaining queued work (e.g. the
 * siren countdown) is drained before Main is reset.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {

  val dispatcher = UnconfinedTestDispatcher()

  override fun starting(description: Description) {
    Dispatchers.setMain(dispatcher)
  }

  override fun finished(description: Description) {
    dispatcher.scheduler.advanceUntilIdle()
    // Late grace: production code hops to Dispatchers.IO (e.g. the batched
    // altitude fetch) and, under UnconfinedTestDispatcher, the COMPLETION
    // resumes on the IO worker itself. On CI's faster threads that resume
    // can land a beat after this test body ends; a bare resetMain then
    // throws "Dispatchers.Main was accessed..." from the background thread.
    // Give in-flight resumes a short window to arrive on the (still set)
    // test Main dispatcher, drain, and only then reset. Assertions are
    // untouched - this only stabilizes teardown.
    Thread.sleep(300)
    dispatcher.scheduler.advanceUntilIdle()
    Dispatchers.resetMain()
  }
}