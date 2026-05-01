package cn.minelock.auth;

import org.bukkit.entity.Player;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SessionManager {
    private final Map<UUID, AuthSession> sessions = new HashMap<>();
    private final Map<String, RememberedSession> remembered = new HashMap<>();
    private ConfigValues settings;

    public SessionManager(ConfigValues settings) {
        this.settings = settings;
    }

    public synchronized void update(ConfigValues settings) {
        this.settings = settings;
    }

    public synchronized AuthSession begin(Player player, PreLoginProfile profile) {
        boolean authenticated = profile.autoLogin() || canReuseSession(profile);
        AuthSession session = new AuthSession(
                player.getName(),
                profile.address(),
                profile.registered(),
                profile.captchaRequired() && !authenticated,
                authenticated
        );
        sessions.put(player.getUniqueId(), session);
        return session;
    }

    public synchronized AuthSession get(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public synchronized boolean isAuthenticated(Player player) {
        AuthSession session = sessions.get(player.getUniqueId());
        return session != null && session.authenticated();
    }

    public synchronized void authenticate(Player player) {
        AuthSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            session.authenticate();
            if (settings.allowSessionReconnect) {
                String ip = session.address() == null ? "unknown" : session.address().getHostAddress();
                remembered.put(UserStore.key(player.getName()), new RememberedSession(ip, System.currentTimeMillis()));
            }
        }
    }

    public synchronized void remove(Player player) {
        sessions.remove(player.getUniqueId());
    }

    private boolean canReuseSession(PreLoginProfile profile) {
        if (!settings.allowSessionReconnect) {
            return false;
        }
        RememberedSession rememberedSession = remembered.get(UserStore.key(profile.identity().name()));
        if (rememberedSession == null || profile.address() == null) {
            return false;
        }
        long maxAge = settings.sessionReconnectMinutes * 60_000L;
        return rememberedSession.ip.equals(profile.address().getHostAddress())
                && System.currentTimeMillis() - rememberedSession.authenticatedAt <= maxAge;
    }

    private record RememberedSession(String ip, long authenticatedAt) {
    }
}
