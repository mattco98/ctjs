package com.chattriggers.ctjs.engine

import com.chattriggers.ctjs.console.printTraceToConsole
import com.chattriggers.ctjs.engine.js.JSContextFactory
import com.chattriggers.ctjs.engine.js.JSLoader
import org.mozilla.javascript.Context
import java.util.concurrent.ForkJoinPool

@Suppress("unused")
class WrappedThread(private val task: Runnable) {
    fun start() {
        ForkJoinPool.commonPool().execute {
            try {
                JSContextFactory.enterContext()
                task.run()
                Context.exit()
            } catch (e: Throwable) {
                e.printTraceToConsole(JSLoader.console)
            }
        }
    }

    companion object {
        @JvmStatic
        @JvmOverloads
        fun sleep(millis: Long, nanos: Int = 0) = Thread.sleep(millis, nanos)

        @JvmStatic
        fun currentThread(): Thread = Thread.currentThread()
    }
}
