package se.magnus.sb4labs.apiconsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import se.magnus.sb4labs.api.composite.product.*;
import se.magnus.sb4labs.api.core.product.Product;
import se.magnus.sb4labs.api.core.recommendation.Recommendation;
import se.magnus.sb4labs.api.core.review.Review;
import se.magnus.sb4labs.apiconsumer.interfaceclients.ProductClient;
import se.magnus.sb4labs.apiconsumer.interfaceclients.RecommendationClient;
import se.magnus.sb4labs.apiconsumer.interfaceclients.ReviewClient;

import java.text.MessageFormat;
import java.util.List;
import java.util.concurrent.StructuredTaskScope;

import static java.util.concurrent.StructuredTaskScope.Subtask;

@RestController
public class ProductCompositeRestController implements ProductCompositeRestService {

  final static private Logger LOG = LoggerFactory.getLogger(ProductCompositeRestController.class);

  final private ProductCompositeIntegration integration;

  final private ProductClient productClient;
  final private RecommendationClient recommendationClient;
  final private ReviewClient reviewClient;

  public ProductCompositeRestController(
    ProductCompositeIntegration integration,

    ProductClient productClient,
    RecommendationClient recommendationClient,
    ReviewClient reviewClient
  ) {
    this.integration = integration;

    this.productClient = productClient;
    this.recommendationClient = recommendationClient;
    this.reviewClient = reviewClient;
  }

  @GetMapping(
    value = "/product-composite/sequential/{productId}",
    produces = "application/json")
  ProductAggregate getProductSequential(
    @PathVariable int productId,
    @RequestParam(value = "delay", required = false, defaultValue = "0") int delay,
    @RequestParam(value = "faultPercent", required = false, defaultValue = "0") int faultPercent
  ) {

    LOG.debug("Calling the three APIs sequentially...");

    var product = productClient.getProduct(productId, delay, faultPercent);
    var recommendation = recommendationClient.getRecommendations(productId);
    var reviews = reviewClient.getReviews(productId);

    return createProductAggregate(product, recommendation, reviews);

  }

  @Override
  @GetMapping(
    value = "/product-composite/rest-client/{productId}",
    produces = "application/json")
  public ProductAggregate getProduct(
    @PathVariable int productId,
    @RequestParam(value = "delay", required = false, defaultValue = "0") int delay,
    @RequestParam(value = "faultPercent", required = false, defaultValue = "0") int faultPercent
  ) {

    LOG.debug("Calling the three APIs using RestClient in parallel using StructuredTaskScope...");

    try (var scope = StructuredTaskScope.open()) {

      Subtask<Product> productSubTask = scope.fork(() -> integration.getProduct(productId, delay, faultPercent));
      Subtask<List<Recommendation>> recommendationsSubTask = scope.fork(() -> integration.getRecommendations(productId));
      Subtask<List<Review>> reviewsSubTask = scope.fork(() -> integration.getReviews(productId));

      scope.join();

      return createProductAggregate(productSubTask.get(), recommendationsSubTask.get(), reviewsSubTask.get());

    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @GetMapping(
    value = "/product-composite/interface-client/{productId}",
    produces = "application/json")
  ProductAggregate getProductWithInterfaceClients(
    @PathVariable int productId,
    @RequestParam(value = "delay", required = false, defaultValue = "0") int delay,
    @RequestParam(value = "faultPercent", required = false, defaultValue = "0") int faultPercent
  ) {

    LOG.debug("Calling the three APIs using interface clients in parallel using StructuredTaskScope...");

    try (var scope = StructuredTaskScope.open()) {

      Subtask<Product> productSubTask = scope.fork(() -> productClient.getProduct(productId, delay, faultPercent));
      Subtask<List<Recommendation>> recommendationsSubTask = scope.fork(() -> recommendationClient.getRecommendations(productId));
      Subtask<List<Review>> reviewsSubTask = scope.fork(() -> reviewClient.getReviews(productId));

      scope.join();

      return createProductAggregate(productSubTask.get(), recommendationsSubTask.get(), reviewsSubTask.get());

    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

  }

  @GetMapping("/thread-info")
  public String getThreadName() {
    var ct = Thread.currentThread();
    String threadInfo = MessageFormat.format("Thread info, name = {0}, is virtual = {1}, thread class = {2}",
      ct.toString(),
      ct.isVirtual(),
      ct.getClass().getName());
    LOG.debug("/thread-info returns: {}", threadInfo);
    return threadInfo;
  }

  private ProductAggregate createProductAggregate(
    Product product,
    List<Recommendation> recommendations,
    List<Review> reviews) {

    // 1. Setup product info
    int productId = product.productId();
    String name = product.name();
    int weight = product.weight();

    // 2. Copy summary recommendation info, if available
    List<RecommendationSummary> recommendationSummaries =
      recommendations.stream()
        .map(r -> new RecommendationSummary(r.recommendationId(), r.author(), r.rate(), r.content()))
        .toList();

    // 3. Copy summary review info, if available
    List<ReviewSummary> reviewSummaries =
      reviews.stream()
        .map(r -> new ReviewSummary(r.reviewId(), r.author(), r.subject(), r.content()))
        .toList();

    return new ProductAggregate(productId, name, weight, recommendationSummaries, reviewSummaries);
  }

// WORK IN PROGRESS. TRYiNG TO PROPAGATE TRACT CONTEXT. COPIED FROM https://github.com/micrometer-metrics/micrometer/issues/5761#issuecomment-2580798283
//  private ProductAggregate getProductAggregatePropagateContextForObservability(int productId) {
//    try (var scope = new StructuredTaskScope<>()) {
//      ObservationRegistry registry = null;
//      return Observation.createNotStarted("parent", registry)
//               .observe( () -> {
//                 // Capture all thread local values
//                 ContextSnapshot snapshot = ContextSnapshotFactory.builder().contextRegistry(ContextRegistry.getInstance()).build().captureAll();
//
//                 StructuredTaskScope.Subtask<Product> productSubTask = scope.fork(snapshot.wrap(() -> integration.getProduct(productId)));
//                 StructuredTaskScope.Subtask<List<Recommendation>> recommendationsSubTask = scope.fork(snapshot.wrap(() -> integration.getRecommendations(productId)));
//                 StructuredTaskScope.Subtask<List<Review>> reviewsSubTask = scope.fork(snapshot.wrap(() -> integration.getReviews(productId)));
//
//                 scope.join();
//
//                 return createProductAggregate(productSubTask.get(), recommendationsSubTask.get(), reviewsSubTask.get());
//               });
//    } catch (InterruptedException e) {
//      throw new RuntimeException(e);
//    }
//  }
//

}
