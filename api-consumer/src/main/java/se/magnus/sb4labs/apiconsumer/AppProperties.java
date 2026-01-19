package se.magnus.sb4labs.apiconsumer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
  @NotNull @Valid AppProperties.ServiceInfo productService,
  @NotNull @Valid AppProperties.ServiceInfo recommendationService,
  @NotNull @Valid AppProperties.ServiceInfo reviewService
) {
  public record ServiceInfo(@NotBlank String host, @Positive int port, @Positive int apiversion) {}
}
