package me.milan.rolesPlugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RolesPlugin extends JavaPlugin implements Listener {

    private final Map<UUID, String> playerRoles = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    @Override
    public void onEnable() {
        getLogger().info("RolesPlugin enabled!");

        Bukkit.getPluginManager().registerEvents(this, this);

        if (getCommand("role") != null) {
            getCommand("role").setExecutor(this);
            getCommand("role").setTabCompleter((sender, command, alias, args) -> {
                if (args.length == 1) {
                    return List.of("giant", "gnome", "reset");
                }
                return List.of();
            });
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("RolesPlugin disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }

        if (args.length != 1) {
            player.sendMessage("§cUsage: /role <giant|gnome|reset>");
            return true;
        }

        String role = args[0].toLowerCase();

        switch (role) {
            case "giant" -> {
                applyGiantRole(player);
                playerRoles.put(player.getUniqueId(), "giant");
                player.sendMessage("§aYou are now a §lGiant§a!");
            }
            case "gnome" -> {
                applyGnomeRole(player);
                playerRoles.put(player.getUniqueId(), "gnome");
                player.sendMessage("§aYou are now a §lGnome§a!");
            }
            case "reset" -> {
                resetPlayer(player);
                playerRoles.remove(player.getUniqueId());
                player.sendMessage("§eYour role has been reset to §lNormal§e!");
            }
            default -> player.sendMessage("§cUnknown role! Use /role <giant|gnome|reset>");
        }

        return true;
    }

    // ========== ROLE LOGIC ==========
    private void applyGiantRole(Player player) {
        resetPlayer(player);

        AttributeInstance healthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(40.0); // 2x health
            player.setHealth(Math.min(player.getHealth(), healthAttr.getValue()));
        }

        AttributeInstance speedAttr = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) speedAttr.setBaseValue(0.1);

        AttributeInstance scaleAttr = player.getAttribute(Attribute.SCALE);
        if (scaleAttr != null) scaleAttr.setBaseValue(1.5);

        AttributeInstance knockbackAttr = player.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (knockbackAttr != null) knockbackAttr.setBaseValue(1);

        AttributeInstance breachAttr = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
        if (breachAttr != null) breachAttr.setBaseValue(10);

        AttributeInstance ereachAttr = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (ereachAttr != null) ereachAttr.setBaseValue(10);

        AttributeInstance jumpAttr = player.getAttribute(Attribute.JUMP_STRENGTH);
        if (jumpAttr != null) jumpAttr.setBaseValue(0.55);

        AttributeInstance stepupAttr = player.getAttribute(Attribute.STEP_HEIGHT);
        if (stepupAttr != null) stepupAttr.setBaseValue(1);

        AttributeInstance fallAttr = player.getAttribute(Attribute.SAFE_FALL_DISTANCE);
        if (fallAttr != null) fallAttr.setBaseValue(10);

        AttributeInstance attAttr = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attAttr != null) attAttr.setBaseValue(4);

        AttributeInstance attdAttr = player.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attdAttr != null) attdAttr.setBaseValue(1.75);
    }

    private void applyGnomeRole(Player player) {
        resetPlayer(player);

        AttributeInstance healthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(14.0);
            player.setHealth(Math.min(player.getHealth(), healthAttr.getValue()));
        }

        AttributeInstance speedAttr = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) speedAttr.setBaseValue(0.16);

        AttributeInstance scaleAttr = player.getAttribute(Attribute.SCALE);
        if (scaleAttr != null) scaleAttr.setBaseValue(0.5);

        AttributeInstance knockbackAttr = player.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (knockbackAttr != null) knockbackAttr.setBaseValue(0);

        AttributeInstance breachAttr = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
        if (breachAttr != null) breachAttr.setBaseValue(3);

        AttributeInstance ereachAttr = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (ereachAttr != null) ereachAttr.setBaseValue(3);

        AttributeInstance jumpAttr = player.getAttribute(Attribute.JUMP_STRENGTH);
        if (jumpAttr != null) jumpAttr.setBaseValue(0.38);

        AttributeInstance stepupAttr = player.getAttribute(Attribute.STEP_HEIGHT);
        if (stepupAttr != null) stepupAttr.setBaseValue(0.4);

        AttributeInstance fallAttr = player.getAttribute(Attribute.SAFE_FALL_DISTANCE);
        if (fallAttr != null) fallAttr.setBaseValue(3);

        AttributeInstance attAttr = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attAttr != null) attAttr.setBaseValue(8);

        AttributeInstance attdAttr = player.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attdAttr != null) attdAttr.setBaseValue(1);
    }

    private void resetPlayer(Player player) {
        AttributeInstance healthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null) healthAttr.setBaseValue(20.0);

        AttributeInstance speedAttr = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) speedAttr.setBaseValue(0.1);

        AttributeInstance scaleAttr = player.getAttribute(Attribute.SCALE);
        if (scaleAttr != null) scaleAttr.setBaseValue(1.0);

        player.setHealth(Math.min(player.getHealth(), (healthAttr != null ? healthAttr.getValue() : 20.0)));

        AttributeInstance attAttr = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attAttr != null) attAttr.setBaseValue(4);

        AttributeInstance attdAttr = player.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attdAttr != null) attdAttr.setBaseValue(1);

        AttributeInstance jumpAttr = player.getAttribute(Attribute.JUMP_STRENGTH);
        if (jumpAttr != null) jumpAttr.setBaseValue(0.42);

        AttributeInstance stepupAttr = player.getAttribute(Attribute.STEP_HEIGHT);
        if (stepupAttr != null) stepupAttr.setBaseValue(0.6);

        AttributeInstance fallAttr = player.getAttribute(Attribute.SAFE_FALL_DISTANCE);
        if (fallAttr != null) fallAttr.setBaseValue(3);

        AttributeInstance knockbackAttr = player.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (knockbackAttr != null) knockbackAttr.setBaseValue(0);

        AttributeInstance breachAttr = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
        if (breachAttr != null) breachAttr.setBaseValue(5);

        AttributeInstance ereachAttr = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (ereachAttr != null) ereachAttr.setBaseValue(5);
    }

    // ========== GIANT SHOCKWAVE ABILITY ==========
    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;
        if (event.getClickedBlock() != null) return;
        if (player.getInventory().getItemInMainHand().getType() != Material.AIR) return;

        String role = playerRoles.get(player.getUniqueId());
        if (role == null || !role.equals("giant")) return;

        // Cooldown check (5 seconds)
        if (cooldowns.containsKey(player.getUniqueId()) &&
                System.currentTimeMillis() - cooldowns.get(player.getUniqueId()) < 5000) {
            long remaining = (5000 - (System.currentTimeMillis() - cooldowns.get(player.getUniqueId()))) / 1000;
            player.sendMessage("§cShockwave on cooldown (" + remaining + "s)");
            return;
        }
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());

        // Start shockwave
        createShockwave(player);
    }

    private void createShockwave(Player player) {
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1.5f, 0.5f);
        player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation(), 3, 1, 0.2, 1, 0.1);

        new BukkitRunnable() {
            double radius = 1.5;

            @Override
            public void run() {
                if (radius > 10) {
                    cancel();
                    return;
                }

                player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 25, radius, 0.1, radius, 0.05);

                for (Entity e : player.getNearbyEntities(radius, 2, radius)) {
                    if (e instanceof LivingEntity target && !e.equals(player)) {
                        target.damage(5, player);
                        Vector knockback = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(1.5);
                        knockback.setY(0.5);
                        target.setVelocity(knockback);
                    }
                }

                radius += 0.5;
            }
        }.runTaskTimer(this, 0, 3);
    }
}
