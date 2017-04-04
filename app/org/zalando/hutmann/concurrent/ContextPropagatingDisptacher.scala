package org.zalando.hutmann.concurrent

import java.util.concurrent.TimeUnit

import akka.dispatch._
import com.typesafe.config.Config
import org.zalando.hutmann.logging.Context
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{ Duration, FiniteDuration }

/**
  * Configurator for a context propagating dispatcher.
  */
class ContextPropagatingDispatcherConfigurator(config: Config, prerequisites: DispatcherPrerequisites)
    extends MessageDispatcherConfigurator(config, prerequisites) {

  private val instance = new ContextPropagatingDisptacher(
    this,
    config.getString("id"),
    config.getInt("throughput"),
    FiniteDuration(config.getDuration("throughput-deadline-time", TimeUnit.NANOSECONDS), TimeUnit.NANOSECONDS),
    configureExecutor(),
    FiniteDuration(config.getDuration("shutdown-timeout", TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS)
  )

  override def dispatcher(): MessageDispatcher = instance
}

/**
  * A context propagating dispatcher.
  *
  * This dispatcher propagates the current request context if it's set when it's executed.
  */
class ContextPropagatingDisptacher(
  _configurator:                  MessageDispatcherConfigurator,
  id:                             String,
  throughput:                     Int,
  throughputDeadlineTime:         Duration,
  executorServiceFactoryProvider: ExecutorServiceFactoryProvider,
  shutdownTimeout:                FiniteDuration
) extends Dispatcher(
  _configurator, id, throughput, throughputDeadlineTime, executorServiceFactoryProvider, shutdownTimeout
) { self =>

  override def prepare(): ExecutionContext = new ExecutionContext {
    private val context = Context.capture()

    def execute(r: Runnable): Unit = self.execute(new Runnable {
      def run(): Unit = context.withContext(r.run())
    })
    def reportFailure(t: Throwable): Unit = self.reportFailure(t)
  }
}
