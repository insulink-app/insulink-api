package de.insulink.api.web.app.nutrition;

import de.insulink.api.nutrition.Drink;
import de.insulink.api.nutrition.DrinkRepository;
import de.insulink.api.nutrition.Product;
import de.insulink.api.nutrition.ProductRepository;
import de.insulink.api.user.UserRepository;
import de.insulink.api.web.AppControllerTest;
import de.insulink.api.web.AsyncEndpoint;
import de.insulink.api.web.TestAuthentication;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The two remaining nutrition collections, both replace-all. The product
 * database carries the one nullable field in the whole sync surface — a product
 * sold by weight has no serving size — and it has to survive the round trip as
 * null rather than turning into a zero the app would then display.
 */
@AppControllerTest({ProductController.class, DrinkController.class})
final class ProductAndDrinkControllerTest {
  private static final UUID USER_ID = UUID.randomUUID();

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private UserRepository userRepository;
  @MockitoBean
  private ProductRepository productRepository;
  @MockitoBean
  private DrinkRepository drinkRepository;

  private AsyncEndpoint endpoint;

  @BeforeEach
  void stubEmptyCollections() {
    endpoint = AsyncEndpoint.on(mockMvc);
    Mockito.when(productRepository.findByUserIdOrderByOrderIndex(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of()));
    Mockito.when(drinkRepository.findByUserIdOrderByDrinkedAt(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of()));
    Mockito.when(productRepository.save(Mockito.any()))
      .thenAnswer(invocation -> CompletableFuture
        .completedFuture(invocation.getArgument(0)));
    Mockito.when(drinkRepository.save(Mockito.any()))
      .thenAnswer(invocation -> CompletableFuture
        .completedFuture(invocation.getArgument(0)));
    Mockito.when(productRepository.delete(Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    Mockito.when(drinkRepository.delete(Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
  }

  private Product storedProduct(Double serving, int orderIndex) {
    return Product.create(UUID.randomUUID(), USER_ID, "4001234567890", "Brot",
      "Bäckerei", "g", serving, "Scheibe", 45.0, 1.2, 7.0, 240.0, orderIndex);
  }

  private Product savedProduct() {
    var saved = ArgumentCaptor.forClass(Product.class);
    Mockito.verify(productRepository).save(saved.capture());
    return saved.getValue();
  }

  private void syncProducts(String payload) throws Exception {
    endpoint.call(post("/nutrition/products/sync/")
        .header("Authorization", TestAuthentication.bearer(USER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content(payload))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  void findRendersAProductWithItsWholeNutritionTable() throws Exception {
    Mockito.when(productRepository.findByUserIdOrderByOrderIndex(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(
        List.of(storedProduct(30.0, 0))));
    endpoint.call(get("/nutrition/products/find/")
        .header("Authorization", TestAuthentication.bearer(USER_ID)))
      .andExpect(jsonPath("$.products[0].barcode").value("4001234567890"))
      .andExpect(jsonPath("$.products[0].name").value("Brot"))
      .andExpect(jsonPath("$.products[0].brand").value("Bäckerei"))
      .andExpect(jsonPath("$.products[0].unit").value("g"))
      .andExpect(jsonPath("$.products[0].serving").value(30.0))
      .andExpect(jsonPath("$.products[0].serving_label").value("Scheibe"))
      .andExpect(jsonPath("$.products[0].carbs").value(45.0))
      .andExpect(jsonPath("$.products[0].fat").value(1.2))
      .andExpect(jsonPath("$.products[0].protein").value(7.0))
      .andExpect(jsonPath("$.products[0].kcal").value(240.0));
  }

  @Test
  void syncStoresTheProductsInTheOrderTheyArrived() throws Exception {
    syncProducts("""
      {"products": [
        {"barcode": "1", "name": "Brot", "brand": "B", "unit": "g",
         "serving": 30.0, "serving_label": "Scheibe", "carbs": 45.0,
         "fat": 1.2, "protein": 7.0, "kcal": 240.0},
        {"barcode": "2", "name": "Milch", "brand": "M", "unit": "ml",
         "serving": 200.0, "serving_label": "Glas", "carbs": 4.8,
         "fat": 3.5, "protein": 3.4, "kcal": 64.0}
      ]}""");
    var saved = ArgumentCaptor.forClass(Product.class);
    Mockito.verify(productRepository, Mockito.times(2)).save(saved.capture());
    Assertions.assertEquals(USER_ID, saved.getAllValues().getFirst().userId());
    Assertions.assertEquals(0, saved.getAllValues().getFirst().orderIndex());
    Assertions.assertEquals("Milch", saved.getAllValues().getLast().name());
    Assertions.assertEquals(1, saved.getAllValues().getLast().orderIndex());
  }

  /**
   * A product sold by weight has no serving; null has to stay null, or the app
   * shows a serving size of zero for it.
   */
  @Test
  void aProductWithoutAServingSizeIsStoredWithoutOne() throws Exception {
    syncProducts("""
      {"products": [
        {"barcode": "3", "name": "Mehl", "brand": "M", "unit": "g",
         "serving": null, "serving_label": "", "carbs": 72.0, "fat": 1.0,
         "protein": 10.0, "kcal": 340.0}
      ]}""");
    Assertions.assertNull(savedProduct().serving());
  }

  @Test
  void aProductWithoutAServingSizeComesBackWithoutOne() throws Exception {
    Mockito.when(productRepository.findByUserIdOrderByOrderIndex(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(
        List.of(storedProduct(null, 0))));
    endpoint.call(get("/nutrition/products/find/")
        .header("Authorization", TestAuthentication.bearer(USER_ID)))
      .andExpect(jsonPath("$.products[0].serving").doesNotExist())
      .andExpect(jsonPath("$.products[0].name").value("Brot"));
  }

  @Test
  void syncDropsTheProductsTheUserHadBefore() throws Exception {
    var stale = storedProduct(30.0, 0);
    Mockito.when(productRepository.findByUserIdOrderByOrderIndex(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of(stale)));
    syncProducts("{\"products\": []}");
    Mockito.verify(productRepository).delete(stale);
    Mockito.verify(productRepository, Mockito.never()).save(Mockito.any());
  }

  @Test
  void findRendersALoggedDrink() throws Exception {
    Mockito.when(drinkRepository.findByUserIdOrderByDrinkedAt(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of(Drink.create(
        UUID.randomUUID(), USER_ID, 1_700_000_000_000L, 250, "water"))));
    endpoint.call(get("/nutrition/drinks/find/")
        .header("Authorization", TestAuthentication.bearer(USER_ID)))
      .andExpect(jsonPath("$.drinks[0].at").value(1_700_000_000_000L))
      .andExpect(jsonPath("$.drinks[0].ml").value(250))
      .andExpect(jsonPath("$.drinks[0].kind").value("water"));
  }

  @Test
  void syncStoresTheLoggedDrinksAgainstTheUser() throws Exception {
    endpoint.call(post("/nutrition/drinks/sync/")
        .header("Authorization", TestAuthentication.bearer(USER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"drinks": [{"at": 1700000000000, "ml": 250, "kind": "water"}]}"""))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true));
    var saved = ArgumentCaptor.forClass(Drink.class);
    Mockito.verify(drinkRepository).save(saved.capture());
    Assertions.assertEquals(USER_ID, saved.getValue().userId());
    Assertions.assertEquals(250, saved.getValue().ml());
    Assertions.assertEquals("water", saved.getValue().kind());
    Assertions.assertEquals(1_700_000_000_000L, saved.getValue().drinkedAt());
  }
}
