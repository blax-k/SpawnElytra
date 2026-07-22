/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.listener;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.blaxk.spawnelytra.Main;
import com.blaxk.spawnelytra.bedrock.TempElytraManager;
import com.blaxk.spawnelytra.common.config.GlobalSettings;
import com.blaxk.spawnelytra.common.stats.FlightTracker;
import com.blaxk.spawnelytra.common.tier.EffectiveSettings;
import com.blaxk.spawnelytra.common.zone.ActivationMode;
import com.blaxk.spawnelytra.common.zone.BoostDirection;
import com.blaxk.spawnelytra.common.zone.BoostDisplay;
import com.blaxk.spawnelytra.common.zone.HungerMode;
import com.blaxk.spawnelytra.common.zone.HungerSettings;
import com.blaxk.spawnelytra.common.zone.Zone;
import com.blaxk.spawnelytra.data.PlayerDataManager;
import com.blaxk.spawnelytra.integration.BedrockSupport;
import com.blaxk.spawnelytra.util.MessageUtil;
import com.blaxk.spawnelytra.util.SchedulerUtil;
import com.blaxk.spawnelytra.util.SoundUtil;
import com.blaxk.spawnelytra.zone.ZoneService;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/**
 * The spawn-elytra gameplay (one listener for all zones). Behaviour per zone mirrors the 1.5 per-world listener;
 * a glide is bound to the zone (and effective tier settings) where it was activated (spec §1.3).
 * <p>
 * Threading (Folia): all per-player state lives in {@link FlightState}, mutated only from that player's own events
 * and entity-scheduler tasks; maps are concurrent because placeholders and commands read them from other threads.
 */
public class SpawnElytra implements Listener {
    private static final long FLIGHT_END_GRACE_MS = 1000L;

    private final Main plugin;
    private final ZoneService zones;
    private final PlayerDataManager playerDataManager;
    private final Map<UUID, FlightState> states = new ConcurrentHashMap<>();

    /** Per-player state; fields are only written on the player's own thread. */
    static final class FlightState {
        volatile boolean flying;
        volatile EffectiveSettings session;
        volatile String zoneName = "";
        volatile int boostsUsed;
        long lastBoostMillis;
        boolean ownsAllowFlight;
        long flightEndGrace;
        boolean sneakPressed;
        long sneakJumpCooldown;
        boolean creativeNotified;
        double hungerDistanceProgress;
        long hungerLastConsumption;
        Location hungerLastLocation;
        FlightTracker tracker;
        BossBar bossBar;
        SchedulerUtil.TaskHandle bossTask;
    }

    public SpawnElytra(final Main plugin, final ZoneService zones) {
        this.plugin = plugin;
        this.zones = zones;
        this.playerDataManager = plugin.getPlayerDataManager();
    }

    private FlightState state(final Player player) {
        return this.states.computeIfAbsent(player.getUniqueId(), id -> new FlightState());
    }

    private GlobalSettings globals() {
        return this.zones.globals();
    }

    // ---- public queries (any thread) ----------------------------------------------------------------------------

    public boolean isFlying(final Player player) {
        final FlightState st = this.states.get(player.getUniqueId());
        return st != null && st.flying;
    }

    public void resumeBedrockFlight(final Player player) {
        final FlightState st = this.state(player);
        final Zone zone = this.activeZoneAt(player, player.getLocation());
        if (zone != null) {
            st.session = this.zones.effective(zone, player);
        }
        st.flying = true;
    }

    /** Name of the zone the player was last seen in ("" if none). */
    public String currentZoneName(final Player player) {
        final FlightState st = this.states.get(player.getUniqueId());
        return st == null ? "" : st.zoneName;
    }

    public int getBoostsRemaining(final Player player) {
        final FlightState st = this.states.get(player.getUniqueId());
        EffectiveSettings settings = st == null ? null : st.session;
        if (settings == null) {
            final Zone zone = this.zones.zone(st == null ? "" : st.zoneName).orElse(null);
            if (zone == null) {
                return 0;
            }
            settings = this.zones.effective(zone, player);
        }
        if (!settings.boostEnabled()) {
            return 0;
        }
        return Math.max(0, settings.maxBoosts() - (st == null ? 0 : st.boostsUsed));
    }

    public boolean isInSpawnArea(final Player player) {
        return this.activeZoneAt(player, player.getLocation()) != null;
    }

    private boolean isToggledOff(final Player player) {
        return this.playerDataManager != null && !this.playerDataManager.getPlayerData(player.getUniqueId()).isEnabled();
    }

    /** The winning zone at the location, or {@code null} when none or when the player turned spawn elytra off. */
    private Zone activeZoneAt(final Player player, final Location location) {
        if (this.isToggledOff(player)) {
            return null;
        }
        return this.zones.zoneAt(location);
    }

    // ---- fireworks ------------------------------------------------------------------------------------------------

    @EventHandler
    public void onPlayerInteract(final PlayerInteractEvent event) {
        final Player player = event.getPlayer();
        final FlightState st = this.states.get(player.getUniqueId());
        if (st == null || !st.flying) {
            return;
        }
        final EffectiveSettings session = st.session;
        final boolean disabled = session != null ? session.fireworksDisabled() : this.globals().fireworksDisabled();
        if (!disabled) {
            return;
        }
        final ItemStack item = event.getItem();
        if (item != null && Material.FIREWORK_ROCKET == item.getType()) {
            final ItemStack chestplate = player.getInventory().getChestplate();
            if (chestplate == null || Material.ELYTRA != chestplate.getType()
                    || this.plugin.getTempElytraManager().isTempElytra(chestplate)) {
                event.setCancelled(true);
            }
        }
    }

    // ---- hunger ---------------------------------------------------------------------------------------------------

    private void initializeHungerTracking(final Player player, final FlightState st) {
        final HungerSettings hunger = st.session == null ? null : st.session.hunger();
        if (hunger == null || !hunger.enabled()) {
            return;
        }
        if (HungerMode.DISTANCE == hunger.mode()) {
            st.hungerLastLocation = player.getLocation().clone();
            st.hungerDistanceProgress = 0;
        } else if (HungerMode.TIME == hunger.mode()) {
            st.hungerLastConsumption = System.currentTimeMillis();
        } else {
            st.hungerLastConsumption = 0;
            st.hungerLastLocation = null;
            st.hungerDistanceProgress = 0;
        }
    }

    private void handleHungerWhileFlying(final Player player, final FlightState st, final HungerSettings hunger) {
        if (!this.shouldConsumeHunger(player, hunger)) {
            if (HungerMode.DISTANCE == hunger.mode()) {
                st.hungerLastLocation = player.getLocation().clone();
                st.hungerDistanceProgress = 0;
            } else if (HungerMode.TIME == hunger.mode() && st.hungerLastConsumption == 0) {
                st.hungerLastConsumption = System.currentTimeMillis();
            }
            return;
        }

        if (HungerMode.DISTANCE == hunger.mode()) {
            final Location current = player.getLocation();
            final Location last = st.hungerLastLocation;
            if (last == null || last.getWorld() != current.getWorld()) {
                st.hungerLastLocation = current.clone();
                st.hungerDistanceProgress = 0;
                return;
            }
            final double distance = last.distance(current);
            if (distance <= 0) {
                st.hungerLastLocation = current.clone();
                return;
            }
            double accumulated = st.hungerDistanceProgress + distance;
            final double threshold = hunger.effectiveBlocksPerPoint();
            final int cost = hunger.effectiveDistanceCost();
            if (accumulated >= threshold && cost > 0) {
                final int steps = (int) (accumulated / threshold);
                this.consumeHunger(player, hunger, cost * steps);
                accumulated -= threshold * steps;
            }
            st.hungerDistanceProgress = accumulated;
            st.hungerLastLocation = current.clone();
        } else if (HungerMode.TIME == hunger.mode()) {
            final long now = System.currentTimeMillis();
            final long last = st.hungerLastConsumption == 0 ? now : st.hungerLastConsumption;
            if (st.hungerLastConsumption == 0) {
                st.hungerLastConsumption = now;
            }
            if (now - last >= hunger.effectiveTimeIntervalMillis()) {
                if (hunger.effectiveTimeCost() > 0) {
                    this.consumeHunger(player, hunger, hunger.effectiveTimeCost());
                }
                st.hungerLastConsumption = now;
            }
        }
    }

    private static void resetHungerTracking(final FlightState st) {
        st.hungerDistanceProgress = 0;
        st.hungerLastConsumption = 0;
        st.hungerLastLocation = null;
    }

    private boolean shouldConsumeHunger(final Player player, final HungerSettings hunger) {
        if (hunger == null || !hunger.enabled()) {
            return false;
        }
        final GameMode mode = player.getGameMode();
        if (GameMode.CREATIVE == mode || GameMode.SPECTATOR == mode) {
            return false;
        }
        return player.getFoodLevel() > hunger.effectiveMinimumFoodLevel();
    }

    private void consumeHunger(final Player player, final HungerSettings hunger, final int amount) {
        if (amount <= 0) {
            return;
        }
        final int min = hunger.effectiveMinimumFoodLevel();
        final int current = player.getFoodLevel();
        if (current <= min) {
            return;
        }
        final int newLevel = Math.max(min, current - amount);
        if (newLevel < current) {
            player.setFoodLevel(newLevel);
            if (player.getSaturation() > newLevel) {
                player.setSaturation(newLevel);
            }
        }
    }

    private boolean hasActivationHungerAvailable(final Player player, final HungerSettings hunger) {
        if (hunger == null || !hunger.enabled() || HungerMode.ACTIVATION != hunger.mode()) {
            return true;
        }
        return this.shouldConsumeHunger(player, hunger);
    }

    private boolean chargeActivationHunger(final Player player, final HungerSettings hunger) {
        if (hunger != null && hunger.enabled() && HungerMode.ACTIVATION == hunger.mode()) {
            if (!this.shouldConsumeHunger(player, hunger)) {
                MessageUtil.sendActionBar(player, "not_enough_hunger");
                return false;
            }
            if (hunger.effectiveActivationCost() > 0) {
                this.consumeHunger(player, hunger, hunger.effectiveActivationCost());
            }
        }
        return true;
    }

    // ---- mode / fly helpers ---------------------------------------------------------------------------------------

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

    /** Creative counts as spawn-elytra capable only when {@code disable_in_creative: false} (spec §8.3). */
    private boolean isCreativeAllowed(final Player player) {
        return GameMode.CREATIVE == player.getGameMode() && !this.globals().disableInCreative();
    }

    private boolean isExternalFlyActive(final Player player, final FlightState st) {
        final GameMode mode = player.getGameMode();
        if (GameMode.SPECTATOR == mode) {
            return true;
        }
        if (GameMode.CREATIVE == mode) {
            // Creative's own mayfly is not "external" when creative may use spawn elytra; real flight still is.
            return !this.isCreativeAllowed(player) || player.isFlying();
        }
        if (player.isFlying()) {
            return true;
        }
        return player.getAllowFlight() && !st.ownsAllowFlight;
    }

    private boolean isElytraAllowedInMode(final Player player) {
        final GameMode mode = player.getGameMode();
        if (GameMode.SURVIVAL == mode) {
            return true;
        }
        if (GameMode.ADVENTURE == mode) {
            return !this.globals().disableInAdventure();
        }
        return GameMode.CREATIVE == mode && !this.globals().disableInCreative();
    }

    private void grantSpawnElytraAllowFlight(final Player player, final FlightState st) {
        if (GameMode.CREATIVE == player.getGameMode()) {
            return; // creative already has mayfly; never take ownership of it
        }
        // Re-grant also when we already "own" the flag but something reset it (vanilla clears mayfly on a game
        // mode change), otherwise double-jump stays dead until the player leaves the zone.
        if (!st.ownsAllowFlight || !player.getAllowFlight()) {
            st.ownsAllowFlight = true;
            player.setAllowFlight(true);
        }
    }

    private void revokeSpawnElytraAllowFlight(final Player player, final FlightState st) {
        if (st.ownsAllowFlight) {
            st.ownsAllowFlight = false;
            if (GameMode.CREATIVE != player.getGameMode() && GameMode.SPECTATOR != player.getGameMode()) {
                player.setAllowFlight(false);
            }
        }
    }

    private void updateTemporaryAllowFlight(final Player player, final FlightState st, final boolean inDoubleJumpZone) {
        if (this.isExternalFlyActive(player, st)) {
            return;
        }
        final boolean prime = inDoubleJumpZone
                && player.hasPermission("spawnelytra.use")
                && this.isElytraAllowedInMode(player)
                && !player.isGliding()
                && !st.flying;
        if (prime) {
            this.grantSpawnElytraAllowFlight(player, st);
            return;
        }
        if (!inDoubleJumpZone && !st.flying) {
            this.revokeSpawnElytraAllowFlight(player, st);
        }
    }

    private void updateBedrockPriming(final Player player, final FlightState st, final Zone zone) {
        if (st.flying || player.isGliding()) {
            return;
        }
        final HungerSettings hunger = zone == null ? null : this.zones.effective(zone, player).hunger();
        final boolean wantPrimed = zone != null
                && player.hasPermission("spawnelytra.use")
                && this.isElytraAllowedInMode(player)
                && !this.isExternalFlyActive(player, st)
                && this.hasActivationHungerAvailable(player, hunger);

        final TempElytraManager manager = this.plugin.getTempElytraManager();
        if (wantPrimed) {
            manager.ensureEquipped(player);
        } else {
            manager.ensureRestored(player);
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

    // ---- activation -----------------------------------------------------------------------------------------------

    private void activateElytraFlight(final Player player, final Zone zone) {
        final FlightState st = this.state(player);
        if (zone == null || this.isExternalFlyActive(player, st)) {
            return;
        }
        final EffectiveSettings settings = this.zones.effective(zone, player);
        if (!this.chargeActivationHunger(player, settings.hunger())) {
            return;
        }

        player.setGliding(true);
        this.revokeSpawnElytraAllowFlight(player, st);

        this.startFlightBookkeeping(player, st, settings);
    }

    private void startFlightBookkeeping(final Player player, final FlightState st, final EffectiveSettings settings) {
        st.session = settings;
        st.boostsUsed = 0;
        st.lastBoostMillis = 0;
        if (this.playerDataManager != null) {
            this.playerDataManager.incrementFlyCount(player);
        }
        final Location loc = player.getLocation();
        st.tracker = new FlightTracker(System.currentTimeMillis(), loc.getX(), loc.getY(), loc.getZ());

        if (settings.boostEnabled()) {
            if (settings.boostDisplay() == BoostDisplay.ACTIONBAR && this.globals().showPressToBoost()) {
                this.sendPressToBoost(player, settings.maxBoosts(), settings.maxBoosts());
            } else if (settings.boostDisplay() == BoostDisplay.BOSSBAR) {
                this.showBossBar(player, st);
            }
        }

        st.flying = true;
        this.initializeHungerTracking(player, st);
    }

    private void sendPressToBoost(final Player player, final int maxBoosts, final int remaining) {
        final boolean bedrock = BedrockSupport.isManaged(player);
        if (maxBoosts > 1) {
            MessageUtil.sendActionBar(player, bedrock ? "press_to_boost_remaining_bedrock" : "press_to_boost_remaining",
                    Placeholder.unparsed("remaining", String.valueOf(remaining)));
        } else {
            MessageUtil.sendActionBar(player, bedrock ? "press_to_boost_bedrock" : "press_to_boost");
        }
    }

    private void disableElytraFlight(final Player player) {
        final FlightState st = this.state(player);
        this.revokeSpawnElytraAllowFlight(player, st);
        player.setGliding(false);
        player.setFallDistance(0);
        final boolean wasFlying = st.flying;
        st.flying = false;
        st.flightEndGrace = System.currentTimeMillis();
        st.lastBoostMillis = 0;
        st.boostsUsed = 0;
        resetHungerTracking(st);
        this.hideBossBar(player, st);
        if (wasFlying && st.tracker != null && this.playerDataManager != null) {
            this.playerDataManager.getPlayerData(player.getUniqueId()).completeFlight(st.tracker, System.currentTimeMillis());
            this.playerDataManager.markDirty(player.getUniqueId());
        }
        st.tracker = null;
        st.session = null;
    }

    /** Ends an active glide safely (no fall damage for that landing), e.g. when the player toggles spawn elytra off. */
    public void endFlightSafely(final Player player) {
        final FlightState st = this.states.get(player.getUniqueId());
        if (st == null) {
            return;
        }
        if (st.flying) {
            this.disableElytraFlight(player);
        } else {
            this.revokeSpawnElytraAllowFlight(player, st);
        }
        if (BedrockSupport.isManaged(player)) {
            this.plugin.getTempElytraManager().ensureRestored(player);
        }
    }

    // ---- events ---------------------------------------------------------------------------------------------------

    @EventHandler
    public void onDoubleJump(final PlayerToggleFlightEvent event) {
        if (!event.isFlying()) {
            return;
        }
        final Player player = event.getPlayer();
        if (BedrockSupport.isManaged(player)) {
            return;
        }
        if (!player.hasPermission("spawnelytra.use")) {
            return;
        }
        if (this.globals().disableInCreative() && GameMode.CREATIVE == player.getGameMode()) {
            return;
        }
        final FlightState st = this.state(player);
        if (this.isExternalFlyActive(player, st)) {
            return;
        }
        final Zone zone = this.activeZoneAt(player, player.getLocation());
        if (zone == null || zone.activationMode() != ActivationMode.DOUBLE_JUMP) {
            return;
        }
        if (this.isElytraAllowedInMode(player)) {
            event.setCancelled(true);
            if (player.isGliding() || st.flying) {
                return;
            }
            this.activateElytraFlight(player, zone);
        }
    }

    @EventHandler
    public void onPlayerSneak(final PlayerToggleSneakEvent event) {
        final Player player = event.getPlayer();
        final FlightState st = this.state(player);

        if (event.isSneaking() && BedrockSupport.isManaged(player) && st.flying && player.isGliding()) {
            this.tryBoost(player);
            return;
        }
        if (!player.hasPermission("spawnelytra.use")) {
            return;
        }
        if (this.globals().disableInCreative() && GameMode.CREATIVE == player.getGameMode()) {
            return;
        }
        final Zone zone = this.activeZoneAt(player, player.getLocation());
        if (zone == null || zone.activationMode() != ActivationMode.SNEAK_JUMP) {
            return;
        }
        if (!this.isElytraAllowedInMode(player) || this.isExternalFlyActive(player, st)) {
            return;
        }

        if (event.isSneaking()) {
            st.sneakPressed = true;
            SchedulerUtil.runAtEntityLater(this.plugin, player, 10L, () -> {
                if (st.sneakPressed && !player.isOnGround() && !st.flying) {
                    final long now = System.currentTimeMillis();
                    if (st.sneakJumpCooldown == 0 || now - st.sneakJumpCooldown >= 1000) {
                        this.activateElytraFlight(player, this.activeZoneAt(player, player.getLocation()));
                        st.sneakJumpCooldown = now;
                    }
                }
                st.sneakPressed = false;
            });
        } else {
            st.sneakPressed = false;
        }
    }

    @EventHandler
    public void onPlayerCommandPreprocess(final PlayerCommandPreprocessEvent event) {
        final Player player = event.getPlayer();
        final FlightState st = this.states.get(player.getUniqueId());
        if (st == null || !st.ownsAllowFlight || !this.isFlyToggleCommand(event.getMessage())) {
            return;
        }
        this.revokeSpawnElytraAllowFlight(player, st);
    }

    @EventHandler
    public void onPlayerMove(final PlayerMoveEvent event) {
        final Player player = event.getPlayer();
        final FlightState st = this.state(player);
        final Location to = event.getTo();
        final Zone zone = this.activeZoneAt(player, to);
        st.zoneName = zone == null ? "" : zone.name();

        this.handleCreativeNotice(player, st, zone);

        if (BedrockSupport.isManaged(player)) {
            this.updateBedrockPriming(player, st, zone);
        } else if (this.isElytraAllowedInMode(player)) {
            this.updateTemporaryAllowFlight(player, st, zone != null && zone.activationMode() == ActivationMode.DOUBLE_JUMP);

            if (zone != null
                    && zone.activationMode() == ActivationMode.AUTO
                    && !st.flying
                    && this.hasAirBelow(player)
                    && !player.isOnGround()
                    && !player.isFlying()
                    && !player.isGliding()
                    && !this.isExternalFlyActive(player, st)
                    && player.hasPermission("spawnelytra.use")) {
                this.activateElytraFlight(player, zone);
            }
        } else if (st.ownsAllowFlight && !st.flying) {
            this.revokeSpawnElytraAllowFlight(player, st);
        }

        if (st.flying) {
            if (player.isOnGround() || player.getLocation().getBlock().getRelative(BlockFace.DOWN).isLiquid()) {
                this.disableElytraFlight(player);
            } else {
                player.setFallDistance(0);
                if (st.tracker != null) {
                    st.tracker.move(to.getX(), to.getY(), to.getZ());
                }
                final EffectiveSettings session = st.session;
                if (player.isGliding() && session != null && session.hunger() != null && session.hunger().enabled()) {
                    this.handleHungerWhileFlying(player, st, session.hunger());
                }
            }
        } else if (!player.isGliding()) {
            resetHungerTracking(st);
        }
    }

    private void handleCreativeNotice(final Player player, final FlightState st, final Zone zone) {
        final boolean show = zone != null
                && GameMode.CREATIVE == player.getGameMode()
                && this.globals().disableInCreative()
                && this.globals().showCreativeDisabled();
        if (!show) {
            if (zone == null || GameMode.CREATIVE != player.getGameMode()) {
                st.creativeNotified = false;
            }
            return;
        }
        if (!st.creativeNotified) {
            st.creativeNotified = true;
            MessageUtil.sendActionBar(player, "creative_mode_elytra_disabled");
        }
    }

    @EventHandler
    public void onEntityDamage(final EntityDamageEvent event) {
        if (EntityType.PLAYER != event.getEntityType()) {
            return;
        }
        if (DamageCause.FALL != event.getCause() && DamageCause.FLY_INTO_WALL != event.getCause()) {
            return;
        }
        final Player player = (Player) event.getEntity();
        final FlightState st = this.states.get(player.getUniqueId());
        if (st != null && st.flying) {
            event.setCancelled(true);
            return;
        }
        if (this.plugin.getTempElytraManager().isTempElytra(player.getInventory().getChestplate())) {
            event.setCancelled(true);
            return;
        }
        if (st != null && st.flightEndGrace != 0) {
            if (System.currentTimeMillis() - st.flightEndGrace <= FLIGHT_END_GRACE_MS) {
                event.setCancelled(true);
            } else {
                st.flightEndGrace = 0;
            }
        }
    }

    @EventHandler
    public void onSwapItem(final PlayerSwapHandItemsEvent event) {
        final Player player = event.getPlayer();
        final FlightState st = this.state(player);

        if (!BedrockSupport.isManaged(player) && !st.flying) {
            final Zone zone = this.activeZoneAt(player, player.getLocation());
            if (zone != null && zone.activationMode() == ActivationMode.F_KEY) {
                if (!player.hasPermission("spawnelytra.use")) {
                    return;
                }
                if (this.globals().disableInCreative() && GameMode.CREATIVE == player.getGameMode()) {
                    return;
                }
                if (this.isElytraAllowedInMode(player) && !this.isExternalFlyActive(player, st)) {
                    event.setCancelled(true);
                    final double launch = this.zones.effective(zone, player).launchStrength();
                    player.setVelocity(new Vector(0, launch, 0));
                    SchedulerUtil.runAtEntityLater(this.plugin, player, 5L, () -> this.activateElytraFlight(player, zone));
                    return;
                }
            }
        }

        if (this.tryBoost(player)) {
            event.setCancelled(true);
        }
    }

    private boolean tryBoost(final Player player) {
        if (!player.hasPermission("spawnelytra.useboost")) {
            return false;
        }
        final FlightState st = this.state(player);
        final EffectiveSettings s = st.session;
        if (s == null || !s.boostEnabled()) {
            return false;
        }
        if (!st.flying || !player.isGliding()) {
            return false;
        }
        final int used = st.boostsUsed;
        if (used >= s.maxBoosts()) {
            return false;
        }
        final long now = System.currentTimeMillis();
        if (s.cooldownMillis() > 0 && st.lastBoostMillis != 0 && now - st.lastBoostMillis < s.cooldownMillis()) {
            return false;
        }

        final int newCount = used + 1;
        st.boostsUsed = newCount;
        if (s.cooldownMillis() > 0) {
            st.lastBoostMillis = now;
        }

        final Vector velocity = s.direction() == BoostDirection.UPWARD
                ? new Vector(0, s.strength(), 0)
                : player.getLocation().getDirection().multiply(s.strength());
        player.setVelocity(velocity);

        if (this.playerDataManager != null) {
            this.playerDataManager.incrementBoostCount(player);
        }
        if (st.tracker != null) {
            st.tracker.boost();
        }

        player.playSound(player.getLocation(), resolveSound(s.sound()), 1.0f, 1.0f);

        final int remaining = s.maxBoosts() - newCount;
        if (s.boostDisplay() == BoostDisplay.ACTIONBAR) {
            this.sendBoostActionBars(player, st, s, remaining);
        } else if (s.boostDisplay() == BoostDisplay.BOSSBAR) {
            this.updateBossBar(player, st);
        }
        return true;
    }

    private void sendBoostActionBars(final Player player, final FlightState st, final EffectiveSettings s, final int remaining) {
        final GlobalSettings g = this.globals();
        if (g.showBoostActivated()) {
            if (remaining > 0 && s.maxBoosts() > 1) {
                MessageUtil.sendActionBar(player, "boost_activated_remaining",
                        Placeholder.unparsed("remaining", String.valueOf(remaining)));
            } else {
                MessageUtil.sendActionBar(player, "boost_activated");
            }
        }
        if (remaining > 0 && g.showPressToBoost() && s.maxBoosts() > 1) {
            final long delayTicks = s.cooldownMillis() > 0 ? Math.max(20L, s.cooldownMillis() / 50) : 30L;
            SchedulerUtil.runAtEntityLater(this.plugin, player, delayTicks, () -> {
                if (st.flying && player.isGliding() && st.session == s) {
                    final int currentRemaining = s.maxBoosts() - st.boostsUsed;
                    if (currentRemaining > 0) {
                        this.sendPressToBoost(player, s.maxBoosts(), currentRemaining);
                    }
                }
            });
        }
    }

    private static Sound resolveSound(final String name) {
        try {
            return SoundUtil.byName(name);
        } catch (final IllegalArgumentException e) {
            return Sound.ENTITY_BAT_TAKEOFF;
        }
    }

    // ---- boss bar (boost_display: bossbar) -------------------------------------------------------------------------

    private void showBossBar(final Player player, final FlightState st) {
        if (st.bossBar == null) {
            st.bossBar = BossBar.bossBar(net.kyori.adventure.text.Component.empty(), 1f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
        }
        this.updateBossBar(player, st);
        MessageUtil.showBossBar(player, st.bossBar);
        if (st.bossTask == null) {
            st.bossTask = SchedulerUtil.runAtEntityTimer(this.plugin, player, 2L, 2L, () -> {
                if (!st.flying || !player.isOnline()) {
                    this.hideBossBar(player, st);
                    return;
                }
                this.updateBossBar(player, st);
            });
        }
    }

    private void updateBossBar(final Player player, final FlightState st) {
        final BossBar bar = st.bossBar;
        final EffectiveSettings s = st.session;
        if (bar == null || s == null) {
            return;
        }
        final int max = Math.max(1, s.maxBoosts());
        final int remaining = Math.max(0, max - st.boostsUsed);
        final long now = System.currentTimeMillis();
        final long cooldownLeft = s.cooldownMillis() > 0 && st.lastBoostMillis != 0
                ? s.cooldownMillis() - (now - st.lastBoostMillis) : 0;
        if (cooldownLeft > 0 && remaining > 0) {
            bar.name(MessageUtil.component("bossbar_cooldown",
                    Placeholder.unparsed("seconds", String.format(Locale.ROOT, "%.1f", cooldownLeft / 1000.0)),
                    Placeholder.unparsed("remaining", String.valueOf(remaining)),
                    Placeholder.unparsed("max", String.valueOf(max))));
            bar.progress(clamp((float) cooldownLeft / s.cooldownMillis()));
            bar.color(BossBar.Color.YELLOW);
        } else {
            bar.name(MessageUtil.component(remaining > 0 ? "bossbar_boosts" : "bossbar_no_boosts",
                    Placeholder.unparsed("remaining", String.valueOf(remaining)),
                    Placeholder.unparsed("max", String.valueOf(max))));
            bar.progress(clamp((float) remaining / max));
            bar.color(remaining > 0 ? BossBar.Color.GREEN : BossBar.Color.RED);
        }
    }

    private static float clamp(final float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private void hideBossBar(final Player player, final FlightState st) {
        if (st.bossTask != null) {
            st.bossTask.cancel();
            st.bossTask = null;
        }
        if (st.bossBar != null) {
            MessageUtil.hideBossBar(player, st.bossBar);
        }
    }

    @EventHandler
    public void onToggleGlide(final EntityToggleGlideEvent event) {
        if (EntityType.PLAYER != event.getEntityType()) {
            return;
        }
        final Player player = (Player) event.getEntity();
        final FlightState st = this.state(player);

        if (event.isGliding() && !st.flying && BedrockSupport.isManaged(player)) {
            this.handleBedrockGlideStart(player, st, event);
            return;
        }
        if (st.flying) {
            if (!event.isGliding()) {
                if (BedrockSupport.isManaged(player)) {
                    return;
                }
                event.setCancelled(true);
            } else {
                this.revokeSpawnElytraAllowFlight(player, st);
            }
        }
    }

    private void handleBedrockGlideStart(final Player player, final FlightState st, final EntityToggleGlideEvent event) {
        final TempElytraManager manager = this.plugin.getTempElytraManager();
        if (!manager.hasTempElytraEquipped(player)) {
            return;
        }
        final Zone zone = this.activeZoneAt(player, player.getLocation());
        final EffectiveSettings settings = zone == null ? null : this.zones.effective(zone, player);
        final boolean allowed = settings != null
                && player.hasPermission("spawnelytra.use")
                && this.isElytraAllowedInMode(player)
                && !this.isExternalFlyActive(player, st)
                && this.chargeActivationHunger(player, settings.hunger());
        if (!allowed) {
            event.setCancelled(true);
            manager.ensureRestored(player);
            return;
        }
        this.startFlightBookkeeping(player, st, settings);
    }

    @EventHandler
    public void onGameModeChange(final PlayerGameModeChangeEvent event) {
        final Player player = event.getPlayer();
        final FlightState st = this.states.get(player.getUniqueId());
        if (st == null) {
            return;
        }
        if (GameMode.CREATIVE == event.getNewGameMode() || GameMode.SPECTATOR == event.getNewGameMode()) {
            // Creative/spectator bring their own mayfly; we no longer own it.
            st.ownsAllowFlight = false;
        }
        if (this.globals().disableInCreative() && GameMode.CREATIVE == event.getNewGameMode() && st.flying) {
            SchedulerUtil.runAtEntityLater(this.plugin, player, 1L, () -> {
                this.disableElytraFlight(player);
                SchedulerUtil.runAtEntityLater(this.plugin, player, 5L, () -> player.setAllowFlight(true));
            });
        }
    }

    @EventHandler
    public void onChangedWorld(final PlayerChangedWorldEvent event) {
        final FlightState st = this.states.get(event.getPlayer().getUniqueId());
        if (st != null && st.flying) {
            this.disableElytraFlight(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        this.cleanupPlayer(event.getPlayer());
        this.states.remove(event.getPlayer().getUniqueId());
    }

    /** Resets gliding / temporary mayfly (quit, reload, disable). Must run on the player's thread. */
    public void cleanupPlayer(final Player player) {
        final FlightState st = this.states.get(player.getUniqueId());
        if (st == null) {
            return;
        }
        if (st.flying) {
            this.disableElytraFlight(player);
        } else {
            this.revokeSpawnElytraAllowFlight(player, st);
            this.hideBossBar(player, st);
        }
        st.sneakPressed = false;
        st.sneakJumpCooldown = 0;
        st.flightEndGrace = 0;
    }
}
