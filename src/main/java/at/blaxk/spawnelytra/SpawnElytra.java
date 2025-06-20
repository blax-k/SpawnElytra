package at.blaxk.spawnelytra;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.*;
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
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.event.player.PlayerMoveEvent;

public class SpawnElytra extends BukkitRunnable implements Listener {
    private final Main plugin;
    private final int multiplyValue;
    private final int spawnRadius;
    private final boolean boostEnabled;
    private final World world;
    private final List<Player> flying = new ArrayList<>();
    private final List<Player> boosted = new ArrayList<>();
    private final String mode;
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

    private final Map<UUID, Long> sneakJumpCooldown = new HashMap<>();
    private final Map<UUID, Boolean> sneakPressed = new HashMap<>();
    private final Map<UUID, BukkitTask> visualizationTasks = new HashMap<>();

    public SpawnElytra(Main plugin) {
        this.plugin = plugin;
        this.multiplyValue = plugin.getConfig().getInt("strength");
        this.spawnRadius = plugin.getConfig().getInt("radius");
        this.boostEnabled = plugin.getConfig().getBoolean("boost_enabled", true);
        this.disableInCreative = plugin.getConfig().getBoolean("disable_in_creative", true);
        this.disableInAdventure = plugin.getConfig().getBoolean("disable_in_adventure", true);
        this.playerDataManager = plugin.getPlayerDataManager();
        this.boostDirection = plugin.getConfig().getString("boost_direction", "forward").toLowerCase();
        this.disableFireworksInSpawnElytra = plugin.getConfig().getBoolean("disable_fireworks_in_spawn_elytra", false);
        this.fKeyLaunchStrength = plugin.getConfig().getDouble("f_key_launch_strength", 0.8);

        this.activationMode = plugin.getConfig().getString("activation_mode", "double_jump").toLowerCase();

        String configWorldName = plugin.getConfig().getString("world");
        this.world = Bukkit.getWorld(configWorldName);
        this.mode = plugin.getConfig().getString("spawn.mode", "auto");

        String soundName = plugin.getConfig().getString("boost_sound", "ENTITY_BAT_TAKEOFF");
        Sound configuredSound;
        try {
            configuredSound = Sound.valueOf(soundName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid sound: " + soundName + ". Using default sound.");
            configuredSound = Sound.ENTITY_BAT_TAKEOFF;
        }
        this.boostSound = configuredSound;

        if (this.world == null) {
            plugin.getLogger().severe("Invalid world: " + configWorldName);
            this.spawnLocation = null;
            this.minX = this.minY = this.minZ = 0;
            this.maxX = this.maxY = this.maxZ = 0;
            this.useRectangularArea = false;
        } else {
            if ("advanced".equalsIgnoreCase(this.mode)) {
                double spawnX, spawnY, spawnZ;
                if (plugin.getConfig().contains("spawn.x")) {
                    spawnX = plugin.getConfig().getDouble("spawn.x");
                    spawnY = plugin.getConfig().getDouble("spawn.y");
                    spawnZ = plugin.getConfig().getDouble("spawn.z");
                } else {
                    spawnX = plugin.getConfig().getDouble("spawnx", world.getSpawnLocation().getX());
                    spawnY = plugin.getConfig().getDouble("spawny", world.getSpawnLocation().getY());
                    spawnZ = plugin.getConfig().getDouble("spawnz", world.getSpawnLocation().getZ());
                }

                this.spawnLocation = new Location(world, spawnX, spawnY, spawnZ);

                if (plugin.getConfig().contains("spawn.x2") ||
                        plugin.getConfig().contains("spawn.y2") ||
                        plugin.getConfig().contains("spawn.z2")) {

                    double x2 = plugin.getConfig().getDouble("spawn.x2", 0);
                    double y2 = plugin.getConfig().getDouble("spawn.y2", 0);
                    double z2 = plugin.getConfig().getDouble("spawn.z2", 0);

                    boolean allZero = (x2 == 0 && y2 == 0 && z2 == 0);

                    if (!allZero) {
                        this.minX = Math.min(spawnX, x2);
                        this.maxX = Math.max(spawnX, x2);

                        this.minY = Math.min(spawnY, y2);
                        this.maxY = Math.max(spawnY, y2);

                        this.minZ = Math.min(spawnZ, z2);
                        this.maxZ = Math.max(spawnZ, z2);

                        this.useRectangularArea = true;
                    } else {
                        this.minX = this.minY = this.minZ = 0;
                        this.maxX = this.maxY = this.maxZ = 0;
                        this.useRectangularArea = false;
                    }
                } else if (plugin.getConfig().contains("spawn.dx") ||
                        plugin.getConfig().contains("spawn.dy") ||
                        plugin.getConfig().contains("spawn.dz")) {
                    double dx = plugin.getConfig().getDouble("spawn.dx", 0);
                    double dy = plugin.getConfig().getDouble("spawn.dy", 0);
                    double dz = plugin.getConfig().getDouble("spawn.dz", 0);

                    if (dx != 0 || dy != 0 || dz != 0) {
                        this.minX = Math.min(spawnX, spawnX + dx);
                        this.maxX = Math.max(spawnX, spawnX + dx);

                        this.minY = Math.min(spawnY, spawnY + dy);
                        this.maxY = Math.max(spawnY, spawnY + dy);

                        this.minZ = Math.min(spawnZ, spawnZ + dz);
                        this.maxZ = Math.max(spawnZ, spawnZ + dz);

                        this.useRectangularArea = true;

                    } else {
                        this.minX = this.minY = this.minZ = 0;
                        this.maxX = this.maxY = this.maxZ = 0;
                        this.useRectangularArea = false;
                    }
                } else {
                    this.minX = this.minY = this.minZ = 0;
                    this.maxX = this.maxY = this.maxZ = 0;
                    this.useRectangularArea = false;
                }
            } else {
                this.spawnLocation = world.getSpawnLocation();
                this.minX = this.minY = this.minZ = 0;
                this.maxX = this.maxY = this.maxZ = 0;
                this.useRectangularArea = false;
            }
        }

        this.runTaskTimer(this.plugin, 0L, 3L);
    }

    public void run() {
        if (this.world == null) {
            return;
        }

        this.world.getPlayers().forEach((player) -> {
            if (disableInCreative && player.getGameMode() == GameMode.CREATIVE) {
                if (flying.contains(player)) {
                    disableElytraFlight(player);
                }
                return;
            }

            if (isElytraAllowedInMode(player)) {
                player.setAllowFlight(this.isInSpawnArea(player));

                if (this.flying.contains(player) && !player.isGliding()) {
                    this.flying.remove(player);
                    this.boosted.remove(player);
                }

                if ("auto".equalsIgnoreCase(this.activationMode) &&
                        this.isInSpawnArea(player) &&
                        !this.flying.contains(player) &&
                        hasAirBelow(player, 3) &&
                        !player.isOnGround() &&
                        !player.isFlying() &&
                        !player.isGliding() &&
                        player.hasPermission("spawnelytra.use")) {

                    activateElytraFlight(player);
                }

                if (this.flying.contains(player) && player.isOnGround()) {
                    disableElytraFlight(player);
                    Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                        this.flying.remove(player);
                    }, 5L);
                }
            }
        });
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!disableFireworksInSpawnElytra) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (this.flying.contains(player) &&
                item != null &&
                item.getType() == Material.FIREWORK_ROCKET) {

            ItemStack chestplate = player.getInventory().getChestplate();
            if (chestplate == null || chestplate.getType() != Material.ELYTRA) {
                event.setCancelled(true);
            }
        }
    }

    public void visualizeArea(Player player) {
        if (this.world == null || this.spawnLocation == null) {
            player.sendMessage(ChatColor.RED + "No valid spawn area configured!");
            return;
        }

        if (visualizationTasks.containsKey(player.getUniqueId())) {
            visualizationTasks.get(player.getUniqueId()).cancel();
            visualizationTasks.remove(player.getUniqueId());
        }

        player.sendMessage(ChatColor.GREEN + "Visualizing spawn area for 30 seconds...");

        BukkitTask task = new BukkitRunnable() {
            private int ticksElapsed = 0;
            private final int maxTicks = 30 * 20;

            @Override
            public void run() {
                if (ticksElapsed >= maxTicks || !player.isOnline()) {
                    visualizationTasks.remove(player.getUniqueId());
                    if (player.isOnline()) {
                        player.sendMessage(ChatColor.YELLOW + "Area visualization ended.");
                    }
                    this.cancel();
                    return;
                }

                if (ticksElapsed % 10 == 0) {
                    showAreaParticles(player);
                }

                ticksElapsed++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        visualizationTasks.put(player.getUniqueId(), task);
    }

    private void showAreaParticles(Player player) {
        if (useRectangularArea) {
            showRectangularAreaParticles(player);
        } else {
            showCircularAreaParticles(player);
        }
    }

    private void showCircularAreaParticles(Player player) {
        Location center = spawnLocation.clone();
        World world = center.getWorld();

        double playerY = player.getLocation().getY();
        for (int yOffset = -5; yOffset <= 5; yOffset++) {
            double y = playerY + yOffset;
            for (double angle = 0; angle < 360; angle += 5) {
                double x = center.getX() + spawnRadius * Math.cos(Math.toRadians(angle));
                double z = center.getZ() + spawnRadius * Math.sin(Math.toRadians(angle));

                Location particleLocation = new Location(world, x, y, z);
                player.spawnParticle(Particle.HAPPY_VILLAGER, particleLocation, 1, 0, 0, 0, 0);
            }
        }
        center.setY(playerY);
        player.spawnParticle(Particle.END_ROD, center, 1, 0, 0, 0, 0);
    }


    private void showRectangularAreaParticles(Player player) {
        double playerY = player.getLocation().getY();

        for (double x = minX; x <= maxX; x += 2) {
            player.spawnParticle(Particle.HAPPY_VILLAGER, new Location(world, x, playerY, minZ), 1, 0, 0, 0, 0);
            player.spawnParticle(Particle.HAPPY_VILLAGER, new Location(world, x, playerY, maxZ), 1, 0, 0, 0, 0);
        }

        for (double z = minZ; z <= maxZ; z += 2) {
            player.spawnParticle(Particle.HAPPY_VILLAGER, new Location(world, minX, playerY, z), 1, 0, 0, 0, 0);
            player.spawnParticle(Particle.HAPPY_VILLAGER, new Location(world, maxX, playerY, z), 1, 0, 0, 0, 0);
        }

        Location center = new Location(world, (minX + maxX) / 2, playerY, (minZ + maxZ) / 2);
        player.spawnParticle(Particle.END_ROD, center, 1, 0, 0, 0, 0);
    }

    private boolean hasAirBelow(Player player, int blocks) {
        Location loc = player.getLocation();
        for (int i = 1; i <= blocks; i++) {
            Block block = loc.clone().subtract(0, i, 0).getBlock();
            if (block.getType() != Material.AIR && block.getType() != Material.CAVE_AIR) {
                return false;
            }
        }
        return true;
    }

    private void activateElytraFlight(Player player) {
        player.setGliding(true);

        if (playerDataManager != null) {
            playerDataManager.incrementFlyCount(player);
        }

        if (plugin.getConfig().getBoolean("messages.show_press_to_boost", true) && boostEnabled) {
            MessageUtils.sendActionBar(player, "press_to_boost");
        }

        this.flying.add(player);
    }

    private void disableElytraFlight(Player player) {
        player.setAllowFlight(false);
        player.setGliding(false);
        this.boosted.remove(player);
    }

    @EventHandler
    public void onDoubleJump(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();

        if (!player.hasPermission("spawnelytra.use")) {
            return;
        }

        if (disableInCreative && player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        if (isElytraAllowedInMode(player) && this.isInSpawnArea(player)) {
            event.setCancelled(true);

            if (this.flying.contains(player)) {
                return;
            }

            if ("double_jump".equalsIgnoreCase(this.activationMode)) {
                activateElytraFlight(player);
            }
        }
    }

    @EventHandler
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (!player.hasPermission("spawnelytra.use")) {
            return;
        }

        if (disableInCreative && player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        if (!"sneak_jump".equalsIgnoreCase(this.activationMode)) {
            return;
        }

        if (!isElytraAllowedInMode(player) || !this.isInSpawnArea(player)) {
            return;
        }

        if (event.isSneaking()) {
            sneakPressed.put(playerId, true);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                UUID playerUUID = player.getUniqueId();
                if (sneakPressed.getOrDefault(playerUUID, false) &&
                        !player.isOnGround() &&
                        !this.flying.contains(player)) {

                    long currentTime = System.currentTimeMillis();
                    Long lastActivation = sneakJumpCooldown.get(playerUUID);

                    if (lastActivation == null || currentTime - lastActivation > 1000) {
                        activateElytraFlight(player);
                        sneakJumpCooldown.put(playerUUID, currentTime);
                    }
                }
                sneakPressed.put(playerUUID, false);
            }, 10L);
        } else {
            sneakPressed.put(playerId, false);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (this.flying.contains(player)) {
            if (player.isOnGround() || player.getLocation().getBlock().getRelative(BlockFace.DOWN).isLiquid()) {
                disableElytraFlight(player);
                this.flying.remove(player);
            } else {
                player.setFallDistance(0);
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntityType() == EntityType.PLAYER) {
            Player player = (Player) event.getEntity();
            if (this.flying.contains(player) && (event.getCause() == DamageCause.FALL || event.getCause() == DamageCause.FLY_INTO_WALL)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onSwapItem(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();

        if ("f_key".equalsIgnoreCase(this.activationMode)) {
            if (!player.hasPermission("spawnelytra.use")) {
                return;
            }

            if (disableInCreative && player.getGameMode() == GameMode.CREATIVE) {
                return;
            }

            if (isElytraAllowedInMode(player) && this.isInSpawnArea(player)) {
                if (!this.flying.contains(player)) {
                    event.setCancelled(true);

                    Vector launchVelocity = new Vector(0, this.fKeyLaunchStrength, 0);
                    player.setVelocity(launchVelocity);

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        activateElytraFlight(player);
                    }, 5L);

                    return;
                }
            }
        }

        if (!player.hasPermission("spawnelytra.useboost")) {
            return;
        }

        if (!boostEnabled) {
            return;
        }

        if (this.flying.contains(player) &&
                !this.boosted.contains(player) &&
                player.isGliding()) {

            event.setCancelled(true);
            this.boosted.add(player);

            Vector velocity;
            if ("upward".equalsIgnoreCase(this.boostDirection)) {
                velocity = new Vector(0, this.multiplyValue, 0);
            } else {
                velocity = player.getLocation().getDirection().multiply(this.multiplyValue);
            }

            player.setVelocity(velocity);

            if (playerDataManager != null) {
                playerDataManager.incrementBoostCount(player);
            }

            player.playSound(player.getLocation(), this.boostSound, 1.0f, 1.0f);

            if (plugin.getConfig().getBoolean("messages.show_boost_activated", true)) {
                MessageUtils.sendActionBar(player, "boost_activated");
            }
        }
    }

    @EventHandler
    public void onToggleGlide(EntityToggleGlideEvent event) {
        if (event.getEntityType() != EntityType.PLAYER) {
            return;
        }

        Player player = (Player) event.getEntity();

        if (this.flying.contains(player)) {
            if (event.isGliding()) {
                this.flying.remove(player);
                player.setAllowFlight(this.isInSpawnArea(player));
                this.boosted.remove(player);
            } else {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (disableInCreative && event.getNewGameMode() == GameMode.CREATIVE) {
            Player player = event.getPlayer();
            if (flying.contains(player)) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    disableElytraFlight(player);
                    flying.remove(player);

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        player.setAllowFlight(true);
                        MessageUtils.sendActionBar(player, "creative_mode_elytra_disabled");
                    }, 5L);
                }, 1L);
            }
        }
    }

    private boolean isInSpawnArea(Player player) {
        if (!player.getWorld().equals(this.world) || this.spawnLocation == null) {
            return false;
        }

        if (this.useRectangularArea) {
            Location loc = player.getLocation();
            double x = loc.getX();
            double y = loc.getY();
            double z = loc.getZ();

            return x >= this.minX && x <= this.maxX &&
                    y >= this.minY && y <= this.maxY &&
                    z >= this.minZ && z <= this.maxZ;
        } else {
            return this.spawnLocation.distance(player.getLocation()) <= (double)this.spawnRadius;
        }
    }

    private boolean isElytraAllowedInMode(Player player) {
        GameMode mode = player.getGameMode();
        if (mode == GameMode.SURVIVAL) return true;
        if (mode == GameMode.ADVENTURE && !disableInAdventure) return true;
        return false;
    }

    public void stopVisualization(Player player) {
        BukkitTask task = visualizationTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
            player.sendMessage(ChatColor.YELLOW + "Area visualization stopped.");
        }
    }
}