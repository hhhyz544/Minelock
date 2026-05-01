package cn.minelock.auth;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public final class AuthCommand implements CommandExecutor, TabCompleter {
    private final MineLockPlugin plugin;

    public AuthCommand(MineLockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();
        return switch (name) {
            case "login" -> login(sender, args);
            case "register" -> register(sender, args);
            case "captcha" -> captcha(sender, args);
            case "changepassword" -> changePassword(sender, args);
            case "minelock" -> minelock(sender, args);
            default -> false;
        };
    }

    private boolean login(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (plugin.sessionManager().isAuthenticated(player)) {
            player.sendMessage(plugin.settings().message("already-authenticated"));
            return true;
        }
        AuthSession session = plugin.sessionManager().get(player);
        if (session == null) {
            player.kickPlayer(plugin.settings().rawMessage("kick-login-timeout"));
            return true;
        }
        if (!session.registered()) {
            player.sendMessage(plugin.settings().message("not-registered"));
            return true;
        }
        if (!ensureCaptcha(player, session)) {
            return true;
        }
        if (args.length != 1) {
            return false;
        }
        if (plugin.userStore().verifyPassword(player.getName(), args[0])) {
            plugin.sessionManager().authenticate(player);
            plugin.antiBotService().recordSuccessfulLogin(session.address());
            player.sendMessage(plugin.settings().message("login-success"));
            return true;
        }

        plugin.antiBotService().recordFailedLogin(session.address());
        int attempts = session.incrementAttempts();
        if (attempts >= plugin.settings().maxLoginAttempts) {
            player.kickPlayer(plugin.settings().rawMessage("kick-too-many-attempts"));
            return true;
        }
        player.sendMessage(plugin.settings().message("password-wrong"));
        return true;
    }

    private boolean register(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (plugin.sessionManager().isAuthenticated(player) && plugin.userStore().hasPassword(player.getName())) {
            player.sendMessage(plugin.settings().message("already-authenticated"));
            return true;
        }
        AuthSession session = plugin.sessionManager().get(player);
        if (session == null) {
            player.kickPlayer(plugin.settings().rawMessage("kick-login-timeout"));
            return true;
        }
        if (plugin.userStore().hasPassword(player.getName())) {
            player.sendMessage(plugin.settings().message("already-registered"));
            return true;
        }
        if (!ensureCaptcha(player, session)) {
            return true;
        }
        if (args.length != 2) {
            return false;
        }
        if (!args[0].equals(args[1])) {
            player.sendMessage(plugin.settings().message("password-mismatch"));
            return true;
        }
        if (args[0].length() < plugin.settings().passwordMinLength) {
            player.sendMessage(plugin.settings().message("password-too-short", "{min}", plugin.settings().passwordMinLength));
            return true;
        }
        plugin.userStore().setPassword(player.getName(), args[0], plugin.settings().passwordHashIterations);
        plugin.sessionManager().authenticate(player);
        plugin.antiBotService().recordSuccessfulLogin(session.address());
        plugin.captchaService().remove(player);
        player.sendMessage(plugin.settings().message("registered"));
        return true;
    }

    private boolean captcha(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        AuthSession session = plugin.sessionManager().get(player);
        if (session == null || !session.captchaRequired() || session.captchaPassed()) {
            player.sendMessage(plugin.settings().message("already-authenticated"));
            return true;
        }
        if (args.length != 1) {
            return false;
        }
        if (!plugin.captchaService().verify(player, args[0])) {
            plugin.antiBotService().recordFailedLogin(session.address());
            player.sendMessage(plugin.settings().message("captcha-wrong"));
            return true;
        }
        session.passCaptcha();
        player.sendMessage(plugin.settings().message("captcha-ok"));
        player.sendMessage(plugin.settings().message(session.registered() ? "need-login" : "need-register"));
        return true;
    }

    private boolean changePassword(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (!plugin.sessionManager().isAuthenticated(player)) {
            player.sendMessage(plugin.settings().message("not-authenticated"));
            return true;
        }
        boolean hasPassword = plugin.userStore().hasPassword(player.getName());
        if (hasPassword) {
            if (args.length != 3) {
                return false;
            }
            if (!plugin.userStore().verifyPassword(player.getName(), args[0])) {
                player.sendMessage(plugin.settings().message("password-wrong"));
                return true;
            }
            if (!args[1].equals(args[2])) {
                player.sendMessage(plugin.settings().message("password-mismatch"));
                return true;
            }
            if (args[1].length() < plugin.settings().passwordMinLength) {
                player.sendMessage(plugin.settings().message("password-too-short", "{min}", plugin.settings().passwordMinLength));
                return true;
            }
            plugin.userStore().setPassword(player.getName(), args[1], plugin.settings().passwordHashIterations);
        } else {
            if (args.length != 2) {
                sender.sendMessage("/changepassword <newPassword> <newPassword>");
                return true;
            }
            if (!args[0].equals(args[1])) {
                player.sendMessage(plugin.settings().message("password-mismatch"));
                return true;
            }
            if (args[0].length() < plugin.settings().passwordMinLength) {
                player.sendMessage(plugin.settings().message("password-too-short", "{min}", plugin.settings().passwordMinLength));
                return true;
            }
            plugin.userStore().setPassword(player.getName(), args[0], plugin.settings().passwordHashIterations);
        }
        player.sendMessage(plugin.settings().message("password-changed"));
        return true;
    }

    private boolean minelock(CommandSender sender, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("minelock.admin")) {
                sender.sendMessage("No permission.");
                return true;
            }
            plugin.reloadPlugin();
            sender.sendMessage(plugin.settings().message("reload-ok"));
            return true;
        }
        sender.sendMessage("/minelock reload");
        return true;
    }

    private boolean ensureCaptcha(Player player, AuthSession session) {
        if (!session.captchaRequired() || session.captchaPassed()) {
            return true;
        }
        player.sendMessage(plugin.settings().message("captcha-required").replace("{code}", "*****"));
        return false;
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage("Only players can use this command.");
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("minelock") && args.length == 1) {
            return List.of("reload");
        }
        return Collections.emptyList();
    }
}
