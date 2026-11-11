package com.myaddons.phoenixaddon;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.ComboAbility;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.FireAbility;
import com.projectkorra.projectkorra.ability.Ability;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.event.AbilityStartEvent;
import com.projectkorra.projectkorra.util.DamageHandler;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;

public class PhoenixNova extends FireAbility implements AddonAbility {

    private static final String PATH = "ExtraAbilities.Lucifer200134520.Fire.PhoenixNova.";

    private static long cooldown;
    private static long eggFormationDuration;
    private static long duration;
    private static double radius;
    private static double tickDamage;
    private static double explosionDamage;
    private static double knockback;
    private static double damageReduction;
    private static boolean fullHealOnExplode;

    private static Listener listener;

    private enum Phase { EGG_FORMING, ACTIVE, EXPLODING, DONE }

    private Phase phase;
    private Location origin;
    private long phaseStartTime;
    private long startTime;
    private long lastDamageTime;
    private boolean oldAllowFlight;
    private boolean oldIsFlying;
    private boolean flightModified;
    private double absorptionPhase;
    private double eggRadius;
    private int soundStage;

    public PhoenixNova(Player player) {
        super(player);

        if (player == null || bPlayer == null) return;
        if (!player.hasPermission("projectkorra.ability.PhoenixNova")) return;
        if (bPlayer.isOnCooldown(this)) return;
        if (hasAbility(player, PhoenixNova.class)) return;
        if (GeneralMethods.isRegionProtectedFromBuild(this, player.getLocation())) return;

        this.phase = Phase.EGG_FORMING;
        this.origin = player.getLocation().clone();
        this.phaseStartTime = System.currentTimeMillis();
        this.startTime = 0;
        this.lastDamageTime = 0;
        this.absorptionPhase = 0;
        this.eggRadius = 0;
        this.soundStage = 0;

        start();

        if (!isStarted() || isRemoved()) return;

        FlightGuard.acquire(player);
        player.setFlying(true);
        this.flightModified = true;

        bPlayer.addCooldown(this, cooldown);

        playInitialSound();
    }

    private void playInitialSound() {
        World world = origin.getWorld();
        if (world == null) return;

        world.playSound(origin, Sound.BLOCK_BEACON_ACTIVATE, 2.5f, 0.5f);
        world.playSound(origin, Sound.ENTITY_WITHER_SPAWN, 2.0f, 1.0f);
    }

    @Override
    public void progress() {
        if (player == null || player.isDead() || !player.isOnline()) {
            remove();
            return;
        }

        if (!bPlayer.canBendIgnoreBindsCooldowns(this)) {
            remove();
            return;
        }

        switch (phase) {
            case EGG_FORMING:
                progressEggForming();
                break;
            case ACTIVE:
                progressActive();
                break;
            case EXPLODING:
                progressExploding();
                break;
            case DONE:
                remove();
                break;
        }
    }

    private void progressEggForming() {
        long elapsed = System.currentTimeMillis() - phaseStartTime;
        double progress = Math.min(1.0, (double) elapsed / eggFormationDuration);

        holdInPlace();
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 40, 1, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 40, 2, false, false));

        eggRadius = 3.0 * progress;
        drawEggFormation(progress);
        playEggFormationSounds(progress);

        if (elapsed >= eggFormationDuration) {
            explodeEgg();
            phase = Phase.ACTIVE;
            startTime = System.currentTimeMillis();
            lastDamageTime = startTime;
        }
    }

    private static final Particle.DustOptions FIRE_DUST =
            new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 140, 15), 1.4f);


    private static void fireDust(World world, Location loc, int count, double dx, double dy, double dz, double speed) {
        world.spawnParticle(Particle.REDSTONE, loc, Math.max(1, count), dx, dy, dz, 0, FIRE_DUST);
    }

    private void drawEggFormation(double progress) {
        Location center = origin.clone().add(0, 2.0, 0);
        World world = center.getWorld();
        if (world == null) return;

        int shellParticles = (int) (40 + progress * 80);
        for (int i = 0; i < shellParticles; i++) {
            double theta = Math.random() * Math.PI * 2;
            double phi = Math.acos(2.0 * Math.random() - 1.0);

            double eggShapeMultiplier = 1.0 + Math.abs(Math.cos(phi)) * 0.4;

            double x = eggRadius * Math.sin(phi) * Math.cos(theta);
            double y = eggRadius * eggShapeMultiplier * Math.cos(phi);
            double z = eggRadius * Math.sin(phi) * Math.sin(theta);

            Location particleLoc = center.clone().add(x, y, z);


            int red = 255;
            int green = (int) (90 + progress * 110);

            Particle.DustOptions shellDust = new Particle.DustOptions(
                org.bukkit.Color.fromRGB(red, Math.min(255, green), 0),
                (float) (1.0 + progress * 0.8));

            world.spawnParticle(Particle.REDSTONE, particleLoc, 1, 0, 0, 0, 0, shellDust);

            if (Math.random() < 0.4) {
                world.spawnParticle(Particle.FLAME, particleLoc, 1, 0.1, 0.1, 0.1, 0.01);
            }
        }

        double time = System.currentTimeMillis() / 80.0;
        int ringCount = (int) (2 + progress * 3);

        for (int ring = 0; ring < ringCount; ring++) {
            double ringHeight = -eggRadius * 1.2 + (ring * eggRadius * 2.4 / ringCount);
            double ringRadius = eggRadius * 1.2 * Math.sin((ring / (double)ringCount) * Math.PI);

            int pointsOnRing = 25;
            for (int i = 0; i < pointsOnRing; i++) {
                double angle = (Math.PI * 2 / pointsOnRing) * i + time * (ring % 2 == 0 ? 1 : -1);
                Location ringLoc = center.clone().add(
                    Math.cos(angle) * ringRadius,
                    ringHeight,
                    Math.sin(angle) * ringRadius
                );

                fireDust(world, ringLoc, 1, 0, 0, 0, 0);

                if (i % 5 == 0) {
                    fireDust(world, ringLoc, 2, 0.05, 0.05, 0.05, 0.01);
                }
            }
        }

        if (progress > 0.2) {
            int spiralCount = 8;
            for (int i = 0; i < spiralCount; i++) {
                double spiralAngle = time * 0.5 + (i * Math.PI * 2 / spiralCount);
                double spiralHeight = ((time * 0.3) % 10) - 5;
                double spiralRadius = eggRadius * 1.5;

                Location spiralLoc = center.clone().add(
                    Math.cos(spiralAngle) * spiralRadius,
                    spiralHeight,
                    Math.sin(spiralAngle) * spiralRadius
                );

                Vector toCenter = center.toVector().subtract(spiralLoc.toVector());
                if (toCenter.lengthSquared() < 1.0E-6) continue;
                toCenter.normalize().multiply(0.2);
                world.spawnParticle(Particle.FLAME, spiralLoc, 0, toCenter.getX(), toCenter.getY(), toCenter.getZ(), 0.3);

                if (progress > 0.5 && Math.random() < 0.3) {
                    world.spawnParticle(Particle.LAVA, spiralLoc, 1, 0.05, 0.05, 0.05, 0);
                }
            }
        }

        double coreIntensity = progress * 15;
        fireDust(world, center, (int) coreIntensity, 0.4, 0.4, 0.4, 0.08);
        world.spawnParticle(Particle.FLAME, center, (int) (coreIntensity * 1.5), 0.3, 0.3, 0.3, 0.05);
        world.spawnParticle(Particle.LAVA, center, (int) (coreIntensity * 0.5), 0.2, 0.2, 0.2, 0);

        if (progress > 0.4) {
            Location playerCenter = player.getLocation().add(0, 1, 0);
            Vector direction = center.toVector().subtract(playerCenter.toVector());
            double distance = direction.length();
            if (distance > 1.0E-3) direction.normalize();
            else distance = 0;

            int beamParticles = (int) (distance * 3);
            for (int i = 0; i < beamParticles; i++) {
                double progressAlongBeam = (double) i / beamParticles;
                Location beamPoint = playerCenter.clone().add(direction.clone().multiply(distance * progressAlongBeam));

                fireDust(world, beamPoint, 1, 0.05, 0.05, 0.05, 0);

                if (i % 3 == 0) {
                    fireDust(world, beamPoint, 1, 0.03, 0.03, 0.03, 0.01);
                }
            }
        }

        if (progress > 0.7) {
            double pulse = Math.sin(time * 4) * 0.5 + 0.5;

            fireDust(world, center, (int) (5 * pulse),
                eggRadius * 0.6, eggRadius * 0.6, eggRadius * 0.6, 0);

            if (pulse > 0.8 && Math.random() < 0.3) {
                world.spawnParticle(Particle.EXPLOSION_LARGE, center, 1, 0.5, 0.5, 0.5, 0);
            }
        }

        if (progress > 0.85) {
            int cracks = (int) ((progress - 0.85) * 100);
            for (int i = 0; i < cracks; i++) {
                double theta = Math.random() * Math.PI * 2;
                double phi = Math.acos(2.0 * Math.random() - 1.0);

                double x = eggRadius * Math.sin(phi) * Math.cos(theta);
                double y = eggRadius * Math.cos(phi);
                double z = eggRadius * Math.sin(phi) * Math.sin(theta);

                Location crackLoc = center.clone().add(x, y, z);
                world.spawnParticle(Particle.LAVA, crackLoc, 2, 0.1, 0.1, 0.1, 0.05);
                world.spawnParticle(Particle.FLAME, crackLoc, 3, 0.15, 0.15, 0.15, 0.08);
            }
        }

        if (progress > 0.5) {
            double distortionRadius = eggRadius * 2.0;
            for (int i = 0; i < 20; i++) {
                double angle = Math.random() * Math.PI * 2;
                Location distortLoc = center.clone().add(
                    Math.cos(angle) * distortionRadius,
                    (Math.random() - 0.5) * eggRadius,
                    Math.sin(angle) * distortionRadius
                );

                world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, distortLoc, 1, 0.2, 0.2, 0.2, 0.01);
            }
        }
    }

    private void playEggFormationSounds(double progress) {
        int stage = (int) (progress * 8);
        if (stage > soundStage) {
            soundStage = stage;
            World world = origin.getWorld();
            if (world != null) {
                float pitch = 0.5f + stage * 0.15f;
                world.playSound(origin, Sound.BLOCK_BEACON_POWER_SELECT, 2.0f, pitch);
                world.playSound(origin, Sound.ENTITY_BLAZE_AMBIENT, 1.5f, pitch);
            }
        }

        if (Math.random() < 0.1) {
            World world = origin.getWorld();
            if (world != null) {
                world.playSound(origin, Sound.BLOCK_FIRE_AMBIENT, 1.2f, 0.5f + (float) progress);
            }
        }

        if (progress > 0.7 && Math.random() < 0.15) {
            World world = origin.getWorld();
            if (world != null) {
                world.playSound(origin, Sound.ENTITY_GUARDIAN_ATTACK, 0.4f, 1.8f);
            }
        }
    }

    private void explodeEgg() {
        Location center = origin.clone().add(0, 2.0, 0);
        World world = center.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.EXPLOSION_HUGE, center, 20, 1, 1, 1, 0);
            fireDust(world, center, 15, 2, 2, 2, 0);
            world.spawnParticle(Particle.FLAME, center, 300, 3, 3, 3, 0.8);
            fireDust(world, center, 150, 2.5, 2.5, 2.5, 0.6);
            world.spawnParticle(Particle.LAVA, center, 100, 2, 2, 2, 0.5);
            fireDust(world, center, 80, 2, 2, 2, 0.4);

            for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 24) {
                for (double r = 0; r < 8; r += 0.5) {
                    Location waveLoc = center.clone().add(
                        Math.cos(angle) * r,
                        Math.sin(r * 0.4) * 1.5,
                        Math.sin(angle) * r
                    );
                    world.spawnParticle(Particle.FLAME, waveLoc, 5, 0.2, 0.2, 0.2, 0.1);
                    world.spawnParticle(Particle.EXPLOSION_LARGE, waveLoc, 1, 0.1, 0.1, 0.1, 0);
                }
            }

            world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 3.5f, 0.5f);
            world.playSound(center, Sound.ITEM_TOTEM_USE, 3.0f, 0.8f);
            world.playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 3.5f, 0.6f);
            world.playSound(center, Sound.BLOCK_END_PORTAL_SPAWN, 2.5f, 1.5f);
        }
    }

    private void progressActive() {
        long elapsed = System.currentTimeMillis() - startTime;

        holdInPlace();

        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 40, 1, false, false));

        absorptionPhase += 0.025;
        drawAbsorptionEffect(origin);
        drawSun(origin, 3.0 + Math.sin(absorptionPhase) * 0.4);

        if (System.currentTimeMillis() - lastDamageTime >= 1000) {
            pulseDamage();
            lastDamageTime = System.currentTimeMillis();

            World world = origin.getWorld();
            if (world != null) {
                world.playSound(origin, Sound.BLOCK_FIRE_AMBIENT, 2.5f, 0.5f);
            }
        }

        if (elapsed >= duration) {
            phase = Phase.EXPLODING;
            phaseStartTime = System.currentTimeMillis();
        }
    }

    private void progressExploding() {
        explode();
        phase = Phase.DONE;
    }

    private void drawAbsorptionEffect(Location center) {
        World world = center.getWorld();
        if (world == null) return;

        double currentTime = System.currentTimeMillis() / 100.0;
        double absorptionRadius = 18.0;

        int particleCount = 100;
        for (int i = 0; i < particleCount; i++) {
            double angle = (Math.PI * 2 / particleCount) * i + currentTime * 0.3;
            double height = Math.sin(currentTime * 0.5 + i) * 6.0;

            double x = Math.cos(angle) * absorptionRadius;
            double y = height;
            double z = Math.sin(angle) * absorptionRadius;

            Location sourceLoc = center.clone().add(x, y, z);

            Vector direction = center.toVector().subtract(sourceLoc.toVector());
            if (direction.lengthSquared() < 1.0E-6) continue;
            direction.normalize();
            double distance = center.distance(sourceLoc);

            Location particleLoc = sourceLoc.clone().add(direction.clone().multiply(Math.random() * distance * 0.4));

            world.spawnParticle(Particle.FLAME, particleLoc, 1, 0.05, 0.05, 0.05, 0);
            fireDust(world, particleLoc, 0,
                direction.getX() * 0.4, direction.getY() * 0.4, direction.getZ() * 0.4, 0.2);

            if (i % 4 == 0) {
                world.spawnParticle(Particle.LAVA, particleLoc, 1, 0.03, 0.03, 0.03, 0);
            }
        }

        int streamCount = 20;
        for (int i = 0; i < streamCount; i++) {
            double angle = (Math.PI * 2 / streamCount) * i;
            double streamX = Math.cos(angle) * absorptionRadius;
            double streamZ = Math.sin(angle) * absorptionRadius;

            for (double progress = 0; progress < 1.0; progress += 0.12) {
                Location streamLoc = center.clone().add(
                    streamX * (1.0 - progress),
                    Math.sin(progress * Math.PI) * 2,
                    streamZ * (1.0 - progress)
                );

                fireDust(world, streamLoc, 1, 0.02, 0.02, 0.02, 0);

                if (progress % 0.4 < 0.12) {
                    fireDust(world, streamLoc, 3, 0.15, 0.15, 0.15, 0.1);
                }
            }
        }

        if (Math.random() < 0.35) {
            double angle = Math.random() * Math.PI * 2;
            Location swirl = center.clone().add(
                Math.cos(angle) * absorptionRadius * 0.9,
                Math.random() * 4 - 2,
                Math.sin(angle) * absorptionRadius * 0.9
            );
            world.spawnParticle(Particle.FLAME, swirl, 8, 0.3, 0.3, 0.3, 0.15);
        }
    }

    private void holdInPlace() {
        player.setVelocity(new Vector(0, 0, 0));
        player.setFallDistance(0);

        Location current = player.getLocation();
        if (current.getWorld() == null || origin.getWorld() == null) {
            remove();
            return;
        }

        if (!current.getWorld().equals(origin.getWorld())) {
            remove();
            return;
        }

        if (current.distanceSquared(origin) > 0.5) {
            Location target = origin.clone();
            target.setYaw(current.getYaw());
            target.setPitch(current.getPitch());
            player.teleport(target);
        }
    }

    private void pulseDamage() {
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!isValidTarget(entity)) continue;

            DamageHandler.damageEntity(entity, tickDamage, this);
            entity.setFireTicks(120);

            Vector push = entity.getLocation().toVector().subtract(origin.toVector());
            if (push.lengthSquared() < 0.01) {
                push = new Vector(0, 0.3, 0);
            } else {
                push.normalize();
            }
            push.setY(0.3);
            entity.setVelocity(push.multiply(knockback));
        }
    }

    private void drawSun(Location center, double r) {
        World world = center.getWorld();
        if (world == null) return;

        for (int i = 0; i < 50; i++) {
            double u = Math.random();
            double v = Math.random();
            double theta = u * 2.0 * Math.PI;
            double phi = Math.acos(2.0 * v - 1.0);

            double x = r * Math.sin(phi) * Math.cos(theta);
            double y = r * Math.sin(phi) * Math.sin(theta) + 1.5;
            double z = r * Math.cos(phi);

            Location particleLoc = center.clone().add(x, y, z);
            world.spawnParticle(Particle.FLAME, particleLoc, 3, 0.15, 0.15, 0.15, 0.08);

            if (Math.random() < 0.4) {
                fireDust(world, particleLoc, 2, 0.08, 0.08, 0.08, 0.03);
            }

            if (Math.random() < 0.15) {
                world.spawnParticle(Particle.LAVA, particleLoc, 1, 0.03, 0.03, 0.03, 0);
            }
        }

        world.spawnParticle(Particle.LAVA, center.clone().add(0, 1.5, 0), 5, r * 0.4, r * 0.4, r * 0.4, 0.08);

        if (Math.random() < 0.25) {
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, center.clone().add(0, 1.5, 0), 3, r * 0.6, r * 0.6, r * 0.6, 0.03);
        }

        if (Math.random() < 0.18) {
            world.playSound(center, Sound.ENTITY_BLAZE_AMBIENT, 0.6f, 1.3f);
        }
    }

    private void explode() {
        World world = origin.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.EXPLOSION_HUGE, origin, 25, 4, 4, 4, 0);
            world.spawnParticle(Particle.FLAME, origin, 500, 6, 6, 6, 1.0);
            world.spawnParticle(Particle.LAVA, origin, 200, 5, 5, 5, 0.8);
            fireDust(world, origin, 150, 5, 5, 5, 0.7);
            fireDust(world, origin, 100, 4, 4, 4, 0.5);

            world.playSound(origin, Sound.ENTITY_GENERIC_EXPLODE, 4.0f, 0.5f);
            world.playSound(origin, Sound.ITEM_TOTEM_USE, 3.0f, 0.8f);
            world.playSound(origin, Sound.ENTITY_ENDER_DRAGON_GROWL, 4.0f, 0.6f);
        }

        double explosionRadius = radius + 5.0;
        for (Entity entity : player.getNearbyEntities(explosionRadius, explosionRadius, explosionRadius)) {
            if (!isValidTarget(entity)) continue;

            double distance = entity.getLocation().distance(origin);
            double damageMult = Math.max(0, 1.0 - (distance / explosionRadius));
            double finalDamage = explosionDamage * damageMult;

            DamageHandler.damageEntity(entity, finalDamage, this);
            entity.setFireTicks(250);

            Vector push = entity.getLocation().toVector().subtract(origin.toVector());
            if (push.lengthSquared() < 0.01) {
                push = new Vector(0, 1.2, 0);
            } else {
                push.normalize();
            }
            push.setY(1.0);
            entity.setVelocity(push.multiply(2.5 * damageMult));
        }

        if (fullHealOnExplode) {
            try {
                player.setHealth(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
            } catch (Exception ignored) {
            }
        }
        player.setFireTicks(0);

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    private boolean isValidTarget(Entity entity) {
        if (!(entity instanceof LivingEntity)) return false;
        if (entity.getUniqueId().equals(player.getUniqueId())) return false;
        return !GeneralMethods.isRegionProtectedFromBuild(this, entity.getLocation());
    }

    private void restoreFlight() {
        if (!flightModified || player == null) {
            flightModified = false;
            return;
        }
        flightModified = false;
        FlightGuard.release(player);
    }

    @Override
    public void remove() {
        restoreFlight();
        super.remove();
    }

    @Override
    public boolean isSneakAbility() {
        return true;
    }

    @Override
    public boolean isHarmlessAbility() {
        return false;
    }

    @Override
    public long getCooldown() {
        return cooldown;
    }

    @Override
    public String getName() {
        return "PhoenixNova";
    }

    @Override
    public Element getElement() {
        return PhoenixElement.PHOENIX;
    }

    @Override
    public Location getLocation() {
        return origin != null ? origin : (player != null ? player.getLocation() : null);
    }

    @Override
    public void load() {
        PhoenixElement.register();

        FileConfiguration config = ConfigManager.defaultConfig.get();
        config.addDefault(PATH + "Cooldown", 30000);
        config.addDefault(PATH + "EggFormationDuration", 1500);
        config.addDefault(PATH + "Duration", 6000);
        config.addDefault(PATH + "Radius", 14.0);
        config.addDefault(PATH + "TickDamage", 2.5);
        config.addDefault(PATH + "ExplosionDamage", 28.0);
        config.addDefault(PATH + "Knockback", 0.5);
        config.addDefault(PATH + "DamageReduction", 0.3);
        config.addDefault(PATH + "FullHealOnExplode", true);
        config.options().copyDefaults(true);
        ConfigManager.defaultConfig.save();

        cooldown = config.getLong(PATH + "Cooldown");
        eggFormationDuration = config.getLong(PATH + "EggFormationDuration");
        duration = config.getLong(PATH + "Duration");
        radius = config.getDouble(PATH + "Radius");
        tickDamage = config.getDouble(PATH + "TickDamage");
        explosionDamage = config.getDouble(PATH + "ExplosionDamage");
        knockback = config.getDouble(PATH + "Knockback");
        damageReduction = config.getDouble(PATH + "DamageReduction");
        fullHealOnExplode = config.getBoolean(PATH + "FullHealOnExplode");

        if (listener == null) {
            listener = new PhoenixNovaListener();
            PhoenixAddonLoader.getPlugin().getServer().getPluginManager().registerEvents(listener, PhoenixAddonLoader.getPlugin());
        }
    }

    @Override
    public void stop() {
        for (PhoenixNova nova : new ArrayList<>(CoreAbility.getAbilities(PhoenixNova.class))) {
            nova.remove();
        }
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
    }

    @Override
    public String getAuthor() {
        return "Lucifer200134520 & KazharovFAKE";
    }

    @Override
    public String getVersion() {
        return "1.1";
    }

    @Override
    public String getDescription() {
        return "Формируйте огненное яйцо 3 сек, затем превратитесь в пылающее солнце! Финальный взрыв восстанавливает полное здоровье.";
    }

    @Override
    public String getInstructions() {
        return "Зажмите Shift для создания огненного яйца. После 3 сек станете сверхновой на 6 сек.";
    }

    public static class PhoenixNovaListener implements Listener {

        @EventHandler
        public void onAbilityStart(AbilityStartEvent event) {
            Ability ability = event.getAbility();
            if (ability == null || ability instanceof PhoenixNova) return;

            Player player = ability.getPlayer();
            if (player != null) {
                PhoenixNova nova = CoreAbility.getAbility(player, PhoenixNova.class);
                if (nova != null && (nova.phase == Phase.EGG_FORMING || nova.phase == Phase.ACTIVE)) {
                    event.setCancelled(true);
                }
            }
        }

        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void onDamage(EntityDamageEvent event) {
            if (!(event.getEntity() instanceof Player)) return;
            Player player = (Player) event.getEntity();

            EntityDamageEvent.DamageCause cause = event.getCause();
            if (cause == EntityDamageEvent.DamageCause.VOID || cause == EntityDamageEvent.DamageCause.SUICIDE) {
                PhoenixNova nova = CoreAbility.getAbility(player, PhoenixNova.class);
                if (nova != null) {
                    nova.remove();
                }
                return;
            }

            PhoenixNova nova = CoreAbility.getAbility(player, PhoenixNova.class);
            if (nova != null && nova.phase == Phase.ACTIVE) {
                event.setDamage(event.getDamage() * (1.0 - damageReduction));
            }
        }

        @EventHandler
        public void onSneak(PlayerToggleSneakEvent event) {
            if (!event.isSneaking()) return;

            Player player = event.getPlayer();
            if (!player.hasPermission("projectkorra.ability.PhoenixNova")) return;

            BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
            if (bPlayer == null || !bPlayer.canBend(CoreAbility.getAbility(PhoenixNova.class))) return;

            for (String cooldownName : bPlayer.getCooldowns().keySet()) {
                CoreAbility abilityOnCooldown = CoreAbility.getAbility(cooldownName);
                if (abilityOnCooldown instanceof ComboAbility) {
                    return;
                }
            }

            new PhoenixNova(player);
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            PhoenixNova nova = CoreAbility.getAbility(event.getPlayer(), PhoenixNova.class);
            if (nova != null) {
                nova.remove();
            }
        }
    }
}
