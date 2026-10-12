package com.campusconnect.util;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PasswordHasherTest {
    @Test void hashesWithRandomSaltAndVerifiesOnlyMatchingPassword() {
        var first = PasswordHasher.hash("correct horse battery staple");
        var second = PasswordHasher.hash("correct horse battery staple");
        assertEquals(PasswordHasher.ITERATIONS, first.iterations());
        assertEquals(16, first.salt().length);
        assertEquals(32, first.value().length);
        assertFalse(java.util.Arrays.equals(first.salt(), second.salt()));
        assertTrue(PasswordHasher.verify("correct horse battery staple", first.salt(), first.value(), first.iterations()));
        assertFalse(PasswordHasher.verify("incorrect horse battery staple", first.salt(), first.value(), first.iterations()));
    }
}
