package de.insulink.api.insulin;

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
