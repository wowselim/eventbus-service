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

We might model the input and output data for this
service as follows:
```kotlin
data class DivisionRequest(val dividend: Double, val divisor: Double)

sealed class Division {
  data class Success(val quotient: Double) : Division()
  data class Error(val message: String) : Division()
}
```

Next, we need to annotate the service verticle as follows:
```kotlin
@EventBusService(
    topic = "co.selim.sample.division",
    consumes = DivisionRequest::class,
    produces = Division::class,
    propertyName = "divisionRequests",
    functionName = "divide"
)
```

**Note:**
> * EventBus topics should follow package naming conventions.
> * Custom types should be preferred over `String` etc.

---

This will generate two things
* An extension function to request divisions:
```kotlin
suspend fun Vertx.divide(request: DivisionRequest): Division
````

* An extension property to get the division
requests:
```kotlin
val Vertx.divisionRequests: Flow<EventBusServiceRequest<DivisionRequest, Division>>
```

This service is fully implemented in the `example` module.

## Adding it to your project
Add the [JitPack repository](https://jitpack.io/#wowselim/eventbus-service) to your build script and include the following dependencies:

```groovy
implementation 'com.github.wowselim.eventbus-service:eventbus-service-core:<latestVersion>'
kapt 'com.github.wowselim.eventbus-service:eventbus-service-codegen:<latestVersion>'
```

The latest version numer can be found on JitPack as well.
