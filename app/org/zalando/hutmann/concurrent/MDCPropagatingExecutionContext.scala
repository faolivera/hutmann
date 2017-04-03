package org.zalando.hutmann.concurrent

import java.util.concurrent.Executor

import scala.concurrent.ExecutionContextExecutor

/*
 * Inspiration taken from
 * http://code.hootsuite.com/logging-contextual-info-in-an-asynchronous-scala-application/
 */

import scala.concurrent.ExecutionContext

import org.slf4j.MDC


trait MDCPropagatingExecutionContext extends ExecutionContext {
  self =>

  override def prepare(): ExecutionContext = new ExecutionContext {
    val context = Option(MDC.getCopyOfContextMap)

    def execute(r: Runnable): Unit = self.execute(new Runnable {
      def run(): Unit = {
        val oldContext = Option(MDC.getCopyOfContextMap)
        try {
          context.fold(MDC.clear())(MDC.setContextMap(_))

          r.run()
        } finally {
          oldContext.fold(MDC.clear())(MDC.setContextMap(_))
        }
      }
    })

    def reportFailure(t: Throwable): Unit = self.reportFailure(t)
  }
}

/**
  * Wrapper around an existing ExecutionContext that makes it propagate MDC information.
  */
class MDCPropagatingExecutionContextWrapper(wrapped: ExecutionContext)
  extends ExecutionContextExecutor with MDCPropagatingExecutionContext  {

  override def execute(r: Runnable): Unit = wrapped.execute(r)

  override def reportFailure(t: Throwable): Unit = wrapped.reportFailure(t)
}

object MDCPropagatingExecutionContextWrapper {
  def apply(wrapped: ExecutionContext): MDCPropagatingExecutionContextWrapper =
    new MDCPropagatingExecutionContextWrapper(wrapped)
}
