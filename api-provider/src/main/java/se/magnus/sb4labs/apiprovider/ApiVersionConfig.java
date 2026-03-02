package se.magnus.sb4labs.apiprovider;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ApiVersionConfig implements WebMvcConfigurer {

  @Override
  public void configureApiVersioning(ApiVersionConfigurer configurer) {
    configurer
      .usePathSegment(0);  // Index of the path segment containing version
//    .useRequestHeader("api-version")
//    .useQueryParam("api-version")
//    .useMediaTypeParameter(MediaType.APPLICATION_JSON, "api-version")
//    .addSupportedVersions("1.0", "2.0")
//    .setDefaultVersion("1.0")
    ;
  }
}