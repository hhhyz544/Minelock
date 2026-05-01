package cn.minelock.auth;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public record PlayerIdentity(
        String name,
        UUID providedUuid,
        UUID offlineUuid,
        UUID premiumUuid,
        IdentityType type
) {
    public static PlayerIdentity detect(String name, UUID providedUuid, PremiumLookup premiumLookup) {
        UUID offlineUuid = offlineUuid(name);
        UUID premiumUuid = premiumLookup != null && premiumLookup.isPremium() ? premiumLookup.uuid() : null;
        IdentityType type;
        if (premiumUuid != null && premiumUuid.equals(providedUuid)) {
            type = IdentityType.PREMIUM_VERIFIED;
        } else if (premiumUuid != null) {
            type = IdentityType.PREMIUM_NAME;
        } else if (providedUuid != null && !providedUuid.equals(offlineUuid)) {
            type = IdentityType.THIRD_PARTY;
        } else {
            type = IdentityType.OFFLINE;
        }
        return new PlayerIdentity(name, providedUuid, offlineUuid, premiumUuid, type);
    }

    public boolean isPremiumVerified() {
        return type == IdentityType.PREMIUM_VERIFIED;
    }

    public boolean isUnverifiedPremiumName() {
        return type == IdentityType.PREMIUM_NAME;
    }

    private static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }
}
