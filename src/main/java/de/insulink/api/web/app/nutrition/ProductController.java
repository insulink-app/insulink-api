package de.insulink.api.web.app.nutrition;

import de.insulink.api.nutrition.Product;
import de.insulink.api.nutrition.ProductRepository;
import de.insulink.api.user.UserRepository;
import de.insulink.api.web.request.ApiRequestBody;
import de.insulink.api.web.response.ApiResponse;
import de.insulink.api.web.security.app.AppEndpoint;
import de.insulink.api.web.security.app.AppRestController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

/**
 * The user's food product database. {@code find} returns them in the user's
 * order; {@code sync} replaces the set with the app's complete current list.
 */
@RestController
public final class ProductController extends AppRestController {
  private final ProductRepository productRepository;

  private ProductController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository, ProductRepository productRepository
  ) {
    super(authenticationKey, userRepository);
    this.productRepository = productRepository;
  }

  @AppEndpoint
  @RequestMapping(path = "/nutrition/products/find/", method = RequestMethod.GET)
  public CompletableFuture<ApiResponse> findProducts(HttpServletRequest request) {
    return productRepository.findByUserIdOrderByOrderIndex(findUserId(request))
      .thenApply(products -> products.stream().map(this::information).toList())
      .thenApply(products -> ApiResponse.success(Map.of("products", products)));
  }

  @AppEndpoint
  @RequestMapping(path = "/nutrition/products/sync/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> syncProducts(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var userId = findUserId(request);
    var entries = ApiRequestBody.of(payload, response).getObjectList("products");
    var fresh = IntStream.range(0, entries.size())
      .mapToObj(index -> product(userId, entries.get(index), index)).toList();
    return productRepository.findByUserIdOrderByOrderIndex(userId)
      .thenCompose(existing ->
        NutritionCollection.create(productRepository).replace(existing, fresh));
  }

  private Product product(UUID userId, ApiRequestBody entry, int index) {
    var serving = entry.raw().isNull("serving") ? null : entry.getDouble("serving");
    return Product.create(UUID.randomUUID(), userId,
      entry.getString("barcode"), entry.getString("name"),
      entry.getString("brand"), entry.getString("unit"), serving,
      entry.getString("serving_label"), entry.getDouble("carbs"),
      entry.getDouble("fat"), entry.getDouble("protein"),
      entry.getDouble("kcal"), index);
  }

  private Map<String, Object> information(Product product) {
    var map = new HashMap<String, Object>();
    map.put("barcode", product.barcode());
    map.put("name", product.name());
    map.put("brand", product.brand());
    map.put("unit", product.unit());
    map.put("serving", product.serving());
    map.put("serving_label", product.servingLabel());
    map.put("carbs", product.carbs());
    map.put("fat", product.fat());
    map.put("protein", product.protein());
    map.put("kcal", product.kcal());
    return map;
  }
}
