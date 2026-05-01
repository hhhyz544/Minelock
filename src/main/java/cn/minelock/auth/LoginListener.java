package cn.minelock.auth;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.net.InetAddress;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class LoginListener implements Listener {
    private static final Set<String> ALLOWED_COMMANDS = Set.of(
            "login",
            "l",
            "register",
            "reg",
            "captcha",
            "minelock"
    );

    private final MineLockPlugin plugin;
    private final Map<UUID, PreLoginProfile> pendingProfiles = new ConcurrentHashMap<>();

    public LoginListener(MineLockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        ConfigValues settings = plugin.settings();
        InetAddress address = event.getAddress();

        AntiBotDecision decision = plugin.antiBotService().checkPreLogin(event.getName(), address);
        if (!decision.allowed()) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, settings.rawMessage(decision.kickMessageKey()));
            return;
        }

        PremiumLookup premiumLookup = PremiumLookup.notFound();
        if (settings.premiumDetect) {
            premiumLookup = plugin.premiumProfileService().lookup(event.getName());
            if (premiumLookup.status() == PremiumLookup.Status.ERROR && settings.premiumFailClosedOnLookupError) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, settings.rawMessage("kick-premium-lookup-failed"));
                return;
            }
        }

        boolean uuidRewritten = tryRewriteUuidFromPremiumName(event, premiumLookup);
        UUID loginUuid = uuidRewritten ? premiumLookup.uuid() : event.getUniqueId();
        PlayerIdentity identity = PlayerIdentity.detect(event.getName(), loginUuid, premiumLookup, uuidRewritten);
        if (identity.isUnverifiedPremiumName() && settings.blockUnverifiedPremiumNames) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, settings.rawMessage("kick-unverified-premium-name"));
            return;
        }

        plugin.userStore().upsertIdentity(
                event.getName(),
                identity.providedUuid(),
                identity.offlineUuid(),
                identity.premiumUuid(),
                identity.type()
        );

        boolean registered = plugin.userStore().hasPassword(event.getName());
        AutoLoginType autoLoginType = AutoLoginType.NONE;
        boolean autoLogin = !uuidRewritten && identity.isPremiumVerified() && settings.premiumAutoLoginVerified;
        if (autoLogin) {
            autoLoginType = AutoLoginType.PREMIUM;
        } else if (!decision.suspicious() && plugin.userStore().canOfflineAutoLogin(event.getName(), address, settings)) {
            autoLogin = true;
            autoLoginType = AutoLoginType.OFFLINE;
        }
        boolean captchaRequired = !autoLogin && plugin.antiBotService().shouldRequireCaptcha(decision.suspicious());
        pendingProfiles.put(loginUuid, new PreLoginProfile(identity, address, registered, autoLogin, autoLoginType, captchaRequired));
    }

    private boolean tryRewriteUuidFromPremiumName(AsyncPlayerPreLoginEvent event, PremiumLookup premiumLookup) {
        if (!plugin.settings().premiumRewriteUuidFromName || premiumLookup == null || !premiumLookup.isPremium()) {
            return false;
        }
        if (premiumLookup.uuid().equals(event.getUniqueId())) {
            return false;
        }
        try {
            Class<?> profileClass = Class.forName("com.destroystokyo.paper.profile.PlayerProfile");
            Object profile = Bukkit.class
                    .getMethod("createProfile", UUID.class, String.class)
                    .invoke(null, premiumLookup.uuid(), event.getName());
            event.getClass().getMethod("setPlayerProfile", profileClass).invoke(event, profile);
            return true;
        } catch (ReflectiveOperationException | LinkageError ex) {
            plugin.getLogger().log(
                    Level.WARNING,
                    "premium.rewrite-uuid-from-premium-name requires Paper profile APIs; UUID was not rewritten.",
                    ex
            );
            return false;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PreLoginProfile profile = pendingProfiles.remove(player.getUniqueId());
        if (profile == null) {
            PlayerIdentity identity = PlayerIdentity.detect(player.getName(), player.getUniqueId(), PremiumLookup.notFound());
            profile = new PreLoginProfile(
                    identity,
                    player.getAddress() == null ? null : player.getAddress().getAddress(),
                    plugin.userStore().hasPassword(player.getName()),
                    false,
                    AutoLoginType.NONE,
                    true
            );
        }

        AuthSession session = plugin.sessionManager().begin(player, profile);
        if (session.authenticated()) {
            plugin.antiBotService().recordSuccessfulLogin(session.address());
            player.sendMessage(plugin.settings().message(autoLoginMessage(profile.autoLoginType())));
            return;
        }

        if (session.captchaRequired()) {
            String code = plugin.captchaService().create(player);
            player.sendMessage(plugin.settings().message("captcha-required").replace("{code}", code));
        }

        player.sendMessage(plugin.settings().message(session.registered() ? "need-login" : "need-register"));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !plugin.sessionManager().isAuthenticated(player)) {
                player.kickPlayer(plugin.settings().rawMessage("kick-login-timeout"));
            }
        }, plugin.settings().loginTimeoutSeconds * 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingProfiles.remove(event.getPlayer().getUniqueId());
        plugin.sessionManager().remove(event.getPlayer());
        plugin.captchaService().remove(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (plugin.sessionManager().isAuthenticated(event.getPlayer())) {
            return;
        }
        if (event.getTo() == null) {
            event.setCancelled(true);
            return;
        }
        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (plugin.sessionManager().isAuthenticated(player)) {
            return;
        }
        String commandName = parseCommandName(event.getMessage());
        if (ALLOWED_COMMANDS.contains(commandName)) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(plugin.settings().message("command-blocked"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!plugin.sessionManager().isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> event.getPlayer().sendMessage(plugin.settings().message("chat-blocked")));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!plugin.sessionManager().isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!plugin.sessionManager().isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && !plugin.sessionManager().isAuthenticated(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.sessionManager().isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.sessionManager().isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && !plugin.sessionManager().isAuthenticated(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && !plugin.sessionManager().isAuthenticated(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && !plugin.sessionManager().isAuthenticated(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && !plugin.sessionManager().isAuthenticated(player)) {
            event.setCancelled(true);
        }
    }

    private static String parseCommandName(String message) {
        String trimmed = message.startsWith("/") ? message.substring(1) : message;
        int space = trimmed.indexOf(' ');
        String command = space >= 0 ? trimmed.substring(0, space) : trimmed;
        int namespace = command.indexOf(':');
        if (namespace >= 0) {
            command = command.substring(namespace + 1);
        }
        return command.toLowerCase(Locale.ROOT);
    }

    private static String autoLoginMessage(AutoLoginType type) {
        return switch (type) {
            case PREMIUM -> "premium-auto-login";
            case OFFLINE -> "offline-auto-login";
            case NONE -> "login-success";
        };
    }
}
