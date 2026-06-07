---
title: Architecture
---

# Architecture

ZIO Magnum is designed with a modular architecture that emphasizes separation of concerns, scalability, and maintainability.

* [ZIO](https://zio.dev) is a powerful library for asynchronous and concurrent programming in Scala, providing a functional programming model that simplifies error handling and resource management. 
* [Magnum](https://github.com/AugustNagro/magnum) is a type-safe, compile-time verified SQL query builder and database access library for Scala, designed to work seamlessly with ZIO.

ZIO magnum relies both on ZIO and Magnum to provide a robust and scalable solution for database access in Scala applications.

OpenTelemetry supports is provided by an optional module.

## ZIO Magnum

The following diagram illustrates the architecture of ZIO Magnum:

![Diagram of ZIO Magnum Architecture](/images/zio-magnum.svg)

## ZIO Magnum with OpenTelemetry Integration

The following diagram illustrates the architecture of ZIO Magnum with OpenTelemetry Integration:

![Diagram of ZIO Magnum with OpenTelemetry Integration Architecture](/images/zio-magnum-opentelemetry.svg)
