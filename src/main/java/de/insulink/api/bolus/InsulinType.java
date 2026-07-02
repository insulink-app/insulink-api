package de.insulink.api.bolus;

public enum InsulinType {
  FAST_ACTING,
  LONG_ACTING;

  public boolean isFastActing() {
    return this == FAST_ACTING;
  }

  public boolean isLongActing() {
    return this == LONG_ACTING;
  }
}
