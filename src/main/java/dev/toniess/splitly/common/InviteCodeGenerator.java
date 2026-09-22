package dev.toniess.splitly.common;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Random;

@Component
public class InviteCodeGenerator {
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTVWXYZ123456789";
    final private SecureRandom random = new SecureRandom();

    public String generate() {
        final StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            stringBuilder.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return stringBuilder.toString();
    }
}
