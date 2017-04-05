package org.zalando.hutmann.logging

import org.zalando.hutmann.spec.UnitSpec
import org.zalando.hutmann.trace.{ Context, JobContext, RequestContext }
import play.api.test.FakeRequest

import scala.util.Random

class LoggerDemo extends UnitSpec {
  val logger = Logger()

  def logStatements(): Unit = {
    logger.trace("This is a test")
    logger.debug("This is a test")
    logger.info("This is a test")
    logger.warn("This is a test")
    logger.error("This is a test")
  }

  "The logger" should "be demonstrated without a context" in {
    logStatements()
  }

  it should "be demonstrated with a request context" in {
    val context: Context = RequestContext(Random.nextLong(), Some("abc123"), FakeRequest())
    Context.withContext(context) {
      logStatements()
    }
  }

  it should "be demonstrated with a job context" in {
    val context: Context = JobContext("FunnyRainbowJob", Some("abc123"))
    Context.withContext(context) {
      logStatements()
    }
  }
}
