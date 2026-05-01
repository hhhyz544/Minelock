package cn.minelock.auth;

import java.net.InetAddress;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

public final class AntiBotService {
    private static final Pattern VALID_NAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    private final Map<String, Deque<Long>> ipConnections = new HashMap<>();
    private final Map<String, Long> lastConnection = new HashMap<>();
    private final Map<String, Long> tempBans = new HashMap<>();
    private final Map<String, Deque<Long>> failedLogins = new HashMap<>();
    private final Deque<Long> globalConnections = new ArrayDeque<>();

    private ConfigValues settings;

    public AntiBotService(ConfigValues settings) {
        this.settings = settings;
    }

    public synchronized void update(ConfigValues settings) {
        this.settings = settings;
    }

    public synchronized AntiBotDecision checkPreLogin(String playerName, InetAddress address) {
        long now = System.currentTimeMillis();
        String ip = ip(address);
        cleanup(now);

        Long bannedUntil = tempBans.get(ip);
        if (bannedUntil != null && bannedUntil > now) {
            return AntiBotDecision.deny("kick-bot-rate");
        }
        if (settings.invalidNameKick && !VALID_NAME.matcher(playerName).matches()) {
            return AntiBotDecision.deny("kick-invalid-name");
        }

        Long previousConnection = lastConnection.get(ip);
        if (previousConnection != null
                && settings.minSecondsBetweenConnections > 0
                && now - previousConnection < settings.minSecondsBetweenConnections * 1000L) {
            return AntiBotDecision.deny("kick-too-fast");
        }
        lastConnection.put(ip, now);

        Deque<Long> ipWindow = ipConnections.computeIfAbsent(ip, ignored -> new ArrayDeque<>());
        ipWindow.addLast(now);
        purgeOlderThan(ipWindow, now - 60_000L);
        globalConnections.addLast(now);
        purgeOlderThan(globalConnections, now - 10_000L);

        if (ipWindow.size() > settings.maxConnectionsPerIpPerMinute) {
            tempBans.put(ip, now + settings.tempBanSeconds * 1000L);
            return AntiBotDecision.deny("kick-bot-rate");
        }

        boolean suspicious = ipWindow.size() >= settings.captchaConnectionsPerIpPerMinute
                || globalConnections.size() >= settings.captchaGlobalConnectionsPer10s;
        return AntiBotDecision.allow(suspicious);
    }

    public synchronized void recordFailedLogin(InetAddress address) {
        long now = System.currentTimeMillis();
        String ip = ip(address);
        Deque<Long> failures = failedLogins.computeIfAbsent(ip, ignored -> new ArrayDeque<>());
        failures.addLast(now);
        purgeOlderThan(failures, now - settings.failedLoginWindowSeconds * 1000L);
        if (failures.size() >= settings.maxFailedLoginsPerIp) {
            tempBans.put(ip, now + settings.tempBanSeconds * 1000L);
        }
    }

    public synchronized void recordSuccessfulLogin(InetAddress address) {
        failedLogins.remove(ip(address));
    }

    public boolean shouldRequireCaptcha(boolean suspicious) {
        if (settings.captchaMode == ConfigValues.CaptchaMode.OFF) {
            return false;
        }
        if (settings.captchaMode == ConfigValues.CaptchaMode.ALWAYS) {
            return true;
        }
        return suspicious;
    }

    private void cleanup(long now) {
        tempBans.entrySet().removeIf(entry -> entry.getValue() <= now);
        long connectionCutoff = now - 60_000L;
        for (Iterator<Map.Entry<String, Deque<Long>>> iterator = ipConnections.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<String, Deque<Long>> entry = iterator.next();
            purgeOlderThan(entry.getValue(), connectionCutoff);
            if (entry.getValue().isEmpty()) {
                iterator.remove();
            }
        }
        long failedCutoff = now - settings.failedLoginWindowSeconds * 1000L;
        for (Iterator<Map.Entry<String, Deque<Long>>> iterator = failedLogins.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<String, Deque<Long>> entry = iterator.next();
            purgeOlderThan(entry.getValue(), failedCutoff);
            if (entry.getValue().isEmpty()) {
                iterator.remove();
            }
        }
        purgeOlderThan(globalConnections, now - 10_000L);
    }

    private static void purgeOlderThan(Deque<Long> timestamps, long cutoff) {
        while (!timestamps.isEmpty() && timestamps.peekFirst() < cutoff) {
            timestamps.removeFirst();
        }
    }

    private static String ip(InetAddress address) {
        return address == null ? "unknown" : address.getHostAddress();
    }
}
