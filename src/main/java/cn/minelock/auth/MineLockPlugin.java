package cn.minelock.auth;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class MineLockPlugin extends JavaPlugin {
    private ConfigValues settings;
    private UserStore userStore;
    private PremiumProfileService premiumProfileService;
    private AntiBotService antiBotService;
    private SessionManager sessionManager;
    private CaptchaService captchaService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.settings = ConfigValues.from(getConfig());
        this.userStore = new UserStore(this);
        this.premiumProfileService = new PremiumProfileService(this, settings);
        this.antiBotService = new AntiBotService(settings);
        this.sessionManager = new SessionManager(settings);
        this.captchaService = new CaptchaService();

        LoginListener listener = new LoginListener(this);
        Bukkit.getPluginManager().registerEvents(listener, this);

        AuthCommand authCommand = new AuthCommand(this);
        registerCommand("login", authCommand);
        registerCommand("register", authCommand);
        registerCommand("captcha", authCommand);
        registerCommand("changepassword", authCommand);
        registerCommand("minelock", authCommand);

        getLogger().info("MineLock enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("MineLock disabled.");
    }

    public void reloadPlugin() {
        reloadConfig();
        this.settings = ConfigValues.from(getConfig());
        this.userStore.reload();
        this.premiumProfileService.update(settings);
        this.antiBotService.update(settings);
        this.sessionManager.update(settings);
    }

    public ConfigValues settings() {
        return settings;
    }

    public UserStore userStore() {
        return userStore;
    }

    public PremiumProfileService premiumProfileService() {
        return premiumProfileService;
    }

    public AntiBotService antiBotService() {
        return antiBotService;
    }

    public SessionManager sessionManager() {
        return sessionManager;
    }

    public CaptchaService captchaService() {
        return captchaService;
    }

    private void registerCommand(String name, AuthCommand executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Command missing from plugin.yml: " + name);
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }
}
