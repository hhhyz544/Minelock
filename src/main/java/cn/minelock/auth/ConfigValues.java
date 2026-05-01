package cn.minelock.auth;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;

public final class ConfigValues {
    public enum CaptchaMode {
        OFF,
        ALWAYS,
        SUSPICIOUS
    }

    public final boolean premiumDetect;
    public final boolean premiumAutoLoginVerified;
    public final boolean premiumRewriteUuidFromName;
    public final boolean blockUnverifiedPremiumNames;
    public final int premiumLookupTimeoutMs;
    public final int premiumLookupCacheMinutes;
    public final boolean premiumFailClosedOnLookupError;

    public final int passwordMinLength;
    public final int passwordHashIterations;
    public final int loginTimeoutSeconds;
    public final int maxLoginAttempts;
    public final boolean allowSessionReconnect;
    public final int sessionReconnectMinutes;
    public final boolean offlineAutoLoginEnabled;
    public final boolean offlineAutoLoginSameIpOnly;
    public final int offlineAutoLoginTtlHours;

    public final boolean invalidNameKick;
    public final int minSecondsBetweenConnections;
    public final int maxConnectionsPerIpPerMinute;
    public final int tempBanSeconds;
    public final CaptchaMode captchaMode;
    public final int captchaConnectionsPerIpPerMinute;
    public final int captchaGlobalConnectionsPer10s;
    public final int maxFailedLoginsPerIp;
    public final int failedLoginWindowSeconds;

    private final FileConfiguration config;
    private final String prefix;

    private ConfigValues(FileConfiguration config) {
        this.config = config;
        this.prefix = color(config.getString("messages.prefix", "&8[&bMineLock&8] &7"));

        this.premiumDetect = config.getBoolean("premium.detect", true);
        this.premiumAutoLoginVerified = config.getBoolean("premium.auto-login-verified", true);
        this.premiumRewriteUuidFromName = config.getBoolean("premium.rewrite-uuid-from-premium-name", false);
        this.blockUnverifiedPremiumNames = config.getBoolean("premium.block-unverified-premium-names", false);
        this.premiumLookupTimeoutMs = Math.max(250, config.getInt("premium.lookup-timeout-ms", 2500));
        this.premiumLookupCacheMinutes = Math.max(1, config.getInt("premium.lookup-cache-minutes", 60));
        this.premiumFailClosedOnLookupError = config.getBoolean("premium.fail-closed-on-lookup-error", false);

        this.passwordMinLength = Math.max(4, config.getInt("security.password-min-length", 6));
        this.passwordHashIterations = Math.max(20000, config.getInt("security.password-hash-iterations", 120000));
        this.loginTimeoutSeconds = Math.max(10, config.getInt("security.login-timeout-seconds", 60));
        this.maxLoginAttempts = Math.max(1, config.getInt("security.max-login-attempts", 5));
        this.allowSessionReconnect = config.getBoolean("security.allow-session-reconnect", false);
        this.sessionReconnectMinutes = Math.max(1, config.getInt("security.session-reconnect-minutes", 10));
        this.offlineAutoLoginEnabled = config.getBoolean("offline-auto-login.enabled", false);
        this.offlineAutoLoginSameIpOnly = config.getBoolean("offline-auto-login.same-ip-only", true);
        this.offlineAutoLoginTtlHours = Math.max(1, config.getInt("offline-auto-login.ttl-hours", 24));

        this.invalidNameKick = config.getBoolean("anti-bot.invalid-name-kick", true);
        this.minSecondsBetweenConnections = Math.max(0, config.getInt("anti-bot.min-seconds-between-connections", 1));
        this.maxConnectionsPerIpPerMinute = Math.max(1, config.getInt("anti-bot.max-connections-per-ip-per-minute", 8));
        this.tempBanSeconds = Math.max(10, config.getInt("anti-bot.temp-ban-seconds", 120));
        this.captchaMode = parseCaptchaMode(config.getString("anti-bot.captcha-mode", "suspicious"));
        this.captchaConnectionsPerIpPerMinute = Math.max(1, config.getInt("anti-bot.captcha-connections-per-ip-per-minute", 3));
        this.captchaGlobalConnectionsPer10s = Math.max(1, config.getInt("anti-bot.captcha-global-connections-per-10s", 20));
        this.maxFailedLoginsPerIp = Math.max(1, config.getInt("anti-bot.max-failed-logins-per-ip", 8));
        this.failedLoginWindowSeconds = Math.max(30, config.getInt("anti-bot.failed-login-window-seconds", 300));
    }

    public static ConfigValues from(FileConfiguration config) {
        return new ConfigValues(config);
    }

    public String message(String key) {
        return prefix + color(config.getString("messages." + key, key));
    }

    public String message(String key, String token, Object value) {
        return message(key).replace(token, String.valueOf(value));
    }

    public String rawMessage(String key) {
        return color(config.getString("messages." + key, key));
    }

    private static CaptchaMode parseCaptchaMode(String value) {
        if (value == null) {
            return CaptchaMode.SUSPICIOUS;
        }
        try {
            return CaptchaMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return CaptchaMode.SUSPICIOUS;
        }
    }

    public static String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }
}
