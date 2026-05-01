package cn.minelock.auth;

import java.net.InetAddress;

public record PreLoginProfile(
        PlayerIdentity identity,
        InetAddress address,
        boolean registered,
        boolean autoLogin,
        AutoLoginType autoLoginType,
        boolean captchaRequired
) {
}
