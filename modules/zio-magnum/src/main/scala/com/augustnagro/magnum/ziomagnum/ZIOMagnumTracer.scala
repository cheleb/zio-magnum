package com.augustnagro.magnum.ziomagnum

import zio.ZIO

  /** A tracer for ZIO effects. */
trait ZIOMagnumTracer {

  /** Wraps a ZIO effect with tracing information.
    *
    * The span name is provided as an argument, and the span kind is set to
    * CLIENT.
    *
    * @param spanName
    * @param zio
    * @return
    */
  def apply[R, E, A](spanName: String)(zio: ZIO[R, E, A]): ZIO[R, E, A]

}

object ZIOMagnumTracer {

  /** A no-op tracer that does nothing.
    *
    * This can be used as a default tracer when no other tracer is available, or
    * for testing purposes.
    */
  val noopTracer = new ZIOMagnumTracer {
    override def apply[R, E, A](spanName: String)(
        zio: ZIO[R, E, A]
    ): ZIO[R, E, A] = zio
  }
}
