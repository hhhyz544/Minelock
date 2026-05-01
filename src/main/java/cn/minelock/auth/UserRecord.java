package cn.minelock.auth;

import java.util.UUID;

public final class UserRecord {
    private final String key;
    private final String lastName;
    private final UUID providedUuid;
    private final UUID offlineUuid;
    private final UUID premiumUuid;
    private final IdentityType identityType;
    private final boolean hasPassword;
    private final String passwordSalt;
    private final String passwordHash;
    private final int passwordIterations;

    public UserRecord(
            String key,
            String lastName,
            UUID providedUuid,
            UUID offlineUuid,
            UUID premiumUuid,
            IdentityType identityType,
            boolean hasPassword,
            String passwordSalt,
            String passwordHash,
            int passwordIterations
    ) {
        this.key = key;
        this.lastName = lastName;
        this.providedUuid = providedUuid;
        this.offlineUuid = offlineUuid;
        this.premiumUuid = premiumUuid;
        this.identityType = identityType;
        this.hasPassword = hasPassword;
        this.passwordSalt = passwordSalt;
        this.passwordHash = passwordHash;
        this.passwordIterations = passwordIterations;
    }

    public String key() {
        return key;
    }

    public String lastName() {
        return lastName;
    }

    public UUID providedUuid() {
        return providedUuid;
    }

    public UUID offlineUuid() {
        return offlineUuid;
    }

    public UUID premiumUuid() {
        return premiumUuid;
    }

    public IdentityType identityType() {
        return identityType;
    }

    public boolean hasPassword() {
        return hasPassword;
    }

    public String passwordSalt() {
        return passwordSalt;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public int passwordIterations() {
        return passwordIterations;
    }
}
