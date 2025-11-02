package me.milan.rolesPlugin;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class RolesPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("RolesPlugin enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("RolesPlugin disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length != 1) {
            player.sendMessage("§cUsage: /role <giant|gnome>");
            return true;
        }

        String role = args[0].toLowerCase();

        switch (role) {
            case "giant":
                applyGiantRole(player);
                player.sendMessage("§aYou are now a §lGiant§a!");
                break;

            case "gnome":
                applyGnomeRole(player);
                player.sendMessage("§aYou are now a §lGnome§a!");
                break;

            default:
                player.sendMessage("§cUnknown role! Use /role <giant|gnome>");
                break;
        }

        return true;
    }

    private void applyGiantRole(Player player) {
        resetPlayer(player);

        // Health
        AttributeInstance healthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(40.0); // more max HP
            player.setHealth(Math.min(player.getHealth(), healthAttr.getValue()));
        }

        // Movement speed (vanilla default ~0.1, giants slower)
        AttributeInstance speedAttr = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(0.06); // slower than default
        }

        // Scale (use the GENERIC_SCALE attribute if available)
        AttributeInstance scaleAttr = player.getAttribute(Attribute.SCALE);
        if (scaleAttr != null) {
            scaleAttr.setBaseValue(2.0); // bigger
        }

        AttributeInstance knockbackAttr = player.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (knockbackAttr != null) {
            knockbackAttr.setBaseValue(1); // Less knockback
        }

        AttributeInstance breachAttr = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
        if (breachAttr != null) {
            breachAttr.setBaseValue(10); //More interaction range for blocks
        }

        AttributeInstance ereachAttr = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (ereachAttr != null) {
            ereachAttr.setBaseValue(10); //More interaction range for entities
        }

        AttributeInstance jumpAttr = player.getAttribute(Attribute.JUMP_STRENGTH);
        if (jumpAttr != null) {
            jumpAttr.setBaseValue(0.55); //More jump strength
        }

        AttributeInstance stepupAttr = player.getAttribute(Attribute.STEP_HEIGHT);
        if (stepupAttr != null) {
            stepupAttr.setBaseValue(1); //More step height
        }

        AttributeInstance fallAttr = player.getAttribute(Attribute.SAFE_FALL_DISTANCE);
        if (fallAttr != null) {
            fallAttr.setBaseValue(7); //More step height
        }
    }

    private void applyGnomeRole(Player player) {
        resetPlayer(player);

        AttributeInstance healthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(14.0);
            player.setHealth(Math.min(player.getHealth(), healthAttr.getValue()));
        }

        AttributeInstance speedAttr = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(0.16); // faster than default
        }

        AttributeInstance scaleAttr = player.getAttribute(Attribute.SCALE);
        if (scaleAttr != null) {
            scaleAttr.setBaseValue(0.6); // smaller
        }

        AttributeInstance knockbackAttr = player.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (knockbackAttr != null) {
            knockbackAttr.setBaseValue(0); // Less knockback
        }

        AttributeInstance breachAttr = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
        if (breachAttr != null) {
            breachAttr.setBaseValue(3); //Less interaction range for blocks
        }

        AttributeInstance ereachAttr = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (ereachAttr != null) {
            ereachAttr.setBaseValue(3); //Less interaction range for entities
        }

        AttributeInstance jumpAttr = player.getAttribute(Attribute.JUMP_STRENGTH);
        if (jumpAttr != null) {
            jumpAttr.setBaseValue(0.38); //Less jump strength
        }

        AttributeInstance stepupAttr = player.getAttribute(Attribute.STEP_HEIGHT);
        if (stepupAttr != null) {
            stepupAttr.setBaseValue(0.4); //Less step height
        }

        AttributeInstance fallAttr = player.getAttribute(Attribute.SAFE_FALL_DISTANCE);
        if (fallAttr != null) {
            fallAttr.setBaseValue(3); //Normal step height
        }
    }

    private void resetPlayer(Player player) {
        AttributeInstance healthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null) healthAttr.setBaseValue(20.0);

        AttributeInstance speedAttr = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) speedAttr.setBaseValue(0.1);

        AttributeInstance scaleAttr = player.getAttribute(Attribute.SCALE);
        if (scaleAttr != null) scaleAttr.setBaseValue(1.0);

        // Keep current health within new max
        if (player.getHealth() > (healthAttr != null ? healthAttr.getValue() : 20.0)) {
            player.setHealth(healthAttr != null ? healthAttr.getValue() : 20.0);
        }
    }
}
