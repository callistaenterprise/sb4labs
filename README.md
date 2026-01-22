# Overview

```mermaid
graph TD;
    Curl-->Composite;
    Composite-->Product;
    Composite-->Recommendation;
    Composite-->Review;
```

# Start Jaeger for OpenTelemetry tracing

```
docker run -d --name jaeger \
  -p 16686:16686 \
  -p 4317:4317 \
  -p 4318:4318 \
  -p 5778:5778 \
  -p 9411:9411 \
  cr.jaegertracing.io/jaegertracing/jaeger:2.11.0
```

WebUI: http://localhost:16686

When done:

```
docker rm -f jaeger
```

# Build and run

```
clear
# ./gradlew build
# java -jar api-provider/build/libs/api-provider-0.0.1-SNAPSHOT.jar
# java --enable-preview --enable-native-access=ALL-UNNAMED -jar api-consumer/build/libs/api-consumer-0.0.1-SNAPSHOT.jar

./gradlew api-provider:build -x test && java -jar api-provider/build/libs/api-provider-0.0.1-SNAPSHOT.jar
./gradlew api-consumer:build -x test && java --enable-preview --enable-native-access=ALL-UNNAMED -jar api-consumer/build/libs/api-consumer-0.0.1-SNAPSHOT.jar

curl localhost:7002/product-composite/interface-client/2 -i
curl localhost:7002/product-composite/rest-client/2 -i
curl localhost:7002/product-composite/sequential/2 -i
curl localhost:7002/thread-info

curl localhost:7001/1/product/1 -i
curl 'localhost:7001/2/recommendation?productId=1' -i
curl 'localhost:7001/3/review?productId=1' -i 

# kill $(jobs -p)

```
# Fine grained dependencies, smaller jars?

Does it result in smaller jars and memory usage?

SB 4.0.0:

```
spring init \
--boot-version=4.0.0 \
--type=gradle-project \
--java-version=25 \
--packaging=jar \
--name=sb400 \
--dependencies=web \
sb400

cd sb400
sdk use java 25-tem
./gradlew build
ls -al build/libs/sb400-0.0.1-SNAPSHOT.jar
cd ..
```

Results in:

```
-rw-r--r--@ 1 magnus  staff  19616003 Dec 17 09:00 build/libs/sb400-0.0.1-SNAPSHOT.jar
```

SB 3.5.8:

```
spring init \
--boot-version=3.5.8 \
--type=gradle-project \
--java-version=21 \
--packaging=jar \
--name=sb358 \
--dependencies=web \
sb358

cd sb358
sdk use java 21.0.3-tem
./gradlew build
ls -al build/libs/sb358-0.0.1-SNAPSHOT.jar
```

Results in:

```
-rw-r--r--@ 1 magnus  staff  21044297 Dec 17 09:01 build/libs/sb358-0.0.1-SNAPSHOT.jar```
```

**Result:** Only dropped from 21 to 19 MB...

# Observability

Dependency:

    implementation 'org.springframework.boot:spring-boot-starter-opentelemetry'


## Problems with Micrometer and Structured Concurrency:

1. Investigate Scoped Values: https://github.com/micrometer-metrics/context-propagation/issues/108
2. Discuss Structured Concurrency: https://github.com/micrometer-metrics/context-propagation/issues/419
3. micrometer observability for the new StructuredTaskScope api: https://github.com/micrometer-metrics/micrometer/issues/5761
4. https://www.unlogged.io/post/enhanced-observability-with-java-21-and-spring-3-2
5. proposed workarounds for programmatically propagate context:
    1. https://github.com/micrometer-metrics/micrometer/issues/5761#issuecomment-2580798283
    2. https://stackoverflow.com/questions/78889603/traceid-propagation-to-virtual-thread
    3. https://stackoverflow.com/questions/78746378/spring-boot-3-micrometer-tracing-in-mdc/78765658#78765658

> **Note: Compare with WebFlux and Project Reactor.**
>
> To propagate the [W3C Trace Context](https://www.w3.org/TR/trace-context/) is to specify
>
>     spring.reactor.context-propagation: AUTO

# API versioning

## Provider config

```java
@Configuration
public class ApiVersionConfig implements WebMvcConfigurer {

  @Override
  public void configureApiVersioning(ApiVersionConfigurer configurer) {
    configurer
      .usePathSegment(0)  // Index of the path segment containing version
      .addSupportedVersions("1.0", "2.0")
      .setDefaultVersion("1.0");
  }
}
```

```java
@GetMapping(
value = "/{version}/product/{productId}",
version = "1",
produces = "application/json")
Product getProduct(@PathVariable int productId);
```

## Consumer config, RestClient

```java
  @Bean
  RestClient restClient() {
    return RestClient.builder().
      apiVersionInserter(ApiVersionInserter.usePathSegment(0)).
      build();
  }
```

```java
      Product product = restClient.get()
        .uri(url)
        .apiVersion("1")
        ...
```

## Consumer config, with Interface clients

No problem.

# Interface clients

1. OK - API Versioning 
2. OK - Structured Concurrency
3. OK - Config of interface clients per API   
   See ~/Documents/projects/books/5th/git/labs/interface-clients-springio-2025-demo-rstoyanchev/api-service/src/main/resources/application.yml
5. OK - OpenTelemetry - Tracing   
   See `ProductCompositeRestController.getProductSequential`
3. Error handling  
   ```
   curl localhost:7001/1/product/-1 -i
   curl localhost:7001/1/product/13 -i

   curl localhost:7002/product-composite/interface-client/-3 -i

   HTTP/1.1 422
   Content-Type: application/json
   Transfer-Encoding: chunked
   Date: Sun, 04 Jan 2026 14:47:01 GMT

   {"timestamp":"2026-01-04T15:47:01.057647+01:00","path":"/product-composite/interface-client/-3","status":422,"error":"Unprocessable Content","message":"Invalid productId: -3"}%            

   curl localhost:7002/product-composite/interface-client/13 -i

   HTTP/1.1 404
   Content-Type: application/json
   Transfer-Encoding: chunked
   Date: Sun, 04 Jan 2026 14:47:42 GMT

   {"timestamp":"2026-01-04T15:47:42.391106+01:00","path":"/product-composite/interface-client/13","status":404,"error":"Not Found","message":"No product found for productId: 13"}%
   ```
4. Circuit Breaker, Retry, and Timeout

   Spring or Resilience4J?   
   Start with Resilience4J!

   * https://docs.spring.io/spring-cloud-circuitbreaker/docs/current/reference/html/spring-cloud-circuitbreaker-resilience4j.html
   * https://resilience4j.readme.io/docs/circuitbreaker

   **TODO**: Timeout ger 500 fel, kan man fånga timeout fel och skicka det vidare istället???

   ``` 
   alias ml-cb='clear; while true; do r=$(curl http://localhost:7002/actuator/circuitbreakers -s | jq -r .circuitBreakers.productGroup.state); sleep 1; clear; echo $r; done'
   alias ml-health='clear; while true; do r=$(curl http://localhost:7002/actuator/health -s | jq -r .status); sleep 1; clear; echo $r; done'
   ```
   
   Verify CB functionality:

   ```
   ./test-resilience.bash
   IMPL=sequential ./test-resilience.bash
   ```

10. Logging
11. Security

# Resilience

## FaultRate

```
curl 'http://localhost:7001/1/faultrate?probability=75' -X PUT
curl http://localhost:7001/1/faultrate
```

## Retryable

## CircuitBreaker

See `Interface clients`!

## ConcurrencyLimit

# Spring Data Ahead of Time Repositories

See:

1. Blog post #1: https://spring.io/blog/2025/05/22/spring-data-ahead-of-time-repositories
2. Blog post #2: https://spring.io/blog/2025/11/25/spring-data-ahead-of-time-repositories-part-2
3. Code example: https://github.com/spring-projects/spring-data-examples/tree/main/jpa/aot-optimization

**Notes:**

1. Does not support reactive JPA repositories.
2. Requires AOT compilation.
   * See examples of extra config required due AOT in CH23 of the MS-book.

# Spring Dev Tools

See:

1. https://docs.spring.io/spring-boot/reference/using/devtools.html
1. https://www.baeldung.com/spring-boot-devtools
1. https://stackoverflow.com/questions/79306534/intellij-spring-boot-devtools-behavior

1. IntelliJ Spring Debugger...

```
dependencies {
    developmentOnly("org.springframework.boot:spring-boot-devtools")
}
```

In addition to installing spring-boot-devtools, you also need to enable automatic build in IntelliJ.

1. Open "Settings"
1. Select "Build, Execution, Deployment"
1. On the "Compiler" page, select "Build project automatically"

Also:

1. Open "Settings"
1. Select "Advanced settings"
1. In the "Compiler" section, select "Allow auto-make to start even if developed application is currently running"


# TODO

Source: https://spring.io/blog/2025/09/02/road_to_ga_introduction

## jackson 2 -> 3

T ex byta ut ObjectMapper...
Kolla användning av com.fasterxml.jackson i bokens källkod...

## resilience

## opentelemetry

## AOT Cache in Java 25

## Null safety + my open rewrite

## HTTP Service clients

* https://docs.spring.io/spring-boot/reference/io/rest-client.html#io.rest-client.httpservice
* https://docs.spring.io/spring-framework/reference/integration/rest-clients.html#rest-http-service-client

## Pkce SA Server

## Spring Data Aot repo

## Migrering & open rewrite

* https://www.moderne.ai/blog/speed-your-spring-boot-3-0-migration
* https://www.moderne.ai/community
* https://docs.openrewrite.org/recipes/java/spring/boot4
* https://docs.openrewrite.org/recipes/java/spring/boot4/upgradespringboot_4_0-community-edition
* https://docs.openrewrite.org/recipes/java/spring/boot4/upgradespringboot_4_0-moderne-edition
* https://docs.moderne.io/user-documentation/moderne-cli/getting-started/cli-intro/
* https://docs.moderne.io/user-documentation/moderne-platform/
 