package com.furocash.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PinHasherTest {
    @Test
    void hashIsNotThePlainPin (){
        String pin = "1234";
        String hash = PinHasher.hash(pin);

        assertNotEquals(pin, hash);
    }

    @Test
    void correctPinMatches(){
        String pin = "1234";
        String hash = PinHasher.hash(pin);

        assertTrue(PinHasher.matches(pin, hash));
    }

    @Test
    void wrongPinDoesNotMatch(){
        String pin = "1234";
        String hash = PinHasher.hash(pin);

        assertFalse(PinHasher.matches("9999", hash));
    }

    @Test
    void samePinGivesDifferentHashes(){
        String pin = "1234";
        String hash1 = PinHasher.hash(pin);
        String hash2 = PinHasher.hash(pin);

        assertNotEquals(hash1, hash2);
    }


}
