package de.insulink.api.sensor;

public enum SensorType {
  DEXCOM_G7;

  public boolean isDexcomG7() {
    return this == DEXCOM_G7;
  }
}
