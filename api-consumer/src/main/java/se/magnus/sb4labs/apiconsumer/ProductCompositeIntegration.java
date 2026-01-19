package se.magnus.sb4labs.apiconsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import se.magnus.sb4labs.api.core.product.Product;
import se.magnus.sb4labs.api.core.product.ProductRestService;
import se.magnus.sb4labs.api.core.recommendation.Recommendation;
import se.magnus.sb4labs.api.core.recommendation.RecommendationRestService;
import se.magnus.sb4labs.api.core.review.Review;
import se.magnus.sb4labs.api.core.review.ReviewRestService;

import java.util.ArrayList;
import java.util.List;

@Component
public class ProductCompositeIntegration implements ProductRestService, RecommendationRestService, ReviewRestService {

  private static final Logger LOG = LoggerFactory.getLogger(ProductCompositeIntegration.class);

  private final RestClient restClient;

  private final String productServiceUrl;
  private final String recommendationServiceUrl;
  private final String reviewServiceUrl;

  private final AppProperties props;

  public ProductCompositeIntegration(
    RestClient restClient,
    AppProperties props) {

    this.restClient = restClient;
    this.props = props;

    productServiceUrl = "http://" + props.productService().host() + ":" + props.productService().port() + "/product/";
    recommendationServiceUrl = "http://" + props.recommendationService().host() + ":" + props.recommendationService().port() + "/recommendation?productId=";
    reviewServiceUrl = "http://" + props.reviewService().host() + ":" + props.reviewService().port() + "/review?productId=";
  }

  public Product getProduct(int productId, int delay, int faultPercent) {

    try {
      String url = productServiceUrl + productId;
      LOG.debug("Will call getProduct API on URL: {}", url);

      Product product = restClient.get()
        .uri(url)
        .apiVersion(props.productService().apiversion())
        .retrieve()
        .body(Product.class);
      LOG.debug("Found a product with id: {}", product.productId());

      return product;

    } catch (HttpClientErrorException ex) {
      LOG.warn("Got an unexpected HTTP error: {}, will rethrow it", ex.getStatusCode());
      LOG.warn("Error body: {}", ex.getResponseBodyAsString());
      throw ex;
    }
  }


  public List<Recommendation> getRecommendations(int productId) {

    try {
      String url = recommendationServiceUrl + productId;

      LOG.debug("Will call getRecommendations API on URL: {}", url);
      List<Recommendation> recommendations = restClient.get()
        .uri(url)
        .apiVersion(props.recommendationService().apiversion())
        .retrieve()
        .body(new ParameterizedTypeReference<>() {});

      LOG.debug("Found {} recommendations for a product with id: {}", recommendations.size(), productId);
      return recommendations;

    } catch (Exception ex) {
      LOG.warn("Got an exception while requesting recommendations, return zero recommendations: {}", ex.getMessage());
      return new ArrayList<>();
    }
  }

  public List<Review> getReviews(int productId) {

    try {
      String url = reviewServiceUrl + productId;

      LOG.debug("Will call getReviews API on URL: {}", url);
      List<Review> reviews = restClient.get()
        .uri(url)
        .apiVersion(props.reviewService().apiversion())
        .retrieve()
        .body(new ParameterizedTypeReference<>() {});

      LOG.debug("Found {} reviews for a product with id: {}", reviews.size(), productId);
      return reviews;

    } catch (Exception ex) {
      LOG.warn("Got an exception while requesting reviews, return zero reviews: {}", ex.getMessage());
      return new ArrayList<>();
    }
  }
}
