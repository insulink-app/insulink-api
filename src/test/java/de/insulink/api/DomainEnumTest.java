package de.insulink.api;

import de.insulink.api.glucose.GlucoseUnit;
import de.insulink.api.insulin.InsulinType;
import de.insulink.api.sensor.SensorType;
import de.insulink.api.sport.SportMeasurementType;
import de.insulink.api.user.session.UserSessionStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The closed sets the whole domain is written against, and the ask-don't-compare
 * accessors the code reads them through. Each is pinned to the constants it has
 * today: adding one is a decision that ripples into the app and the panel, so it
 * should fail here first rather than surface as a silently unhandled case.
 */
final class DomainEnumTest {
  @Test
  void insulinIsEitherFastOrLongActing() {
    Assertions.assertEquals(2, InsulinType.values().length);
    Assertions.assertTrue(InsulinType.FAST_ACTING.isFastActing());
    Assertions.assertFalse(InsulinType.FAST_ACTING.isLongActing());
    Assertions.assertTrue(InsulinType.LONG_ACTING.isLongActing());
    Assertions.assertFalse(InsulinType.LONG_ACTING.isFastActing());
  }

  /**
   * Storage is mg/dL everywhere; mmol/L exists only as a display choice.
   */
  @Test
  void glucoseIsMeasuredInOneOfTwoUnits() {
    Assertions.assertEquals(2, GlucoseUnit.values().length);
    Assertions.assertTrue(GlucoseUnit.MG_DL.isMgDL());
    Assertions.assertFalse(GlucoseUnit.MG_DL.isMmolL());
    Assertions.assertTrue(GlucoseUnit.MMOL_L.isMmolL());
    Assertions.assertFalse(GlucoseUnit.MMOL_L.isMgDL());
  }

  @Test
  void theTwoSupportedSensorsAreTheDexcomAndTheLibre() {
    Assertions.assertEquals(2, SensorType.values().length);
    Assertions.assertTrue(SensorType.DEXCOM_G7.isDexcomG7());
    Assertions.assertFalse(SensorType.DEXCOM_G7.isAbbottLibre3());
    Assertions.assertTrue(SensorType.ABBOTT_LIBRE3.isAbbottLibre3());
  }

  @Test
  void aSessionIsEitherActiveOrClosed() {
    Assertions.assertEquals(2, UserSessionStatus.values().length);
    Assertions.assertTrue(UserSessionStatus.ACTIVE.isActive());
    Assertions.assertFalse(UserSessionStatus.ACTIVE.isClosed());
    Assertions.assertTrue(UserSessionStatus.CLOSED.isClosed());
  }

  @Test
  void theActivityMetricsAreTheFourTheAppCanRecord() {
    Assertions.assertEquals(4, SportMeasurementType.values().length);
    Assertions.assertNotNull(SportMeasurementType.valueOf("STEPS"));
    Assertions.assertNotNull(SportMeasurementType.valueOf("DISTANCE"));
    Assertions.assertNotNull(SportMeasurementType.valueOf("CALORIES"));
    Assertions.assertNotNull(SportMeasurementType.valueOf("WEIGHT"));
  }
}
