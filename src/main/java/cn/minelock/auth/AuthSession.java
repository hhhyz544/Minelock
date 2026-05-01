package cn.minelock.auth;

import java.net.InetAddress;

public final class AuthSession {
    private final String name;
    private final InetAddress address;
    private final boolean registered;
    private final boolean captchaRequired;
    private boolean authenticated;
    private boolean captchaPassed;
    private int attempts;

    public AuthSession(String name, InetAddress address, boolean registered, boolean captchaRequired, boolean authenticated) {
        this.name = name;
        this.address = address;
        this.registered = registered;
        this.captchaRequired = captchaRequired;
        this.authenticated = authenticated;
        this.captchaPassed = !captchaRequired;
    }

    public String name() {
        return name;
    }

    public InetAddress address() {
        return address;
    }

    public boolean registered() {
        return registered;
    }

    public boolean captchaRequired() {
        return captchaRequired;
    }

    public boolean authenticated() {
        return authenticated;
    }

    public void authenticate() {
        this.authenticated = true;
    }

    public boolean captchaPassed() {
        return captchaPassed;
    }

    public void passCaptcha() {
        this.captchaPassed = true;
    }

    public int attempts() {
        return attempts;
    }

    public int incrementAttempts() {
        return ++attempts;
    }
}
