package cn.minelock.auth;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PremiumProfileService {
    private static final Pattern ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-fA-F]{32})\"");
    private static final Pattern NAME_PATTERN = Pattern.compile("\"name\"\\s*:\\s*\"([A-Za-z0-9_]{3,16})\"");

    private final JavaPlugin plugin;
    private final Map<String, CachedLookup> cache = new ConcurrentHashMap<>();
    private volatile int timeoutMs;
    private volatile long cacheMillis;

    public PremiumProfileService(JavaPlugin plugin, ConfigValues settings) {
        this.plugin = plugin;
        update(settings);
    }

    public void update(ConfigValues settings) {
        this.timeoutMs = settings.premiumLookupTimeoutMs;
        this.cacheMillis = Duration.ofMinutes(settings.premiumLookupCacheMinutes).toMillis();
    }

    public PremiumLookup lookup(String playerName) {
        String key = playerName.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        CachedLookup cached = cache.get(key);
        if (cached != null && cached.expiresAt > now) {
            return cached.lookup;
        }
        PremiumLookup lookup = requestProfile(playerName);
        cache.put(key, new CachedLookup(lookup, now + cacheMillis));
        return lookup;
    }

    private PremiumLookup requestProfile(String playerName) {
        HttpURLConnection connection = null;
        try {
            String encodedName = URLEncoder.encode(playerName, StandardCharsets.UTF_8);
            URI uri = URI.create("https://api.mojang.com/users/profiles/minecraft/" + encodedName);
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "MineLock/" + plugin.getDescription().getVersion());

            int status = connection.getResponseCode();
            if (status == 204 || status == 404) {
                return PremiumLookup.notFound();
            }
            if (status != 200) {
                plugin.getLogger().warning("Mojang profile lookup failed for " + playerName + ": HTTP " + status);
                return PremiumLookup.error();
            }
            try (InputStream inputStream = connection.getInputStream()) {
                String body = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                UUID uuid = parseMojangUuid(body);
                String name = parseJsonName(body);
                if (uuid == null) {
                    return PremiumLookup.error();
                }
                return PremiumLookup.premium(uuid, name == null ? playerName : name);
            }
        } catch (IOException | IllegalArgumentException ex) {
            plugin.getLogger().fine("Mojang profile lookup failed for " + playerName + ": " + ex.getMessage());
            return PremiumLookup.error();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static UUID parseMojangUuid(String body) {
        Matcher matcher = ID_PATTERN.matcher(body);
        if (!matcher.find()) {
            return null;
        }
        String raw = matcher.group(1).toLowerCase(Locale.ROOT);
        String dashed = raw.substring(0, 8) + "-"
                + raw.substring(8, 12) + "-"
                + raw.substring(12, 16) + "-"
                + raw.substring(16, 20) + "-"
                + raw.substring(20);
        try {
            return UUID.fromString(dashed);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String parseJsonName(String body) {
        Matcher matcher = NAME_PATTERN.matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }

    private record CachedLookup(PremiumLookup lookup, long expiresAt) {
    }
}
