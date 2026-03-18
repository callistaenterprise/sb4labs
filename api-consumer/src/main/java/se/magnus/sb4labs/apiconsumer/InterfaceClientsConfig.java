package se.magnus.sb4labs.apiconsumer;

import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ApiVersionInserter;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;
import se.magnus.sb4labs.api.exceptions.HttpErrorInfo;
import se.magnus.sb4labs.api.exceptions.InvalidInputException;
import se.magnus.sb4labs.api.exceptions.NotFoundException;
import se.magnus.sb4labs.apiconsumer.interfaceclients.ProductClient;
import se.magnus.sb4labs.apiconsumer.interfaceclients.RecommendationClient;
import se.magnus.sb4labs.apiconsumer.interfaceclients.ReviewClient;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT;
import static org.springframework.web.client.ApiVersionInserter.usePathSegment;

@ImportHttpServices(group = "productGroup", types = ProductClient.class)
@ImportHttpServices(group = "recommendationGroup", types = RecommendationClient.class)
@ImportHttpServices(group = "reviewGroup", types = ReviewClient.class)
@Configuration
public class InterfaceClientsConfig {

  final static private Logger LOG = LoggerFactory.getLogger(InterfaceClientsConfig.class);

  private final JsonMapper mapper;

  public InterfaceClientsConfig(JsonMapper mapper) {
    this.mapper = mapper;
  }

  @Bean
  RestClientHttpServiceGroupConfigurer groupConfigurer() {
    return groups -> {

      groups.forEachClient((group, builder) -> {
        builder
//          .baseUrl("http://localhost:7001")
//          .defaultApiVersion("1")
          .defaultHeader("Accept", "application/json")
          .apiVersionInserter(usePathSegment(0))
          .defaultStatusHandler(this::shallErrorBeHandled, this::handleError)
          .requestInterceptor(new LoggingInterceptor());
      });
    };
  }

  private boolean shallErrorBeHandled(HttpStatusCode status) {
    if (status.isError()) LOG.warn("Checking an HTTP error: {}", status.value());
    return status.isSameCodeAs(NOT_FOUND) || status.isSameCodeAs(UNPROCESSABLE_CONTENT);
  }

  private void handleError(HttpRequest request, ClientHttpResponse response) throws IOException {

    switch (response.getStatusCode()) {
      case NOT_FOUND:
        LOG.warn("Got an NOT_FOUND HTTP error response");
        throw new NotFoundException(getErrorMessage(response));
      case UNPROCESSABLE_CONTENT:
        LOG.warn("Got an UNPROCESSABLE_CONTENT HTTP error response");
        throw new InvalidInputException(getErrorMessage(response));
      default:
        LOG.warn("Got an unexpected HTTP error: {}...", response.getStatusCode().value());
        throw new IllegalStateException("Unexpected HTTP error: " + response.getStatusCode().value());
    }
  }

  private String getErrorMessage(ClientHttpResponse response) throws IOException {
    var body = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
    return mapper.readValue(body, HttpErrorInfo.class).message();
  }

  private static class LoggingInterceptor implements ClientHttpRequestInterceptor {

    @Override
    @NullMarked
    public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {

      LOG.info("""
        Performing request: {} {}
        Headers: {}
        """, request.getMethod(), request.getURI(), request.getHeaders());

      ClientHttpResponse response = execution.execute(request, body);

      LOG.info("""
        Response, status code: {}
        Headers: {}
        """, response.getStatusCode(), response.getHeaders());

      return response;
    }
  }
}
