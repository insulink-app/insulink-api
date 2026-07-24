package de.insulink.api.web.app.inventory;

import de.insulink.api.inventory.InventoryDelivery;
import de.insulink.api.inventory.InventoryDeliveryRepository;
import de.insulink.api.inventory.InventoryItem;
import de.insulink.api.inventory.InventoryItemRepository;
import de.insulink.api.inventory.InventoryItemType;
import de.insulink.api.inventory.PumpBrand;
import de.insulink.api.inventory.SensorBrand;
import de.insulink.api.user.UserRepository;
import de.insulink.api.web.AppControllerTest;
import de.insulink.api.web.AsyncEndpoint;
import de.insulink.api.web.TestAuthentication;
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
 * The inventory endpoints as the app sees them: {@code find} renders items with
 * their deliveries nested under the item they belong to, brands only when the
 * item has one; {@code sync} is replace-all, so whatever the user had before is
 * deleted and the posted list becomes the new truth, keeping the order it
 * arrived in.
 */
@AppControllerTest(InventoryController.class)
final class InventoryControllerTest {
  private static final UUID USER_ID = UUID.randomUUID();

  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private UserRepository userRepository;
  @MockitoBean
  private InventoryItemRepository inventoryItemRepository;
  @MockitoBean
  private InventoryDeliveryRepository inventoryDeliveryRepository;

  private AsyncEndpoint endpoint;

  @BeforeEach
  void stubEmptyInventory() {
    endpoint = AsyncEndpoint.on(mockMvc);
    Mockito.when(inventoryItemRepository.findByUserId(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of()));
    Mockito.when(inventoryItemRepository.findByUserIdOrderByOrderIndex(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of()));
    Mockito.when(inventoryDeliveryRepository.findByUserId(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of()));
    Mockito.when(inventoryItemRepository.save(Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    Mockito.when(inventoryDeliveryRepository.save(Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    Mockito.when(inventoryItemRepository.delete(Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    Mockito.when(inventoryDeliveryRepository.delete(Mockito.any()))
      .thenReturn(CompletableFuture.completedFuture(null));
  }

  private InventoryItem sensorItem(UUID id, String itemId, int orderIndex) {
    return InventoryItem.create(id, USER_ID, itemId, "Libre 3", 4, 6, 14.0,
      1_700_000_000_000L, InventoryItemType.SENSOR, SensorBrand.LIBRE, null,
      orderIndex);
  }

  @Test
  void findReturnsAnEmptyItemListForAUserWithNothingTracked() throws Exception {
    endpoint.call(get("/inventory/items/find/")
        .header("Authorization", TestAuthentication.bearer(USER_ID)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true))
      .andExpect(jsonPath("$.items").isEmpty());
  }

  @Test
  void findRendersAnItemWithItsAppSideIdAndLowercaseTypeKey() throws Exception {
    Mockito.when(inventoryItemRepository.findByUserIdOrderByOrderIndex(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(
        List.of(sensorItem(UUID.randomUUID(), "app-item-1", 0))));
    endpoint.call(get("/inventory/items/find/")
        .header("Authorization", TestAuthentication.bearer(USER_ID)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items[0].id").value("app-item-1"))
      .andExpect(jsonPath("$.items[0].name").value("Libre 3"))
      .andExpect(jsonPath("$.items[0].stock").value(4))
      .andExpect(jsonPath("$.items[0].base_stock").value(6))
      .andExpect(jsonPath("$.items[0].days_per_unit").value(14.0))
      .andExpect(jsonPath("$.items[0].anchor_ms").value(1_700_000_000_000L))
      .andExpect(jsonPath("$.items[0].type").value("sensor"))
      .andExpect(jsonPath("$.items[0].sensor_brand").value("libre"));
  }

  @Test
  void anItemWithoutABrandDoesNotCarryTheBrandKeysAtAll() throws Exception {
    var item = InventoryItem.create(UUID.randomUUID(), USER_ID, "app-item-2",
      "Pflaster", 1, 1, 1.0, 0L, InventoryItemType.OTHER, null, null, 0);
    Mockito.when(inventoryItemRepository.findByUserIdOrderByOrderIndex(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of(item)));
    endpoint.call(get("/inventory/items/find/")
        .header("Authorization", TestAuthentication.bearer(USER_ID)))
      .andExpect(jsonPath("$.items[0].type").value("other"))
      .andExpect(jsonPath("$.items[0].sensor_brand").doesNotExist())
      .andExpect(jsonPath("$.items[0].pump_brand").doesNotExist());
  }

  /**
   * Deliveries are their own rows, so the response only holds together if each
   * one is grouped back onto the item it was stored against.
   */
  @Test
  void deliveriesAreNestedUnderTheItemTheyBelongTo() throws Exception {
    var firstId = UUID.randomUUID();
    var secondId = UUID.randomUUID();
    Mockito.when(inventoryItemRepository.findByUserIdOrderByOrderIndex(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of(
        sensorItem(firstId, "app-item-1", 0),
        sensorItem(secondId, "app-item-2", 1))));
    Mockito.when(inventoryDeliveryRepository.findByUserId(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of(
        InventoryDelivery.create(UUID.randomUUID(), USER_ID, secondId, 42L, 3))));
    endpoint.call(get("/inventory/items/find/")
        .header("Authorization", TestAuthentication.bearer(USER_ID)))
      .andExpect(jsonPath("$.items[0].deliveries").isEmpty())
      .andExpect(jsonPath("$.items[1].deliveries[0].at").value(42))
      .andExpect(jsonPath("$.items[1].deliveries[0].quantity").value(3));
  }

  @Test
  void syncStoresThePostedItemsAgainstTheUserInTheOrderTheyArrived()
    throws Exception {
    var payload = """
      {"items": [
        {"id": "a", "name": "Libre 3", "stock": 4, "base_stock": 6,
         "days_per_unit": 14.0, "anchor_ms": 1700000000000, "type": "sensor",
         "sensor_brand": "libre", "deliveries": []},
        {"id": "b", "name": "Pod", "stock": 2, "base_stock": 5,
         "days_per_unit": 3.0, "anchor_ms": 1700000000000, "type": "pump",
         "pump_brand": "omnipod", "deliveries": []}
      ]}""";
    endpoint.call(post("/inventory/items/sync/")
        .header("Authorization", TestAuthentication.bearer(USER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content(payload))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(true));
    var saved = ArgumentCaptor.forClass(InventoryItem.class);
    Mockito.verify(inventoryItemRepository, Mockito.times(2)).save(saved.capture());
    var items = saved.getAllValues();
    org.junit.jupiter.api.Assertions.assertEquals(USER_ID, items.getFirst().userId());
    org.junit.jupiter.api.Assertions.assertEquals("a", items.getFirst().itemId());
    org.junit.jupiter.api.Assertions.assertEquals(0, items.getFirst().orderIndex());
    org.junit.jupiter.api.Assertions.assertEquals(SensorBrand.LIBRE,
      items.getFirst().sensorBrand());
    org.junit.jupiter.api.Assertions.assertEquals("b", items.getLast().itemId());
    org.junit.jupiter.api.Assertions.assertEquals(1, items.getLast().orderIndex());
    org.junit.jupiter.api.Assertions.assertEquals(PumpBrand.OMNIPOD,
      items.getLast().pumpBrand());
  }

  /**
   * Sync is replace-all: the rows the user had before have to be gone, or a
   * removed item would come back on the next pull.
   */
  @Test
  void syncDeletesEverythingTheUserHadBeforeWritingTheNewList() throws Exception {
    var itemId = UUID.randomUUID();
    var oldItem = sensorItem(itemId, "gone", 0);
    var oldDelivery = InventoryDelivery.create(UUID.randomUUID(), USER_ID,
      itemId, 42L, 3);
    Mockito.when(inventoryItemRepository.findByUserId(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of(oldItem)));
    Mockito.when(inventoryDeliveryRepository.findByUserId(USER_ID))
      .thenReturn(CompletableFuture.completedFuture(List.of(oldDelivery)));
    endpoint.call(post("/inventory/items/sync/")
        .header("Authorization", TestAuthentication.bearer(USER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"items\": []}"))
      .andExpect(status().isOk());
    Mockito.verify(inventoryItemRepository).delete(oldItem);
    Mockito.verify(inventoryDeliveryRepository).delete(oldDelivery);
    Mockito.verify(inventoryItemRepository, Mockito.never()).save(Mockito.any());
  }

  @Test
  void syncKeepsTheDeliveriesAttachedToTheItemTheyWerePostedUnder()
    throws Exception {
    var payload = """
      {"items": [
        {"id": "a", "name": "Libre 3", "stock": 4, "base_stock": 6,
         "days_per_unit": 14.0, "anchor_ms": 0, "type": "sensor",
         "deliveries": [{"at": 42, "quantity": 3}]}
      ]}""";
    endpoint.call(post("/inventory/items/sync/")
        .header("Authorization", TestAuthentication.bearer(USER_ID))
        .contentType(MediaType.APPLICATION_JSON)
        .content(payload))
      .andExpect(status().isOk());
    var savedItem = ArgumentCaptor.forClass(InventoryItem.class);
    var savedDelivery = ArgumentCaptor.forClass(InventoryDelivery.class);
    Mockito.verify(inventoryItemRepository).save(savedItem.capture());
    Mockito.verify(inventoryDeliveryRepository).save(savedDelivery.capture());
    org.junit.jupiter.api.Assertions.assertEquals(savedItem.getValue().id(),
      savedDelivery.getValue().itemId());
    org.junit.jupiter.api.Assertions.assertEquals(42L,
      savedDelivery.getValue().arrivesAt());
    org.junit.jupiter.api.Assertions.assertEquals(3,
      savedDelivery.getValue().quantity());
  }
}
