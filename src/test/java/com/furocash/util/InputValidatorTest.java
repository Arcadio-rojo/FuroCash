package com.furocash.util;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

public class InputValidatorTest {
    @Test
    void validMobileReturnsTrue(){
        assertTrue(InputValidator.isValidMobile("09270050401"));
    }

    @Test
    void invalidMobileReturnsFalse(){
        assertFalse(InputValidator.isValidMobile("08270050401"));
        assertFalse(InputValidator.isValidMobile("0927005040"));
        assertFalse(InputValidator.isValidMobile("092700504011"));
        assertFalse(InputValidator.isValidMobile("09270050AB"));
        assertFalse(InputValidator.isValidMobile(null));
    }

    @Test
    void validPinReturnsTrue(){
        assertTrue(InputValidator.isValidPin("0000"));
        assertTrue(InputValidator.isValidPin("1234"));
    }

    @Test
    void invalidPinReturnsFalse(){
        assertFalse(InputValidator.isValidPin("023"));
        assertFalse(InputValidator.isValidPin("123456"));
        assertFalse(InputValidator.isValidPin("123A"));
        assertFalse(InputValidator.isValidPin("ABCD"));
        assertFalse(InputValidator.isValidPin(null));
    }

    @Test
    void validAmountReturnsTrue(){
        assertTrue(InputValidator.isValidAmount(new BigDecimal("100.50")));
    }

    @Test
    void amountWithTrailingZerosIsValid(){
        assertTrue(InputValidator.isValidAmount(new BigDecimal("10.500")));
    }

    @Test
    void zeroAmountReturnsFalse(){
        assertFalse(InputValidator.isValidAmount(new BigDecimal("0")));
    }

    @Test
    void negativeAmountReturnsFalse(){
        assertFalse(InputValidator.isValidAmount(new BigDecimal("-5")));
    }

    @Test
    void tooManyDecimalsReturnsFalse(){
        assertFalse(InputValidator.isValidAmount(new BigDecimal("10.999")));
    }

    @Test
    void nullAmountReturnsFalse(){
        assertFalse(InputValidator.isValidAmount(null));
    }

    @Test
    void validNameReturnsTrue(){
        assertTrue(InputValidator.isValidFullName("Arcadio Flocarencia"));
        assertTrue(InputValidator.isValidFullName("Arcadio Jr. Flocarencia"));
    }

    @Test
    void nameWith100CharactersIsValid(){
        assertTrue(InputValidator.isValidFullName("a".repeat(100)));
    }

    @Test
    void emptyNameReturnsFalse(){
        assertFalse(InputValidator.isValidFullName(""));
    }

    @Test
    void blankNameReturnsFalse() {
        assertFalse(InputValidator.isValidFullName(" "));
    }

    @Test
    void nullNameReturnsFalse(){
        assertFalse(InputValidator.isValidFullName(null));
    }

    @Test
    void nameOver100CharactersReturnsFalse(){
        assertFalse(InputValidator.isValidFullName("a".repeat(101)));
    }

}

