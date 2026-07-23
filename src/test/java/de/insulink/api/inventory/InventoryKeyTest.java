package de.insulink.api.inventory;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pins the wire form of the three inventory enums: the app sends and receives
 * the lowercase key, every constant survives the round trip, and anything the
 * app does not know — a null, a typo, a brand added by a newer client — lands
 * on OTHER instead of throwing inside a request.
 */
final class InventoryKeyTest {
  @Test
  void everyItemTypeRoundTripsThroughItsKey() {
    for (var type : InventoryItemType.values()) {
      Assertions.assertEquals(type.name().toLowerCase(), type.key());
      Assertions.assertEquals(type, InventoryItemType.fromKey(type.key()));
    }
  }

  @Test
  void everySensorBrandRoundTripsThroughItsKey() {
    for (var brand : SensorBrand.values()) {
      Assertions.assertEquals(brand.name().toLowerCase(), brand.key());
      Assertions.assertEquals(brand, SensorBrand.fromKey(brand.key()));
    }
  }

  @Test
  void everyPumpBrandRoundTripsThroughItsKey() {
    for (var brand : PumpBrand.values()) {
      Assertions.assertEquals(brand.name().toLowerCase(), brand.key());
      Assertions.assertEquals(brand, PumpBrand.fromKey(brand.key()));
    }
  }

  @Test
  void unknownKeysFallBackToOtherInsteadOfThrowing() {
    Assertions.assertEquals(InventoryItemType.OTHER, InventoryItemType.fromKey("cannula"));
    Assertions.assertEquals(SensorBrand.OTHER, SensorBrand.fromKey("medtronic"));
    Assertions.assertEquals(PumpBrand.OTHER, PumpBrand.fromKey(""));
  }

  @Test
  void aMissingKeyFallsBackToOtherRatherThanFailingOnNull() {
    Assertions.assertEquals(InventoryItemType.OTHER, InventoryItemType.fromKey(null));
    Assertions.assertEquals(SensorBrand.OTHER, SensorBrand.fromKey(null));
    Assertions.assertEquals(PumpBrand.OTHER, PumpBrand.fromKey(null));
  }

  @Test
  void keysAreMatchedCaseInsensitivelySoOlderClientsKeepWorking() {
    Assertions.assertEquals(SensorBrand.DEXCOM, SensorBrand.fromKey("Dexcom"));
    Assertions.assertEquals(InventoryItemType.PUMP, InventoryItemType.fromKey("PUMP"));
  }
}
