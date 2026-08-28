package com.jzqs.app.common.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordUtilsTest {

    @Test
    void verifyRejectsNoopAndPlaintextHashes() {
        assertFalse(PasswordUtils.verify("123456", "17671863805", "{noop}123456"));
        assertFalse(PasswordUtils.verify("123456", "17671863805", "123456"));
    }

    @Test
    void verifyAcceptsCurrentSha256Hashes() {
        String hash = PasswordUtils.hash("123456", "17671863805");

        assertTrue(PasswordUtils.verify("123456", "17671863805", hash));
    }
}
