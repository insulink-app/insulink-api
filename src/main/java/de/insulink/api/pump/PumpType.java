package de.insulink.api.pump;

public enum PumpType {
  OMNIPOD_DASH;

  public boolean isOmnipodDash() {
    return this == OMNIPOD_DASH;
  }
}
