package org.zalando.hutmann.logging

import java.time.ZonedDateTime

import org.slf4j.MDC
import org.zalando.hutmann.authentication.{ AuthorizationProblem, NoAuthorization, User, UserRequest }
import org.zalando.hutmann.filters.FlowIdFilter.FlowIdHeader
import play.api.mvc.RequestHeader

import scala.language.implicitConversions

/** Holds context information about what should be logged.*/
sealed trait Context {
  val contextInitializationTime = ZonedDateTime.now()
  val extraInfo: Map[String, String]
  def updated(key: String, value: String): Context
}

/**
  * The default value when no context information is available -
  * in this case, context information logging is omitted. Must be
  * given explicitly to enforce thinking about what you want to
  * achieve, instead of forgetting to log flow ids and only see it
  * on the live system when it is too late.
  */
case object NoContextAvailable extends Context {
  override val extraInfo: Map[String, String] = Map.empty
  def updated(key: String, value: String): NoContextAvailable.type = this
}

/** Marks objects that have a flow id. Comes with an extractor to get the flow id if needed.*/
trait FlowIdAware {
  val flowId: Option[String]
}

object FlowIdAware {
  /**
    * Unapply method for objects that have a flow id. Can be used to extract flow ids without a hassle, simply do it like this:
    * {{{
    *   //request is given
    *   val context: Context = request
    *   val optFlowId = context match {
    *     case FlowIdAware(flowId) => Some(flowId)
    *     case _ => None
    *   }
    * }}}
    */
  def unapply(flowIdAware: FlowIdAware): Option[String] = flowIdAware.flowId
}

/**
  * Context information that should be logged.
  *
  * @param requestId     The play request id that is unique for a request.
  * @param flowId        The flow id that is used to identify a flow over system boundaries.
  * @param requestHeader The request headers the request has. The request body is not forwarded here since you should do that explicitly if you need it.
  */
case class RequestContext(
    requestId:              Long,
    override val flowId:    Option[String],
    requestHeader:          RequestHeader,
    override val extraInfo: Map[String, String] = Map.empty
) extends Context with FlowIdAware {
  override def updated(key: String, value: String): RequestContext = this.copy(extraInfo = extraInfo.updated(key, value))
}

object RequestId {
  /**
    * Unapply method for objects that have a request id. Can be used to extract request ids without a hassle, simply do it like this:
    * {{{
    *   //request is given
    *   val context: Context = request
    *   val optFlowId = context match {
    *     case RequestId(requestId) => Some(requestId)
    *     case _ => None
    *   }
    * }}}
    */
  def unapply(requestContext: RequestContext): Option[Long] = Some(requestContext.requestId)
}

case class JobContext(
    name:                   String,
    override val flowId:    Option[String],
    override val extraInfo: Map[String, String] = Map.empty
) extends Context with FlowIdAware {
  override def updated(key: String, value: String): JobContext = this.copy(extraInfo = extraInfo.updated(key, value))
}

object Context {
  self =>
  private val context = new ThreadLocal[Context]()

  def getContext: Option[Context] = {
    Option(context.get())
  }

  def setContext(ctx: Context): Unit = {
    context.set(ctx)
    setMdcContext(ctx)
  }

  def clear(): Unit = {
    context.remove()
    removeMdcContext()
  }

  private def setMdcContext(context: Context): Unit = {
    context match {
      case FlowIdAware(flowId) =>
        MDC.put(FlowIdHeader, flowId)
      case _ =>
    }
  }

  private def removeMdcContext(): Unit = {
    MDC.remove(FlowIdHeader)
  }

  def capture(): CapturedContext = new CapturedContext {
    val maybeContext = getContext
    def withContext[T](block: => T): T = maybeContext match {
      case Some(ctx) => self.withContext(ctx)(block)
      case None      => block
    }
  }

  /**
    * Execute the given block with the given context.
    */
  def withContext[T](ctx: Context)(block: => T) = {
    assert(ctx != null, "Context must not be null")

    val maybeOld = getContext
    try {
      setContext(ctx)
      block
    } finally {
      maybeOld match {
        case Some(old) => setContext(old)
        case None      => clear()
      }
    }
  }

  /**
    * Implicit conversion to allow easy creation of {{{Context}}}. Usage:
    *
    * {{{
    * implicit val context: ContextInformation = request
    * }}}
    *
    * @param request The play request object that should be used to extract information.
    * @tparam A      The type of the body of the request.
    */
  implicit def request2loggingContext[A](request: RequestHeader): RequestContext =
    RequestContext(requestId = request.id, flowId = request.headers.get(FlowIdHeader), requestHeader = request)
}

trait CapturedContext {

  /**
    * Execute the given block with the captured context.
    */
  def withContext[T](block: => T): T
}