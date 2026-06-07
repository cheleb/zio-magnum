package com.augustnagro.magnum.ziomagnum

import com.augustnagro.magnum.SqlLogger
import com.augustnagro.magnum.SqlSuccessEvent
import com.augustnagro.magnum.SqlExceptionEvent
import scala.concurrent.duration.FiniteDuration

import zio.ZLayer
import org.slf4j.LoggerFactory
import zio.ULayer
import org.slf4j.Logger

/** SLF4J-based implementation of the SqlLogger trait.
  */

object Slf4jMagnumLogger {
  private def paramsString(params: Iterator[Iterator[Any]]): String =
    params.map(_.mkString("(", ", ", ")")).mkString("", ",\n", "\n")

  /** Default implementation of the SqlLogger trait.
    */
  class Slf4jMagnumLogger(name: String = "com.augustnagro.magnum")
      extends SqlLogger {

    protected val logger = org.slf4j.LoggerFactory.getLogger(name)

    override def log(successEvent: SqlSuccessEvent): Unit = {
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
    }

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
  }

  /** Creates a logger that only logs slow queries.
    */
  def logSlowQueries(
      slowerThan: FiniteDuration,
      name: String = "com.augustnagro.magnum"
  ): SqlLogger = new Slf4jMagnumLogger(name):
    override def log(logEvent: SqlSuccessEvent): Unit = {
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
      else super.log(logEvent)
    }

    override def exceptionMsg(exceptionEvent: SqlExceptionEvent): String =
      super.exceptionMsg(exceptionEvent)

  def slf4jLogger(name: String): ULayer[Logger] =
    ZLayer.succeed(LoggerFactory.getLogger(name))

  /** Provides a ZLayer that supplies a SLF4J-based SqlLogger implementation.
    *
    * @return
    */
  def live(
      name: String = "com.augustnagro.magnum"
  ): ZLayer[Any, Nothing, SqlLogger] = ZLayer.succeed(Slf4jMagnumLogger(name))

  /** Provides a ZLayer that supplies a SLF4J-based SqlLogger implementation,
    * that only logs queries that exceed the specified duration threshold.
    *
    * @param slowerThan
    *   Duration threshold for logging slow queries.
    * @return
    */
  def logSlowQueriesLive(
      slowerThan: FiniteDuration,
      name: String = "com.augustnagro.magnum"
  ): ZLayer[Any, Nothing, SqlLogger] =
    ZLayer.succeed(logSlowQueries(slowerThan, name))
}
