package se.magnus.sb4labs.apiconsumer.interfaceclients.resilience;

import org.springframework.cloud.client.circuitbreaker.httpservice.HttpServiceFallback;
import org.springframework.context.annotation.Configuration;
import se.magnus.sb4labs.apiconsumer.interfaceclients.ProductClient;

@Configuration
@HttpServiceFallback(
  group = "productGroup",
  service = ProductClient.class,
  value = ProductFallbacks.class)
class ProductClientFallbackConfig {}
