package org.blackgeyer.ausmerzer;

import org.blackgeyer.ausmerzer.config.ConfigLoader;
import org.blackgeyer.ausmerzer.config.MessageUtil;
import org.blackgeyer.ausmerzer.engine.ScheduleManager;
import org.blackgeyer.ausmerzer.explosions.ExplosionNerfer;
import org.blackgeyer.ausmerzer.itemban.ItemBan;
import org.blackgeyer.ausmerzer.itemdamage.ItemDamageNerfer;
import org.blackgeyer.ausmerzer.itemban.RecipeBan;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;

public final class Ausmerzer extends JavaPlugin implements TabExecutor {

    private static final String PERMISSION = "asm.use";

    private ItemBan itemBan;
    private RecipeBan recipeBan;

    @Override
    public void onEnable() {
        ScheduleManager.init(this);

        saveDefaultConfig();
        if (ConfigLoader.load(this) == ConfigLoader.Result.FAILED) {
            getLogger().severe("config.yml is broken, built-in defaults are used until a successful /asm reload.");
        }

        itemBan = new ItemBan();
        recipeBan = new RecipeBan();
        recipeBan.rebuild();

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new ExplosionNerfer(), this);
        pm.registerEvents(new ItemDamageNerfer(), this);
        pm.registerEvents(itemBan, this);
        pm.registerEvents(recipeBan, this);

        PluginCommand command = getCommand("asm");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }

        for (Player player : getServer().getOnlinePlayers()) {
            itemBan.track(player);
            ScheduleManager.get().runAtEntity(player, () -> {
                itemBan.sweep(player);
                recipeBan.hideFrom(player);
            });
        }
    }

    @Override
    public void onDisable() {
        ScheduleManager.shutdown();
    }

    // ---- /asm ----

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            MessageUtil.send(sender, ConfigLoader.MSG_NO_PERMISSION);
            return true;
        }
        if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) {
            MessageUtil.send(sender, ConfigLoader.MSG_INVALID);
            return true;
        }

        ConfigLoader.Result result = ConfigLoader.load(this);

        if (result != ConfigLoader.Result.FAILED) {
            recipeBan.refresh();
            for (Player player : getServer().getOnlinePlayers()) {
                ScheduleManager.get().runAtEntity(player, () -> itemBan.sweep(player));
            }
        }

        String message = switch (result) {
            case SUCCESS -> ConfigLoader.MSG_RELOAD_SUCCESS;
            case WITH_ERRORS -> ConfigLoader.MSG_RELOAD_WITH_ERRORS;
            case FAILED -> ConfigLoader.MSG_RELOAD_FAIL;
        };
        MessageUtil.send(sender, message);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION) || args.length != 1) return List.of();
        return "reload".startsWith(args[0].toLowerCase(Locale.ROOT)) ? List.of("reload") : List.of();
    }
}