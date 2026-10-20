package com.furocash.util;

import com.furocash.exception.InvalidAmountException;
import java.math.BigDecimal;
import java.util.Locale;

public final class BankingSupport {

  private BankingSupport() {}

  public static String accountIdentifier(long id) {
    return String.format(Locale.ROOT, "FC-%010d", id);
  }

  public static void validAmount(BigDecimal amount) {
    if (
      !InputValidator.isValidAmount(amount) ||
      amount.compareTo(new BigDecimal("999999999.00")) > 0
    ) throw new InvalidAmountException(
      "Enter an amount from 0.01 to 999999999.00 with at most 2 decimal places"
    );
  }
}
