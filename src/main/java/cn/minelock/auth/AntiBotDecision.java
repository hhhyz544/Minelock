package cn.minelock.auth;

public record AntiBotDecision(boolean allowed, boolean suspicious, String kickMessageKey) {
    public static AntiBotDecision allow(boolean suspicious) {
        return new AntiBotDecision(true, suspicious, null);
    }

    public static AntiBotDecision deny(String kickMessageKey) {
        return new AntiBotDecision(false, false, kickMessageKey);
    }
}
