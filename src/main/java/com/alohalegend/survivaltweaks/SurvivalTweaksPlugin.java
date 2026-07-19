package com.alohalegend.survivaltweaks;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class SurvivalTweaksPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter, HeadDropPolicy.HeadDropState {
    private static final String HEAD_ROLL_FILE = "player-head-rolls.yml";

    private File headDropDataFile;
    private YamlConfiguration headDropData;
    private HeadDropSettings headDropSettings;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        loadHeadDropData();

        registerCommand("biome");
        registerCommand("survivaltweaks");
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        saveHeadDropData();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("biome")) {
            return handleBiome(sender);
        }
        if (!command.getName().equalsIgnoreCase("survivaltweaks")) {
            return false;
        }
        return handleAdmin(sender, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("survivaltweaks") || args.length != 1) {
            return Collections.emptyList();
        }
        return List.of("reload", "status").stream()
            .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT)))
            .toList();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!headDropSettings.enabled()) {
            return;
        }

        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) {
            return;
        }

        HeadDropSubject killerSubject = subjectOf(killer);
        HeadDropSubject victimSubject = subjectOf(victim);
        if (!HeadDropPolicy.isEligible(headDropSettings, killerSubject, victimSubject, this, System.currentTimeMillis())) {
            return;
        }

        recordHeadRoll(killer.getUniqueId(), victim.getUniqueId());
        if (ThreadLocalRandom.current().nextDouble() > headDropSettings.chance()) {
            return;
        }

        ItemStack head = createPlayerHead(victim);
        if (headDropSettings.addToDeathDrops()) {
            event.getDrops().add(head);
        } else {
            victim.getWorld().dropItemNaturally(victim.getLocation(), head);
        }

        headDropData.set("drops." + victim.getUniqueId(), headDropData.getInt("drops." + victim.getUniqueId(), 0) + 1);
        saveHeadDropData();

        if (headDropSettings.messageKillerOnDrop()) {
            String message = getConfig().getString("messages.player-head-drop", "&6A rare trophy dropped: &f%player%'s Head");
            killer.sendMessage(color(message.replace("%player%", victim.getName())));
        }
        if (headDropSettings.logDrops()) {
            getLogger().info(killer.getName() + " received a rare player head drop from " + victim.getName());
        }
    }

    private boolean handleBiome(CommandSender sender) {
        if (!getConfig().getBoolean("biome.enabled", true)) {
            sender.sendMessage(color(getConfig().getString("messages.biome-disabled", "&cThe biome command is currently disabled.")));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /biome.");
            return true;
        }
        if (!hasBiomePermission(player)) {
            player.sendMessage(color(getConfig().getString("messages.no-permission", "&cYou do not have permission to use that command.")));
            return true;
        }

        Biome biome = player.getLocation().getBlock().getBiome();
        String biomeKey = biome.getKey().toString();
        String readableBiome = toTitleCase(biomeKey.substring(biomeKey.indexOf(':') + 1));
        String message = getConfig().getString("messages.biome", "&eBiome: &f%biome% &8(%key%)");
        player.sendMessage(color(message
            .replace("%biome%", readableBiome)
            .replace("%key%", biomeKey)));
        return true;
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("survivaltweaks.admin")) {
            sender.sendMessage(color(getConfig().getString("messages.no-permission", "&cYou do not have permission to use that command.")));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            loadSettings();
            loadHeadDropData();
            sender.sendMessage("SurvivalTweaks config reloaded.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
            sender.sendMessage("SurvivalTweaks status");
            sender.sendMessage("Biome command: " + getConfig().getBoolean("biome.enabled", true));
            sender.sendMessage("Player head drops: " + headDropSettings.enabled());
            sender.sendMessage("Head drop chance: " + headDropSettings.chance());
            sender.sendMessage("Tracked killers: " + childCount("killers"));
            sender.sendMessage("Tracked victims: " + childCount("victims"));
            sender.sendMessage("Recorded drops: " + childCount("drops"));
            return true;
        }
        sender.sendMessage("Usage: /survivaltweaks <reload|status>");
        return true;
    }

    private void registerCommand(String name) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
    }

    private void loadSettings() {
        headDropSettings = HeadDropSettings.fromConfig(getConfig());
    }

    private HeadDropSubject subjectOf(Player player) {
        return new HeadDropSubject(player.getUniqueId(), playtimeHours(player), addressOf(player));
    }

    @Override
    public long lastKillerRoll(UUID killer) {
        return headDropData.getLong("killers." + killer + ".last-roll", 0L);
    }

    @Override
    public long lastVictimRoll(UUID victim) {
        return headDropData.getLong("victims." + victim + ".last-roll", 0L);
    }

    @Override
    public long lastPairRoll(UUID killer, UUID victim) {
        return headDropData.getLong("pairs." + killer + "." + victim, 0L);
    }

    private void recordHeadRoll(UUID killerId, UUID victimId) {
        long now = System.currentTimeMillis();
        headDropData.set("killers." + killerId + ".last-roll", now);
        headDropData.set("victims." + victimId + ".last-roll", now);
        headDropData.set("pairs." + killerId + "." + victimId, now);
        saveHeadDropData();
    }

    private long playtimeHours(Player player) {
        return player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L / 60L / 60L;
    }

    private String addressOf(Player player) {
        InetSocketAddress address = player.getAddress();
        if (address == null || address.getAddress() == null) {
            return "";
        }
        return address.getAddress().getHostAddress();
    }

    private boolean hasBiomePermission(Player player) {
        return player.hasPermission("survivaltweaks.biome");
    }

    private ItemStack createPlayerHead(Player victim) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(victim);
            meta.displayName(Component.text(victim.getName() + "'s Head").decoration(TextDecoration.ITALIC, false));
            meta.lore(null);
            head.setItemMeta(meta);
        }
        return head;
    }

    private String toTitleCase(String key) {
        String[] parts = key.toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private Component color(String message) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(message == null ? "" : message);
    }

    private void loadHeadDropData() {
        headDropDataFile = new File(getDataFolder(), HEAD_ROLL_FILE);
        headDropData = YamlConfiguration.loadConfiguration(headDropDataFile);
    }

    private void saveHeadDropData() {
        if (headDropDataFile == null || headDropData == null) {
            return;
        }
        try {
            headDropData.save(headDropDataFile);
        } catch (IOException exception) {
            getLogger().log(Level.WARNING, "Unable to save player head roll data", exception);
        }
    }

    private int childCount(String path) {
        return headDropData.getConfigurationSection(path) == null ? 0 : headDropData.getConfigurationSection(path).getKeys(false).size();
    }
}
