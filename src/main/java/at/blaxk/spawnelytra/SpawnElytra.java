package at.blaxk.spawnelytra;

import java.util.ArrayList;
import java.util.List;

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
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.event.player.PlayerMoveEvent;

public class SpawnElytra extends BukkitRunnable implements Listener {
    private final main plugin;
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
    private final PlayerDataManager playerDataManager;

    private final String activationMode;
    private final double minX, minY, minZ;
    private final double maxX, maxY, maxZ;
    private final boolean useRectangularArea;

    public SpawnElytra(main plugin) {
        this.plugin = plugin;
        this.multiplyValue = plugin.getConfig().getInt("strength");
        this.spawnRadius = plugin.getConfig().getInt("radius");
        this.boostEnabled = true;
        this.disableInCreative = plugin.getConfig().getBoolean("disable_in_creative", true);
        this.playerDataManager = plugin.getPlayerDataManager();

        this.activationMode = plugin.getConfig().getString("activation_mode", "double_jump").toLowerCase();

        String configWorldName = plugin.getConfig().getString("world");
        this.world = Bukkit.getWorld(configWorldName);
        this.mode = plugin.getConfig().getString("mode", "auto");

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

                    double x2 = plugin.getConfig().getDouble("spawn.x2", spawnX);
                    double y2 = plugin.getConfig().getDouble("spawn.y2", spawnY);
                    double z2 = plugin.getConfig().getDouble("spawn.z2", spawnZ);

                    boolean useRectArea = (x2 != spawnX || y2 != spawnY || z2 != spawnZ);

                    if (useRectArea) {
                        this.minX = Math.min(spawnX, x2);
                        this.maxX = Math.max(spawnX, x2);

                        this.minY = Math.min(spawnY, y2);
                        this.maxY = Math.max(spawnY, y2);

                        this.minZ = Math.min(spawnZ, z2);
                        this.maxZ = Math.max(spawnZ, z2);

                        this.useRectangularArea = true;

                        plugin.getLogger().info("Using advanced mode with rectangular area: " +
                                "(" + this.minX + ", " + this.minY + ", " + this.minZ + ") to " +
                                "(" + this.maxX + ", " + this.maxY + ", " + this.maxZ + ")");
                    } else {
                        this.minX = this.minY = this.minZ = 0;
                        this.maxX = this.maxY = this.maxZ = 0;
                        this.useRectangularArea = false;

                        plugin.getLogger().info("Using advanced mode with radius-based spawn location: " +
                                this.spawnLocation.getX() + ", " +
                                this.spawnLocation.getY() + ", " +
                                this.spawnLocation.getZ());
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

                        plugin.getLogger().info("Using advanced mode with rectangular area (legacy format): " +
                                "(" + this.minX + ", " + this.minY + ", " + this.minZ + ") to " +
                                "(" + this.maxX + ", " + this.maxY + ", " + this.maxZ + ")");
                    } else {
                        this.minX = this.minY = this.minZ = 0;
                        this.maxX = this.maxY = this.maxZ = 0;
                        this.useRectangularArea = false;

                        plugin.getLogger().info("Using advanced mode with radius-based spawn location: " +
                                this.spawnLocation.getX() + ", " +
                                this.spawnLocation.getY() + ", " +
                                this.spawnLocation.getZ());
                    }
                } else {
                    this.minX = this.minY = this.minZ = 0;
                    this.maxX = this.maxY = this.maxZ = 0;
                    this.useRectangularArea = false;

                    plugin.getLogger().info("Using advanced mode with radius-based spawn location: " +
                            this.spawnLocation.getX() + ", " +
                            this.spawnLocation.getY() + ", " +
                            this.spawnLocation.getZ());
                }
            } else {
                this.spawnLocation = world.getSpawnLocation();
                this.minX = this.minY = this.minZ = 0;
                this.maxX = this.maxY = this.maxZ = 0;
                this.useRectangularArea = false;

                plugin.getLogger().info("Using auto mode with world spawn location: " +
                        this.spawnLocation.getX() + ", " +
                        this.spawnLocation.getY() + ", " +
                        this.spawnLocation.getZ());
            }
        }

        this.runTaskTimer(this.plugin, 0L, 3L);
    }

    public void run() {
        if (!this.boostEnabled || this.world == null) {
            return;
        }

        this.world.getPlayers().forEach((player) -> {
            if (disableInCreative && player.getGameMode() == GameMode.CREATIVE) {
                if (flying.contains(player)) {
                    disableElytraFlight(player);
                }
                return;
            }

            if (player.getGameMode() == GameMode.SURVIVAL) {
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

        if (plugin.getConfig().getBoolean("messages.show_press_to_boost", true)) {
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
        if (!this.boostEnabled) return;

        Player player = event.getPlayer();

        if (!player.hasPermission("spawnelytra.use")) {
            return;
        }

        if (disableInCreative && player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        if (player.getGameMode() == GameMode.SURVIVAL && this.isInSpawnArea(player)) {
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

        if (!player.hasPermission("spawnelytra.useboost")) {
            return;
        }

        if (this.boostEnabled &&
                this.flying.contains(player) &&
                !this.boosted.contains(player) &&
                player.isGliding()) {

            event.setCancelled(true);
            this.boosted.add(player);
            player.setVelocity(player.getLocation().getDirection().multiply(this.multiplyValue));

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
            return loc.getX() >= this.minX && loc.getX() <= this.maxX &&
                    loc.getY() >= this.minY && loc.getY() <= this.maxY &&
                    loc.getZ() >= this.minZ && loc.getZ() <= this.maxZ;
        } else {
            return this.spawnLocation.distance(player.getLocation()) <= (double)this.spawnRadius;
        }
    }
}