package org.zalando.hutmann.filters

import scala.concurrent.Future
import scala.language.implicitConversions
import play.api.mvc.{ Filter, RequestHeader, Result }
import play.api.mvc.Results.BadRequest
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import java.util.{ Base64, UUID }
import akka.stream.Materializer
import com.google.inject.Inject
import FlowIdFilter.FlowIdHeader
import org.zalando.hutmann.logging.Context
/**
  * A flow id filter that checks flow ids and can add them if they are not present, as well as copy them to the output
  * if needed.
  * @param behavior Accepts: <br />
  *                 <ul><li> Strict -> declines the request if the header doesn't contain a flow-id</li>
  *                 <li> Create -> creates a new flow-id if the header doesn't contain one</li></ul>
  * @param copyFlowIdToResult If the flow-id should be copied from the input to the output headers.
  */

sealed abstract class FlowIdFilter(
    behavior:           FlowIdBehavior,
    copyFlowIdToResult: Boolean
)(implicit val mat: Materializer) extends Filter {

  override def apply(nextFilter: (RequestHeader) => Future[Result])(rh: RequestHeader): Future[Result] = {

    val optFlowId = rh.headers.get(FlowIdHeader).orElse(behavior match {
      case Create => Some(createFlowId)
      case Strict => None
    })

    optFlowId match {
      case None =>
        Future.successful(BadRequest("Missing flow id header"))
      case Some(flowId) =>
        val headers = rh.copy(headers = rh.headers.add(FlowIdHeader -> flowId))
        val ctx = Context.request2loggingContext(headers)
        Context.withContext(ctx) {
          if (copyFlowIdToResult) {
            nextFilter(ctx.requestHeader).map(_.withHeaders(FlowIdHeader -> flowId))
          } else {
            nextFilter(ctx.requestHeader)
          }
        }
    }
  }

  private def createFlowId: String = {
    val uuid = UUID.randomUUID()
    val uuidByte = uuid.toByte
    val base64 = Base64.getEncoder.encodeToString(uuidByte).replace("=", "").drop(1)
    s"J$base64"
  }

  private implicit class UUIDExtension(self: UUID) {
    def toByte: Array[Byte] = {
      val msb = self.getMostSignificantBits
      val lsb = self.getLeastSignificantBits

      Array(
        (msb >>> 56).toByte,
        (msb >>> 48).toByte,
        (msb >>> 40).toByte,
        (msb >>> 32).toByte,
        (msb >>> 24).toByte,
        (msb >>> 16).toByte,
        (msb >>> 8).toByte,
        msb.toByte,
        (lsb >>> 56).toByte,
        (lsb >>> 48).toByte,
        (lsb >>> 40).toByte,
        (lsb >>> 32).toByte,
        (lsb >>> 24).toByte,
        (lsb >>> 16).toByte,
        (lsb >>> 8).toByte,
        lsb.toByte
      )
    }
  }
}

sealed trait FlowIdBehavior
case object Strict extends FlowIdBehavior
case object Create extends FlowIdBehavior

final class CreateFlowIdFilter @Inject() (implicit mat: Materializer) extends FlowIdFilter(Create, true)
final class StrictFlowIdFilter @Inject() (implicit mat: Materializer) extends FlowIdFilter(Strict, true)

object FlowIdFilter {
  val FlowIdHeader: String = "X-Flow-ID"
}
