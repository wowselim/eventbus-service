# EventBus Service
[![](https://jitpack.io/v/wowselim/eventbus-service.svg)](https://jitpack.io/#wowselim/eventbus-service)

EbService generates kotlin code that enables
a type-safe way of using the Vert.x EventBus.
The generated code eliminates the need of guessing
which types are required by EventBus consumers and
which types are produced by them.

On top of that, the generated functions avoid
unnecessary serialization and deserialization by
making use of a special
[EventBus codec](https://dev.to/sip3/how-to-extend-vert-x-eventbus-api-to-save-on-serialization-3akf).

## Getting started
Imagine we have a service that can divide a
double by another double.

We might model this service as follows:
```kotlin
interface DivisionService {
  suspend fun divide(dividend: Double, divisor: Double): Division

  sealed class Division {
    data class Success(val quotient: Double) : Division()
    data class Error(val message: String) : Division()
  }
}
```

Next, we need to annotate the service verticle as follows:
```kotlin
@EventBusService
interface DivisionService
```

This will generate two things
* An implementation of this service (`DivisionServiceImpl`).
  This implementation forwards the parameters to subscribers
  of the generated properties.
* An extension property to get the division requests:
  ```kotlin
  val Vertx.divisionRequests: Flow<EventBusServiceRequest<DivisionRequest, Division>>
  ```
  Where `DivisionRequest` is a data class that wraps the two parameters.

This service is fully implemented in the `example` module.

## Adding it to your project
Add the [JitPack repository](https://jitpack.io/#wowselim/eventbus-service) to your build script and include the following dependencies:

```groovy
implementation 'com.github.wowselim.eventbus-service:eventbus-service-core:<latestVersion>'
kapt 'com.github.wowselim.eventbus-service:eventbus-service-codegen:<latestVersion>'
```

The latest version can be found in the [releases section](https://github.com/wowselim/eventbus-service/releases/latest).
