package de.insulink.api.web.app.inventory;

import de.insulink.api.inventory.InventoryDelivery;
import de.insulink.api.inventory.InventoryDeliveryRepository;
import de.insulink.api.inventory.InventoryItem;
import de.insulink.api.inventory.InventoryItemRepository;
import de.insulink.api.inventory.InventoryItemType;
import de.insulink.api.inventory.PumpBrand;
import de.insulink.api.inventory.SensorBrand;
import de.insulink.api.iterator.AsyncIterator;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * The user's inventory. {@code find} returns the tracked items with their
 * planned deliveries; {@code sync} replaces the set with the app's complete
 * current list (the app owns writes). Items and deliveries are separate rows,
 * replaced together per user.
 */
@RestController
public final class InventoryController extends AppRestController {
  private final InventoryItemRepository inventoryItemRepository;
  private final InventoryDeliveryRepository inventoryDeliveryRepository;

  private InventoryController(
    @Qualifier("authenticationKey") Key authenticationKey,
    UserRepository userRepository,
    InventoryItemRepository inventoryItemRepository,
    InventoryDeliveryRepository inventoryDeliveryRepository
  ) {
    super(authenticationKey, userRepository);
    this.inventoryItemRepository = inventoryItemRepository;
    this.inventoryDeliveryRepository = inventoryDeliveryRepository;
  }

  @AppEndpoint
  @RequestMapping(path = "/inventory/items/find/", method = RequestMethod.GET)
  public CompletableFuture<ApiResponse> findItems(HttpServletRequest request) {
    var userId = findUserId(request);
    return inventoryItemRepository.findByUserId(userId).thenCompose(items ->
      inventoryDeliveryRepository.findByUserId(userId)
        .thenApply(deliveries -> itemsResponse(items, deliveries)));
  }

  private ApiResponse itemsResponse(
    List<InventoryItem> items, List<InventoryDelivery> deliveries
  ) {
    var byItem = deliveries.stream()
      .collect(Collectors.groupingBy(InventoryDelivery::itemId));
    var infos = items.stream()
      .map(item -> information(item, byItem.getOrDefault(item.id(), List.of())))
      .toList();
    return ApiResponse.success(Map.of("items", infos));
  }

  @AppEndpoint
  @RequestMapping(path = "/inventory/items/sync/", method = RequestMethod.POST)
  public CompletableFuture<ApiResponse> syncItems(
    HttpServletRequest request, @RequestBody String payload,
    HttpServletResponse response
  ) {
    var userId = findUserId(request);
    var items = new ArrayList<InventoryItem>();
    var deliveries = new ArrayList<InventoryDelivery>();
    for (var entry : ApiRequestBody.of(payload, response).getObjectList("items")) {
      var itemId = UUID.randomUUID();
      items.add(item(itemId, userId, entry));
      for (var delivery : entry.getObjectList("deliveries")) {
        deliveries.add(delivery(userId, itemId, delivery));
      }
    }
    return replace(userId, items, deliveries);
  }

  private CompletableFuture<ApiResponse> replace(
    UUID userId, List<InventoryItem> items, List<InventoryDelivery> deliveries
  ) {
    return inventoryItemRepository.findByUserId(userId)
      .thenCompose(oldItems -> inventoryDeliveryRepository.findByUserId(userId)
        .thenCompose(oldDeliveries -> AsyncIterator
          .execute(oldDeliveries, inventoryDeliveryRepository::delete)
          .thenCompose(_ -> AsyncIterator.execute(oldItems,
            inventoryItemRepository::delete))
          .thenCompose(_ -> AsyncIterator.execute(items,
            inventoryItemRepository::save))
          .thenCompose(_ -> AsyncIterator.execute(deliveries,
            inventoryDeliveryRepository::save))
          .thenApply(_ -> ApiResponse.success())));
  }

  private InventoryItem item(UUID id, UUID userId, ApiRequestBody entry) {
    return InventoryItem.create(id, userId, entry.getString("id"),
      entry.getString("name"), entry.getInt("stock"), entry.getInt("base_stock"),
      entry.getDouble("days_per_unit"), entry.getLong("anchor_ms"),
      InventoryItemType.fromKey(entry.getString("type")),
      entry.has("sensor_brand") ? SensorBrand.fromKey(entry.getString("sensor_brand")) : null,
      entry.has("pump_brand") ? PumpBrand.fromKey(entry.getString("pump_brand")) : null);
  }

  private InventoryDelivery delivery(UUID userId, UUID itemId, ApiRequestBody entry) {
    return InventoryDelivery.create(UUID.randomUUID(), userId, itemId,
      entry.getLong("at"), entry.getInt("quantity"));
  }

  private Map<String, Object> information(
    InventoryItem item, List<InventoryDelivery> deliveries
  ) {
    var info = new HashMap<String, Object>();
    info.put("id", item.itemId());
    info.put("name", item.name());
    info.put("stock", item.stock());
    info.put("base_stock", item.baseStock());
    info.put("days_per_unit", item.daysPerUnit());
    info.put("anchor_ms", item.anchorMs());
    info.put("type", item.type().key());
    info.put("deliveries", deliveries.stream()
      .map(delivery -> Map.of(
        "at", (Object) delivery.arrivesAt(), "quantity", delivery.quantity()))
      .toList());
    if (item.sensorBrand() != null) {
      info.put("sensor_brand", item.sensorBrand().key());
    }
    if (item.pumpBrand() != null) {
      info.put("pump_brand", item.pumpBrand().key());
    }
    return info;
  }
}
