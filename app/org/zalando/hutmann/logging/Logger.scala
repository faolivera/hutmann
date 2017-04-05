package org.zalando.hutmann.logging

import java.time.{ Duration, ZonedDateTime }

import org.zalando.hutmann.trace.{ Context, NoContextAvailable, RequestContext, JobContext }

class Logger(name: String) {
  val logger = play.api.Logger(name)

  protected def createLogString(message: => String, file: sourcecode.File, line: sourcecode.Line): String = {
    val context = Context.getContext.getOrElse(NoContextAvailable)
    val codeContext = s"${file.value.substring(file.value.lastIndexOf("/") + 1)}:${line.value}"
    val flowDuration = Duration.between(context.contextInitializationTime, ZonedDateTime.now())

    val contextInfo = context match {
      case RequestContext(_, Some(flowId), _, _) => s"${flowDuration.toMillis}ms/$codeContext/$flowId"
      case RequestContext(requestId, None, _, _) => s"${flowDuration.toMillis}ms/$codeContext/requestId_$requestId"
      case JobContext(name, Some(flowId), _)     => s"${flowDuration.toMillis}ms/$codeContext/$flowId - $name"
      case JobContext(name, None, _)             => s"${flowDuration.toMillis}ms/$codeContext/$name"
      case NoContextAvailable                    => s"$codeContext/NoContextAvailable"
    }

    lazy val extraInfo = (for { (key, value) <- context.extraInfo } yield { s"$key=$value" }).mkString(",")
    if (context.extraInfo.isEmpty) {
      s"$message - $contextInfo"
    } else {
      s"$message - $extraInfo - $contextInfo"
    }
  }

  def isTraceEnabled: Boolean = logger.isTraceEnabled
  def isDebugEnabled: Boolean = logger.isDebugEnabled
  def isInfoEnabled: Boolean = logger.isInfoEnabled
  def isWarnEnabled: Boolean = logger.isWarnEnabled
  def isErrorEnabled: Boolean = logger.isErrorEnabled

  def trace(message: => String)(implicit file: sourcecode.File, line: sourcecode.Line): Unit =
    logger.trace(createLogString(message, file, line))
  def trace(message: => String, error: => Throwable)(implicit file: sourcecode.File, line: sourcecode.Line): Unit =
    logger.trace(createLogString(message, file, line), error)

  def debug(message: => String)(implicit file: sourcecode.File, line: sourcecode.Line): Unit =
    logger.debug(createLogString(message, file, line))
  def debug(message: => String, error: => Throwable)(implicit file: sourcecode.File, line: sourcecode.Line): Unit =
    logger.debug(createLogString(message, file, line), error)

  def info(message: => String)(implicit file: sourcecode.File, line: sourcecode.Line): Unit =
    logger.info(createLogString(message, file, line))
  def info(message: => String, error: => Throwable)(implicit file: sourcecode.File, line: sourcecode.Line): Unit =
    logger.info(createLogString(message, file, line), error)

  def warn(message: => String)(implicit file: sourcecode.File, line: sourcecode.Line): Unit =
    logger.warn(createLogString(message, file, line))
  def warn(message: => String, error: => Throwable)(implicit file: sourcecode.File, line: sourcecode.Line): Unit =
    logger.warn(createLogString(message, file, line), error)

  def error(message: => String)(implicit file: sourcecode.File, line: sourcecode.Line): Unit =
    logger.error(createLogString(message, file, line))
  def error(message: => String, error: => Throwable)(implicit file: sourcecode.File, line: sourcecode.Line): Unit =
    logger.error(createLogString(message, file, line), error)
}

/**
  * Replaces the default play logger. Has access functionality that is
  * equal to the play logger, and uses that logger underneath. However, it
  * additionally puts context information in the log output, like flow ids.
  * It does not implement {{{LoggerLike}}} though, since we change the
  * function declarations.
  *
  * Giving an implicit context is needed - you can't simply omit the
  * implicit parameter. This is for a reason: It lets the compiler check if
  * you have enough information to write a logging statement with flow ids,
  * or not - instead of seeing this on the live system when it is too late.
  * If you really do not want to have a context, you can supply the case object
  * {{{NoContextAvailable}}} - either explicitly, or as an implicit value.
  */
object Logger extends Logger("application") {
  def apply(name: String): Logger = new Logger(name)
  def apply()(implicit name: sourcecode.Name, fullname: sourcecode.FullName): Logger = {
    //use the enclosing class name as logger name. To get it, extract the full name and remove the length of the name plus the extra dot.
    val loggerName = fullname.value.dropRight(name.value.length + 1)
    new Logger(loggerName)
  }
}
