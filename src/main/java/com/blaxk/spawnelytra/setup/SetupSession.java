package com.blaxk.spawnelytra.setup;

import com.blaxk.spawnelytra.Main;
import com.blaxk.spawnelytra.util.MessageUtil;
import com.blaxk.spawnelytra.util.SchedulerUtil;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class SetupSession {
    private final Main plugin;
    private final Player player;

    private Location pos1;
    private Location pos2;
    private String activationMode;

    private boolean showBoostActivated;
    private boolean showPressToBoost;

    private SchedulerUtil.TaskHandle previewTask;
    private SchedulerUtil.TaskHandle animateTask;

    public SetupSession(final Main plugin, final Player player) {
        this.plugin = plugin;
        this.player = player;
        
        final String worldName = player.getWorld().getName();
        activationMode = plugin.getConfig().getString("worlds." + worldName + ".activation_mode", "double_jump");
    }

    public void begin() {
        final boolean originalShowBoostActivated = this.plugin.getConfig().getBoolean("messages.show_boost_activated", true);
        final boolean originalShowPressToBoost = this.plugin.getConfig().getBoolean("messages.show_press_to_boost", true);
        this.showBoostActivated = originalShowBoostActivated;
        this.showPressToBoost = originalShowPressToBoost;

        MessageUtil.send(this.player, "setup_started");
        
        final String exitLabel = MessageUtil.plain("setup_exit_label");
        final String exitHover = MessageUtil.plain("setup_exit_hover");
        MessageUtil.sendRaw(this.player, net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                "<#aaa8a8>[<click:run_command:'/spawnelytra setup exit'><hover:show_text:'" + exitHover + "'>" + exitLabel + "</hover></click>]")
        );
    }

    public void end() {
        if (this.previewTask != null) {
            this.previewTask.cancel();
            this.previewTask = null;
        }
        if (animateTask != null) {
            this.animateTask.cancel();
            this.animateTask = null;
        }
    }

    public boolean hasBothPositions() {
        return pos1 != null && pos2 != null && this.pos1.getWorld().equals(this.pos2.getWorld());
    }

    public Location getPos1() { return this.pos1; }
    public Location getPos2() { return this.pos2; }

    public void setPos1(final Location loc) {
        pos1 = loc.clone();
        this.spawnMarker(loc);
        this.player.playSound(loc, Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
        MessageUtil.send(this.player, "setup_pos1_set");
        if (this.pos2 != null) {
            this.startRectangleAnimation();
        } else {
            MessageUtil.send(this.player, "setup_pos1_next_instruction");
        }
    }

    public void setPos2(final Location loc) {
        if (pos1 != null && !this.pos1.getWorld().equals(loc.getWorld())) {
            MessageUtil.send(this.player, "setup_world_mismatch");
            return;
        }
        pos2 = loc.clone();
        this.spawnMarker(loc);

        this.player.playSound(loc, Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
        MessageUtil.send(this.player, "setup_pos2_set");
        this.startRectangleAnimation();
        SchedulerUtil.runAtEntityLater(this.plugin, this.player, 20L, () -> this.plugin.getSetupManager().showOptions(this.player));
    }

    private void spawnMarker(final Location loc) {
        this.player.spawnParticle(Particle.DUST, loc, 15, 0.15, 0.15, 0.15, 0.0,
                new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.8f));
    }

    private void startRectangleAnimation() {
        if (!this.hasBothPositions()) {
            return;
        }
        if (animateTask != null) {
            this.animateTask.cancel();
        }
        Location a = this.pos1.clone();
        Location b = this.pos2.clone();

        double minX = Math.min(a.getX(), b.getX());
        double minY = Math.min(a.getY(), b.getY());
        double minZ = Math.min(a.getZ(), b.getZ());
        double maxX = Math.max(a.getX(), b.getX());
        double maxY = Math.max(a.getY(), b.getY());
        double maxZ = Math.max(a.getZ(), b.getZ());

        this.animateTask = SchedulerUtil.runAtEntityTimer(this.plugin, this.player, 0L, 1L, new Runnable() {
            int tick;
            final int total = 40;
            @Override
            public void run() {
                if (!SetupSession.this.player.isOnline()) {
                    SetupSession.this.animateTask.cancel();
                    return;
                }
                final double progress = Math.min(1.0, (double) this.tick / this.total);
                final double currentY = minY + (maxY - minY) * progress;

                SetupSession.this.drawFilledPlane(minX, minZ, maxX, maxZ, currentY, 1.0);
                SetupSession.this.drawRectangleOnY(minX, minZ, maxX, maxZ, currentY, 1.0);
                this.tick++;
                if (this.total < this.tick) {
                    SetupSession.this.animateTask.cancel();
                    SetupSession.this.startPreview(minX, minY, minZ, maxX, maxY, maxZ);
                    SetupSession.this.player.playSound(SetupSession.this.player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.4f);
                }
            }
        });
    }

    private void startPreview(final double minX, final double minY, final double minZ, final double maxX, final double maxY, final double maxZ) {
        if (previewTask != null) {
            this.previewTask.cancel();
        }
        this.previewTask = SchedulerUtil.runAtEntityTimer(this.plugin, this.player, 0L, 10L, () -> {
            if (!this.player.isOnline()) {
                this.previewTask.cancel();
                return;
            }
            this.drawBoxEdges(1.0, minX, minY, minZ, maxX, maxY, maxZ);
        });
    }

    private void drawBoxEdges(final double progress, final double minX, final double minY, final double minZ, final double maxX, final double maxY, final double maxZ) {
        final double y1 = minY;
        final double y2 = minY + (maxY - minY) * progress;
        final Particle.DustOptions edgeDust = new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 215, 0), 1.2f);
        for (double y = y1; y <= y2; y += 0.5) {
            this.player.spawnParticle(Particle.DUST, new Location(this.player.getWorld(), minX, y, minZ), 1, 0, 0, 0, 0, edgeDust);
            this.player.spawnParticle(Particle.DUST, new Location(this.player.getWorld(), minX, y, maxZ), 1, 0, 0, 0, 0, edgeDust);
            this.player.spawnParticle(Particle.DUST, new Location(this.player.getWorld(), maxX, y, minZ), 1, 0, 0, 0, 0, edgeDust);
            this.player.spawnParticle(Particle.DUST, new Location(this.player.getWorld(), maxX, y, maxZ), 1, 0, 0, 0, 0, edgeDust);
        }
        this.drawRectangleOnY(minX, minZ, maxX, maxZ, y1, progress);
        this.drawRectangleOnY(minX, minZ, maxX, maxZ, y2, progress);
    }

    private void drawRectangleOnY(final double minX, final double minZ, final double maxX, final double maxZ, final double y, final double progress) {
        final Particle.DustOptions gold = new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 215, 0), 1.2f);
        final double xProgress = minX + (maxX - minX) * progress;
        final double zProgress = minZ + (maxZ - minZ) * progress;
        for (double x = minX; x <= xProgress; x += 0.5) {
            this.player.spawnParticle(Particle.DUST, new Location(this.player.getWorld(), x, y, minZ), 1, 0, 0, 0, 0, gold);
            this.player.spawnParticle(Particle.DUST, new Location(this.player.getWorld(), x, y, maxZ), 1, 0, 0, 0, 0, gold);
        }
        for (double z = minZ; z <= zProgress; z += 0.5) {
            this.player.spawnParticle(Particle.DUST, new Location(this.player.getWorld(), minX, y, z), 1, 0, 0, 0, 0, gold);
            this.player.spawnParticle(Particle.DUST, new Location(this.player.getWorld(), maxX, y, z), 1, 0, 0, 0, 0, gold);
        }
    }

    private void drawFilledPlane(final double minX, final double minZ, final double maxX, final double maxZ, final double y, double spacing) {
        final Particle.DustOptions gold = new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 215, 0), 1.0f);
        if (spacing <= 0) spacing = 1.0;
        for (double x = minX; x <= maxX; x += spacing) {
            for (double z = minZ; z <= maxZ; z += spacing) {
                this.player.spawnParticle(Particle.DUST, new Location(this.player.getWorld(), x, y, z), 1, 0, 0, 0, 0, gold);
            }
        }
    }

    public void setActivationMode(final String activationMode) {
        this.activationMode = activationMode;
    }

    public String getActivationMode() {
        return this.activationMode;
    }

    public boolean isShowBoostActivated() {
        return this.showBoostActivated;
    }

    public void setShowBoostActivated(final boolean showBoostActivated) {
        this.showBoostActivated = showBoostActivated;
    }

    public boolean isShowPressToBoost() {
        return this.showPressToBoost;
    }

    public void setShowPressToBoost(final boolean showPressToBoost) {
        this.showPressToBoost = showPressToBoost;
    }
}

