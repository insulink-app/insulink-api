package de.insulink.api.hashing;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * The only thing standing between a database dump and everyone's account. What
 * has to hold: the password is never recoverable from what is stored, the same
 * password hashed twice gives different rows (so equal hashes never reveal equal
 * passwords), and verification still accepts the right one and only the right
 * one.
 */
@SpringJUnitConfig(classes = Hashing.class)
final class HashingTest {
  private static final String PASSWORD = "correct horse battery staple";

  @Autowired
  private Hashing hashing;

  private String hash;

  @BeforeEach
  void hashThePassword() {
    hash = hashing.hash(PASSWORD);
  }

  @Test
  void theStoredHashDoesNotContainThePassword() {
    Assertions.assertFalse(hash.contains(PASSWORD));
    Assertions.assertNotEquals(PASSWORD, hash);
  }

  /**
   * Pins the variant and the cost parameters, because changing either silently
   * invalidates nothing but weakens every hash written from then on.
   */
  @Test
  void theHashCarriesTheVariantAndCostItWasWrittenWith() {
    Assertions.assertTrue(hash.startsWith("$argon2i$"), hash);
    Assertions.assertTrue(hash.contains("m=65536,t=3,p=1"), hash);
  }

  /**
   * A per-hash salt is what stops a leaked table from showing which accounts
   * share a password.
   */
  @Test
  void theSamePasswordHashedTwiceGivesTwoDifferentHashes() {
    Assertions.assertNotEquals(hash, hashing.hash(PASSWORD));
  }

  @Test
  void theRightPasswordVerifiesAgainstItsHash() {
    Assertions.assertTrue(hashing.matches(PASSWORD, hash));
  }

  @Test
  void bothHashesOfTheSamePasswordVerify() {
    Assertions.assertTrue(hashing.matches(PASSWORD, hashing.hash(PASSWORD)));
  }

  @Test
  void aWrongPasswordDoesNotVerify() {
    Assertions.assertFalse(hashing.matches("wrong password", hash));
  }

  @Test
  void aPasswordThatOnlyDiffersInCaseDoesNotVerify() {
    Assertions.assertFalse(hashing.matches(PASSWORD.toUpperCase(), hash));
  }

  @Test
  void anEmptyPasswordDoesNotVerifyAgainstARealOne() {
    Assertions.assertFalse(hashing.matches("", hash));
  }
}
