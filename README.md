# MineLock

MineLock is a Paper/Spigot login plugin with:

- Premium name lookup through Mojang's profile API.
- Verified premium auto-login when the server/proxy supplies the Mojang UUID.
- Offline and third-party UUID storage in `plugins/MineLock/users.yml`.
- Password registration/login for non-verified accounts.
- Optional offline auto-login after a successful password login.
- Anti-bot limits, login freeze, timeout, failed-login bans, and optional captcha.

## Important security note

On an offline-mode backend, a plugin cannot cryptographically prove that a player
owns a premium account just from the name. MineLock records premium names, but it
only auto-logins premium players when the login UUID equals Mojang's UUID. Use
`online-mode=true` or Velocity/Bungee secure UUID forwarding if you want safe
premium auto-login.

If you must run offline-mode and want to keep old premium UUID-based data, enable
`premium.rewrite-uuid-from-premium-name`. This rewrites the login UUID to the
Mojang UUID when the name exists, but it is name-based compatibility mode, not
real ownership verification. Password login is still required.

For offline players who have already logged in with a password, enable
`offline-auto-login.enabled`. By default it only trusts the same IP and expires
after 24 hours.

## Build

```bash
gradle build
```

The jar will be in `build/libs/MineLock-1.0.0.jar`.

If your server is newer than the compile API in `build.gradle`, you can update
the `paper-api` version. The plugin only uses stable Bukkit/Paper APIs.

## Install

1. Put the built jar into your server's `plugins/` directory.
2. Start the server once.
3. Edit `plugins/MineLock/config.yml`.
4. Restart or run `/minelock reload`.

For proxy networks, also enable secure player info forwarding at the proxy and
backend server level.
