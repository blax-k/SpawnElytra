package com.blaxk.spawnelytra.listener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.bukkit.event.player.PlayerMoveEvent;
import com.blaxk.spawnelytra.Main;
import com.blaxk.spawnelytra.data.PlayerDataManager;
import com.blaxk.spawnelytra.util.SchedulerUtil;
import com.blaxk.spawnelytra.util.MessageUtil;

public class SpawnElytra implements Listener {
    private enum HungerMode {
        ACTIVATION,
        DISTANCE,
        TIME;

        static HungerMode fromString(final String value) {
            if (value == null) {
                return HungerMode.ACTIVATION;
            }
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "distance" -> HungerMode.DISTANCE;
                case "time" -> HungerMode.TIME;
                default -> HungerMode.ACTIVATION;
            };
        }
    }

    private final Main plugin;
    private final int multiplyValue;
    private final int spawnRadius;
    private final boolean boostEnabled;
    private final int maxBoosts;
    private final long boostCooldownMs;
    private final World world;
    private final List<Player> flying = new ArrayList<>();
    private final Location spawnLocation;
    private final Sound boostSound;
    private final boolean disableInCreative;
    private final boolean disableInAdventure;
    private final PlayerDataManager playerDataManager;
    private final String boostDirection;
    private final boolean disableFireworksInSpawnElytra;
    private final double fKeyLaunchStrength;

    private final String activationMode;
    private final double minX, minY, minZ;
    private final double maxX, maxY, maxZ;
    private final boolean useRectangularArea;

    private final boolean hungerEnabled;
    private final HungerMode hungerMode;
    private final int hungerActivationCost;
    private final double hungerDistanceBlocksPerPoint;
    private final int hungerDistanceCost;
    private final long hungerTimeIntervalMillis;
    private final int hungerTimeCost;
    private final int hungerMinimumFoodLevel;

    private final Map<UUID, Double> hungerDistanceProgress = new HashMap<>();
    private final Map<UUID, Long> hungerLastConsumption = new HashMap<>();
    private final Map<UUID, Location> hungerLastLocation = new HashMap<>();

    private final Map<UUID, Long> sneakJumpCooldown = new HashMap<>();
    private final Map<UUID, Long> boostCooldown = new HashMap<>();
    private final Map<UUID, Integer> boostUseCount = new HashMap<>();
    private final Map<UUID, Boolean> sneakPressed = new HashMap<>();
    private final Set<UUID> spawnElytraAllowFlight = new HashSet<>();
    private final Map<UUID, SchedulerUtil.TaskHandle> visualizationTasks = new HashMap<>();

    private final int visualizationVerticalRange;
    private final int visualizationPillarRange;
    private final int visualizationUpdateFrequency;
    private final float visualizationParticleSize;
    private final boolean visualizationEnhancedParticles;

    public SpawnElytra(final Main plugin, final String worldName, final ConfigurationSection worldConfig) {
        this.plugin = plugin;
        
        multiplyValue = worldConfig.getInt("boost.strength", 2);
        spawnRadius = worldConfig.getInt("radius", 100);
        boostEnabled = worldConfig.getBoolean("boost.enabled", true);
        maxBoosts = Math.max(1, worldConfig.getInt("boost.max_boosts", 1));
        boostCooldownMs = Math.max(0L, (long) (worldConfig.getDouble("boost.boost_cooldown", 0) * 1000));
        disableInCreative = plugin.getConfig().getBoolean("game_modes.disable_in_creative", true);
        disableInAdventure = plugin.getConfig().getBoolean("game_modes.disable_in_adventure", false);
        playerDataManager = plugin.getPlayerDataManager();
        boostDirection = worldConfig.getString("boost.direction", "forward").toLowerCase();
        disableFireworksInSpawnElytra = plugin.getConfig().getBoolean("fireworks.disable_in_spawn_elytra", false);
        fKeyLaunchStrength = worldConfig.getDouble("f_key.launch_strength", 1.5);

        final HungerConfig hungerConfig = loadHungerConfig(plugin);
        hungerEnabled = hungerConfig.enabled;
        hungerMode = hungerConfig.mode;
        hungerMinimumFoodLevel = hungerConfig.minimumFoodLevel;
        hungerActivationCost = hungerConfig.activationCost;
        hungerDistanceBlocksPerPoint = hungerConfig.distanceBlocksPerPoint;
        hungerDistanceCost = hungerConfig.distanceCost;
        hungerTimeIntervalMillis = hungerConfig.timeIntervalMillis;
        hungerTimeCost = hungerConfig.timeCost;

        final VisualizationConfig vizConfig = loadVisualizationConfig(plugin);
        visualizationVerticalRange = vizConfig.verticalRange;
        visualizationPillarRange = vizConfig.pillarRange;
        visualizationUpdateFrequency = vizConfig.updateFrequency;
        visualizationParticleSize = vizConfig.particleSize;
        visualizationEnhancedParticles = vizConfig.enhancedParticles;

        activationMode = worldConfig.getString("activation_mode", "double_jump").toLowerCase();
        world = Bukkit.getWorld(worldName);
        final String mode = worldConfig.getString("spawn_area.mode", "auto");

        boostSound = loadBoostSound(plugin, worldConfig);

        if (this.world == null) {
            plugin.getLogger().severe("Invalid world: " + worldName + ". Available worlds: " +
                Bukkit.getWorlds().stream().map(World::getName).reduce((a, b) -> a + ", " + b).orElse("none"));
            spawnLocation = null;
            minX = minY = minZ = 0;
            maxX = maxY = maxZ = 0;
            useRectangularArea = false;
        } else {
            final SpawnAreaConfig areaConfig = loadSpawnAreaConfig(worldConfig, mode);
            spawnLocation = areaConfig.spawnLocation;
            minX = areaConfig.minX;
            minY = areaConfig.minY;
            minZ = areaConfig.minZ;
            maxX = areaConfig.maxX;
            maxY = areaConfig.maxY;
            maxZ = areaConfig.maxZ;
            useRectangularArea = areaConfig.useRectangular;
        }
    }

    private static class HungerConfig {
        boolean enabled;
        HungerMode mode;
        int minimumFoodLevel;
        int activationCost;
        double distanceBlocksPerPoint;
        int distanceCost;
        long timeIntervalMillis;
        int timeCost;
    }

    private static class VisualizationConfig {
        int verticalRange;
        int pillarRange;
        int updateFrequency;
        float particleSize;
        boolean enhancedParticles;
    }

    private static class SpawnAreaConfig {
        Location spawnLocation;
        double minX, minY, minZ;
        double maxX, maxY, maxZ;
        boolean useRectangular;
    }

    private static HungerConfig loadHungerConfig(final Main plugin) {
        final HungerConfig config = new HungerConfig();
        final ConfigurationSection hungerSection = plugin.getConfig().getConfigurationSection("hunger_consumption");
        
        if (hungerSection != null && hungerSection.getBoolean("enabled", false)) {
            config.enabled = true;
            config.mode = HungerMode.fromString(hungerSection.getString("mode"));
            config.minimumFoodLevel = Math.max(0, hungerSection.getInt("minimum_food_level", 0));

            final ConfigurationSection activationSection = hungerSection.getConfigurationSection("activation");
            config.activationCost = Math.max(0, activationSection != null ? activationSection.getInt("hunger_cost", 1) : 1);

            final ConfigurationSection distanceSection = hungerSection.getConfigurationSection("distance");
            config.distanceBlocksPerPoint = Math.max(1.0D, distanceSection != null ? distanceSection.getDouble("blocks_per_point", 50.0D) : 50.0D);
            config.distanceCost = Math.max(0, distanceSection != null ? distanceSection.getInt("hunger_cost", 1) : 1);

            final ConfigurationSection timeSection = hungerSection.getConfigurationSection("time");
            final double secondsPerPoint = timeSection != null ? timeSection.getDouble("seconds_per_point", 30.0D) : 30.0D;
            config.timeIntervalMillis = Math.max(1L, (long) Math.ceil(secondsPerPoint * 1000.0D));
            config.timeCost = Math.max(0, timeSection != null ? timeSection.getInt("hunger_cost", 1) : 1);
        } else {
            config.enabled = false;
            config.mode = HungerMode.ACTIVATION;
            config.activationCost = 0;
            config.distanceBlocksPerPoint = 1.0D;
            config.distanceCost = 0;
            config.timeIntervalMillis = 1000L;
            config.timeCost = 0;
            config.minimumFoodLevel = 0;
        }
        
        return config;
    }

    private static VisualizationConfig loadVisualizationConfig(final Main plugin) {
        final VisualizationConfig config = new VisualizationConfig();
        final ConfigurationSection visualizationSection = plugin.getConfig().getConfigurationSection("visualization");
        
        if (visualizationSection != null) {
            config.verticalRange = visualizationSection.getInt("vertical_range", 20);
            config.pillarRange = visualizationSection.getInt("pillar_vertical_range", 25);
            config.updateFrequency = Math.max(1, visualizationSection.getInt("update_frequency", 10));
            config.particleSize = (float) Math.max(0.1, visualizationSection.getDouble("particle_size", 2.0));
            config.enhancedParticles = visualizationSection.getBoolean("enhanced_particles", true);
        } else {
            config.verticalRange = 20;
            config.pillarRange = 25;
            config.updateFrequency = 10;
            config.particleSize = 2.0f;
            config.enhancedParticles = true;
        }
        
        return config;
    }

    private static Sound loadBoostSound(final Main plugin, final ConfigurationSection worldConfig) {
        final String soundName = worldConfig.getString("boost.sound", "ENTITY_BAT_TAKEOFF");
        try {
            return Sound.valueOf(soundName.toUpperCase());
        } catch (final IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid sound: " + soundName + ". Using default sound.");
            return Sound.ENTITY_BAT_TAKEOFF;
        }
    }

    private SpawnAreaConfig loadSpawnAreaConfig(final ConfigurationSection worldConfig, final String mode) {
        final SpawnAreaConfig config = new SpawnAreaConfig();
        
        if ("advanced".equalsIgnoreCase(mode)) {
            final double spawnX = worldConfig.getDouble("spawn_area.x", this.world.getSpawnLocation().getX());
            final double spawnY = worldConfig.getDouble("spawn_area.y", this.world.getSpawnLocation().getY());
            final double spawnZ = worldConfig.getDouble("spawn_area.z", this.world.getSpawnLocation().getZ());

            config.spawnLocation = new Location(this.world, spawnX, spawnY, spawnZ);

            final double x2 = worldConfig.getDouble("spawn_area.x2", 0);
            final double y2 = worldConfig.getDouble("spawn_area.y2", 0);
            final double z2 = worldConfig.getDouble("spawn_area.z2", 0);

            final boolean allZero = (x2 == 0 && y2 == 0 && z2 == 0);
            final String areaType = worldConfig.getString("spawn_area.area_type", "circular");

            if (!allZero && "rectangular".equalsIgnoreCase(areaType)) {
                config.minX = Math.min(spawnX, x2);
                config.maxX = Math.max(spawnX, x2);
                config.minY = Math.min(spawnY, y2);
                config.maxY = Math.max(spawnY, y2);
                config.minZ = Math.min(spawnZ, z2);
                config.maxZ = Math.max(spawnZ, z2);
                config.useRectangular = true;
            } else {
                config.minX = config.minY = config.minZ = 0;
                config.maxX = config.maxY = config.maxZ = 0;
                config.useRectangular = false;
            }
        } else {
            config.spawnLocation = this.world.getSpawnLocation();
            config.minX = config.minY = config.minZ = 0;
            config.maxX = config.maxY = config.maxZ = 0;
            config.useRectangular = false;
        }
        
        return config;
    }

    public boolean isValid() {
        return this.world != null && this.spawnLocation != null;
    }

    public boolean isFlying(final Player player) {
        return flying.contains(player);
    }

    public int getBoostsRemaining(final Player player) {
        if (!this.boostEnabled) {
            return 0;
        }
        return Math.max(0, this.maxBoosts - this.boostUseCount.getOrDefault(player.getUniqueId(), 0));
    }

    @EventHandler
    public void onPlayerInteract(final PlayerInteractEvent event) {
        if (!this.isValid()) {
            return;
        }
        
        if (!this.disableFireworksInSpawnElytra) {
            return;
        }

        final Player player = event.getPlayer();
        final ItemStack item = event.getItem();

        if (flying.contains(player) &&
                item != null &&
                Material.FIREWORK_ROCKET == item.getType()) {

            final ItemStack chestplate = player.getInventory().getChestplate();
            if (chestplate == null || Material.ELYTRA != chestplate.getType()) {
                event.setCancelled(true);
            }
        }
    }

    private void initializeHungerTracking(final Player player) {
        if (!this.hungerEnabled) {
            return;
        }
        final UUID playerId = player.getUniqueId();
        if (HungerMode.DISTANCE == hungerMode) {
            this.hungerLastLocation.put(playerId, player.getLocation().clone());
            this.hungerDistanceProgress.put(playerId, 0.0);
        } else if (HungerMode.TIME == hungerMode) {
            this.hungerLastConsumption.put(playerId, System.currentTimeMillis());
        } else {
            this.hungerLastConsumption.remove(playerId);
            this.hungerLastLocation.remove(playerId);
            this.hungerDistanceProgress.remove(playerId);
        }
    }

    private void handleHungerWhileFlying(final Player player) {
        final UUID playerId = player.getUniqueId();
        if (!this.shouldConsumeHunger(player)) {
            if (HungerMode.DISTANCE == hungerMode) {
                this.hungerLastLocation.put(playerId, player.getLocation().clone());
                this.hungerDistanceProgress.put(playerId, 0.0);
            } else if (HungerMode.TIME == hungerMode) {
                this.hungerLastConsumption.putIfAbsent(playerId, System.currentTimeMillis());
            }
            return;
        }

        if (HungerMode.DISTANCE == hungerMode) {
            final Location current = player.getLocation();
            final Location last = this.hungerLastLocation.get(playerId);
            if (last == null) {
                this.hungerLastLocation.put(playerId, current.clone());
                this.hungerDistanceProgress.put(playerId, 0.0);
                return;
            }
            final double distance = last.distance(current);
            if (distance <= 0) {
                this.hungerLastLocation.put(playerId, current.clone());
                return;
            }
            double accumulated = this.hungerDistanceProgress.getOrDefault(playerId, 0.0) + distance;
            final double threshold = hungerDistanceBlocksPerPoint <= 0 ? 1.0D : this.hungerDistanceBlocksPerPoint;
            if (accumulated >= threshold && hungerDistanceCost > 0) {
                final int steps = (int) (accumulated / threshold);
                this.consumeHunger(player, this.hungerDistanceCost * steps);
                accumulated -= threshold * steps;
            }
            this.hungerDistanceProgress.put(playerId, accumulated);
            this.hungerLastLocation.put(playerId, current.clone());
        } else if (HungerMode.TIME == hungerMode) {
            final long now = System.currentTimeMillis();
            final long last = this.hungerLastConsumption.getOrDefault(playerId, now);
            if (now - last >= this.hungerTimeIntervalMillis) {
                if (hungerTimeCost > 0) {
                    this.consumeHunger(player, this.hungerTimeCost);
                }
                this.hungerLastConsumption.put(playerId, now);
            }
        }
    }

    private void resetHungerTracking(final Player player) {
        if (!this.hungerEnabled) {
            return;
        }
        final UUID playerId = player.getUniqueId();
        this.hungerDistanceProgress.remove(playerId);
        this.hungerLastConsumption.remove(playerId);
        this.hungerLastLocation.remove(playerId);
    }

    private boolean shouldConsumeHunger(final Player player) {
        if (!this.hungerEnabled) {
            return false;
        }
        final GameMode mode = player.getGameMode();
        if (GameMode.CREATIVE == mode || GameMode.SPECTATOR == mode) {
            return false;
        }
        return player.getFoodLevel() > this.hungerMinimumFoodLevel;
    }

    private void consumeHunger(final Player player, final int amount) {
        if (!this.hungerEnabled || amount <= 0) {
            return;
        }
        final int current = player.getFoodLevel();
        if (current <= this.hungerMinimumFoodLevel) {
            return;
        }
        final int newLevel = Math.max(this.hungerMinimumFoodLevel, current - amount);
        if (newLevel < current) {
            player.setFoodLevel(newLevel);
            if (player.getSaturation() > newLevel) {
                player.setSaturation(newLevel);
            }
        }
    }

    public void visualizeArea(final Player player, final int seconds) {
        if (this.world == null || this.spawnLocation == null) {
            MessageUtil.send(player, "visualize_no_area");
            return;
        }

        final SchedulerUtil.TaskHandle existingTask = this.visualizationTasks.remove(player.getUniqueId());
        if (existingTask != null) {
            existingTask.cancel();
        }

        MessageUtil.send(player, "visualize_start", Placeholder.unparsed("seconds", String.valueOf(seconds)));

        final SchedulerUtil.TaskHandle task = SchedulerUtil.runAtEntityTimer(this.plugin, player, 0L, 1L, new Runnable() {
            private int ticksElapsed;
            private final int maxTicks = seconds * 20;

            @Override
            public void run() {
                if (this.ticksElapsed >= this.maxTicks || !player.isOnline()) {
                    final SchedulerUtil.TaskHandle handle = SpawnElytra.this.visualizationTasks.remove(player.getUniqueId());
                    if (handle != null) {
                        handle.cancel();
                    }
                    if (player.isOnline()) {
                        MessageUtil.send(player, "visualize_end");
                    }
                    return;
                }

                if (ticksElapsed % visualizationUpdateFrequency == 0) {
                    SpawnElytra.this.showAreaParticles(player);
                }

                this.ticksElapsed++;
            }
        });

        this.visualizationTasks.put(player.getUniqueId(), task);
    }

    private void showAreaParticles(final Player player) {
        if (this.useRectangularArea) {
            this.showRectangularAreaParticles(player);
        } else {
            this.showCircularAreaParticles(player);
        }
    }

    private void showCircularAreaParticles(final Player player) {
        final Location center = this.spawnLocation.clone();
        final World world = center.getWorld();

        final double playerY = player.getLocation().getY();

        final Particle.DustOptions gold = new Particle.DustOptions(Color.fromRGB(255, 215, 0), this.visualizationParticleSize);
        final Particle.DustOptions brightGold = this.visualizationEnhancedParticles ?
            new Particle.DustOptions(Color.fromRGB(255, 255, 100), this.visualizationParticleSize * 0.9f) :
            gold;

        for (int yOffset = -this.visualizationVerticalRange; yOffset <= this.visualizationVerticalRange; yOffset += 2) {
            final double y = playerY + yOffset;

            final double angleStep = Math.abs(yOffset) <= 5 ? 2.0 : (Math.abs(yOffset) <= 10 ? 3.0 : 4.0);

            for (double angle = 0; angle < 360; angle += angleStep) {
                final double rad = Math.toRadians(angle);
                final double x = center.getX() + this.spawnRadius * Math.cos(rad);
                final double z = center.getZ() + this.spawnRadius * Math.sin(rad);
                final Location particleLocation = new Location(world, x, y, z);

                final Particle.DustOptions dustOptions = Math.abs(yOffset) <= 8 ? brightGold : gold;
                player.spawnParticle(Particle.DUST, particleLocation, 1, 0, 0, 0, 0, dustOptions);
            }
        }

        final double[][] cardinals = {
                {center.getX() + this.spawnRadius, center.getZ()},
                {center.getX() - this.spawnRadius, center.getZ()},
                {center.getX(), center.getZ() + this.spawnRadius},
                {center.getX(), center.getZ() - this.spawnRadius}
        };
        for (final double[] p : cardinals) {

            for (double y = playerY - this.visualizationPillarRange; y <= playerY + this.visualizationPillarRange; y += 0.5) {
                player.spawnParticle(Particle.END_ROD, new Location(world, p[0], y, p[1]), 1, 0, 0, 0, 0);
            }

            if (this.visualizationEnhancedParticles) {
                final Location cardinalMarker = new Location(world, p[0], playerY, p[1]);
                player.spawnParticle(Particle.FIREWORK, cardinalMarker, 2, 0.1, 0.1, 0.1, 0.02);
            }
        }

        center.setY(playerY);
        player.spawnParticle(Particle.END_ROD, center, 5, 0.2, 0.2, 0.2, 0);
        if (this.visualizationEnhancedParticles) {
            player.spawnParticle(Particle.FIREWORK, center, 3, 0.1, 0.1, 0.1, 0.05);
        }

        final int centerPillarRange = Math.max(10, this.visualizationVerticalRange * 3 / 4);
        for (double y = playerY - centerPillarRange; y <= playerY + centerPillarRange; y += 1.0) {
            final Location centerPillar = new Location(world, center.getX(), y, center.getZ());
            if (this.visualizationEnhancedParticles) {
                player.spawnParticle(Particle.SOUL_FIRE_FLAME, centerPillar, 1, 0, 0, 0, 0);
            } else {
                player.spawnParticle(Particle.FLAME, centerPillar, 1, 0, 0, 0, 0);
            }
        }
    }

    private void showRectangularAreaParticles(final Player player) {
        final double playerY = player.getLocation().getY();

        final Particle.DustOptions gold = new Particle.DustOptions(Color.fromRGB(255, 215, 0), this.visualizationParticleSize);
        final Particle.DustOptions brightGold = this.visualizationEnhancedParticles ?
            new Particle.DustOptions(Color.fromRGB(255, 255, 100), this.visualizationParticleSize * 0.9f) :
            gold;

        for (int yOffset = -this.visualizationVerticalRange; yOffset <= this.visualizationVerticalRange; yOffset += 3) {
            final double y = playerY + yOffset;

            final Particle.DustOptions dustOptions = Math.abs(yOffset) <= 6 ? brightGold : gold;

            for (double x = this.minX; x <= this.maxX; x += 0.75) {
                player.spawnParticle(Particle.DUST, new Location(this.world, x, y, this.minZ), 1, 0, 0, 0, 0, dustOptions);
                player.spawnParticle(Particle.DUST, new Location(this.world, x, y, this.maxZ), 1, 0, 0, 0, 0, dustOptions);
            }

            for (double z = this.minZ; z <= this.maxZ; z += 0.75) {
                player.spawnParticle(Particle.DUST, new Location(this.world, this.minX, y, z), 1, 0, 0, 0, 0, dustOptions);
                player.spawnParticle(Particle.DUST, new Location(this.world, this.maxX, y, z), 1, 0, 0, 0, 0, dustOptions);
            }
        }

        final double[][] corners = {
                {this.minX, this.minZ}, {this.minX, this.maxZ}, {this.maxX, this.minZ}, {this.maxX, this.maxZ}
        };
        for (final double[] c : corners) {

            for (double y = playerY - this.visualizationPillarRange; y <= playerY + this.visualizationPillarRange; y += 0.4) {
                player.spawnParticle(Particle.END_ROD, new Location(this.world, c[0], y, c[1]), 1, 0, 0, 0, 0);
            }

            if (this.visualizationEnhancedParticles) {
                final Location cornerMarker = new Location(this.world, c[0], playerY, c[1]);
                player.spawnParticle(Particle.FIREWORK, cornerMarker, 2, 0.1, 0.1, 0.1, 0.02);
            }
        }

        final Location center = new Location(this.world, (this.minX + this.maxX) / 2, playerY, (this.minZ + this.maxZ) / 2);
        player.spawnParticle(Particle.END_ROD, center, 5, 0.2, 0.2, 0.2, 0);
        if (this.visualizationEnhancedParticles) {
            player.spawnParticle(Particle.FIREWORK, center, 3, 0.1, 0.1, 0.1, 0.05);
        }

        final int centerPillarRange = Math.max(10, this.visualizationVerticalRange * 3 / 4);
        for (double y = playerY - centerPillarRange; y <= playerY + centerPillarRange; y += 1.0) {
            final Location centerPillar = new Location(this.world, center.getX(), y, center.getZ());
            if (this.visualizationEnhancedParticles) {
                player.spawnParticle(Particle.SOUL_FIRE_FLAME, centerPillar, 1, 0, 0, 0, 0);
            } else {
                player.spawnParticle(Particle.FLAME, centerPillar, 1, 0, 0, 0, 0);
            }
        }

        if (this.visualizationEnhancedParticles) {
            final double[][] midpoints = {
                    {(this.minX + this.maxX) / 2, this.minZ},
                    {(this.minX + this.maxX) / 2, this.maxZ},
                    {this.minX, (this.minZ + this.maxZ) / 2},
                    {this.maxX, (this.minZ + this.maxZ) / 2}
            };
            for (final double[] m : midpoints) {

                final int midpointPillarRange = Math.max(8, this.visualizationVerticalRange / 2);
                for (double y = playerY - midpointPillarRange; y <= playerY + midpointPillarRange; y += 1.5) {
                    player.spawnParticle(Particle.FLAME, new Location(this.world, m[0], y, m[1]), 1, 0, 0, 0, 0);
                }
            }
        }
    }

    private boolean hasAirBelow(final Player player) {
        final Location loc = player.getLocation();
        for (int i = 1; i <= 3; i++) {
            final Block block = loc.clone().subtract(0, i, 0).getBlock();
            if (Material.AIR != block.getType() && Material.CAVE_AIR != block.getType()) {
                return false;
            }
        }
        return true;
    }

    private boolean isExternalFlyActive(final Player player) {
        final GameMode mode = player.getGameMode();
        if (GameMode.CREATIVE == mode || GameMode.SPECTATOR == mode) {
            return true;
        }
        if (player.isFlying()) {
            return true;
        }
        return player.getAllowFlight() && !this.spawnElytraAllowFlight.contains(player.getUniqueId());
    }

    private boolean shouldPrimeDoubleJump(final Player player, final boolean inArea) {
        return inArea
                && player.hasPermission("spawnelytra.use")
                && this.isElytraAllowedInMode(player)
                && !this.isExternalFlyActive(player)
                && !player.isGliding()
                && !flying.contains(player);
    }

    private void grantSpawnElytraAllowFlight(final Player player) {
        final UUID uuid = player.getUniqueId();
        if (this.spawnElytraAllowFlight.add(uuid)) {
            player.setAllowFlight(true);
        }
    }

    private void updateTemporaryAllowFlight(final Player player, final boolean inArea) {
        if (this.isExternalFlyActive(player)) {
            return;
        }
        if (this.shouldPrimeDoubleJump(player, inArea)) {
            this.grantSpawnElytraAllowFlight(player);
            return;
        }
        if (!inArea && !flying.contains(player)) {
            this.revokeSpawnElytraAllowFlight(player);
        }
    }

    private void revokeSpawnElytraAllowFlight(final Player player) {
        final UUID uuid = player.getUniqueId();
        if (this.spawnElytraAllowFlight.remove(uuid)) {
            player.setAllowFlight(false);
        }
    }

    private boolean isFlyToggleCommand(final String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        final String trimmed = message.charAt(0) == '/' ? message.substring(1) : message;
        final int spaceIndex = trimmed.indexOf(' ');
        final String command = (spaceIndex >= 0 ? trimmed.substring(0, spaceIndex) : trimmed).toLowerCase(Locale.ROOT);
        return "fly".equals(command) || "essentials:fly".equals(command) || "essentialsx:fly".equals(command);
    }

    private void activateElytraFlight(final Player player) {
        if (this.isExternalFlyActive(player)) {
            return;
        }

        if (this.hungerEnabled && HungerMode.ACTIVATION == hungerMode) {
            if (!this.shouldConsumeHunger(player)) {
                MessageUtil.sendActionBar(player, "not_enough_hunger");
                return;
            }
            if (hungerActivationCost > 0) {
                this.consumeHunger(player, this.hungerActivationCost);
            }
        }

        player.setGliding(true);
        this.revokeSpawnElytraAllowFlight(player);

        if (playerDataManager != null) {
            this.playerDataManager.incrementFlyCount(player);
        }

        if (this.plugin.getConfig().getBoolean("messages.show_press_to_boost", true) && this.boostEnabled) {
            if (this.maxBoosts > 1) {
                MessageUtil.sendActionBar(player, "press_to_boost_remaining",
                        Placeholder.unparsed("remaining", String.valueOf(this.maxBoosts)));
            } else {
                MessageUtil.sendActionBar(player, "press_to_boost");
            }
        }

        flying.add(player);

        this.initializeHungerTracking(player);
    }

    private void disableElytraFlight(final Player player) {
        this.revokeSpawnElytraAllowFlight(player);
        player.setGliding(false);
        flying.remove(player);
        final UUID uuid = player.getUniqueId();
        this.boostCooldown.remove(uuid);
        this.boostUseCount.remove(uuid);
        this.resetHungerTracking(player);
    }

    @EventHandler
    public void onDoubleJump(final PlayerToggleFlightEvent event) {
        if (!this.isValid()) {
            return;
        }

        if (!"double_jump".equalsIgnoreCase(activationMode)) {
            return;
        }

        if (!event.isFlying()) {
            return;
        }

        final Player player = event.getPlayer();

        if (!player.hasPermission("spawnelytra.use")) {
            return;
        }

        if (this.disableInCreative && GameMode.CREATIVE == player.getGameMode()) {
            return;
        }

        if (this.isExternalFlyActive(player)) {
            return;
        }

        if (this.isElytraAllowedInMode(player) && isInSpawnArea(player)) {
            event.setCancelled(true);

            if (player.isGliding()) {
                return;
            }

            if (flying.contains(player)) {
                return;
            }

            this.activateElytraFlight(player);
        }
    }

    @EventHandler
    public void onPlayerSneak(final PlayerToggleSneakEvent event) {
        if (!this.isValid()) {
            return;
        }
        
        final Player player = event.getPlayer();
        final UUID playerId = player.getUniqueId();

        if (!player.hasPermission("spawnelytra.use")) {
            return;
        }

        if (this.disableInCreative && GameMode.CREATIVE == player.getGameMode()) {
            return;
        }

        if (!"sneak_jump".equalsIgnoreCase(activationMode)) {
            return;
        }

        if (!this.isElytraAllowedInMode(player) || !isInSpawnArea(player)) {
            return;
        }

        if (this.isExternalFlyActive(player)) {
            return;
        }

        if (event.isSneaking()) {
            this.sneakPressed.put(playerId, true);

            SchedulerUtil.runAtEntityLater(this.plugin, player, 10L, () -> {
                final UUID playerUUID = player.getUniqueId();
                if (this.sneakPressed.getOrDefault(playerUUID, false) &&
                        !player.isOnGround() &&
                        !flying.contains(player)) {

                    final long currentTime = System.currentTimeMillis();
                    final Long lastActivation = this.sneakJumpCooldown.get(playerUUID);

                    if (lastActivation == null || (currentTime - lastActivation) >= 1000) {
                        this.activateElytraFlight(player);
                        this.sneakJumpCooldown.put(playerUUID, currentTime);
                    }
                }
                this.sneakPressed.put(playerUUID, false);
            });
        } else {
            this.sneakPressed.put(playerId, false);
        }
    }

    @EventHandler
    public void onPlayerCommandPreprocess(final PlayerCommandPreprocessEvent event) {
        if (!this.isValid()) {
            return;
        }

        if (!"double_jump".equalsIgnoreCase(this.activationMode)) {
            return;
        }

        final Player player = event.getPlayer();
        if (!this.spawnElytraAllowFlight.contains(player.getUniqueId())) {
            return;
        }

        if (!this.isFlyToggleCommand(event.getMessage())) {
            return;
        }

        this.revokeSpawnElytraAllowFlight(player);
    }

    @EventHandler
    public void onPlayerMove(final PlayerMoveEvent event) {
        if (!this.isValid()) {
            return;
        }
        
        final Player player = event.getPlayer();

        if (this.isElytraAllowedInMode(player)) {
            final boolean inArea = isInSpawnArea(player);

            if ("double_jump".equalsIgnoreCase(activationMode)) {
                this.updateTemporaryAllowFlight(player, inArea);
            }

            if ("auto".equalsIgnoreCase(activationMode)
                    && inArea
                    && !flying.contains(player)
                    && this.hasAirBelow(player)
                    && !player.isOnGround()
                    && !player.isFlying()
                    && !player.isGliding()
                    && !this.isExternalFlyActive(player)
                    && player.hasPermission("spawnelytra.use")) {
                this.activateElytraFlight(player);
            }
        }

        if (flying.contains(player)) {
            if (player.isOnGround() || player.getLocation().getBlock().getRelative(BlockFace.DOWN).isLiquid()) {
                this.disableElytraFlight(player);
            } else {
                player.setFallDistance(0);
                if (player.isGliding() && this.hungerEnabled) {
                    this.handleHungerWhileFlying(player);
                }
            }
        } else {

            if (!player.isGliding()) {
                this.resetHungerTracking(player);
            }
        }
    }

    @EventHandler
    public void onEntityDamage(final EntityDamageEvent event) {
        if (EntityType.PLAYER == event.getEntityType()) {
            final Player player = (Player) event.getEntity();
            if (flying.contains(player) && (DamageCause.FALL == event.getCause() || DamageCause.FLY_INTO_WALL == event.getCause())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onSwapItem(final PlayerSwapHandItemsEvent event) {
        if (!this.isValid()) {
            return;
        }
        
        final Player player = event.getPlayer();

        if ("f_key".equalsIgnoreCase(activationMode)) {
            if (!player.hasPermission("spawnelytra.use")) {
                return;
            }

            if (this.disableInCreative && GameMode.CREATIVE == player.getGameMode()) {
                return;
            }

            if (this.isElytraAllowedInMode(player) && isInSpawnArea(player)) {
                if (!flying.contains(player) && !this.isExternalFlyActive(player)) {
                    event.setCancelled(true);

                    final Vector launchVelocity = new Vector(0, fKeyLaunchStrength, 0);
                    player.setVelocity(launchVelocity);

                    SchedulerUtil.runAtEntityLater(this.plugin, player, 5L, () -> this.activateElytraFlight(player));

                    return;
                }
            }
        }

        if (!player.hasPermission("spawnelytra.useboost")) {
            return;
        }

        if (!this.boostEnabled) {
            return;
        }

        if (!flying.contains(player) || !player.isGliding()) {
            return;
        }

        final UUID uuid = player.getUniqueId();
        final int usedBoosts = this.boostUseCount.getOrDefault(uuid, 0);

        if (usedBoosts >= this.maxBoosts) {
            return;
        }

        if (this.boostCooldownMs > 0) {
            final long now = System.currentTimeMillis();
            final Long lastBoost = this.boostCooldown.get(uuid);
            if (lastBoost != null && (now - lastBoost) < this.boostCooldownMs) {
                return;
            }
        }

        event.setCancelled(true);

        final int newCount = usedBoosts + 1;
        this.boostUseCount.put(uuid, newCount);
        if (this.boostCooldownMs > 0) {
            this.boostCooldown.put(uuid, System.currentTimeMillis());
        }

        final Vector velocity;
        if ("upward".equalsIgnoreCase(boostDirection)) {
            velocity = new Vector(0, multiplyValue, 0);
        } else {
            velocity = player.getLocation().getDirection().multiply(multiplyValue);
        }

        player.setVelocity(velocity);

        if (playerDataManager != null) {
            this.playerDataManager.incrementBoostCount(player);
        }

        player.playSound(player.getLocation(), boostSound, 1.0f, 1.0f);

        final int remaining = this.maxBoosts - newCount;
        final boolean showBoostActivated = this.plugin.getConfig().getBoolean("messages.show_boost_activated", true);
        final boolean showPressToBoost = this.plugin.getConfig().getBoolean("messages.show_press_to_boost", true);

        if (showBoostActivated) {
            if (remaining > 0 && this.maxBoosts > 1) {
                MessageUtil.sendActionBar(player, "boost_activated_remaining",
                        Placeholder.unparsed("remaining", String.valueOf(remaining)));
            } else {
                MessageUtil.sendActionBar(player, "boost_activated");
            }
        }

        if (remaining > 0 && showPressToBoost && this.maxBoosts > 1) {
            final long delayTicks = this.boostCooldownMs > 0 ? Math.max(20L, this.boostCooldownMs / 50) : 30L;
            SchedulerUtil.runAtEntityLater(this.plugin, player, delayTicks, () -> {
                if (flying.contains(player) && player.isGliding()) {
                    final int currentRemaining = this.maxBoosts - this.boostUseCount.getOrDefault(uuid, 0);
                    if (currentRemaining > 0) {
                        MessageUtil.sendActionBar(player, "press_to_boost_remaining",
                                Placeholder.unparsed("remaining", String.valueOf(currentRemaining)));
                    }
                }
            });
        }
    }

    @EventHandler
    public void onToggleGlide(final EntityToggleGlideEvent event) {
        if (!this.isValid()) {
            return;
        }
        
        if (EntityType.PLAYER != event.getEntityType()) {
            return;
        }

        final Player player = (Player) event.getEntity();

        if (flying.contains(player)) {
            if (!event.isGliding()) {

                event.setCancelled(true);
            } else {

                this.revokeSpawnElytraAllowFlight(player);
            }
        }
    }

    @EventHandler
    public void onGameModeChange(final PlayerGameModeChangeEvent event) {
        if (!this.isValid()) {
            return;
        }
        
        if (this.disableInCreative && GameMode.CREATIVE == event.getNewGameMode()) {
            final Player player = event.getPlayer();
            if (this.flying.contains(player)) {
                SchedulerUtil.runAtEntityLater(this.plugin, player, 1L, () -> {
                    this.disableElytraFlight(player);

                    SchedulerUtil.runAtEntityLater(this.plugin, player, 5L, () -> {
                        player.setAllowFlight(true);
                    });
                });
            }
        }
    }

    public boolean isInSpawnArea(final Player player) {
        if (!player.getWorld().equals(world) || this.spawnLocation == null) {
            return false;
        }

        if (useRectangularArea) {
            final Location loc = player.getLocation();
            final double x = loc.getX();
            final double y = loc.getY();
            final double z = loc.getZ();

            return x >= minX && x <= maxX &&
                    y >= minY && y <= maxY &&
                    z >= minZ && z <= maxZ;
        } else {
            final double distance = spawnLocation.distance(player.getLocation());
            return distance <= spawnRadius;
        }
    }

    private boolean isElytraAllowedInMode(final Player player) {
        final GameMode mode = player.getGameMode();
        if (GameMode.SURVIVAL == mode) return true;
        return GameMode.ADVENTURE == mode && !this.disableInAdventure;
    }

    public void cleanupPlayer(final Player player) {
        if (flying.contains(player)) {
            this.disableElytraFlight(player);
        } else {
            this.revokeSpawnElytraAllowFlight(player);
        }
        final UUID uuid = player.getUniqueId();
        this.sneakJumpCooldown.remove(uuid);
        this.sneakPressed.remove(uuid);
    }

    public void stopVisualization(final Player player) {
        final SchedulerUtil.TaskHandle task = this.visualizationTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
            MessageUtil.send(player, "visualize_stop");
        }
    }
}
