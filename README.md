# EventBus Service
[![](https://jitpack.io/v/wowselim/eventbus-service.svg)](https://jitpack.io/#wowselim/eventbus-service)
[![](https://github.com/wowselim/eventbus-service/workflows/Tests/badge.svg)](https://github.com/wowselim/eventbus-service)

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

  sealed interface Division {
    data class Success(val quotient: Double) : Division
    data class Error(val message: String) : Division
  }
}
```

Next, we need to annotate this interface as follows:
```kotlin
@EventBusService
interface DivisionService
```

This will generate two things
* An implementation of this service (`DivisionServiceImpl`) that translates function
  calls into EventBus requests.
* A function that allows you to handle those requests:
  ```kotlin
  object DivisionServiceRequests {

  fun divide(
    vertx: Vertx
  ): Flow<EventBusServiceRequest<DivideParameters, Division>>

  }
  ```

Since the function has two parameters, we need to wrap them in a container. This is handled automatically
via a generated data class (`DivideParameters`).

Before running the application, we need to call
`EventBus#initializeServiceCodec`, which also lets
us add our own codecs in case we want the code
to run in a clustered setup.

This service is fully implemented in the `example` module.

## Adding it to your project

Add the [JitPack repository](https://jitpack.io/#wowselim/eventbus-service) to your build script and include the
following dependencies:

```groovy
implementation 'com.github.wowselim.eventbus-service:eventbus-service-core:<latestVersion>'
ksp 'com.github.wowselim.eventbus-service:eventbus-service-codegen:<latestVersion>'
```

The latest version can be found in [releases](https://github.com/wowselim/eventbus-service/releases).

## Debugging

To debug the code generator, run the `kspKotlin` task in the following way to be able to attach the debugger:

```bash
./gradlew clean kspKotlin --no-daemon -Dorg.gradle.debug=true -Dkotlin.compiler.execution.strategy="in-process" -Dkotlin.daemon.jvm.options="-Xdebug,-Xrunjdwp:transport=dt_socket\,address=5005\,server=y\,suspend=n"
```
