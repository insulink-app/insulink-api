package de.insulink.api.sensor;

public enum SensorType {
  DEXCOM_G7,
  ABBOTT_LIBRE3;

  public boolean isDexcomG7() {
    return this == DEXCOM_G7;
  }

  public boolean isAbbottLibre3() {
    return this == ABBOTT_LIBRE3;
  }
}
