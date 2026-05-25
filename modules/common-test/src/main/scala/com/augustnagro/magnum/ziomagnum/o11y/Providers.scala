package com.augustnagro.magnum.ziomagnum.o11y

import zio._

import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.trace.SdkTracerProvider
import zio.telemetry.opentelemetry.OpenTelemetry
import io.opentelemetry.api
import io.opentelemetry.sdk.logs.SdkLoggerProvider

import zio.telemetry.opentelemetry.context.ContextStorage
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.semconv.ServiceAttributes
import io.opentelemetry.semconv.DeploymentAttributes

import zio.telemetry.opentelemetry.tracing.Tracing

import scala.annotation.nowarn
import zio.logging.slf4j.bridge.Slf4jBridge
import zio.logging.LogFilter

trait ZIOpenTelemetry {
  this: ZIOApp =>

  type Environment = Tracing

  def environmentTag: Tag[Environment] =
    Tag[Environment]

  def resourceName: String

  def withZIOMetrics: Boolean = true

  def version: Option[String] = None

  def environment: Option[String] = None

  def extraAttributes: Attributes = Attributes.empty()

  def attributes: Attributes = {
    val builder = Attributes
      .builder()
      .put(ServiceAttributes.SERVICE_NAME, resourceName)
    version.foreach(v => builder.put(ServiceAttributes.SERVICE_VERSION, v))
    environment.foreach(e =>
      builder.put(DeploymentAttributes.DEPLOYMENT_ENVIRONMENT_NAME, e)
    )
    builder.putAll(extraAttributes).build()
  }

  def tracerProvider: URIO[Scope, Option[SdkTracerProvider]] = ZIO.none
  def meterProvider: URIO[Scope, Option[SdkMeterProvider]] = ZIO.none
  def logProvider: URIO[Scope, Option[SdkLoggerProvider]] = ZIO.none

  final def otelProviders: ULayer[OtelProviders] = ZLayer.scoped[Any](for {
    logger <- logProvider
    meter <- meterProvider
    tracer <- tracerProvider
  } yield OtelProviders(tracer, meter, logger))

  /** The console log layer for the ZIOpenTelemetry trait.
    *
    * Default implementation uses the default ZIO console logger, which logs to
    * stdout. You can override this to use a different logger, e.g. SLF4J,
    * Logback, etc. To use SLF4J, you can use the following layer:
    * {{{
    *  def consoleLogLayer: ZLayer[Any, Nothing, Unit] = Runtime.removeDefaultLoggers >>> SLF4J.slf4j
    * }}}
    */
  def consoleLogLayer: ZLayer[Any, Nothing, Unit] = Runtime.removeDefaultLoggers

  @nowarn
  def otel4zLogging(
      instrumentationScopeName: String,
      logLevel: LogLevel = LogLevel.Info
  ): URLayer[api.OpenTelemetry & ContextStorage, Unit] = ZLayer.unit

  @nowarn
  def otel4zTracing(
      instrumentationScopeName: String,
      instrumentationVersion: Option[String] = None,
      schemaUrl: Option[String] = None,
      logAnnotated: Boolean = false
  ): ZLayer[
    io.opentelemetry.api.OpenTelemetry & ContextStorage,
    Nothing,
    Tracing
  ] = ZLayer.succeed(noop.NoopTracing(resourceName))

  /** The bootstrap layer for the ZIOpenTelemetry trait.
    *
    * This is the layer that will be used to bootstrap the ZIO application. It
    * includes the OpenTelemetry layer, the Tracing layer, and the Meter layer.
    */

  val logFilterConfig = LogFilter.LogLevelByNameConfig(
    LogLevel.Warning,
    "com.augustnagro.magnum.ziomagnum" -> LogLevel.Trace,
    "com.augustnagro.magnum" -> LogLevel.Trace,
    "zio.logging.slf4j" -> LogLevel.Debug,
    "SLF4J-LOGGER" -> LogLevel.Warning
  )

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Environment] =
    OpenTelemetry.contextZIO >+> (otelProviders >>>
      ZIOpenTelemetryLayer
        .live(withZIOMetrics)) >+> (Slf4jBridge.init(
      logFilterConfig.toFilter
    ) ++ otel4zLogging(
      resourceName,
      logLevel = LogLevel.Trace
    ) ++ otel4zTracing(resourceName))

}

/** Logging provider for OpenTelemetry.
  *
  * The providers are configured to export logs in OTLP gRPC format to
  * collector.
  *
  * The providers are used by the OpenTelemetry layers, which are/must be
  * provided by the [[ZIOpenTelemetry]] trait bootstrap layer.
  */
trait Logging {
  this: ZIOpenTelemetry =>

  /** Provides a logger provider for OpenTelemetry, which logs in OTLP gRPC
    * format with [[LoggerProvider]]
    */
  override def logProvider: URIO[Scope, Option[SdkLoggerProvider]] =
    LoggerProvider.grpc(attributes)

  /** A OpenTelemetry logging layer, with configurable instrumentation scope
    * name and log level.
    *
    * @param instrumentationScopeName
    * @param logLevel
    * @return
    */
  override def otel4zLogging(
      instrumentationScopeName: String,
      logLevel: LogLevel = LogLevel.Info
  ): URLayer[api.OpenTelemetry & ContextStorage, Unit] = OpenTelemetry.logging(
    instrumentationScopeName = instrumentationScopeName,
    logLevel = logLevel
  )
}

/** Metrics providers for OpenTelemetry.
  *
  * The providers are configured to export metricsin OTLP gRPC format to
  * collector.
  *
  * The providers are used by the OpenTelemetry layers, which are/must be
  * provided by the [[ZIOpenTelemetry]] trait bootstrap layer.
  */
trait Metrics {
  this: ZIOpenTelemetry =>

  /** Provides a meter provider for OpenTelemetry, which exports metrics in OTLP
    * gRPC format with [[MeterProvider]]
    */
  override def meterProvider: URIO[Scope, Option[SdkMeterProvider]] =
    MeterProvider.grpc(attributes)

  /** A OpenTelemetry metrics layer, with configurable instrumentation scope
    * name, version and schema url.
    *
    * @param instrumentationScopeName
    * @param instrumentationVersion
    * @param schemaUrl
    * @param logAnnotated
    * @return
    */
  def otel4zMetrics(
      instrumentationScopeName: String,
      instrumentationVersion: Option[String] = None,
      schemaUrl: Option[String] = None,
      logAnnotated: Boolean = false
  ) = OpenTelemetry.metrics(
    instrumentationScopeName = instrumentationScopeName,
    instrumentationVersion = instrumentationVersion,
    schemaUrl = schemaUrl,
    logAnnotated = logAnnotated
  )

}

/** Tracing providers for OpenTelemetry.
  *
  * The providers are configured to export traces in OTLP gRPC format to
  * collector.
  *
  * The providers are used by the OpenTelemetry layers, which are/must be
  * provided by the [[ZIOpenTelemetry]] trait bootstrap layer.
  */
trait Traces {
  this: ZIOpenTelemetry =>

  /** Provides a tracer provider for OpenTelemetry, which exports traces in OTLP
    * gRPC format with [[TracerProvider]]
    */
  override def tracerProvider: URIO[Scope, Option[SdkTracerProvider]] =
    TracerProvider.grpc(attributes)

  override def otel4zTracing(
      instrumentationScopeName: String,
      instrumentationVersion: Option[String] = None,
      schemaUrl: Option[String] = None,
      logAnnotated: Boolean = false
  ): ZLayer[
    io.opentelemetry.api.OpenTelemetry & ContextStorage,
    Nothing,
    Tracing
  ] = OpenTelemetry.tracing(
    instrumentationScopeName = instrumentationScopeName,
    instrumentationVersion = instrumentationVersion,
    schemaUrl = schemaUrl,
    logAnnotated = logAnnotated
  )
}
