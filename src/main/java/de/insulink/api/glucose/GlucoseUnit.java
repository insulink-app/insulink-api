package de.insulink.api.glucose;

public enum GlucoseUnit {
  MG_DL,
  MMOL_L;

  public boolean isMgDL() {
    return this == MG_DL;
  }

  public boolean isMmolL() {
    return this == MMOL_L;
  }
}
