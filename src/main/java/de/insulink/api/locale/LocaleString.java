package de.insulink.api.locale;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class LocaleString {
  public static LocaleString of(String key, String... args) {
    return new LocaleString(key, args);
  }

  private final String key;
  private final String[] args;
}
