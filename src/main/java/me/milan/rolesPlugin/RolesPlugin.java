package me.milan.rolesPlugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * RolesPlugin — full implementation:
 * - /role <giant|gnome|reset> [player]
 * - /acceptrole and /declinerole
 * - shockwave for giants (sneak + right click with empty hand)
 * - role-swap offer on kill (30s), accept/decline via command or chat (yes/no)
 * - roles persisted to plugins/RolesPlugin/roles.yml
 */
public final class RolesPlugin extends JavaPlugin implements Listener {

    // In-memory maps
    private final Map<UUID, String> playerRoles = new HashMap<>();                  // uuid -> "giant"/"gnome"
    private final Map<UUID, Long> shockwaveCooldowns = new HashMap<>();           // ability cooldowns
    private final Map<UUID, RoleSwapOffer> pendingSwaps = new HashMap<>();        // killerUuid -> offer
    private final Map<UUID, BukkitTask> expiryTasks = new HashMap<>();            // killerUuid -> scheduled expiry

    // Persistence file
    private File rolesFile;
    private YamlConfiguration rolesCfg;

    // constants
    private static final long SHOCKWAVE_COOLDOWN_MS = 5_000L;
    private static final long SWAP_OFFER_MS = 30_000L; // 30s to accept

    private static class RoleSwapOffer {
        final UUID killerId;
        final UUID victimId;
        final String killerRole; // may be null
        final String victimRole; // may be null
        final long timestamp;

        RoleSwapOffer(UUID killerId, UUID victimId, String killerRole, String victimRole) {
            this.killerId = killerId;
            this.victimId = victimId;
            this.killerRole = killerRole;
            this.victimRole = victimRole;
            this.timestamp = System.currentTimeMillis();
        }
    }

    // -------------------- lifecycle --------------------
    @Override
    public void onEnable() {
        getLogger().info("RolesPlugin enabled!");
        Bukkit.getPluginManager().registerEvents(this, this);
        setupPersistence();

        // Register tab completion and executor are automatic because we implement onCommand,
        // but plugin.yml must list the commands (provided below).
    }

    @Override
    public void onDisable() {
        saveRoles();
        cancelAllExpiryTasks();
        getLogger().info("RolesPlugin disabled!");
    }

    // -------------------- persistence --------------------
    private void setupPersistence() {
        rolesFile = new File(getDataFolder(), "roles.yml");
        if (!rolesFile.exists()) {
            rolesFile.getParentFile().mkdirs();
            try {
                rolesFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Failed to create roles.yml: " + e.getMessage());
            }
        }
        rolesCfg = YamlConfiguration.loadConfiguration(rolesFile);
        loadRolesFromConfig();
    }

    private void loadRolesFromConfig() {
        playerRoles.clear();
        if (rolesCfg == null) return;
        for (String key : rolesCfg.getKeys(false)) {
            try {
                UUID u = UUID.fromString(key);
                String role = rolesCfg.getString(key, null);
                if (role != null) playerRoles.put(u, role);
            } catch (IllegalArgumentException ignored) {
            }
        }
        getLogger().info("Loaded " + playerRoles.size() + " persisted roles.");
    }

    private void saveRoles() {
        if (rolesCfg == null) rolesCfg = new YamlConfiguration();
        for (String key : rolesCfg.getKeys(false)) {
            rolesCfg.set(key, null);
        }
        rolesCfg = new YamlConfiguration(); // reset
        for (Map.Entry<UUID, String> e : playerRoles.entrySet()) {
            rolesCfg.set(e.getKey().toString(), e.getValue());
        }
        try {
            rolesCfg.save(rolesFile);
            getLogger().info("Saved " + playerRoles.size() + " roles to roles.yml");
        } catch (IOException ex) {
            getLogger().severe("Failed to save roles.yml: " + ex.getMessage());
        }
    }

    // -------------------- commands --------------------
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase();

        if (cmd.equals("role")) {
            return handleRoleCommand(sender, args);
        }

        if (cmd.equals("acceptrole")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Only players can use this command.");
                return true;
            }
            return handleAccept(p);
        }

        if (cmd.equals("declinerole")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Only players can use this command.");
                return true;
            }
            return handleDecline(p);
        }

        return false;
    }

    private boolean handleRoleCommand(CommandSender sender, String[] args) {
        // Usage: /role <giant|gnome|reset> [player]
        if (args.length < 1 || args.length > 2) {
            sender.sendMessage("§cUsage: /role <giant|gnome|reset> [player]");
            return true;
        }

        String role = args[0].toLowerCase(Locale.ROOT);
        Player target;

        if (args.length == 2) {
            // changing someone else
            if (!sender.hasPermission("rolesplugin.manage")) {
                sender.sendMessage("§cYou do not have permission to change other players' roles.");
                return true;
            }
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found or offline.");
                return true;
            }
        } else {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("§cOnly players can use this command without a target.");
                return true;
            }
            target = p;
        }

        switch (role) {
            case "giant" -> {
                applyGiantRole(target);
                playerRoles.put(target.getUniqueId(), "giant");
                saveRoles();
                target.sendMessage("§aYou are now a §lGiant§a!");
                if (sender != target) sender.sendMessage("§aSet " + target.getName() + " as a Giant.");
            }
            case "gnome" -> {
                applyGnomeRole(target);
                playerRoles.put(target.getUniqueId(), "gnome");
                saveRoles();
                target.sendMessage("§aYou are now a §lGnome§a!");
                if (sender != target) sender.sendMessage("§aSet " + target.getName() + " as a Gnome.");
            }
            case "reset" -> {
                resetPlayer(target);
                playerRoles.remove(target.getUniqueId());
                saveRoles();
                target.sendMessage("§eYou have been reset to normal.");
                if (sender != target) sender.sendMessage("§aReset " + target.getName() + " to normal.");
            }
            default -> sender.sendMessage("§cUnknown role: use giant, gnome or reset.");
        }

        return true;
    }

    // -------------------- role attribute helpers --------------------
    private void setAttr(Player player, Attribute attr, double value) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst != null) inst.setBaseValue(value);
    }

    private void applyGiantRole(Player player) {
        resetPlayer(player);
        setAttr(player, Attribute.MAX_HEALTH, 40.0);
        setAttr(player, Attribute.MOVEMENT_SPEED, 0.1);
        setAttr(player, Attribute.SCALE, 1.5);
        setAttr(player, Attribute.KNOCKBACK_RESISTANCE, 1);
        setAttr(player, Attribute.BLOCK_INTERACTION_RANGE, 10);
        setAttr(player, Attribute.ENTITY_INTERACTION_RANGE, 10);
        setAttr(player, Attribute.JUMP_STRENGTH, 0.55);
        setAttr(player, Attribute.STEP_HEIGHT, 1);
        setAttr(player, Attribute.SAFE_FALL_DISTANCE, 10);
        setAttr(player, Attribute.ATTACK_SPEED, 4);
        setAttr(player, Attribute.ATTACK_DAMAGE, 1.75);
        player.setHealth(Math.min(player.getHealth(), 40.0));
    }

    private void applyGnomeRole(Player player) {
        resetPlayer(player);
        setAttr(player, Attribute.MAX_HEALTH, 14.0);
        setAttr(player, Attribute.MOVEMENT_SPEED, 0.16);
        setAttr(player, Attribute.SCALE, 0.5);
        setAttr(player, Attribute.KNOCKBACK_RESISTANCE, 0);
        setAttr(player, Attribute.BLOCK_INTERACTION_RANGE, 3);
        setAttr(player, Attribute.ENTITY_INTERACTION_RANGE, 3);
        setAttr(player, Attribute.JUMP_STRENGTH, 0.38);
        setAttr(player, Attribute.STEP_HEIGHT, 0.4);
        setAttr(player, Attribute.SAFE_FALL_DISTANCE, 3);
        setAttr(player, Attribute.ATTACK_SPEED, 8);
        setAttr(player, Attribute.ATTACK_DAMAGE, 1);
        player.setHealth(Math.min(player.getHealth(), 14.0));
    }

    private void resetPlayer(Player player) {
        setAttr(player, Attribute.MAX_HEALTH, 20.0);
        setAttr(player, Attribute.MOVEMENT_SPEED, 0.1);
        setAttr(player, Attribute.SCALE, 1.0);
        setAttr(player, Attribute.ATTACK_SPEED, 4);
        setAttr(player, Attribute.ATTACK_DAMAGE, 1);
        setAttr(player, Attribute.JUMP_STRENGTH, 0.42);
        setAttr(player, Attribute.STEP_HEIGHT, 0.6);
        setAttr(player, Attribute.SAFE_FALL_DISTANCE, 3);
        setAttr(player, Attribute.KNOCKBACK_RESISTANCE, 0);
        setAttr(player, Attribute.BLOCK_INTERACTION_RANGE, 5);
        setAttr(player, Attribute.ENTITY_INTERACTION_RANGE, 5);
        player.setHealth(Math.min(player.getHealth(), 20.0));
    }

    // -------------------- shockwave ability --------------------
    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!event.getAction().toString().contains("RIGHT_CLICK")) return;
        if (event.getClickedBlock() != null) return;
        if (player.getInventory().getItemInMainHand().getType() != Material.AIR) return;

        String role = playerRoles.get(player.getUniqueId());
        if (!"giant".equals(role)) return;

        long now = System.currentTimeMillis();
        Long last = shockwaveCooldowns.get(player.getUniqueId());
        if (last != null && now - last < SHOCKWAVE_COOLDOWN_MS) {
            long secs = (SHOCKWAVE_COOLDOWN_MS - (now - last)) / 1000;
            player.sendMessage("§cShockwave on cooldown (" + secs + "s)");
            return;
        }
        shockwaveCooldowns.put(player.getUniqueId(), now);

        // shockwave visuals + damage
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1.5f, 0.5f);
        player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation(), 6, 1, 0.2, 1, 0.05);

        new BukkitRunnable() {
            double radius = 1.0;
            @Override
            public void run() {
                if (radius > 8) {
                    cancel();
                    return;
                }
                player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 20, radius, 0.1, radius, 0.02);
                for (Entity e : player.getNearbyEntities(radius, 2, radius)) {
                    if (e instanceof LivingEntity target && !e.equals(player)) {
                        target.damage(5.0, player);
                        Vector kb = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(1.5);
                        kb.setY(0.5);
                        target.setVelocity(kb);
                    }
                }
                radius += 0.6;
            }
        }.runTaskTimer(this, 0L, 3L);

        player.sendMessage("§6§lShockwave unleashed!");
    }

    // -------------------- role-swap on kill --------------------
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) return;

        UUID vicId = victim.getUniqueId();
        UUID kilId = killer.getUniqueId();
        String victimRole = playerRoles.get(vicId); // may be null
        String killerRole = playerRoles.get(kilId); // may be null

        // If both have no role -> do nothing
        if (victimRole == null && killerRole == null) return;

        // create offer for killer
        killer.sendMessage("§eYou killed " + victim.getName() + "§e who was §l" + (victimRole != null ? capitalize(victimRole) : "Normal") + "§e.");
        killer.sendMessage("§7Type §a/acceptrole §7to swap roles with them, or §c/declinerole§7 to keep yours. (30s)");
        killer.sendMessage("§7(You can also type §ayes§7 or §ano§7 in chat.)");

        RoleSwapOffer offer = new RoleSwapOffer(kilId, vicId, killerRole, victimRole);
        pendingSwaps.put(kilId, offer);

        // schedule expiry
        BukkitTask task = Bukkit.getScheduler().runTaskLater(this, () -> {
            if (pendingSwaps.remove(kilId) != null) {
                killer.sendMessage("§cRole swap offer expired.");
            }
            expiryTasks.remove(kilId);
        }, SWAP_OFFER_MS / 50); // convert ms to ticks (~20 ticks = 1000ms). but Bukkit API runTaskLater expects ticks; easier to use milliseconds/50.
        expiryTasks.put(kilId, task);
    }

    // Accept via command
    private boolean handleAccept(Player player) {
        UUID id = player.getUniqueId();
        RoleSwapOffer offer = pendingSwaps.get(id);
        if (offer == null) {
            player.sendMessage("§cNo pending role swap offer.");
            return true;
        }

        performSwap(offer);
        pendingSwaps.remove(id);
        cancelExpiryTask(id);
        player.sendMessage("§aYou accepted the role swap!");
        return true;
    }

    // Decline via command
    private boolean handleDecline(Player player) {
        UUID id = player.getUniqueId();
        RoleSwapOffer offer = pendingSwaps.remove(id);
        if (offer == null) {
            player.sendMessage("§cNo pending role swap offer.");
            return true;
        }
        cancelExpiryTask(id);
        player.sendMessage("§7You declined the role swap.");
        return true;
    }

    // Also allow yes/no chat shortcut
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player p = event.getPlayer();
        UUID id = p.getUniqueId();
        if (!pendingSwaps.containsKey(id)) return;

        String msg = event.getMessage().trim().toLowerCase(Locale.ROOT);
        if (!msg.equals("yes") && !msg.equals("no") && !msg.equals("y") && !msg.equals("n")) return;

        event.setCancelled(true); // hide from public chat

        if (msg.startsWith("y")) {
            Bukkit.getScheduler().runTask(this, () -> handleAccept(p));
        } else {
            Bukkit.getScheduler().runTask(this, () -> handleDecline(p));
        }
    }

    private void cancelExpiryTask(UUID killerId) {
        BukkitTask t = expiryTasks.remove(killerId);
        if (t != null) t.cancel();
    }

    private void cancelAllExpiryTasks() {
        for (BukkitTask t : expiryTasks.values()) t.cancel();
        expiryTasks.clear();
    }

    private void performSwap(RoleSwapOffer offer) {
        Player killer = Bukkit.getPlayer(offer.killerId);
        Player victim = Bukkit.getPlayer(offer.victimId);

        // Swap roles in map (may be null)
        String oldKillerRole = offer.killerRole;
        String oldVictimRole = offer.victimRole;

        if (oldVictimRole == null && oldKillerRole == null) {
            // nothing
            if (killer != null) killer.sendMessage("§cNothing to swap.");
            return;
        }

        // assign
        if (oldVictimRole != null) playerRoles.put(offer.killerId, oldVictimRole);
        else playerRoles.remove(offer.killerId);

        if (oldKillerRole != null) playerRoles.put(offer.victimId, oldKillerRole);
        else playerRoles.remove(offer.victimId);

        // Apply attributes to both players if online
        if (killer != null) {
            if (oldVictimRole != null && oldVictimRole.equals("giant")) applyGiantRole(killer);
            else if (oldVictimRole != null && oldVictimRole.equals("gnome")) applyGnomeRole(killer);
            else resetPlayer(killer);
        }

        if (victim != null) {
            if (oldKillerRole != null && oldKillerRole.equals("giant")) applyGiantRole(victim);
            else if (oldKillerRole != null && oldKillerRole.equals("gnome")) applyGnomeRole(victim);
            else resetPlayer(victim);
        }

        // messages
        if (killer != null) killer.sendMessage("§aRoles swapped! You are now §l" + (oldVictimRole != null ? capitalize(oldVictimRole) : "Normal") + "§a.");
        if (victim != null) victim.sendMessage("§eYour role was swapped to §l" + (oldKillerRole != null ? capitalize(oldKillerRole) : "Normal") + "§e.");

        // persist
        saveRoles();
    }

    // -------------------- cleanup on quit --------------------
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // we don't remove roles (they persist). But clean temporary maps like cooldowns/pending if needed.
        shockwaveCooldowns.remove(event.getPlayer().getUniqueId());
        // If a player with a pending offer logs out, remove the offer and cancel expiry:
        UUID id = event.getPlayer().getUniqueId();
        if (pendingSwaps.remove(id) != null) cancelExpiryTask(id);
    }

    // -------------------- utilities --------------------
    private String capitalize(String s) {
        if (s == null) return "Normal";
        return s.substring(0,1).toUpperCase(Locale.ROOT) + s.substring(1).toLowerCase(Locale.ROOT);
    }
}
