package cn.minelock.auth;

import java.util.UUID;

public record PremiumLookup(Status status, UUID uuid, String name) {
    public enum Status {
        PREMIUM,
        NOT_FOUND,
        ERROR
    }

    public static PremiumLookup premium(UUID uuid, String name) {
        return new PremiumLookup(Status.PREMIUM, uuid, name);
    }

    public static PremiumLookup notFound() {
        return new PremiumLookup(Status.NOT_FOUND, null, null);
    }

    public static PremiumLookup error() {
        return new PremiumLookup(Status.ERROR, null, null);
    }

    public boolean isPremium() {
        return status == Status.PREMIUM && uuid != null;
    }
}
