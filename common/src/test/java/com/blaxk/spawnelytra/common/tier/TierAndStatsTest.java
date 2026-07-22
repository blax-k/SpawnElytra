/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.tier;

import com.blaxk.spawnelytra.common.TestYaml;
import com.blaxk.spawnelytra.common.config.GlobalSettings;
import com.blaxk.spawnelytra.common.config.MapConfigView;
import com.blaxk.spawnelytra.common.stats.FlightTracker;
import com.blaxk.spawnelytra.common.stats.PlayerStats;
import com.blaxk.spawnelytra.common.stats.StatsFormat;
import com.blaxk.spawnelytra.common.zone.BoostDisplay;
import com.blaxk.spawnelytra.common.zone.BoostSettings;
import com.blaxk.spawnelytra.common.zone.BoostDirection;
import com.blaxk.spawnelytra.common.zone.CircleShape;
import com.blaxk.spawnelytra.common.zone.HungerMode;
import com.blaxk.spawnelytra.common.zone.HungerSettings;
import com.blaxk.spawnelytra.common.zone.Zone;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TierAndStatsTest {

    private static final String TIERS = """
            boost_display: bossbar
            fireworks: {disable_in_spawn_elytra: true}
            permission_tiers:
              vip: {priority: 10, max_boosts: 3, strength: 5.0, boost_cooldown: 1, launch_strength: 2.0}
              mvp: {priority: 20, max_boosts: 5}
              alt: {priority: 20, strength: 9}
              Bad Tier: {priority: 99}
              plain: {}
            """;

    @Test
    void readTiersInResolutionOrder() {
        final TierResolver.ReadResult r = TierResolver.read(TestYaml.parse(TierAndStatsTest.TIERS));
        assertEquals(List.of("alt", "mvp", "vip", "plain"), r.tiers().stream().map(PermissionTier::name).toList());
        assertEquals(1, r.warnings().size());
        final PermissionTier vip = r.tiers().get(2);
        assertEquals(new PermissionTier("vip", 10, 3, 5.0, 1.0, 2.0), vip);
        assertEquals("spawnelytra.tier.vip", vip.permission());
        assertEquals(new PermissionTier("plain", 0, null, null, null, null), r.tiers().get(3));
    }

    @Test
    void resolveHighestPriorityThenName() {
        final List<PermissionTier> tiers = TierResolver.read(TestYaml.parse(TierAndStatsTest.TIERS)).tiers();
        assertTrue(TierResolver.resolve(tiers, p -> false).isEmpty());
        assertEquals("vip", TierResolver.resolve(tiers, Set.of("spawnelytra.tier.vip")::contains).orElseThrow().name());
        assertEquals("mvp", TierResolver.resolve(tiers, Set.of("spawnelytra.tier.vip", "spawnelytra.tier.mvp")::contains).orElseThrow().name());
        assertEquals("alt", TierResolver.resolve(tiers, Set.of("spawnelytra.tier.alt", "spawnelytra.tier.mvp")::contains).orElseThrow().name());
    }

    @Test
    void effectiveSettingsFieldByField() {
        final MapConfigView root = TestYaml.parse(TierAndStatsTest.TIERS);
        final GlobalSettings g = GlobalSettings.read(root);
        final Zone zone = Zone.createDefault("spawn", "world", CircleShape.worldSpawn(10))
                .withBoost(new BoostSettings(true, 4, BoostDirection.UPWARD, 0, 2.5, "S"));
        final EffectiveSettings none = EffectiveSettings.resolve(zone, g, null);
        assertEquals(4, none.strength());
        assertEquals(1, none.maxBoosts()); // clamped like 1.5
        assertEquals(2500, none.cooldownMillis());
        assertEquals(1.5, none.launchStrength());
        assertEquals(BoostDisplay.BOSSBAR, none.boostDisplay());
        assertTrue(none.fireworksDisabled());
        assertEquals("", none.tierName());

        final PermissionTier mvp = g.tiers().stream().filter(t -> t.name().equals("mvp")).findFirst().orElseThrow();
        final EffectiveSettings e = EffectiveSettings.resolve(zone.withBoostDisplayOverride(BoostDisplay.NONE)
                .withFireworksDisabledOverride(false), g, mvp);
        assertEquals(5, e.maxBoosts());
        assertEquals(4, e.strength()); // not overridden
        assertEquals(2500, e.cooldownMillis());
        assertEquals(BoostDisplay.NONE, e.boostDisplay());
        assertFalse(e.fireworksDisabled());
        assertEquals("mvp", e.tierName());
        assertEquals(BoostDirection.UPWARD, e.direction());

        final HungerSettings h = new HungerSettings(true, HungerMode.TIME, 0, 1, 50, 1, 10, 1);
        assertEquals(h, EffectiveSettings.resolve(zone.withHungerOverride(h), g, null).hunger());
        assertEquals(g.hunger(), none.hunger());
    }

    @Test
    void hungerClampingMatches15() {
        final HungerSettings h = new HungerSettings(true, HungerMode.TIME, -3, -1, 0.2, -2, 0.0001, -5);
        assertEquals(0, h.effectiveMinimumFoodLevel());
        assertEquals(0, h.effectiveActivationCost());
        assertEquals(1.0, h.effectiveBlocksPerPoint());
        assertEquals(0, h.effectiveDistanceCost());
        assertEquals(1, h.effectiveTimeIntervalMillis());
        assertEquals(30_000, HungerSettings.DEFAULTS.effectiveTimeIntervalMillis());
    }

    @Test
    void statsAccumulation() {
        PlayerStats s = PlayerStats.ZERO.withFlightStarted();
        final FlightTracker t = new FlightTracker(1_000, 0, 100, 0);
        t.move(3, 100, 4);      // 5
        t.move(3, 100, 4);      // 0
        t.move(3, 88, 4);       // 12
        t.move(500, 88, 4);     // teleport, ignored
        t.move(500, 88, 14);    // 10
        s = s.withBoost().withBoost();
        s = t.complete(s, 13_500);
        assertEquals(27, t.distance(), 1e-9);
        assertEquals(new PlayerStats(1, 27, 2, 12.5, 27), s);

        final FlightTracker t2 = new FlightTracker(0, 0, 0, 0);
        t2.move(10, 0, 0);
        s = t2.complete(s.withFlightStarted(), 2_000);
        assertEquals(new PlayerStats(2, 37, 2, 14.5, 27), s);
        assertEquals(0, new FlightTracker(5_000, 0, 0, 0).durationSeconds(1_000));
    }

    @Test
    void statsPersistenceAndFormatting() {
        final PlayerStats s = new PlayerStats(12, 1234.567, 7, 3725.9, 456.04);
        final MapConfigView v = new MapConfigView();
        s.write(v.createSection(PlayerStats.SECTION));
        final PlayerStats back = PlayerStats.read(TestYaml.parse(TestYaml.dump(v)).section(PlayerStats.SECTION));
        assertEquals(new PlayerStats(12, 1234.6, 7, 3725.9, 456.0), back);
        assertEquals(PlayerStats.ZERO, PlayerStats.read(null));
        assertEquals("12", StatsFormat.placeholder(s, "flights"));
        assertEquals("1234.6", StatsFormat.placeholder(s, "distance"));
        assertEquals("7", StatsFormat.placeholder(s, "boosts"));
        assertEquals("3725", StatsFormat.placeholder(s, "glide_time"));
        assertEquals("456.0", StatsFormat.placeholder(s, "longest_flight"));
        assertNull(StatsFormat.placeholder(s, "nope"));
        assertEquals("1h 2m 5s", StatsFormat.duration(3725.9));
        assertEquals("2m 0s", StatsFormat.duration(120));
        assertEquals("0s", StatsFormat.duration(-4));
    }
}
