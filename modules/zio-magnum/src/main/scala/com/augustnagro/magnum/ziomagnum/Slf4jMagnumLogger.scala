package com.augustnagro.magnum.ziomagnum

import com.augustnagro.magnum.SqlLogger
import com.augustnagro.magnum.SqlSuccessEvent
import com.augustnagro.magnum.SqlExceptionEvent
import scala.concurrent.duration.FiniteDuration

/** SLF4J-based implementation of the SqlLogger trait.
  */

object Slf4jMagnumLogger {
  private def paramsString(params: Iterator[Iterator[Any]]): String =
    params.map(_.mkString("(", ", ", ")")).mkString("", ",\n", "\n")

  private val logger = org.slf4j.LoggerFactory.getLogger("zio-magnum")

  /** Default implementation of the SqlLogger trait.
    */
  object Default extends SqlLogger:

    override def log(successEvent: SqlSuccessEvent): Unit = (
      if logger.isTraceEnabled then
        logger.trace(
          s"""Executed Query in ${successEvent.execTime}:
             |${successEvent.sql}
             |
             |With values:
             |${paramsString(successEvent.params)}
             |""".stripMargin
        )
      else if logger.isDebugEnabled then
        logger.debug(
          s"""Executed Query in ${successEvent.execTime}:
             |${successEvent.sql}
             |""".stripMargin
        )
    )

    override def exceptionMsg(exceptionEvent: SqlExceptionEvent): String =
      if logger.isTraceEnabled() then s"""Error executing query:
           |${exceptionEvent.sql}
           |With message:
           |${exceptionEvent.cause.getMessage}
           |And values:
           |${paramsString(exceptionEvent.params)}
           |""".stripMargin
      else s"""Error executing query:
              |${exceptionEvent.sql}
              |With message:
              |${exceptionEvent.cause}
              |""".stripMargin
  end Default

  /** Creates a logger that only logs slow queries.
    */
  def logSlowQueries(slowerThan: FiniteDuration): SqlLogger = new:
    override def log(logEvent: SqlSuccessEvent): Unit =
      if logEvent.execTime > slowerThan then
        if logger.isTraceEnabled() then
          logger.warn(
            s"""Executed SLOW Query in ${logEvent.execTime}:
               |${logEvent.sql}
               |
               |With values:
               |${paramsString(logEvent.params)}
               |""".stripMargin
          )
        else if logger.isWarnEnabled() then
          logger.warn(
            s"""Executed SLOW Query in ${logEvent.execTime}:
               |${logEvent.sql}
               |""".stripMargin
          )
        end if
      else Default.log(logEvent)

    override def exceptionMsg(exceptionEvent: SqlExceptionEvent): String =
      Default.exceptionMsg(exceptionEvent)
}
