package com.alohalegend.survivaltweaks;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;

public final class SurvivalTweaksPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter, HeadDropPolicy.HeadDropState {
    private static final String HEAD_ROLL_FILE = "player-head-rolls.yml";

    private File headDropDataFile;
    private YamlConfiguration headDropData;
    private HeadDropSettings headDropSettings;
    private boolean keepInventoryEnabled;
    private KeepInventoryPolicy keepInventoryPolicy;
    private boolean logKeptDeaths;
    private boolean spawnerProximityEnabled;
    private int spawnerProximityRadius;
    private String spawnerProximityMessage;
    private boolean platformCheckEnabled;
    private String platformBedrockPermission;
    private String platformBedrockMessage;
    private String platformJavaMessage;
    private String platformUnavailableMessage;
    private String platformUsageMessage;
    private final AtomicLong keptPvpDeaths = new AtomicLong();
    private final AtomicLong keptMobDeaths = new AtomicLong();
    private final AtomicLong keptWorldDeaths = new AtomicLong();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        loadHeadDropData();

        registerCommand("biome");
        registerCommand("platform");
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
        if (command.getName().equalsIgnoreCase("platform")) {
            return handlePlatform(sender, args);
        }
        if (!command.getName().equalsIgnoreCase("survivaltweaks")) {
            return false;
        }
        return handleAdmin(sender, label, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("platform") && args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                .toList();
        }
        if (!command.getName().equalsIgnoreCase("survivaltweaks") || args.length != 1) {
            return Collections.emptyList();
        }
        return List.of("help", "reload", "status").stream()
            .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT)))
            .toList();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onPlayerDeath(PlayerDeathEvent event) {
        applyKeepInventoryRules(event);
        rollPlayerHeadDrop(event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onSpawnerPlace(BlockPlaceEvent event) {
        if (!spawnerProximityEnabled || event.getBlockPlaced().getType() != Material.SPAWNER) {
            return;
        }
        Player player = event.getPlayer();
        if (player.hasPermission("survivaltweaks.spawner.bypass") || player.hasPermission("spawnerslimit.bypass")) {
            return;
        }
        if (!hasNearbySpawner(event.getBlockPlaced())) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(color(spawnerProximityMessage));
    }

    private void rollPlayerHeadDrop(PlayerDeathEvent event) {
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

    private boolean handlePlatform(CommandSender sender, String[] args) {
        if (!platformCheckEnabled) {
            sender.sendMessage("The platform command is currently disabled.");
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(color(platformUsageMessage));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(color(platformUnavailableMessage));
            return true;
        }

        String playerName = target.getName();
        String message = target.hasPermission(platformBedrockPermission) ? platformBedrockMessage : platformJavaMessage;
        sender.sendMessage(color(message.replace("<player>", playerName).replace("%player%", playerName)));
        return true;
    }

    private boolean handleAdmin(CommandSender sender, String label, String[] args) {
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
            sender.sendMessage("Platform check: " + platformCheckEnabled);
            sender.sendMessage("Keep inventory: enabled=" + keepInventoryEnabled + ", pvp=" + keepInventoryPolicy.keepPvp()
                + ", mobs=" + keepInventoryPolicy.keepMobs() + ", world=" + keepInventoryPolicy.keepWorld());
            sender.sendMessage("Kept deaths this uptime: pvp=" + keptPvpDeaths.get() + ", mobs=" + keptMobDeaths.get()
                + ", world=" + keptWorldDeaths.get());
            sender.sendMessage("Spawner proximity: enabled=" + spawnerProximityEnabled + ", radius=" + spawnerProximityRadius);
            sender.sendMessage("Player head drops: " + headDropSettings.enabled());
            sender.sendMessage("Head drop chance: " + headDropSettings.chance());
            sender.sendMessage("Tracked killers: " + childCount("killers"));
            sender.sendMessage("Tracked victims: " + childCount("victims"));
            sender.sendMessage("Recorded drops: " + childCount("drops"));
            return true;
        }
        if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("help"))) {
            sendAdminHelp(sender, label);
            return true;
        }
        sender.sendMessage("Usage: /" + label + " <help|reload|status>");
        return true;
    }

    private void sendAdminHelp(CommandSender sender, String label) {
        sender.sendMessage(color("&6SurvivalTweaks &7- small survival quality-of-life controls"));
        sender.sendMessage(color("&e/biome &7- Shows your current biome. Permission: &fsurvivaltweaks.biome"));
        sender.sendMessage(color("&e/platform <player> &7- Shows whether an online player is marked Bedrock or Java."));
        sender.sendMessage(color("&e/" + label + " status &7- Shows enabled modules, counters, and head-roll data totals."));
        sender.sendMessage(color("&e/" + label + " reload &7- Reloads config.yml and player-head-rolls.yml from disk."));
        sender.sendMessage(color("&7Modules: keep-inventory rules, spawner spacing, rare player heads, biome lookup, platform check."));
        sender.sendMessage(color("&7Bypass: &fsurvivaltweaks.spawner.bypass &7or legacy &fspawnerslimit.bypass"));
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
        keepInventoryEnabled = getConfig().getBoolean("keep-inventory.enabled", true);
        keepInventoryPolicy = new KeepInventoryPolicy(
            getConfig().getBoolean("keep-inventory.rules.pvp", getConfig().getBoolean("PVP", true)),
            getConfig().getBoolean("keep-inventory.rules.mobs", getConfig().getBoolean("Mobs", false)),
            getConfig().getBoolean("keep-inventory.rules.world", getConfig().getBoolean("World", false))
        );
        logKeptDeaths = getConfig().getBoolean("keep-inventory.log-kept-deaths", false);
        spawnerProximityEnabled = getConfig().getBoolean("spawner-proximity.enabled", true);
        spawnerProximityRadius = Math.max(0, Math.min(32, getConfig().getInt("spawner-proximity.radius", 4)));
        spawnerProximityMessage = getConfig().getString("spawner-proximity.message",
            getConfig().getString("message", "&cYou can't place a spawner that close to another, 4 blocks."));
        platformCheckEnabled = getConfig().getBoolean("platform-check.enabled", true);
        platformBedrockPermission = getConfig().getString("platform-check.bedrock-permission", "bedrock.notify");
        platformBedrockMessage = getConfig().getString("platform-check.bedrock-message",
            getConfig().getString("bedrock-message", "<player> is playing on Bedrock Edition"));
        platformJavaMessage = getConfig().getString("platform-check.java-message",
            getConfig().getString("java-message", "<player> is playing on Java Edition"));
        platformUnavailableMessage = getConfig().getString("platform-check.unavailable-message", "&cUnavailable player!");
        platformUsageMessage = getConfig().getString("platform-check.usage-message", "&cValid syntax: /platform <player>");
    }

    private void applyKeepInventoryRules(PlayerDeathEvent event) {
        if (!keepInventoryEnabled) {
            return;
        }
        DeathType deathType = classifyDeath(event);
        if (!keepInventoryPolicy.shouldKeep(deathType)) {
            return;
        }

        event.setDroppedExp(0);
        event.getDrops().clear();
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        incrementKeptDeathCounter(deathType);

        if (logKeptDeaths) {
            getLogger().info("Kept inventory for " + event.getEntity().getName() + " after "
                + deathType.name().toLowerCase(Locale.ROOT) + " death.");
        }
    }

    private DeathType classifyDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Player killer = player.getKiller();
        if (killer != null && !player.getUniqueId().equals(killer.getUniqueId())) {
            return DeathType.PVP;
        }

        if (player.getLastDamageCause() instanceof EntityDamageByEntityEvent entityDamage) {
            if (isPlayerCaused(entityDamage)) {
                return DeathType.PVP;
            }
            return DeathType.MOBS;
        }

        return DeathType.WORLD;
    }

    private boolean isPlayerCaused(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            return true;
        }
        if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            return shooter instanceof Player;
        }
        return false;
    }

    private void incrementKeptDeathCounter(DeathType deathType) {
        switch (deathType) {
            case PVP -> keptPvpDeaths.incrementAndGet();
            case MOBS -> keptMobDeaths.incrementAndGet();
            case WORLD -> keptWorldDeaths.incrementAndGet();
        }
    }

    private boolean hasNearbySpawner(Block placedBlock) {
        World world = placedBlock.getWorld();
        int originX = placedBlock.getX();
        int originY = placedBlock.getY();
        int originZ = placedBlock.getZ();
        for (int x = originX - spawnerProximityRadius; x <= originX + spawnerProximityRadius; x++) {
            for (int y = originY - spawnerProximityRadius; y <= originY + spawnerProximityRadius; y++) {
                for (int z = originZ - spawnerProximityRadius; z <= originZ + spawnerProximityRadius; z++) {
                    if (x == originX && y == originY && z == originZ) {
                        continue;
                    }
                    if (!SpawnerProximityPolicy.isWithinCube(originX, originY, originZ, x, y, z, spawnerProximityRadius)) {
                        continue;
                    }
                    if (world.getBlockAt(x, y, z).getType() == Material.SPAWNER) {
                        return true;
                    }
                }
            }
        }
        return false;
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
