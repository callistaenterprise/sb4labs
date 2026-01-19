package se.magnus.sb4labs.apiprovider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RestController;
import se.magnus.sb4labs.api.core.recommendation.Recommendation;
import se.magnus.sb4labs.api.core.recommendation.RecommendationRestService;
import se.magnus.sb4labs.api.exceptions.InvalidInputException;

import java.util.ArrayList;
import java.util.List;

@RestController
public class RecommendationRestController implements RecommendationRestService {

  private static final Logger LOG = LoggerFactory.getLogger(RecommendationRestController.class);

  @Override
  public List<Recommendation> getRecommendations(int productId) {

    if (productId < 1) {
      throw new InvalidInputException("Invalid productId: " + productId);
    }

    if (productId == 113) {
      LOG.debug("No recommendations found for productId: {}", productId);
      return new ArrayList<>();
    }

    List<Recommendation> list = new ArrayList<>();
    list.add(new Recommendation(productId, 1, "Author 1", 1, "Content 1"));
    list.add(new Recommendation(productId, 2, "Author 2", 2, "Content 2"));
    list.add(new Recommendation(productId, 3, "Author 3", 3, "Content 3"));

    LOG.debug("/recommendation response size: {} for productId={}", list.size(), productId);

    return list;
  }
}
