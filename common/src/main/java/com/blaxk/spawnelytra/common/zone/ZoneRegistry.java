/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.blaxk.spawnelytra.common.zone;

import com.blaxk.spawnelytra.common.geom.Vec2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Immutable snapshot of all zones with overlap resolution (spec §1.2). Thread-safe: build a new registry on every
 * change and publish it through a {@code volatile} field / {@code AtomicReference} (hot path reads never lock).
 *
 * <p>World matching goes through a canonicalizer ({@code worldKey}): Paper uses the identity, Fabric maps aliases
 * ({@code world} ↔ {@code minecraft:overworld}, ...) to one key. Every {@code world} argument and every
 * {@link Zone#world()} is passed through it before comparing.</p>
 */
public final class ZoneRegistry {

    /** Overlap winner order: highest priority first, then name ascending. */
    public static final Comparator<Zone> RESOLUTION_ORDER =
            Comparator.comparingInt(Zone::priority).reversed().thenComparing(Zone::name);

    /** Live world spawn per (canonical) world key; may return {@code null} if the world is not loaded. */
    @FunctionalInterface
    public interface SpawnLookup {
        Vec2 spawn(String world);

        SpawnLookup NONE = w -> null;
    }

    private static final ZoneRegistry EMPTY = new ZoneRegistry(List.of(), UnaryOperator.identity());

    private final Map<String, Zone> byName;
    private final Map<String, List<Zone>> byWorld;
    private final Function<String, String> worldKey;

    public ZoneRegistry(final Collection<Zone> zones, final Function<String, String> worldKey) {
        this.worldKey = worldKey == null ? UnaryOperator.identity() : worldKey;
        final List<Zone> sorted = new ArrayList<>(zones);
        sorted.sort(Comparator.comparing(Zone::name));
        final Map<String, Zone> names = new LinkedHashMap<>();
        final Map<String, List<Zone>> worlds = new LinkedHashMap<>();
        for (final Zone z : sorted) {
            names.put(z.name().toLowerCase(Locale.ROOT), z);
            worlds.computeIfAbsent(this.key(z.world()), k -> new ArrayList<>()).add(z);
        }
        worlds.replaceAll((k, v) -> {
            v.sort(ZoneRegistry.RESOLUTION_ORDER);
            return List.copyOf(v);
        });
        this.byName = Collections.unmodifiableMap(names);
        this.byWorld = Collections.unmodifiableMap(worlds);
    }

    public ZoneRegistry(final Collection<Zone> zones) {
        this(zones, UnaryOperator.identity());
    }

    public static ZoneRegistry empty() {
        return ZoneRegistry.EMPTY;
    }

    private String key(final String world) {
        return world == null ? "" : this.worldKey.apply(world);
    }

    /** The world canonicalizer this registry uses. */
    public Function<String, String> worldKey() {
        return this.worldKey;
    }

    /** All zones sorted by name. */
    public List<Zone> all() {
        return List.copyOf(this.byName.values());
    }

    public List<String> names() {
        return List.copyOf(this.byName.keySet());
    }

    public int size() {
        return this.byName.size();
    }

    public boolean isEmpty() {
        return this.byName.isEmpty();
    }

    /** Case-insensitive lookup. */
    public Optional<Zone> get(final String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(this.byName.get(name.toLowerCase(Locale.ROOT)));
    }

    /** Zones in the world (enabled or not), in resolution order. */
    public List<Zone> inWorld(final String world) {
        return this.byWorld.getOrDefault(this.key(world), List.of());
    }

    /** {@code true} if any enabled zone exists in the world (cheap pre-check for tick handlers). */
    public boolean hasEnabledZones(final String world) {
        for (final Zone z : this.inWorld(world)) {
            if (z.enabled()) {
                return true;
            }
        }
        return false;
    }

    /** Enabled zones containing the point, in resolution order (winner first). */
    public List<Zone> candidates(final String world, final double x, final double y, final double z, final SpawnLookup spawns) {
        final List<Zone> inWorld = this.inWorld(world);
        if (inWorld.isEmpty()) {
            return List.of();
        }
        final Vec2 spawn = spawns == null ? null : spawns.spawn(world);
        final List<Zone> out = new ArrayList<>(2);
        for (final Zone zone : inWorld) {
            if (zone.enabled() && zone.contains(x, y, z, spawn)) {
                out.add(zone);
            }
        }
        return out;
    }

    /** The winning zone at the point (highest priority, ties by name), if any. */
    public Optional<Zone> resolve(final String world, final double x, final double y, final double z, final SpawnLookup spawns) {
        final List<Zone> inWorld = this.inWorld(world);
        if (inWorld.isEmpty()) {
            return Optional.empty();
        }
        final Vec2 spawn = spawns == null ? null : spawns.spawn(world);
        for (final Zone zone : inWorld) { // already in resolution order: first match wins
            if (zone.enabled() && zone.contains(x, y, z, spawn)) {
                return Optional.of(zone);
            }
        }
        return Optional.empty();
    }

    /** Copy with the zone added or replaced (by name). */
    public ZoneRegistry with(final Zone zone) {
        final Map<String, Zone> copy = new LinkedHashMap<>(this.byName);
        copy.put(zone.name().toLowerCase(Locale.ROOT), zone);
        return new ZoneRegistry(copy.values(), this.worldKey);
    }

    /** Copy without the named zone. */
    public ZoneRegistry without(final String name) {
        final Map<String, Zone> copy = new LinkedHashMap<>(this.byName);
        copy.remove(name.toLowerCase(Locale.ROOT));
        return new ZoneRegistry(copy.values(), this.worldKey);
    }

    /** Copy with {@code oldName} replaced by {@code zone} (which may carry a new name). */
    public ZoneRegistry replace(final String oldName, final Zone zone) {
        return this.without(oldName).with(zone);
    }
}
