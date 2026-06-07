package com.augustnagro.magnum.ziomagnum

import zio.*
import zio.telemetry.opentelemetry.tracing.Tracing
import io.opentelemetry.api.trace.SpanKind


/** Companion object for ZIOMagnumTracer. */
object ZIOpenTelemetryMagnumTracer {


  /** Creates a ZIOMagnumTracer that uses OpenTelemetry for tracing.
    *
    * This tracer will create spans with the provided span name and set the span
    * kind to CLIENT.
    *
    * @param tracing
    *   The OpenTelemetry Tracing instance to use for creating spans.
    * @return
    *   A ZIOMagnumTracer that uses OpenTelemetry for tracing.
    */
  def apply(tracing: Tracing) = new ZIOMagnumTracer {

    import tracing.aspects.*

    inline override def apply[R, E, A](spanName: String)(
        zio: ZIO[R, E, A]
    ): ZIO[R, E, A] = zio @@ span(spanName, SpanKind.CLIENT)

  }

  /** ZLayer that provides a ZIOMagnumTracer using OpenTelemetry.
    *
    * This layer requires a Tracing instance to be provided, and will produce a
    * ZIOMagnumTracer that uses OpenTelemetry for tracing.
    *
    * @param tracing
    *   The OpenTelemetry Tracing instance to use for creating spans.
    * @return
    *   A ZLayer that provides a ZIOMagnumTracer using OpenTelemetry.
    */
  def live(tracing: Tracing): ZLayer[Any, Nothing, ZIOMagnumTracer] =
    ZLayer.succeed(apply(tracing))

  /** ZLayer that provides a ZIOMagnumTracer using OpenTelemetry.
    *
    * This layer requires a Tracing instance to be provided from the
    * environment, and will produce a ZIOMagnumTracer that uses OpenTelemetry
    * for tracing.
    *
    * @return
    *   A ZLayer that provides a ZIOMagnumTracer using OpenTelemetry.
    */
  def live: ZLayer[Tracing, Nothing, ZIOMagnumTracer] =
    ZLayer.fromFunction(apply)

}
