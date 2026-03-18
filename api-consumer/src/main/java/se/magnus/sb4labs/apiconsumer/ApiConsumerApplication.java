package se.magnus.sb4labs.apiconsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.client.RestClient;

import static org.springframework.web.client.ApiVersionInserter.usePathSegment;

@SpringBootApplication
@ConfigurationPropertiesScan
@ComponentScan("se.magnus.sb4labs")
public class ApiConsumerApplication {

  final static private Logger LOG = LoggerFactory.getLogger(ApiConsumerApplication.class);

  @Bean
  RestClient restClient(RestClient.Builder builder) {
    return builder
     .apiVersionInserter(usePathSegment(0))
     .build();
  }

  static void main(String[] args) {
    SpringApplication.run(ApiConsumerApplication.class, args);
    LOG.info("ApiConsumerApplication v1 started");
  }

}
