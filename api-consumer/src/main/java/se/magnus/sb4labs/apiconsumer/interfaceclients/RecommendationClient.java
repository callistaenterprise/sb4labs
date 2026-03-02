package se.magnus.sb4labs.apiconsumer.interfaceclients;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import se.magnus.sb4labs.api.core.recommendation.Recommendation;

import java.util.List;

@HttpExchange("/recommendation")
public interface RecommendationClient {

  @GetExchange
  List<Recommendation> getRecommendations(@RequestParam int productId);
}
