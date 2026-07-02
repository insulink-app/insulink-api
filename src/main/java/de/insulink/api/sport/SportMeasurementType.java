package de.insulink.api.sport;

/**
 * The kind of a {@link SportMeasurement}. Body weight plus the daily activity
 * metrics, each stored as a value + timestamp like a glucose reading.
 */
public enum SportMeasurementType {
  STEPS,
  DISTANCE,
  CALORIES,
  WEIGHT
}
