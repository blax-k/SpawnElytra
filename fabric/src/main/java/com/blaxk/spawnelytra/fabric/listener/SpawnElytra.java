/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.fabric.listener;

import com.blaxk.spawnelytra.common.config.GlobalSettings;
import com.blaxk.spawnelytra.common.stats.FlightTracker;
import com.blaxk.spawnelytra.common.tier.EffectiveSettings;
import com.blaxk.spawnelytra.common.zone.ActivationMode;
import com.blaxk.spawnelytra.common.zone.BoostDirection;
import com.blaxk.spawnelytra.common.zone.BoostDisplay;
import com.blaxk.spawnelytra.common.zone.HungerMode;
import com.blaxk.spawnelytra.common.zone.HungerSettings;
import com.blaxk.spawnelytra.common.zone.Zone;
import com.blaxk.spawnelytra.fabric.Main;
import com.blaxk.spawnelytra.fabric.bedrock.TempElytraManager;
import com.blaxk.spawnelytra.fabric.data.PlayerDataManager;
import com.blaxk.spawnelytra.fabric.integration.BedrockSupport;
import com.blaxk.spawnelytra.fabric.integration.Perms;
import com.blaxk.spawnelytra.fabric.util.Compat;
import com.blaxk.spawnelytra.fabric.util.MessageUtil;
import com.blaxk.spawnelytra.fabric.util.Scheduler;
import com.blaxk.spawnelytra.fabric.util.SoundResolver;
import com.blaxk.spawnelytra.fabric.util.Texts;
import com.blaxk.spawnelytra.fabric.zone.ZoneService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The spawn-elytra gameplay for all zones; a line-by-line port of the Paper 1.6 listener. Every
 * {@code on*} method corresponds to the Bukkit event handler of the same name; the event sources
 * are Fabric API callbacks or the mixins (see {@code Hooks}). Methods returning {@code boolean}
 * return {@code true} when the event should be cancelled. A glide is bound to the zone (and
 * effective tier settings) where it was activated (spec 1.3).
 */
public class SpawnElytra {
    private static final long FLIGHT_END_GRACE_MS = 1000L;

    private final Main plugin;
    private final ZoneService zones;
    private final PlayerDataManager playerDataManager;
    private final Map<UUID, FlightState> states = new ConcurrentHashMap<>();

    /** Per-player state (server thread). */
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
        Vec3 hungerLastLocation;
        FlightTracker tracker;
        ServerBossEvent bossBar;
        Scheduler.TaskHandle bossTask;
    }

    public SpawnElytra(final Main plugin, final ZoneService zones) {
        this.plugin = plugin;
        this.zones = zones;
        this.playerDataManager = plugin.getPlayerDataManager();
    }

    private FlightState state(final ServerPlayer player) {
        return this.states.computeIfAbsent(player.getUUID(), id -> new FlightState());
    }

    private GlobalSettings globals() {
        return this.zones.globals();
    }

    // ---- public queries ----------------------------------------------------------------------------------------

    public boolean isFlying(final ServerPlayer player) {
        final FlightState st = this.states.get(player.getUUID());
        return st != null && st.flying;
    }

    /** Re-binds a Bedrock glide after a rejoin mid-air; {@code false} if no zone exists in the world. */
    public boolean resumeBedrockFlight(final ServerPlayer player) {
        if (!this.zones.registry().hasEnabledZones(ZoneService.worldOf(Compat.level(player)))) {
            return false;
        }
        final FlightState st = this.state(player);
        final Zone zone = this.activeZoneAt(player);
        if (zone != null) {
            st.session = this.zones.effective(zone, player);
        }
        st.flying = true;
        return true;
    }

    /** Name of the zone the player was last seen in ("" if none). */
    public String currentZoneName(final ServerPlayer player) {
        final FlightState st = this.states.get(player.getUUID());
        return st == null ? "" : st.zoneName;
    }

    public int getBoostsRemaining(final ServerPlayer player) {
        final FlightState st = this.states.get(player.getUUID());
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

    public boolean isInSpawnArea(final ServerPlayer player) {
        return this.activeZoneAt(player) != null;
    }

    private boolean isToggledOff(final ServerPlayer player) {
        return this.playerDataManager != null && !this.playerDataManager.getPlayerData(player.getUUID()).isEnabled();
    }

    /** The winning zone at the player's position, or {@code null} when none or when the player turned spawn elytra off. */
    private Zone activeZoneAt(final ServerPlayer player) {
        if (this.isToggledOff(player)) {
            return null;
        }
        return this.zones.zoneAt(player);
    }

    // ---- fireworks ---------------------------------------------------------------------------------------------

    /** Bukkit {@code PlayerInteractEvent}: fireworks are blocked while in spawn elytra flight. */
    public boolean onPlayerInteract(final ServerPlayer player, final ItemStack item) {
        final FlightState st = this.states.get(player.getUUID());
        if (st == null || !st.flying) {
            return false;
        }
        final EffectiveSettings session = st.session;
        final boolean disabled = session != null ? session.fireworksDisabled() : this.globals().fireworksDisabled();
        if (!disabled) {
            return false;
        }
        if (item != null && item.is(Items.FIREWORK_ROCKET)) {
            final ItemStack chestplate = player.getItemBySlot(EquipmentSlot.CHEST);
            return chestplate.isEmpty() || !chestplate.is(Items.ELYTRA)
                    || this.plugin.getTempElytraManager().isTempElytra(chestplate);
        }
        return false;
    }

    // ---- hunger ------------------------------------------------------------------------------------------------

    private void initializeHungerTracking(final ServerPlayer player, final FlightState st) {
        final HungerSettings hunger = st.session == null ? null : st.session.hunger();
        if (hunger == null || !hunger.enabled()) {
            return;
        }
        if (HungerMode.DISTANCE == hunger.mode()) {
            st.hungerLastLocation = player.position();
            st.hungerDistanceProgress = 0;
        } else if (HungerMode.TIME == hunger.mode()) {
            st.hungerLastConsumption = System.currentTimeMillis();
        } else {
            st.hungerLastConsumption = 0;
            st.hungerLastLocation = null;
            st.hungerDistanceProgress = 0;
        }
    }

    private void handleHungerWhileFlying(final ServerPlayer player, final FlightState st, final HungerSettings hunger) {
        if (!this.shouldConsumeHunger(player, hunger)) {
            if (HungerMode.DISTANCE == hunger.mode()) {
                st.hungerLastLocation = player.position();
                st.hungerDistanceProgress = 0;
            } else if (HungerMode.TIME == hunger.mode() && st.hungerLastConsumption == 0) {
                st.hungerLastConsumption = System.currentTimeMillis();
            }
            return;
        }

        if (HungerMode.DISTANCE == hunger.mode()) {
            final Vec3 current = player.position();
            final Vec3 last = st.hungerLastLocation;
            if (last == null) {
                st.hungerLastLocation = current;
                st.hungerDistanceProgress = 0;
                return;
            }
            final double distance = last.distanceTo(current);
            if (distance <= 0) {
                st.hungerLastLocation = current;
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
            st.hungerLastLocation = current;
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

    private boolean shouldConsumeHunger(final ServerPlayer player, final HungerSettings hunger) {
        if (hunger == null || !hunger.enabled()) {
            return false;
        }
        final GameType mode = Compat.gameMode(player);
        if (GameType.CREATIVE == mode || GameType.SPECTATOR == mode) {
            return false;
        }
        return player.getFoodData().getFoodLevel() > hunger.effectiveMinimumFoodLevel();
    }

    private void consumeHunger(final ServerPlayer player, final HungerSettings hunger, final int amount) {
        if (amount <= 0) {
            return;
        }
        final int min = hunger.effectiveMinimumFoodLevel();
        final int current = player.getFoodData().getFoodLevel();
        if (current <= min) {
            return;
        }
        final int newLevel = Math.max(min, current - amount);
        if (newLevel < current) {
            player.getFoodData().setFoodLevel(newLevel);
            if (player.getFoodData().getSaturationLevel() > newLevel) {
                player.getFoodData().setSaturation(newLevel);
            }
        }
    }

    private boolean hasActivationHungerAvailable(final ServerPlayer player, final HungerSettings hunger) {
        if (hunger == null || !hunger.enabled() || HungerMode.ACTIVATION != hunger.mode()) {
            return true;
        }
        return this.shouldConsumeHunger(player, hunger);
    }

    private boolean chargeActivationHunger(final ServerPlayer player, final HungerSettings hunger) {
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

    // ---- mode / fly helpers -----------------------------------------------------------------------------------

    /** Block at the player's block position minus {@code i} (Bukkit {@code loc.subtract(0, i, 0).getBlock()}). */
    private static BlockState blockBelow(final ServerPlayer player, final int i) {
        return player.level().getBlockState(BlockPos.containing(player.getX(), player.getY() - i, player.getZ()));
    }

    private boolean hasAirBelow(final ServerPlayer player) {
        for (int i = 1; i <= 3; i++) {
            final BlockState block = blockBelow(player, i);
            if (!block.is(Blocks.AIR) && !block.is(Blocks.CAVE_AIR)) {
                return false;
            }
        }
        return true;
    }

    /** Bukkit {@code Block#isLiquid} for the block below the player (water, lava, bubble column). */
    private static boolean liquidBelow(final ServerPlayer player) {
        final BlockState below = blockBelow(player, 1);
        return below.is(Blocks.WATER) || below.is(Blocks.LAVA) || below.is(Blocks.BUBBLE_COLUMN);
    }

    /** Creative counts as spawn-elytra capable only when {@code disable_in_creative: false} (spec 8.3). */
    private boolean isCreativeAllowed(final ServerPlayer player) {
        return GameType.CREATIVE == Compat.gameMode(player) && !this.globals().disableInCreative();
    }

    private boolean isExternalFlyActive(final ServerPlayer player, final FlightState st) {
        final GameType mode = Compat.gameMode(player);
        if (GameType.SPECTATOR == mode) {
            return true;
        }
        if (GameType.CREATIVE == mode) {
            // Creative's own mayfly is not "external" when creative may use spawn elytra; real flight still is.
            return !this.isCreativeAllowed(player) || player.getAbilities().flying;
        }
        if (player.getAbilities().flying) {
            return true;
        }
        return player.getAbilities().mayfly && !st.ownsAllowFlight;
    }

    private boolean isElytraAllowedInMode(final ServerPlayer player) {
        final GameType mode = Compat.gameMode(player);
        if (GameType.SURVIVAL == mode) {
            return true;
        }
        if (GameType.ADVENTURE == mode) {
            return !this.globals().disableInAdventure();
        }
        return GameType.CREATIVE == mode && !this.globals().disableInCreative();
    }

    /** Bukkit {@code Player#setAllowFlight}. */
    private static void setAllowFlight(final ServerPlayer player, final boolean value) {
        if (player.getAbilities().flying && !value) {
            player.getAbilities().flying = false;
        }
        player.getAbilities().mayfly = value;
        player.onUpdateAbilities();
    }

    private void grantSpawnElytraAllowFlight(final ServerPlayer player, final FlightState st) {
        if (GameType.CREATIVE == Compat.gameMode(player)) {
            return; // creative already has mayfly; never take ownership of it
        }
        // Re-grant also when we already "own" the flag but something reset it (vanilla clears mayfly on a game
        // mode change), otherwise double-jump stays dead until the player leaves the zone.
        if (!st.ownsAllowFlight || !player.getAbilities().mayfly) {
            st.ownsAllowFlight = true;
            setAllowFlight(player, true);
        }
    }

    private void revokeSpawnElytraAllowFlight(final ServerPlayer player, final FlightState st) {
        if (st.ownsAllowFlight) {
            st.ownsAllowFlight = false;
            final GameType mode = Compat.gameMode(player);
            if (GameType.CREATIVE != mode && GameType.SPECTATOR != mode) {
                setAllowFlight(player, false);
            }
        }
    }

    private void updateTemporaryAllowFlight(final ServerPlayer player, final FlightState st, final boolean inDoubleJumpZone) {
        if (this.isExternalFlyActive(player, st)) {
            return;
        }
        final boolean prime = inDoubleJumpZone
                && Perms.has(player, Perms.USE)
                && this.isElytraAllowedInMode(player)
                && !player.isFallFlying()
                && !st.flying;
        if (prime) {
            this.grantSpawnElytraAllowFlight(player, st);
            return;
        }
        if (!inDoubleJumpZone && !st.flying) {
            this.revokeSpawnElytraAllowFlight(player, st);
        }
    }

    private void updateBedrockPriming(final ServerPlayer player, final FlightState st, final Zone zone) {
        if (st.flying || player.isFallFlying()) {
            return;
        }
        final HungerSettings hunger = zone == null ? null : this.zones.effective(zone, player).hunger();
        final boolean wantPrimed = zone != null
                && Perms.has(player, Perms.USE)
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

    // ---- activation --------------------------------------------------------------------------------------------

    private void activateElytraFlight(final ServerPlayer player, final Zone zone) {
        final FlightState st = this.state(player);
        if (zone == null || this.isExternalFlyActive(player, st)) {
            return;
        }
        final EffectiveSettings settings = this.zones.effective(zone, player);
        if (!this.chargeActivationHunger(player, settings.hunger())) {
            return;
        }

        Compat.setGliding(player, true);
        this.revokeSpawnElytraAllowFlight(player, st);

        this.startFlightBookkeeping(player, st, settings);
    }

    private void startFlightBookkeeping(final ServerPlayer player, final FlightState st, final EffectiveSettings settings) {
        st.session = settings;
        st.boostsUsed = 0;
        st.lastBoostMillis = 0;
        if (this.playerDataManager != null) {
            this.playerDataManager.incrementFlyCount(player);
        }
        st.tracker = new FlightTracker(System.currentTimeMillis(), player.getX(), player.getY(), player.getZ());

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

    private void sendPressToBoost(final ServerPlayer player, final int maxBoosts, final int remaining) {
        final boolean bedrock = BedrockSupport.isManaged(player);
        if (maxBoosts > 1) {
            MessageUtil.sendActionBar(player, bedrock ? "press_to_boost_remaining_bedrock" : "press_to_boost_remaining",
                    Placeholder.unparsed("remaining", String.valueOf(remaining)));
        } else {
            MessageUtil.sendActionBar(player, bedrock ? "press_to_boost_bedrock" : "press_to_boost");
        }
    }

    private void disableElytraFlight(final ServerPlayer player) {
        final FlightState st = this.state(player);
        this.revokeSpawnElytraAllowFlight(player, st);
        Compat.setGliding(player, false);
        player.fallDistance = 0;
        final boolean wasFlying = st.flying;
        st.flying = false;
        st.flightEndGrace = System.currentTimeMillis();
        st.lastBoostMillis = 0;
        st.boostsUsed = 0;
        resetHungerTracking(st);
        this.hideBossBar(player, st);
        if (wasFlying && st.tracker != null && this.playerDataManager != null) {
            this.playerDataManager.getPlayerData(player.getUUID()).completeFlight(st.tracker, System.currentTimeMillis());
            this.playerDataManager.markDirty(player.getUUID());
        }
        st.tracker = null;
        st.session = null;
    }

    /** Ends an active glide safely (no fall damage for that landing), e.g. when the player toggles spawn elytra off. */
    public void endFlightSafely(final ServerPlayer player) {
        final FlightState st = this.states.get(player.getUUID());
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

    // ---- events ------------------------------------------------------------------------------------------------

    /** Bukkit {@code PlayerToggleFlightEvent} (abilities packet while allow-flight is set). */
    public boolean onDoubleJump(final ServerPlayer player, final boolean isFlying) {
        if (!isFlying) {
            return false;
        }
        if (BedrockSupport.isManaged(player)) {
            return false;
        }
        if (!Perms.has(player, Perms.USE)) {
            return false;
        }
        if (this.globals().disableInCreative() && GameType.CREATIVE == Compat.gameMode(player)) {
            return false;
        }
        final FlightState st = this.state(player);
        if (this.isExternalFlyActive(player, st)) {
            return false;
        }
        final Zone zone = this.activeZoneAt(player);
        if (zone == null || zone.activationMode() != ActivationMode.DOUBLE_JUMP) {
            return false;
        }
        if (this.isElytraAllowedInMode(player)) {
            if (player.isFallFlying() || st.flying) {
                return true;
            }
            this.activateElytraFlight(player, zone);
            return true;
        }
        return false;
    }

    /** Bukkit {@code PlayerToggleSneakEvent}. */
    public void onPlayerSneak(final ServerPlayer player, final boolean isSneaking) {
        final FlightState st = this.state(player);

        if (isSneaking && BedrockSupport.isManaged(player) && st.flying && player.isFallFlying()) {
            this.tryBoost(player);
            return;
        }
        if (!Perms.has(player, Perms.USE)) {
            return;
        }
        if (this.globals().disableInCreative() && GameType.CREATIVE == Compat.gameMode(player)) {
            return;
        }
        final Zone zone = this.activeZoneAt(player);
        if (zone == null || zone.activationMode() != ActivationMode.SNEAK_JUMP) {
            return;
        }
        if (!this.isElytraAllowedInMode(player) || this.isExternalFlyActive(player, st)) {
            return;
        }

        if (isSneaking) {
            st.sneakPressed = true;
            final UUID uuid = player.getUUID();
            this.plugin.getScheduler().runLater(10L, () -> {
                final ServerPlayer online = this.plugin.getPlayer(uuid);
                if (online != null && st.sneakPressed && !online.onGround() && !st.flying) {
                    final long now = System.currentTimeMillis();
                    if (st.sneakJumpCooldown == 0 || now - st.sneakJumpCooldown >= 1000) {
                        this.activateElytraFlight(online, this.activeZoneAt(online));
                        st.sneakJumpCooldown = now;
                    }
                }
                st.sneakPressed = false;
            });
        } else {
            st.sneakPressed = false;
        }
    }

    /** Bukkit {@code PlayerCommandPreprocessEvent}: avoid conflicts with /fly style commands. */
    public void onPlayerCommandPreprocess(final ServerPlayer player, final String message) {
        final FlightState st = this.states.get(player.getUUID());
        if (st == null || !st.ownsAllowFlight || !this.isFlyToggleCommand(message)) {
            return;
        }
        this.revokeSpawnElytraAllowFlight(player, st);
    }

    /** Bukkit {@code PlayerMoveEvent}. */
    public void onPlayerMove(final ServerPlayer player) {
        final FlightState st = this.state(player);
        final Zone zone = this.activeZoneAt(player);
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
                    && !player.onGround()
                    && !player.getAbilities().flying
                    && !player.isFallFlying()
                    && !this.isExternalFlyActive(player, st)
                    && Perms.has(player, Perms.USE)) {
                this.activateElytraFlight(player, zone);
            }
        } else if (st.ownsAllowFlight && !st.flying) {
            this.revokeSpawnElytraAllowFlight(player, st);
        }

        if (st.flying) {
            if (player.onGround() || liquidBelow(player)) {
                this.disableElytraFlight(player);
            } else {
                player.fallDistance = 0;
                if (st.tracker != null) {
                    st.tracker.move(player.getX(), player.getY(), player.getZ());
                }
                final EffectiveSettings session = st.session;
                if (player.isFallFlying() && session != null && session.hunger() != null && session.hunger().enabled()) {
                    this.handleHungerWhileFlying(player, st, session.hunger());
                }
            }
        } else if (!player.isFallFlying()) {
            resetHungerTracking(st);
        }
    }

    private void handleCreativeNotice(final ServerPlayer player, final FlightState st, final Zone zone) {
        final boolean creative = GameType.CREATIVE == Compat.gameMode(player);
        final boolean show = zone != null
                && creative
                && this.globals().disableInCreative()
                && this.globals().showCreativeDisabled();
        if (!show) {
            if (zone == null || !creative) {
                st.creativeNotified = false;
            }
            return;
        }
        if (!st.creativeNotified) {
            st.creativeNotified = true;
            MessageUtil.sendActionBar(player, "creative_mode_elytra_disabled");
        }
    }

    /** Bukkit {@code EntityDamageEvent} (FALL / FLY_INTO_WALL). */
    public boolean onEntityDamage(final ServerPlayer player, final DamageSource source) {
        if (!source.is(DamageTypes.FALL) && !source.is(DamageTypes.FLY_INTO_WALL)) {
            return false;
        }
        final FlightState st = this.states.get(player.getUUID());
        if (st != null && st.flying) {
            return true;
        }
        if (this.plugin.getTempElytraManager().isTempElytra(player.getItemBySlot(EquipmentSlot.CHEST))) {
            return true;
        }
        if (st != null && st.flightEndGrace != 0) {
            if (System.currentTimeMillis() - st.flightEndGrace <= FLIGHT_END_GRACE_MS) {
                return true;
            }
            st.flightEndGrace = 0;
        }
        return false;
    }

    /** Bukkit {@code PlayerSwapHandItemsEvent} (offhand key). */
    public boolean onSwapItem(final ServerPlayer player) {
        final FlightState st = this.state(player);

        if (!BedrockSupport.isManaged(player) && !st.flying) {
            final Zone zone = this.activeZoneAt(player);
            if (zone != null && zone.activationMode() == ActivationMode.F_KEY) {
                if (!Perms.has(player, Perms.USE)) {
                    return false;
                }
                if (this.globals().disableInCreative() && GameType.CREATIVE == Compat.gameMode(player)) {
                    return false;
                }
                if (this.isElytraAllowedInMode(player) && !this.isExternalFlyActive(player, st)) {
                    final double launch = this.zones.effective(zone, player).launchStrength();
                    Compat.setVelocity(player, new Vec3(0, launch, 0));
                    final UUID uuid = player.getUUID();
                    this.plugin.getScheduler().runLater(5L, () -> {
                        final ServerPlayer online = this.plugin.getPlayer(uuid);
                        if (online != null) {
                            this.activateElytraFlight(online, zone);
                        }
                    });
                    return true;
                }
            }
        }

        return this.tryBoost(player);
    }

    private boolean tryBoost(final ServerPlayer player) {
        if (!Perms.has(player, Perms.USE_BOOST)) {
            return false;
        }
        final FlightState st = this.state(player);
        final EffectiveSettings s = st.session;
        if (s == null || !s.boostEnabled()) {
            return false;
        }
        if (!st.flying || !player.isFallFlying()) {
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

        final Vec3 velocity = s.direction() == BoostDirection.UPWARD
                ? new Vec3(0, s.strength(), 0)
                : player.getLookAngle().scale(s.strength());
        Compat.setVelocity(player, velocity);

        if (this.playerDataManager != null) {
            this.playerDataManager.incrementBoostCount(player);
        }
        if (st.tracker != null) {
            st.tracker.boost();
        }

        final Holder<SoundEvent> sound = resolveSound(s.sound());
        if (sound != null) {
            Compat.playSound(player, sound, player.getX(), player.getY(), player.getZ(), 1.0f, 1.0f);
        }

        final int remaining = s.maxBoosts() - newCount;
        if (s.boostDisplay() == BoostDisplay.ACTIONBAR) {
            this.sendBoostActionBars(player, st, s, remaining);
        } else if (s.boostDisplay() == BoostDisplay.BOSSBAR) {
            this.updateBossBar(st);
        }
        return true;
    }

    private void sendBoostActionBars(final ServerPlayer player, final FlightState st, final EffectiveSettings s, final int remaining) {
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
            final UUID uuid = player.getUUID();
            this.plugin.getScheduler().runLater(delayTicks, () -> {
                final ServerPlayer online = this.plugin.getPlayer(uuid);
                if (online != null && st.flying && online.isFallFlying() && st.session == s) {
                    final int currentRemaining = s.maxBoosts() - st.boostsUsed;
                    if (currentRemaining > 0) {
                        this.sendPressToBoost(online, s.maxBoosts(), currentRemaining);
                    }
                }
            });
        }
    }

    private static Holder<SoundEvent> resolveSound(final String name) {
        final Holder<SoundEvent> sound = SoundResolver.resolve(name);
        return sound != null ? sound : SoundResolver.resolve("ENTITY_BAT_TAKEOFF");
    }

    // ---- boss bar (boost_display: bossbar) -----------------------------------------------------------------------

    private void showBossBar(final ServerPlayer player, final FlightState st) {
        if (st.bossBar == null) {
            st.bossBar = Compat.bossBar(net.minecraft.network.chat.Component.empty(), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
        }
        this.updateBossBar(st);
        st.bossBar.addPlayer(player);
        if (st.bossTask == null) {
            final UUID uuid = player.getUUID();
            st.bossTask = this.plugin.getScheduler().runTimer(2L, 2L, () -> {
                final ServerPlayer online = this.plugin.getPlayer(uuid);
                if (!st.flying || online == null) {
                    this.hideBossBar(online, st);
                    return;
                }
                this.updateBossBar(st);
            });
        }
    }

    private void updateBossBar(final FlightState st) {
        final ServerBossEvent bar = st.bossBar;
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
            bar.setName(Texts.toNative(MessageUtil.component("bossbar_cooldown",
                    Placeholder.unparsed("seconds", String.format(Locale.ROOT, "%.1f", cooldownLeft / 1000.0)),
                    Placeholder.unparsed("remaining", String.valueOf(remaining)),
                    Placeholder.unparsed("max", String.valueOf(max)))));
            bar.setProgress(clamp((float) cooldownLeft / s.cooldownMillis()));
            bar.setColor(BossEvent.BossBarColor.YELLOW);
        } else {
            bar.setName(Texts.toNative(MessageUtil.component(remaining > 0 ? "bossbar_boosts" : "bossbar_no_boosts",
                    Placeholder.unparsed("remaining", String.valueOf(remaining)),
                    Placeholder.unparsed("max", String.valueOf(max)))));
            bar.setProgress(clamp((float) remaining / max));
            bar.setColor(remaining > 0 ? BossEvent.BossBarColor.GREEN : BossEvent.BossBarColor.RED);
        }
    }

    private static float clamp(final float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private void hideBossBar(final ServerPlayer player, final FlightState st) {
        if (st.bossTask != null) {
            st.bossTask.cancel();
            st.bossTask = null;
        }
        if (st.bossBar != null) {
            st.bossBar.removeAllPlayers();
        }
    }

    /**
     * Bukkit {@code EntityToggleGlideEvent}. Returns {@code true} to cancel the toggle (i.e.
     * keep the current gliding state).
     */
    public boolean onToggleGlide(final ServerPlayer player, final boolean isGliding) {
        final FlightState st = this.state(player);

        if (isGliding && !st.flying && BedrockSupport.isManaged(player)) {
            return this.handleBedrockGlideStart(player, st);
        }
        if (st.flying) {
            if (!isGliding) {
                return !BedrockSupport.isManaged(player);
            }
            this.revokeSpawnElytraAllowFlight(player, st);
        }
        return false;
    }

    private boolean handleBedrockGlideStart(final ServerPlayer player, final FlightState st) {
        final TempElytraManager manager = this.plugin.getTempElytraManager();
        if (!manager.hasTempElytraEquipped(player)) {
            return false;
        }
        final Zone zone = this.activeZoneAt(player);
        final EffectiveSettings settings = zone == null ? null : this.zones.effective(zone, player);
        final boolean allowed = settings != null
                && Perms.has(player, Perms.USE)
                && this.isElytraAllowedInMode(player)
                && !this.isExternalFlyActive(player, st)
                && this.chargeActivationHunger(player, settings.hunger());
        if (!allowed) {
            manager.ensureRestored(player);
            return true;
        }
        this.startFlightBookkeeping(player, st, settings);
        return false;
    }

    /** Bukkit {@code PlayerGameModeChangeEvent} (fired before the change). */
    public void onGameModeChange(final ServerPlayer player, final GameType newGameMode) {
        final FlightState st = this.states.get(player.getUUID());
        if (st == null) {
            return;
        }
        if (GameType.CREATIVE == newGameMode || GameType.SPECTATOR == newGameMode) {
            // Creative/spectator bring their own mayfly; we no longer own it.
            st.ownsAllowFlight = false;
        }
        if (this.globals().disableInCreative() && GameType.CREATIVE == newGameMode && st.flying) {
            final UUID uuid = player.getUUID();
            this.plugin.getScheduler().runLater(1L, () -> {
                final ServerPlayer online = this.plugin.getPlayer(uuid);
                if (online == null) {
                    return;
                }
                this.disableElytraFlight(online);
                this.plugin.getScheduler().runLater(5L, () -> {
                    final ServerPlayer later = this.plugin.getPlayer(uuid);
                    if (later != null) {
                        setAllowFlight(later, true);
                    }
                });
            });
        }
    }

    /** Bukkit {@code PlayerChangedWorldEvent}. */
    public void onChangedWorld(final ServerPlayer player) {
        final FlightState st = this.states.get(player.getUUID());
        if (st != null && st.flying) {
            this.disableElytraFlight(player);
        }
    }

    /** Bukkit {@code PlayerQuitEvent}. */
    public void onQuit(final ServerPlayer player) {
        this.cleanupPlayer(player);
        this.states.remove(player.getUUID());
    }

    /** Resets gliding / temporary mayfly (quit, reload, disable). */
    public void cleanupPlayer(final ServerPlayer player) {
        final FlightState st = this.states.get(player.getUUID());
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

    /** All online players' states (reload). */
    public List<UUID> trackedPlayers() {
        return List.copyOf(this.states.keySet());
    }
}
