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
import org.bukkit.event.block.Action;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * RolesPlugin — full implementation with working shockwave:
 * - /role <giant|gnome|reset> [player]
 * - /acceptrole and /declinerole
 * - shockwave for giants (sneak + right click or swing with empty hand)
 * - role-swap offer on kill (30s)
 * - roles persisted to plugins/RolesPlugin/roles.yml
 */
public final class RolesPlugin extends JavaPlugin implements Listener {

    private final Map<UUID, String> playerRoles = new HashMap<>();
    private final Map<UUID, Long> shockwaveCooldowns = new HashMap<>();
    private final Map<UUID, RoleSwapOffer> pendingSwaps = new HashMap<>();
    private final Map<UUID, BukkitTask> expiryTasks = new HashMap<>();

    private File rolesFile;
    private YamlConfiguration rolesCfg;

    private static final long SHOCKWAVE_COOLDOWN_MS = 15_000L;
    private static final long SWAP_OFFER_MS = 30_000L;

    private static class RoleSwapOffer {
        final UUID killerId;
        final UUID victimId;
        final String killerRole;
        final String victimRole;

        RoleSwapOffer(UUID killerId, UUID victimId, String killerRole, String victimRole) {
            this.killerId = killerId;
            this.victimId = victimId;
            this.killerRole = killerRole;
            this.victimRole = victimRole;
        }
    }

    @Override
    public void onEnable() {
        getLogger().info("RolesPlugin enabled!");
        Bukkit.getPluginManager().registerEvents(this, this);
        setupPersistence();
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
            } catch (IllegalArgumentException ignored) {}
        }
        getLogger().info("Loaded " + playerRoles.size() + " persisted roles.");
    }

    private void saveRoles() {
        if (rolesCfg == null) rolesCfg = new YamlConfiguration();
        rolesCfg.getKeys(false).forEach(k -> rolesCfg.set(k, null));
        for (Map.Entry<UUID, String> e : playerRoles.entrySet()) {
            rolesCfg.set(e.getKey().toString(), e.getValue());
        }
        try {
            rolesCfg.save(rolesFile);
        } catch (IOException ex) {
            getLogger().severe("Failed to save roles.yml: " + ex.getMessage());
        }
    }

    // -------------------- commands --------------------
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);

        if (cmd.equals("role")) return handleRoleCommand(sender, args);
        if (cmd.equals("acceptrole") && sender instanceof Player p) return handleAccept(p);
        if (cmd.equals("declinerole") && sender instanceof Player p) return handleDecline(p);

        sender.sendMessage("§cCommand only for players.");
        return true;
    }

    private boolean handleRoleCommand(CommandSender sender, String[] args) {
        if (args.length < 1 || args.length > 2) {
            sender.sendMessage("§cUsage: /role <giant|gnome|reset> [player]");
            return true;
        }

        String role = args[0].toLowerCase(Locale.ROOT);
        Player target;

        if (args.length == 2) {
            if (!sender.hasPermission("rolesplugin.manage")) {
                sender.sendMessage("§cYou do not have permission to change others' roles.");
                return true;
            }
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found.");
                return true;
            }
        } else {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("§cConsole must specify a player.");
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
            }
            case "gnome" -> {
                applyGnomeRole(target);
                playerRoles.put(target.getUniqueId(), "gnome");
                saveRoles();
                target.sendMessage("§aYou are now a §lGnome§a!");
            }
            case "reset" -> {
                resetPlayer(target);
                playerRoles.remove(target.getUniqueId());
                saveRoles();
                target.sendMessage("§eYou have been reset to normal.");
            }
            default -> sender.sendMessage("§cUnknown role.");
        }
        return true;
    }

    // -------------------- attributes --------------------
    private void setAttr(Player p, Attribute a, double v) {
        AttributeInstance inst = p.getAttribute(a);
        if (inst != null) inst.setBaseValue(v);
    }

    private void applyGiantRole(Player p) {
        resetPlayer(p);
        setAttr(p, Attribute.MAX_HEALTH, 40.0);
        setAttr(p, Attribute.MOVEMENT_SPEED, 0.1);
        setAttr(p, Attribute.SCALE, 1.33);
        setAttr(p, Attribute.STEP_HEIGHT, 1);
        setAttr(p, Attribute.SAFE_FALL_DISTANCE, 10);
        setAttr(p, Attribute.BLOCK_INTERACTION_RANGE, 6.5);
        setAttr(p, Attribute.ENTITY_INTERACTION_RANGE, 6.5);
        setAttr(p, Attribute.KNOCKBACK_RESISTANCE, 1);
        setAttr(p, Attribute.EXPLOSION_KNOCKBACK_RESISTANCE, 1);
        setAttr(p, Attribute.ATTACK_KNOCKBACK, 0.5);
        setAttr(p, Attribute.BLOCK_BREAK_SPEED, 1);
        setAttr(p, Attribute.SNEAKING_SPEED, 0.3);
        setAttr(p, Attribute.ATTACK_DAMAGE, 2.5);
        p.setHealth(Math.min(p.getHealth(), 40.0));
    }

    private void applyGnomeRole(Player p) {
        resetPlayer(p);
        setAttr(p, Attribute.MAX_HEALTH, 14.0);
        setAttr(p, Attribute.MOVEMENT_SPEED, 0.16);
        setAttr(p, Attribute.SCALE, 0.5);
        setAttr(p, Attribute.STEP_HEIGHT, 0.49);
        setAttr(p, Attribute.SAFE_FALL_DISTANCE, 2);
        setAttr(p, Attribute.BLOCK_INTERACTION_RANGE, 3);
        setAttr(p, Attribute.ENTITY_INTERACTION_RANGE, 3);
        setAttr(p, Attribute.KNOCKBACK_RESISTANCE, 0);
        setAttr(p, Attribute.EXPLOSION_KNOCKBACK_RESISTANCE, 0);
        setAttr(p, Attribute.ATTACK_KNOCKBACK, 0);
        setAttr(p, Attribute.BLOCK_BREAK_SPEED, 2);
        setAttr(p, Attribute.SNEAKING_SPEED, 0.6);
        setAttr(p, Attribute.ATTACK_DAMAGE, 1);
        p.setHealth(Math.min(p.getHealth(), 14.0));
    }

    private void resetPlayer(Player p) {
        setAttr(p, Attribute.MAX_HEALTH, 20.0);
        setAttr(p, Attribute.MOVEMENT_SPEED, 0.1);
        setAttr(p, Attribute.SCALE, 1);
        setAttr(p, Attribute.STEP_HEIGHT, 0.5);
        setAttr(p, Attribute.SAFE_FALL_DISTANCE, 3);
        setAttr(p, Attribute.BLOCK_INTERACTION_RANGE, 5);
        setAttr(p, Attribute.ENTITY_INTERACTION_RANGE, 5);
        setAttr(p, Attribute.KNOCKBACK_RESISTANCE, 0);
        setAttr(p, Attribute.EXPLOSION_KNOCKBACK_RESISTANCE, 0);
        setAttr(p, Attribute.ATTACK_KNOCKBACK, 0);
        setAttr(p, Attribute.BLOCK_BREAK_SPEED, 1);
        setAttr(p, Attribute.SNEAKING_SPEED, 0.3);
        setAttr(p, Attribute.ATTACK_DAMAGE, 1);
        p.setHealth(Math.min(p.getHealth(), 20.0));
    }

    // -------------------- shockwave (fixed) --------------------
    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();
        if (!(action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) return;
        if (!player.isSneaking()) return;
        if (player.getInventory().getItemInMainHand().getType() != Material.AIR) return;

        triggerShockwave(player);
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;
        if (player.getInventory().getItemInMainHand().getType() != Material.AIR) return;
        triggerShockwave(player);
    }

    private void triggerShockwave(Player player) {
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
                        Vector kb = target.getLocation().toVector()
                                .subtract(player.getLocation().toVector())
                                .normalize().multiply(1.5);
                        kb.setY(0.5);
                        target.setVelocity(kb);
                    }
                }
                radius += 0.6;
            }
        }.runTaskTimer(this, 0L, 3L);

        player.sendMessage("§6§lShockwave unleashed!");
    }

    // -------------------- role-swap system --------------------
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) return;

        UUID vid = victim.getUniqueId();
        UUID kid = killer.getUniqueId();
        String vRole = playerRoles.get(vid);
        String kRole = playerRoles.get(kid);
        if (vRole == null && kRole == null) return;

        killer.sendMessage("§eYou killed " + victim.getName() + " who was " + (vRole != null ? vRole : "Normal") + ".");
        killer.sendMessage("§7Type §a/acceptrole§7 or §c/declinerole§7 (30s).");
        pendingSwaps.put(kid, new RoleSwapOffer(kid, vid, kRole, vRole));

        BukkitTask task = Bukkit.getScheduler().runTaskLater(this, () -> {
            if (pendingSwaps.remove(kid) != null) killer.sendMessage("§cRole swap expired.");
            expiryTasks.remove(kid);
        }, SWAP_OFFER_MS / 50);
        expiryTasks.put(kid, task);
    }

    private boolean handleAccept(Player p) {
        RoleSwapOffer o = pendingSwaps.remove(p.getUniqueId());
        if (o == null) {
            p.sendMessage("§cNo pending swap.");
            return true;
        }
        cancelExpiryTask(p.getUniqueId());
        performSwap(o);
        p.sendMessage("§aYou accepted the swap!");
        return true;
    }

    private boolean handleDecline(Player p) {
        RoleSwapOffer o = pendingSwaps.remove(p.getUniqueId());
        if (o == null) {
            p.sendMessage("§cNo pending swap.");
            return true;
        }
        cancelExpiryTask(p.getUniqueId());
        p.sendMessage("§7You declined the swap.");
        return true;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        if (!pendingSwaps.containsKey(id)) return;

        String msg = e.getMessage().trim().toLowerCase(Locale.ROOT);
        if (!List.of("yes", "y", "no", "n").contains(msg)) return;
        e.setCancelled(true);

        Bukkit.getScheduler().runTask(this, () -> {
            if (msg.startsWith("y")) handleAccept(p);
            else handleDecline(p);
        });
    }

    private void performSwap(RoleSwapOffer o) {
        Player killer = Bukkit.getPlayer(o.killerId);
        Player victim = Bukkit.getPlayer(o.victimId);

        if (o.victimRole == null && o.killerRole == null) return;

        if (o.victimRole != null) playerRoles.put(o.killerId, o.victimRole);
        else playerRoles.remove(o.killerId);

        if (o.killerRole != null) playerRoles.put(o.victimId, o.killerRole);
        else playerRoles.remove(o.victimId);

        if (killer != null) {
            if ("giant".equals(o.victimRole)) applyGiantRole(killer);
            else if ("gnome".equals(o.victimRole)) applyGnomeRole(killer);
            else resetPlayer(killer);
            killer.sendMessage("§aYou are now " + (o.victimRole != null ? o.victimRole : "Normal"));
        }

        if (victim != null) {
            if ("giant".equals(o.killerRole)) applyGiantRole(victim);
            else if ("gnome".equals(o.killerRole)) applyGnomeRole(victim);
            else resetPlayer(victim);
            victim.sendMessage("§eYou are now " + (o.killerRole != null ? o.killerRole : "Normal"));
        }

        saveRoles();
    }

    private void cancelExpiryTask(UUID id) {
        BukkitTask t = expiryTasks.remove(id);
        if (t != null) t.cancel();
    }

    private void cancelAllExpiryTasks() {
        expiryTasks.values().forEach(BukkitTask::cancel);
        expiryTasks.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        shockwaveCooldowns.remove(id);
        if (pendingSwaps.remove(id) != null) cancelExpiryTask(id);
    }
}
