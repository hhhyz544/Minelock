package cn.minelock.auth;

import org.bukkit.entity.Player;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class CaptchaService {
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private final SecureRandom random = new SecureRandom();
    private final Map<UUID, String> codes = new HashMap<>();

    public synchronized String create(Player player) {
        String code = nextCode();
        codes.put(player.getUniqueId(), code);
        return code;
    }

    public synchronized boolean verify(Player player, String input) {
        String expected = codes.get(player.getUniqueId());
        if (expected == null || input == null) {
            return false;
        }
        boolean ok = expected.equals(input.trim().toUpperCase(Locale.ROOT));
        if (ok) {
            codes.remove(player.getUniqueId());
        }
        return ok;
    }

    public synchronized void remove(Player player) {
        codes.remove(player.getUniqueId());
    }

    private String nextCode() {
        StringBuilder builder = new StringBuilder(5);
        for (int i = 0; i < 5; i++) {
            builder.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return builder.toString();
    }
}
