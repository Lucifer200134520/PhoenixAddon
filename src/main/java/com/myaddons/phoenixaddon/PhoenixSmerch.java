package com.myaddons.phoenixaddon;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.FireAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.util.DamageHandler;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class PhoenixSmerch extends FireAbility implements AddonAbility {

    private static final String PATH = "ExtraAbilities.Lucifer200134520.Fire.PhoenixSmerch.";

    private static long cooldown;
    private static long earlyCooldown;

    private static double flightSpeed;
    private static double flightTurn;
    private static int takeoffTicks;
    private static int maxFlightTicks;
    private static double wingScale;

    private static double maxSize;
    private static double minSize;
    private static double minScale;

    private static double maxHeight;
    private static double maxBaseRadius;
    private static double maxTopRadius;
    private static double tornadoSpeed;
    private static int tornadoMinTicks;
    private static int tornadoMaxTicks;
    private static long maxTotalMillis;
    private static double maxPullRadius;
    private static double pullStrength;
    private static double falloff;
    private static double orbitSpeed;
    private static double liftSpeed;
    private static double liftCeiling;
    private static double maxFallDamage;
    private static double maxFireRadius;
    private static double pulseDamage;
    private static int pulseInterval;

    private static int arms;
    private static double pointsPerArm;

    private static double flingSpeed;
    private static double flingUp;
    private static double flingDamage;
    private static double fallMultiplier;
    private static long fallProtectMillis;
    private static long ownerProtectMillis;

    private static final Particle.DustOptions BIG_GOLD = new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 205, 55), 3.8f);
    private static final Particle.DustOptions BIG_ORANGE = new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 150, 20), 4.0f);

    private static final Map<UUID, Long> VICTIM_SOFT = new HashMap<>();

    private static Listener listener;

    private enum Phase { FLIGHT, TORNADO }

    private Phase phase;
    private World world;
    private long startTime;
    private int tick;
    private int stuckTicks;
    private boolean glideHeld;
    private boolean cooldownApplied;

    private Vector velocity;
    private Vector lastPos;
    private final List<Vector> trail = new ArrayList<>();
    private double minX = Double.MAX_VALUE;
    private double maxX = -Double.MAX_VALUE;
    private double minZ = Double.MAX_VALUE;
    private double maxZ = -Double.MAX_VALUE;

    private List<Vector> path;
    private double[] cumulative;
    private double pathTotal;
    private double pathSpeed;
    private double travelled;
    private int segment;

    private double scale;
    private double tHeight;
    private double tBase;
    private double tTop;
    private double tPull;
    private double tFire;
    private double activeHeight;

    private Vector base;
    private double rotation;
    private int tornadoTick;
    private List<LivingEntity> candidates = new ArrayList<>();
    private Map<LivingEntity, Double> affected = new LinkedHashMap<>();

    public PhoenixSmerch(Player player) {
        super(player);

        if (player == null || bPlayer == null) return;
        if (!player.hasPermission("projectkorra.ability.PhoenixSmerch")) return;
        if (bPlayer.isOnCooldown(this)) return;
        if (hasAbility(player, PhoenixSmerch.class)) return;
        if (player.isInsideVehicle() || player.isGliding()) return;
        if (GeneralMethods.isRegionProtectedFromBuild(this, player.getLocation())) return;

        this.world = player.getWorld();
        this.phase = Phase.FLIGHT;
        this.startTime = System.currentTimeMillis();
        this.tick = 0;

        Vector look = player.getEyeLocation().getDirection();
        if (look.lengthSquared() < 1.0E-6) {
            look = new Vector(0, 1, 0);
        }
        look.normalize();
        this.velocity = look.multiply(flightSpeed * 0.55);
        if (velocity.getY() < 0.5) {
            velocity.setY(0.5);
        }
        this.lastPos = bodyCenter();

        start();

        if (!isStarted() || isRemoved()) return;

        PhoenixAscension.beginGlide(player);
        glideHeld = true;

        world.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.6f, 1.2f);
        world.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 1.4f, 1.0f);
        world.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.2f, 0.8f);
        world.spawnParticle(Particle.FLAME, player.getLocation(), 14, 0.5, 0.2, 0.5, 0.06);
    }

    private Vector bodyCenter() {
        return player.getLocation().toVector().add(new Vector(0, 0.9, 0));
    }

    private static Vector clamp(Vector v, double max) {
        if (Double.isNaN(v.getX()) || Double.isNaN(v.getY()) || Double.isNaN(v.getZ())) {
            return new Vector(0, 0, 0);
        }
        double length = v.length();
        if (length > max && length > 1.0E-6) {
            v.multiply(max / length);
        }
        return v;
    }

    public void finishFlight() {
        if (phase != Phase.FLIGHT || isRemoved()) return;
        startTornado();
    }

    @Override
    public void progress() {
        if (player == null || !player.isOnline() || player.isDead()) {
            remove();
            return;
        }

        if (!bPlayer.canBendIgnoreBindsCooldowns(this)) {
            remove();
            return;
        }

        if (phase == Phase.FLIGHT) {
            progressFlight();
        } else {
            progressTornado();
        }
    }

    private void progressFlight() {
        tick++;

        if (!player.getWorld().equals(world)) {
            remove();
            return;
        }

        Vector look = player.getEyeLocation().getDirection();
        if (look.lengthSquared() < 1.0E-6) {
            look = velocity.clone();
        }
        if (look.lengthSquared() < 1.0E-6) {
            look = new Vector(0, 1, 0);
        }
        look.normalize();

        double ramp = Math.min(1.0, 0.45 + tick * 0.06);
        Vector target = look.multiply(flightSpeed * ramp);
        if (tick <= takeoffTicks && target.getY() < 0.5) {
            target.setY(0.5);
        }

        velocity.multiply(1.0 - flightTurn).add(target.multiply(flightTurn));
        velocity = clamp(velocity, flightSpeed * 1.25);

        if (!player.isGliding()) {
            player.setGliding(true);
        }
        player.setVelocity(velocity.clone());
        player.setFallDistance(0);

        Vector pos = bodyCenter();

        double nMinX = Math.min(minX, pos.getX());
        double nMaxX = Math.max(maxX, pos.getX());
        double nMinZ = Math.min(minZ, pos.getZ());
        double nMaxZ = Math.max(maxZ, pos.getZ());

        if (!trail.isEmpty() && (nMaxX - nMinX > maxSize || nMaxZ - nMinZ > maxSize)) {
            startTornado();
            return;
        }

        minX = nMinX;
        maxX = nMaxX;
        minZ = nMinZ;
        maxZ = nMaxZ;
        trail.add(pos.clone());

        double moved = pos.distance(lastPos);
        lastPos = pos.clone();

        if (tick > 6 && moved < flightSpeed * 0.25) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
        }

        drawFlight(pos);
        drawTerritory();

        if (tick > 10 && (stuckTicks >= 3 || GeneralMethods.isSolid(player.getLocation().clone().add(0, -0.25, 0).getBlock()))) {
            startTornado();
            return;
        }

        if (tick >= maxFlightTicks) {
            startTornado();
        }
    }

    private void drawFlight(Vector pos) {
        Vector heading = velocity.clone();
        if (heading.lengthSquared() < 1.0E-6) {
            heading = new Vector(0, 0, 1);
        }
        heading.normalize();
        PhoenixAscension.drawFlightWings(world, pos.clone().add(heading.clone().multiply(-0.1)), heading, tick * 0.35, wingScale, 0.0);

        world.spawnParticle(Particle.FLAME, pos.getX(), pos.getY(), pos.getZ(), 2, 0.18, 0.18, 0.18, 0.01);
        PhoenixAscension.dust(world, pos, tick % 2 == 0 ? PhoenixAscension.DUST_GOLD : PhoenixAscension.DUST_ORANGE);
        if (tick % 3 == 0) {
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, pos.getX(), pos.getY(), pos.getZ(), 1, 0.1, 0.1, 0.1, 0.004);
        }
        if (tick % 6 == 0) {
            world.playSound(player.getLocation(), Sound.ITEM_ELYTRA_FLYING, 0.7f, 1.3f);
        }
    }

    private void drawTerritory() {
        int n = trail.size();
        for (int i = 0; i < n; i += 4) {
            Vector p = trail.get(i);
            world.spawnParticle(Particle.FLAME, p.getX(), p.getY(), p.getZ(), 1, 0.05, 0.05, 0.05, 0.002);
        }
    }

    private void fail() {
        Location loc = player.getLocation();
        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc, 8, 0.4, 0.4, 0.4, 0.02);
        world.spawnParticle(Particle.FLAME, loc, 6, 0.3, 0.3, 0.3, 0.03);
        world.playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 0.8f);
        remove();
    }

    private double groundY(Vector p) {
        int x = p.getBlockX();
        int z = p.getBlockZ();
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return base != null ? base.getY() : p.getY();
        }

        int top = Math.min(world.getMaxHeight() - 1, (int) Math.floor(p.getY()));
        int bottom = Math.max(0, top - 96);
        for (int y = top; y >= bottom; y--) {
            if (GeneralMethods.isSolid(world.getBlockAt(x, y, z))) {
                return y + 1.0;
            }
        }
        return Math.max(0, p.getY() - 40);
    }

    private void startTornado() {
        double extent = Math.max(maxX - minX, maxZ - minZ);
        if (trail.size() < 8 || extent < minSize) {
            fail();
            return;
        }

        this.scale = Math.max(minScale, Math.min(1.0, extent / maxSize));
        this.tHeight = maxHeight * scale;
        this.tBase = maxBaseRadius * scale;
        this.tTop = maxTopRadius * scale;
        this.tPull = maxPullRadius * scale;
        this.tFire = maxFireRadius * scale;

        List<Vector> points = new ArrayList<>(trail);
        points.add(trail.get(0).clone());
        points.addAll(trail.subList(1, trail.size()));
        points.add(trail.get(0).clone());

        double[] cum = new double[points.size()];
        for (int i = 1; i < points.size(); i++) {
            cum[i] = cum[i - 1] + points.get(i).distance(points.get(i - 1));
        }

        double total = cum[cum.length - 1];
        if (total < 4.0) {
            fail();
            return;
        }

        double speed = tornadoSpeed;
        double ticksNeeded = total / speed;
        if (ticksNeeded > tornadoMaxTicks) {
            speed = total / tornadoMaxTicks;
        } else if (ticksNeeded < tornadoMinTicks) {
            speed = total / tornadoMinTicks;
        }

        this.path = points;
        this.cumulative = cum;
        this.pathTotal = total;
        this.pathSpeed = speed;
        this.travelled = 0;
        this.segment = 0;
        this.tornadoTick = 0;
        this.rotation = 0;

        Vector start = points.get(0);
        this.base = new Vector(start.getX(), groundY(start), start.getZ());
        this.activeHeight = Math.max(20.0, tHeight);
        this.phase = Phase.TORNADO;

        bPlayer.addCooldown(this, cooldown);
        cooldownApplied = true;
        releaseFlight();

        for (int i = 0; i < trail.size(); i++) {
            Vector p = trail.get(i);
            world.spawnParticle(Particle.FLAME, p.getX(), p.getY(), p.getZ(), 1, 0.05, 0.05, 0.05, 0.02);
            if (i % 2 == 0) {
                PhoenixAscension.dust(world, p, PhoenixAscension.DUST_GOLD);
            }
        }

        Location at = new Location(world, base.getX(), base.getY(), base.getZ());
        world.playSound(at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 4.0f, 0.6f);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.5f);
        world.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 1.5f, 0.7f);
    }

    private Vector pointAt(double distance) {
        while (segment < cumulative.length - 2 && cumulative[segment + 1] < distance) {
            segment++;
        }
        Vector a = path.get(segment);
        Vector b = path.get(segment + 1);
        double span = cumulative[segment + 1] - cumulative[segment];
        double t = span > 1.0E-6 ? Math.max(0.0, Math.min(1.0, (distance - cumulative[segment]) / span)) : 0.0;
        return a.clone().add(b.clone().subtract(a).multiply(t));
    }

    private double radiusAt(double h) {
        double t = Math.max(0.0, Math.min(1.0, h / activeHeight));
        return tBase + (tTop - tBase) * Math.pow(t, 1.6);
    }

    private void progressTornado() {
        tornadoTick++;

        if (System.currentTimeMillis() - startTime > maxTotalMillis) {
            finishTornado();
            return;
        }

        travelled += pathSpeed;
        if (travelled >= pathTotal) {
            finishTornado();
            return;
        }

        Vector p = pointAt(travelled);
        double targetY = groundY(p);
        base.setX(p.getX());
        base.setZ(p.getZ());
        base.setY(base.getY() + (targetY - base.getY()) * 0.2);
        activeHeight = Math.max(20.0, tHeight);

        rotation += 0.12;

        drawTornado();
        affectEntities();
    }

    private void drawTornado() {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        int armCount = Math.max(3, (int) Math.round(arms * (0.6 + 0.4 * scale)));
        double step = Math.max(1.0, activeHeight / pointsPerArm);
        int index = 0;

        for (int arm = 0; arm < armCount; arm++) {
            for (double h = 0; h <= activeHeight; h += step) {
                double angle = rotation + arm * (Math.PI * 2 / armCount) + h * 0.05 / Math.max(0.3, scale);
                double r = radiusAt(h);
                double x = base.getX() + Math.cos(angle) * r;
                double y = base.getY() + h;
                double z = base.getZ() + Math.sin(angle) * r;

                int mode = (index + arm) % 4;
                if (mode == 0) {
                    PhoenixAscension.dust(world, new Vector(x, y, z), BIG_GOLD);
                } else if (mode == 1) {
                    PhoenixAscension.dust(world, new Vector(x, y, z), BIG_ORANGE);
                } else {
                    world.spawnParticle(Particle.FLAME, x, y, z, 1, 0.6, 0.6, 0.6, 0.01);
                }
                index++;
            }
        }

        double ringGap = Math.max(8.0, activeHeight / 10.0);
        int ringIndex = 0;
        for (double h = 4.0; h <= activeHeight; h += ringGap) {
            double r = radiusAt(h);
            int points = (int) Math.max(12, Math.min(90, r * 1.2));
            int offset = (tornadoTick + ringIndex) & 1;
            for (int i = offset; i < points; i += 2) {
                double angle = rotation * 0.7 + i * Math.PI * 2 / points;
                world.spawnParticle(Particle.FLAME,
                        base.getX() + Math.cos(angle) * r, base.getY() + h, base.getZ() + Math.sin(angle) * r,
                        1, 0.8, 1.2, 0.8, 0.01);
            }
            ringIndex++;
        }

        int smoke = (int) (30 + 70 * scale);
        for (int i = 0; i < smoke; i++) {
            double h = random.nextDouble() * activeHeight;
            double angle = random.nextDouble() * Math.PI * 2;
            double r = radiusAt(h) * random.nextDouble() * 0.7;
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE,
                    base.getX() + Math.cos(angle) * r, base.getY() + h, base.getZ() + Math.sin(angle) * r,
                    1, 1.0, 1.0, 1.0, 0.01);
        }

        for (int i = 0; i < smoke; i++) {
            double h = random.nextDouble() * activeHeight;
            double angle = random.nextDouble() * Math.PI * 2;
            double r = radiusAt(h) * (1.05 + 0.2 * random.nextDouble());
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE,
                    base.getX() + Math.cos(angle) * r, base.getY() + h, base.getZ() + Math.sin(angle) * r,
                    1, 1.0, 1.0, 1.0, 0.01);
        }

        for (int i = 0; i < 10; i++) {
            double h = random.nextDouble() * activeHeight;
            double angle = random.nextDouble() * Math.PI * 2;
            double r = radiusAt(h) * (0.5 + 0.5 * random.nextDouble());
            world.spawnParticle(Particle.LAVA,
                    base.getX() + Math.cos(angle) * r, base.getY() + h, base.getZ() + Math.sin(angle) * r,
                    1, 0.1, 0.1, 0.1, 0);
        }

        if (tornadoTick % 2 == 0) {
            for (int i = 0; i < 16; i++) {
                double angle = rotation + i * Math.PI / 8;
                double r = tTop * 1.1;
                world.spawnParticle(Particle.SMOKE_LARGE,
                        base.getX() + Math.cos(angle) * r, base.getY() + activeHeight, base.getZ() + Math.sin(angle) * r,
                        1, 2.0, 1.0, 2.0, 0.01);
            }
        }

        for (int i = 0; i < 24; i++) {
            double angle = rotation * 2 + i * Math.PI / 12;
            double r = tBase * 1.3;
            world.spawnParticle(Particle.FLAME,
                    base.getX() + Math.cos(angle) * r, base.getY() + 0.2, base.getZ() + Math.sin(angle) * r,
                    1, 0.6, 0.1, 0.6, 0.02);
        }

        if (tornadoTick % 5 == 0) {
            Location at = new Location(world, base.getX(), base.getY() + 3, base.getZ());
            world.playSound(at, Sound.BLOCK_FIRE_AMBIENT, 5.0f, 0.6f);
            world.playSound(at, Sound.ITEM_ELYTRA_FLYING, 4.0f, 0.5f);
        }
    }

    private boolean isValidVictim(Entity entity) {
        if (!(entity instanceof LivingEntity)) return false;
        if (entity instanceof ArmorStand) return false;
        if (entity.getUniqueId().equals(player.getUniqueId())) return false;
        if (entity.isDead() || !entity.isValid()) return false;
        if (entity.isInsideVehicle()) return false;
        if (entity instanceof Player) {
            Player other = (Player) entity;
            if (other.getGameMode() == GameMode.SPECTATOR) return false;
            UUID id = other.getUniqueId();
            if (PrometheusChain.isHeld(id) || PhoenixSnare.isCaught(id) || PhoenixBird.isScouting(id)) return false;
        }
        return !GeneralMethods.isRegionProtectedFromBuild(this, entity.getLocation());
    }

    private void refreshCandidates() {
        double half = activeHeight * 0.5 + 6.0;
        double query = Math.sqrt(tPull * tPull + half * half);
        Location mid = new Location(world, base.getX(), base.getY() + activeHeight * 0.5, base.getZ());

        List<LivingEntity> list = new ArrayList<>();
        for (Entity entity : GeneralMethods.getEntitiesAroundPoint(mid, query)) {
            if (isValidVictim(entity)) {
                list.add((LivingEntity) entity);
            }
        }
        candidates = list;
    }

    private void affectEntities() {
        if (tornadoTick % 3 == 1) {
            refreshCandidates();
        }

        Map<LivingEntity, Double> now = new LinkedHashMap<>();
        long until = System.currentTimeMillis() + fallProtectMillis;

        for (LivingEntity living : candidates) {
            if (living.isDead() || !living.isValid()) continue;
            if (!living.getWorld().equals(world)) continue;

            Location at = living.getLocation();
            double rel = at.getY() - base.getY();
            if (rel < -4.0 || rel > activeHeight + 6.0) continue;

            double dx = base.getX() - at.getX();
            double dz = base.getZ() - at.getZ();
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > tPull) continue;

            double nx = 0;
            double nz = 0;
            double tx = 0;
            double tz = 0;
            if (d > 1.0E-3) {
                nx = dx / d;
                nz = dz / d;
                tx = dz / d;
                tz = -dx / d;
            }

            double rf = radiusAt(Math.max(0.0, rel));
            double f = Math.pow(Math.max(0.0, 1.0 - d / tPull), falloff);
            double vx;
            double vz;
            double vy;

            if (d <= rf) {
                double ring = rf * 0.6;
                double radial = Math.max(-1.6, Math.min(1.6, (d - ring) * 0.1));
                double orbit = orbitSpeed * Math.min(1.0, d / Math.max(1.0, rf * 0.15));
                vx = nx * radial + tx * orbit;
                vz = nz * radial + tz * orbit;

                double t = 1.0 - d / rf;
                vy = liftSpeed * (0.5 + 0.5 * t);
                double ceiling = liftCeiling * (0.4 + 0.6 * scale);
                if (rel < 0) {
                    vy = liftSpeed * 0.6;
                } else if (rel > ceiling) {
                    vy = Math.max(-0.3, (ceiling - rel) * 0.04);
                }
            } else {
                double pull = pullStrength * f;
                vx = nx * pull + tx * orbitSpeed * 0.5 * f;
                vz = nz * pull + tz * orbitSpeed * 0.5 * f;
                vy = -0.02;
            }

            Vector current = living.getVelocity();
            Vector next = new Vector(
                    current.getX() * 0.3 + vx * 0.7,
                    current.getY() * 0.3 + vy * 0.7,
                    current.getZ() * 0.3 + vz * 0.7);
            next = clamp(next, 2.8);

            living.setVelocity(next);
            living.setFallDistance(0);

            if (d <= tFire && living.getFireTicks() < 40) {
                living.setFireTicks(60);
            }

            if (d <= rf * 1.6 && pulseDamage > 0 && tornadoTick % pulseInterval == 0) {
                living.setNoDamageTicks(0);
                DamageHandler.damageEntity(living, d <= rf ? pulseDamage : pulseDamage * 0.5, this);
            }

            now.put(living, Math.max(f, d <= rf ? 1.0 : 0.0));
            VICTIM_SOFT.put(living.getUniqueId(), until);
        }

        affected = now;
    }

    private void finishTornado() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        long until = System.currentTimeMillis() + fallProtectMillis;

        for (Map.Entry<LivingEntity, Double> entry : affected.entrySet()) {
            LivingEntity living = entry.getKey();
            if (living == null || living.isDead() || !living.isValid()) continue;
            if (!living.getWorld().equals(world)) continue;

            double strength = 0.5 + 0.5 * entry.getValue();
            Location at = living.getLocation();
            double dx = at.getX() - base.getX();
            double dz = at.getZ() - base.getZ();
            double d = Math.sqrt(dx * dx + dz * dz);

            Vector out;
            if (d > 1.0E-3) {
                out = new Vector(dx / d, 0, dz / d);
            } else {
                double angle = random.nextDouble() * Math.PI * 2;
                out = new Vector(Math.cos(angle), 0, Math.sin(angle));
            }

            Vector tangent = new Vector(-out.getZ(), 0, out.getX());
            Vector fling = out.multiply(flingSpeed * strength).add(tangent.multiply(flingSpeed * 0.4 * strength));
            fling.setY(flingUp * (0.6 + 0.4 * strength));
            fling = clamp(fling, 2.6);

            living.setVelocity(fling);
            living.setFallDistance(0);
            VICTIM_SOFT.put(living.getUniqueId(), until);

            if (flingDamage > 0) {
                living.setNoDamageTicks(0);
                DamageHandler.damageEntity(living, flingDamage * strength, this);
            }
        }

        Location center = new Location(world, base.getX(), base.getY() + activeHeight * 0.4, base.getZ());
        world.spawnParticle(Particle.EXPLOSION_HUGE, center, 6, tTop * 0.2, activeHeight * 0.15, tTop * 0.2, 0);
        world.spawnParticle(Particle.FLASH, center, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.FLAME, center, 150, tTop * 0.3, activeHeight * 0.25, tTop * 0.3, 0.3);
        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, center, 60, tTop * 0.3, activeHeight * 0.25, tTop * 0.3, 0.08);

        for (int ring = 1; ring <= 4; ring++) {
            double r = tBase + ring * tTop * 0.15;
            int points = (int) Math.min(120, 24 + r);
            for (int i = 0; i < points; i++) {
                double angle = i * Math.PI * 2 / points;
                Vector p = new Vector(base.getX() + Math.cos(angle) * r, base.getY() + 0.3, base.getZ() + Math.sin(angle) * r);
                PhoenixAscension.dust(world, p, (i + ring) % 2 == 0 ? BIG_GOLD : BIG_ORANGE);
            }
        }

        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 4.0f, 0.5f);
        world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 4.0f, 0.8f);
        world.playSound(center, Sound.ENTITY_ENDER_DRAGON_FLAP, 4.0f, 0.6f);

        remove();
    }

    private void releaseFlight() {
        if (!glideHeld) return;
        glideHeld = false;
        try {
            PhoenixAscension.endGlide(player, ownerProtectMillis);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void remove() {
        if (!cooldownApplied && player != null && bPlayer != null) {
            cooldownApplied = true;
            bPlayer.addCooldown(this, earlyCooldown);
        }
        releaseFlight();
        super.remove();
    }

    @Override
    public boolean isSneakAbility() {
        return false;
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
        return "PhoenixSmerch";
    }

    @Override
    public Element getElement() {
        return PhoenixElement.PHOENIX;
    }

    @Override
    public Location getLocation() {
        if (base != null && world != null) {
            return new Location(world, base.getX(), base.getY(), base.getZ());
        }
        return player != null ? player.getLocation() : null;
    }

    @Override
    public void load() {
        PhoenixElement.register();

        FileConfiguration config = ConfigManager.defaultConfig.get();
        config.addDefault(PATH + "Cooldown", 35000);
        config.addDefault(PATH + "EarlyEndCooldown", 7000);

        config.addDefault(PATH + "Flight.Speed", 1.7);
        config.addDefault(PATH + "Flight.Turn", 0.42);
        config.addDefault(PATH + "Flight.TakeoffTicks", 7);
        config.addDefault(PATH + "Flight.MaxTicks", 140);
        config.addDefault(PATH + "Flight.WingScale", 1.6);

        config.addDefault(PATH + "Territory.MaxSize", 45.0);
        config.addDefault(PATH + "Territory.MinSize", 6.0);
        config.addDefault(PATH + "Territory.MinScale", 0.25);

        config.addDefault(PATH + "Tornado.Height", 700.0);
        config.addDefault(PATH + "Tornado.BaseRadius", 70.0);
        config.addDefault(PATH + "Tornado.TopRadius", 280.0);
        config.addDefault(PATH + "Tornado.Speed", 1.1);
        config.addDefault(PATH + "Tornado.MinTicks", 120);
        config.addDefault(PATH + "Tornado.MaxTicks", 420);
        config.addDefault(PATH + "Tornado.MaxTotalMillis", 40000);
        config.addDefault(PATH + "Tornado.PullRadius", 320.0);
        config.addDefault(PATH + "Tornado.PullStrength", 3.0);
        config.addDefault(PATH + "Tornado.Falloff", 0.8);
        config.addDefault(PATH + "Tornado.OrbitSpeed", 1.6);
        config.addDefault(PATH + "Tornado.LiftSpeed", 0.6);
        config.addDefault(PATH + "Tornado.LiftCeiling", 70.0);
        config.addDefault(PATH + "Tornado.FireRadius", 180.0);
        config.addDefault(PATH + "Tornado.PulseDamage", 1.0);
        config.addDefault(PATH + "Tornado.PulseIntervalTicks", 5);

        config.addDefault(PATH + "Visual.Arms", 6);
        config.addDefault(PATH + "Visual.PointsPerArm", 90.0);

        config.addDefault(PATH + "Fling.Speed", 1.8);
        config.addDefault(PATH + "Fling.Up", 0.8);
        config.addDefault(PATH + "Fling.Damage", 2.0);
        config.addDefault(PATH + "Fling.FallDamageMultiplier", 0.3);
        config.addDefault(PATH + "Fling.MaxFallDamage", 6.0);
        config.addDefault(PATH + "Fling.FallProtectMillis", 5000);
        config.addDefault(PATH + "Safety.OwnerFallProtectMillis", 6000);

        config.options().copyDefaults(true);
        ConfigManager.defaultConfig.save();

        cooldown = config.getLong(PATH + "Cooldown");
        earlyCooldown = config.getLong(PATH + "EarlyEndCooldown");

        flightSpeed = Math.max(0.6, Math.min(2.4, config.getDouble(PATH + "Flight.Speed")));
        flightTurn = Math.max(0.1, Math.min(1.0, config.getDouble(PATH + "Flight.Turn")));
        takeoffTicks = Math.max(1, config.getInt(PATH + "Flight.TakeoffTicks"));
        maxFlightTicks = Math.max(30, config.getInt(PATH + "Flight.MaxTicks"));
        wingScale = Math.max(0.8, Math.min(3.0, config.getDouble(PATH + "Flight.WingScale")));

        maxSize = Math.max(10.0, config.getDouble(PATH + "Territory.MaxSize"));
        minSize = Math.max(3.0, config.getDouble(PATH + "Territory.MinSize"));
        minScale = Math.max(0.05, Math.min(1.0, config.getDouble(PATH + "Territory.MinScale")));

        maxHeight = Math.max(20.0, config.getDouble(PATH + "Tornado.Height"));
        maxBaseRadius = Math.max(3.0, config.getDouble(PATH + "Tornado.BaseRadius"));
        maxTopRadius = Math.max(maxBaseRadius, config.getDouble(PATH + "Tornado.TopRadius"));
        tornadoSpeed = Math.max(0.1, config.getDouble(PATH + "Tornado.Speed"));
        tornadoMinTicks = Math.max(20, config.getInt(PATH + "Tornado.MinTicks"));
        tornadoMaxTicks = Math.max(tornadoMinTicks, config.getInt(PATH + "Tornado.MaxTicks"));
        maxTotalMillis = Math.max(5000L, config.getLong(PATH + "Tornado.MaxTotalMillis"));
        maxPullRadius = Math.max(8.0, config.getDouble(PATH + "Tornado.PullRadius"));
        pullStrength = Math.max(0.0, Math.min(6.0, config.getDouble(PATH + "Tornado.PullStrength")));
        falloff = Math.max(0.5, config.getDouble(PATH + "Tornado.Falloff"));
        orbitSpeed = Math.max(0.0, Math.min(3.0, config.getDouble(PATH + "Tornado.OrbitSpeed")));
        liftSpeed = Math.max(0.0, Math.min(1.0, config.getDouble(PATH + "Tornado.LiftSpeed")));
        liftCeiling = Math.max(5.0, config.getDouble(PATH + "Tornado.LiftCeiling"));
        maxFireRadius = Math.max(1.0, config.getDouble(PATH + "Tornado.FireRadius"));
        pulseDamage = Math.max(0.0, config.getDouble(PATH + "Tornado.PulseDamage"));
        pulseInterval = Math.max(1, config.getInt(PATH + "Tornado.PulseIntervalTicks"));

        arms = Math.max(1, Math.min(12, config.getInt(PATH + "Visual.Arms")));
        pointsPerArm = Math.max(20.0, config.getDouble(PATH + "Visual.PointsPerArm"));

        flingSpeed = Math.max(0.0, Math.min(2.6, config.getDouble(PATH + "Fling.Speed")));
        flingUp = Math.max(0.0, Math.min(1.5, config.getDouble(PATH + "Fling.Up")));
        flingDamage = Math.max(0.0, config.getDouble(PATH + "Fling.Damage"));
        fallMultiplier = Math.max(0.0, Math.min(1.0, config.getDouble(PATH + "Fling.FallDamageMultiplier")));
        maxFallDamage = Math.max(0.0, config.getDouble(PATH + "Fling.MaxFallDamage"));
        fallProtectMillis = Math.max(0L, config.getLong(PATH + "Fling.FallProtectMillis"));
        ownerProtectMillis = Math.max(0L, config.getLong(PATH + "Safety.OwnerFallProtectMillis"));

        if (listener == null) {
            listener = new PhoenixSmerchListener();
            PhoenixAddonLoader.getPlugin().getServer().getPluginManager().registerEvents(listener, PhoenixAddonLoader.getPlugin());
        }
    }

    @Override
    public void stop() {
        for (PhoenixSmerch smerch : new ArrayList<>(CoreAbility.getAbilities(PhoenixSmerch.class))) {
            smerch.remove();
        }
        VICTIM_SOFT.clear();
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
        return "1.0";
    }

    @Override
    public String getDescription() {
        return "Взлетите с огненными крыльями и обведите в воздухе любую территорию до 45 на 45 блоков. "
                + "Чем больше территория, тем больше смерч. Гигантский огненный смерч дважды обходит обведённую территорию: "
                + "он притягивает всех вокруг (чем дальше, тем слабее), поджигает, поднимает вверх и крутит. "
                + "В конце смерч исчезает и разбрасывает всех в стороны.";
    }

    @Override
    public String getInstructions() {
        return "ЛКМ - взлететь. Летите по границе будущей территории. Повторный ЛКМ или Shift - создать смерч сразу, "
                + "иначе он появится сам, когда время полёта выйдет или территория достигнет предела. "
                + "Замыкать круг не нужно.";
    }

    public static class PhoenixSmerchListener implements Listener {

        @EventHandler
        public void onClick(PlayerInteractEvent event) {
            if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
            if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) return;

            Player player = event.getPlayer();
            if (!player.hasPermission("projectkorra.ability.PhoenixSmerch")) return;

            PhoenixSmerch existing = CoreAbility.getAbility(player, PhoenixSmerch.class);
            if (existing != null) {
                existing.finishFlight();
                return;
            }

            BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
            if (bPlayer == null || !bPlayer.canBend(CoreAbility.getAbility(PhoenixSmerch.class))) return;

            new PhoenixSmerch(player);
        }

        @EventHandler
        public void onSneak(PlayerToggleSneakEvent event) {
            if (!event.isSneaking()) return;

            PhoenixSmerch existing = CoreAbility.getAbility(event.getPlayer(), PhoenixSmerch.class);
            if (existing != null) {
                existing.finishFlight();
            }
        }

        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void onFall(EntityDamageEvent event) {
            if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;

            UUID id = event.getEntity().getUniqueId();
            long now = System.currentTimeMillis();

            Long soft = VICTIM_SOFT.get(id);
            if (soft != null) {
                if (soft > now) {
                    if (fallMultiplier <= 0.0) {
                        event.setCancelled(true);
                    } else {
                        event.setDamage(Math.min(maxFallDamage, event.getDamage() * fallMultiplier));
                    }
                    return;
                }
                VICTIM_SOFT.remove(id);
            }
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            VICTIM_SOFT.remove(event.getPlayer().getUniqueId());
        }
    }
}
