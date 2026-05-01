package cn.minelock.auth;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

public final class UserStore {
    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration users;

    public UserStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "users.yml");
        reload();
    }

    public synchronized void reload() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder.");
        }
        this.users = YamlConfiguration.loadConfiguration(file);
    }

    public synchronized UserRecord get(String playerName) {
        String key = key(playerName);
        ConfigurationSection section = users.getConfigurationSection("users." + key);
        if (section == null) {
            return null;
        }
        UUID providedUuid = parseUuid(section.getString("provided-uuid"));
        UUID offlineUuid = parseUuid(section.getString("offline-uuid"));
        UUID premiumUuid = parseUuid(section.getString("premium-uuid"));
        IdentityType identityType = parseIdentity(section.getString("identity-type"));
        String passwordHash = section.getString("password.hash");
        String passwordSalt = section.getString("password.salt");
        int iterations = section.getInt("password.iterations", 0);
        return new UserRecord(
                key,
                section.getString("last-name", playerName),
                providedUuid,
                offlineUuid,
                premiumUuid,
                identityType,
                passwordHash != null && passwordSalt != null && iterations > 0,
                passwordSalt,
                passwordHash,
                iterations
        );
    }

    public synchronized boolean hasPassword(String playerName) {
        UserRecord record = get(playerName);
        return record != null && record.hasPassword();
    }

    public synchronized void upsertIdentity(
            String playerName,
            UUID providedUuid,
            UUID offlineUuid,
            UUID premiumUuid,
            IdentityType identityType
    ) {
        String key = key(playerName);
        String path = "users." + key;
        long now = Instant.now().toEpochMilli();
        if (!users.isConfigurationSection(path)) {
            users.createSection(path);
            users.set(path + ".first-seen", now);
        }
        users.set(path + ".last-name", playerName);
        users.set(path + ".provided-uuid", stringify(providedUuid));
        users.set(path + ".offline-uuid", stringify(offlineUuid));
        users.set(path + ".premium-uuid", stringify(premiumUuid));
        users.set(path + ".identity-type", identityType.name());
        users.set(path + ".last-seen", now);
        save();
    }

    public synchronized void setPassword(String playerName, String password, int iterations) {
        String key = key(playerName);
        String path = "users." + key;
        if (!users.isConfigurationSection(path)) {
            users.createSection(path);
            users.set(path + ".first-seen", Instant.now().toEpochMilli());
            users.set(path + ".last-name", playerName);
        }
        PasswordHasher.PasswordHash passwordHash = PasswordHasher.hash(password, iterations);
        users.set(path + ".password.iterations", passwordHash.iterations());
        users.set(path + ".password.salt", passwordHash.salt());
        users.set(path + ".password.hash", passwordHash.hash());
        users.set(path + ".password-changed", Instant.now().toEpochMilli());
        save();
    }

    public synchronized boolean verifyPassword(String playerName, String password) {
        UserRecord record = get(playerName);
        if (record == null || !record.hasPassword()) {
            return false;
        }
        return PasswordHasher.verify(password, record.passwordIterations(), record.passwordSalt(), record.passwordHash());
    }

    private synchronized void save() {
        try {
            users.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save users.yml", ex);
        }
    }

    public static String key(String playerName) {
        return playerName.toLowerCase(Locale.ROOT);
    }

    private static String stringify(UUID uuid) {
        return uuid == null ? null : uuid.toString();
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static IdentityType parseIdentity(String value) {
        if (value == null) {
            return IdentityType.OFFLINE;
        }
        try {
            return IdentityType.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return IdentityType.OFFLINE;
        }
    }
}
