package org.zalando.hutmann.concurrent

import scala.concurrent.ExecutionContextExecutor

/**
  * Created by folivera on 03/04/2017.
  */
object ExecutionContext {

  /**
    * A wrapper around scala.concurrent.ExecutionContext.global that propagates the MDC context
    *
    * @return the global `ExecutionContext` extended to propagate the MDC context
    */

  def global: ExecutionContextExecutor = Implicits.global

  object Implicits {
    /**
      * The implicit global `ExecutionContext`. Import `global` when you want to provide the global
      * `ExecutionContext` implicitly.
      *
      */
    implicit lazy val global: ExecutionContextExecutor =
      MDCPropagatingExecutionContextWrapper.apply(scala.concurrent.ExecutionContext.global)
  }

}
