package com.stormai.plugin;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

public class Main extends JavaPlugin implements Listener {
    private final Map<UUID, Double> healthMap = new HashMap<>();
    private final Map<UUID, Double> maxHealthMap = new HashMap<>();
    private FileConfiguration config;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        config = getConfig();

        // Load health data for online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadPlayerHealth(player);
        }

        getLogger().info("LifeSteal plugin enabled!");
    }

    @Override
    public void onDisable() {
        // Save health data for all online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            savePlayerHealth(player);
        }
        getLogger().info("LifeSteal plugin disabled!");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        loadPlayerHealth(player);
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        if (attacker.equals(victim)) return; // Prevent self-damage

        double damage = event.getFinalDamage();
        double attackerMaxHealth = attacker.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
        double victimMaxHealth = victim.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();

        // Calculate lifesteal
        double lifestealAmount = damage * config.getDouble("lifesteal-percentage") / 100.0;
        double newAttackerHealth = Math.min(attacker.getHealth() + lifestealAmount, attackerMaxHealth);
        double newVictimHealth = Math.max(victim.getHealth() - damage, 0);

        // Apply damage to victim
        victim.setHealth(newVictimHealth);

        // Apply lifesteal to attacker
        attacker.setHealth(newAttackerHealth);

        // Update stored health values
        healthMap.put(attacker.getUniqueId(), newAttackerHealth);
        healthMap.put(victim.getUniqueId(), newVictimHealth);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
        double newMaxHealth = maxHealth - config.getDouble("max-health-decrease-on-death");

        // Update max health
        player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).setBaseValue(newMaxHealth);
        double currentHealth = player.getHealth();
        double newHealth = Math.min(currentHealth, newMaxHealth);

        // Update stored values
        maxHealthMap.put(player.getUniqueId(), newMaxHealth);
        healthMap.put(player.getUniqueId(), newHealth);

        // Update actual player health
        player.setHealth(newHealth);

        // Save to config
        savePlayerHealth(player);
    }

    private void loadPlayerHealth(Player player) {
        UUID uuid = player.getUniqueId();
        double maxHealth = config.getDouble("players." + uuid + ".max-health", config.getDouble("default-max-health"));
        double health = config.getDouble("players." + uuid + ".health", maxHealth);

        // Apply max health
        player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).setBaseValue(maxHealth);
        // Apply current health (clamped to max)
        player.setHealth(Math.min(health, maxHealth));

        // Store in memory
        maxHealthMap.put(uuid, maxHealth);
        healthMap.put(uuid, player.getHealth());
    }

    private void savePlayerHealth(Player player) {
        UUID uuid = player.getUniqueId();
        double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
        double health = player.getHealth();

        config.set("players." + uuid + ".max-health", maxHealth);
        config.set("players." + uuid + ".health", health);
        saveConfig();
    }
}