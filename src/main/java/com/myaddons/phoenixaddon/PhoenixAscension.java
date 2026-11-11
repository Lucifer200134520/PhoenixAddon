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
import org.bukkit.block.Block;
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
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class PhoenixAscension extends FireAbility implements AddonAbility {

    private static final String PATH = "ExtraAbilities.Lucifer200134520.Fire.PhoenixAscension.";

    private static long cooldown;
    private static double ascentHeight;
    private static double ascentSpeed;
    private static double updraftRadius;
    private static double updraftDamage;
    private static long soarDuration;
    private static double soarSpeed;
    private static double lockRange;
    private static double lockCos;
    private static double diveSpeed;
    private static double diveHoming;
    private static double grabRadius;
    private static int maxGrabbed;
    private static double grabDamage;
    private static int diveMaxTicks;
    private static double impactRadius;
    private static double impactDamage;
    private static double slamDamage;
    private static double impactKnockback;
    private static int zoneTicks;
    private static double zoneDamage;
    private static int zonePulseTicks;
    private static long maxTotalMillis;

    private static final long GUARD_AFTER_MS = 4000;

    public static final Particle.DustOptions DUST_WHITE = new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 244, 205), 1.3f);
    public static final Particle.DustOptions DUST_GOLD = new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 205, 55), 1.5f);
    public static final Particle.DustOptions DUST_ORANGE = new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 160, 25), 1.6f);
    public static final Particle.DustOptions DUST_EMBER = new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 120, 10), 1.7f);
    public static final Particle.DustOptions DUST_DARK = new Particle.DustOptions(org.bukkit.Color.fromRGB(70, 48, 32), 1.4f);

    private static final Map<UUID, Long> PROTECTED_UNTIL = new HashMap<>();
    private static final Set<UUID> GLIDING = new HashSet<>();

    private static Listener listener;

    private enum Phase { ASCEND, SOAR, DIVE, AFTERMATH }

    private static class Wave {
        final Vector center;
        final double speed;
        final double max;
        double radius;
        int delay;

        Wave(Vector center, double speed, double max, int delay) {
            this.center = center;
            this.speed = speed;
            this.max = max;
            this.delay = delay;
            this.radius = 0.5;
        }
    }

    private final List<Wave> waves = new ArrayList<>();
    private final List<LivingEntity> grabbed = new ArrayList<>();

    private Phase phase;
    private long startTime;
    private long soarStart;
    private int ticks;
    private int phaseTicks;
    private boolean wingsHeld;
    private boolean wasSneaking;
    private double startY;
    private Vector flightVelocity;
    private LivingEntity lockTarget;
    private Vector lockPoint;
    private Vector impact;
    private Vector impactDir;
    private double[] cracks;
    private int aftermathTicks;

    public PhoenixAscension(Player player) {
        super(player);

        if (player == null || bPlayer == null) return;
        if (!player.hasPermission("projectkorra.ability.PhoenixAscension")) return;
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        if (bPlayer.isOnCooldown(this)) return;
        if (hasAbility(player, PhoenixAscension.class)) return;
        if (GeneralMethods.isRegionProtectedFromBuild(this, player.getLocation())) return;

        this.startTime = System.currentTimeMillis();
        this.phase = Phase.ASCEND;

        start();

        if (!isStarted() || isRemoved()) return;

        bPlayer.addCooldown(this, cooldown);
        startFlight();
    }

    public static void jet(World world, Particle particle, Vector pos, Vector dir, double speed) {
        world.spawnParticle(particle, pos.getX(), pos.getY(), pos.getZ(), 0, dir.getX(), dir.getY(), dir.getZ(), speed);
    }

    public static void dust(World world, Vector pos, Particle.DustOptions options) {
        world.spawnParticle(Particle.REDSTONE, pos.getX(), pos.getY(), pos.getZ(), 1, 0, 0, 0, 0, options);
    }

    public static void puff(World world, Particle particle, Vector pos, int count, double spread, double extra) {
        world.spawnParticle(particle, pos.getX(), pos.getY(), pos.getZ(), count, spread, spread, spread, extra);
    }

    private static Vector featherPoint(Vector base, Vector right, Vector back, Vector up, int side, int index,
                                       double length, double frac, double phase, double amp) {
        double angle = Math.toRadians(6 + index * 14);
        double t = length * frac;
        double lift = t * (0.12 + frac * 0.22) + Math.sin(phase - frac * 1.3 - index * 0.1) * amp * t * 0.7;
        return base.clone()
                .add(right.clone().multiply(side * Math.cos(angle) * t))
                .add(back.clone().multiply(Math.sin(angle) * t))
                .add(up.clone().multiply(lift));
    }

    public static void drawWings(World world, Vector base, Vector dir, double phase, double scale, double amp) {
        Vector fwd = dir.clone();
        if (fwd.lengthSquared() < 1.0E-6) {
            fwd = new Vector(0, 0, 1);
        }
        fwd.normalize();

        Vector right = fwd.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1.0E-4) {
            right = new Vector(1, 0, 0);
        } else {
            right.normalize();
        }
        Vector up = right.clone().crossProduct(fwd).normalize();
        Vector back = fwd.clone().multiply(-1);

        double stepScale = Math.max(1.0, scale);
        double[] lengths = {3.4, 4.0, 4.3, 4.0, 3.4, 2.6, 1.8};
        double[] marks = {0.45, 0.75, 1.0};
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int side = -1; side <= 1; side += 2) {
            Vector[][] points = new Vector[lengths.length][marks.length];

            for (int j = 0; j < lengths.length; j++) {
                double length = lengths[j] * scale;
                double fracStep = 0.45 * stepScale / length;

                for (double frac = 0.12; frac <= 1.0001; frac += fracStep) {
                    Vector p = featherPoint(base, right, back, up, side, j, length, frac, phase, amp);
                    Particle.DustOptions options = frac < 0.4 ? DUST_EMBER : (frac < 0.75 ? DUST_ORANGE : DUST_GOLD);
                    dust(world, p, options);

                    if (j % 2 == 0 && frac > 0.45) {
                        puff(world, Particle.FLAME, p, 1, 0.03, 0.005);
                    }
                    if (frac > 0.95 && random.nextInt(3) == 0) {
                        puff(world, Particle.END_ROD, p, 1, 0.02, 0.01);
                    }
                }

                for (int m = 0; m < marks.length; m++) {
                    points[j][m] = featherPoint(base, right, back, up, side, j, length, marks[m], phase, amp);
                }
            }

            for (int j = 0; j < lengths.length - 1; j++) {
                for (int m = 0; m < marks.length; m++) {
                    Vector a = points[j][m];
                    Vector b = points[j + 1][m];
                    double distance = a.distance(b);
                    int steps = (int) Math.max(1, distance / (0.7 * stepScale));

                    for (int s = 1; s < steps; s++) {
                        Vector p = a.clone().add(b.clone().subtract(a).multiply(s / (double) steps));
                        dust(world, p, m == 2 ? DUST_GOLD : DUST_ORANGE);
                    }
                }
            }
        }
    }

    public static void beginGlide(Player p) {
        GLIDING.add(p.getUniqueId());
        PROTECTED_UNTIL.put(p.getUniqueId(), Long.MAX_VALUE);
        p.setGliding(true);
    }

    public static void endGlide(Player p, long guardMillis) {
        GLIDING.remove(p.getUniqueId());
        PROTECTED_UNTIL.put(p.getUniqueId(), System.currentTimeMillis() + guardMillis);
        p.setGliding(false);
        p.setFallDistance(0);
    }

    private static Vector[] basisOf(Vector dir) {
        Vector fwd = dir.clone();
        if (fwd.lengthSquared() < 1.0E-6) {
            fwd = new Vector(0, 0, 1);
        }
        fwd.normalize();
        Vector right = fwd.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1.0E-4) {
            right = new Vector(1, 0, 0);
        } else {
            right.normalize();
        }
        Vector up = right.clone().crossProduct(fwd).normalize();
        return new Vector[]{fwd, right, up};
    }

    public static void drawFlightWings(World world, Vector base, Vector dir, double phase, double scale, double fold) {
        Vector[] b = basisOf(dir);
        Vector fwd = b[0];
        Vector right = b[1];
        Vector up = b[2];
        double flap = Math.sin(phase);

        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 5; i++) {
                double a = Math.toRadians(14 + i * 15 + fold * 45);
                Vector d = right.clone().multiply(side * Math.cos(a)).add(fwd.clone().multiply(-Math.sin(a)));
                double len = (1.9 - i * 0.22) * scale * (1.0 - fold * 0.3);

                for (int m = 1; m <= 3; m++) {
                    double f = m / 3.0;
                    Vector p = base.clone()
                            .add(d.clone().multiply(len * f))
                            .add(up.clone().multiply((flap * 0.45 * f + 0.12 * f) * len * (1.0 - fold)));
                    dust(world, p, f < 0.4 ? DUST_EMBER : (f < 0.8 ? DUST_ORANGE : DUST_GOLD));
                }
            }
        }
    }

    private boolean isValidVictim(Entity entity) {
        if (!(entity instanceof LivingEntity)) return false;
        if (entity instanceof ArmorStand) return false;
        if (entity.getUniqueId().equals(player.getUniqueId())) return false;
        if (entity instanceof Player && ((Player) entity).getGameMode() == GameMode.SPECTATOR) return false;
        if (entity.isDead() || !entity.isValid()) return false;
        return !GeneralMethods.isRegionProtectedFromBuild(this, entity.getLocation());
    }

    private static Vector center(LivingEntity e) {
        return e.getLocation().toVector().add(new Vector(0, e.getHeight() * 0.5, 0));
    }

    private void startFlight() {
        try {
            wingsHeld = true;
            GLIDING.add(player.getUniqueId());
            PROTECTED_UNTIL.put(player.getUniqueId(), Long.MAX_VALUE);

            wasSneaking = player.isSneaking();
            startY = player.getLocation().getY();

            player.setGliding(true);

            Vector look = player.getLocation().getDirection();
            Vector drift = new Vector(look.getX(), 0, look.getZ());
            if (drift.lengthSquared() > 1.0E-4) {
                drift.normalize().multiply(0.12);
            } else {
                drift = new Vector(0, 0, 0);
            }
            flightVelocity = new Vector(drift.getX(), ascentSpeed * 0.8, drift.getZ());
            player.setVelocity(flightVelocity.clone());

            World world = player.getWorld();
            Location loc = player.getLocation().add(0, 0.4, 0);
            Vector origin = player.getLocation().toVector().add(new Vector(0, 0.1, 0));

            world.spawnParticle(Particle.EXPLOSION_LARGE, loc, 3, 0.8, 0.4, 0.8, 0.1);
            world.spawnParticle(Particle.FLAME, loc, 50, 1.0, 0.6, 1.0, 0.2);
            world.spawnParticle(Particle.FLASH, loc, 1, 0, 0, 0, 0);
            waves.add(new Wave(origin.clone(), 0.8, 7.0, 0));
            waves.add(new Wave(origin.clone(), 0.55, 5.5, 3));

            world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.8f);
            world.playSound(loc, Sound.ITEM_TOTEM_USE, 1.4f, 0.8f);
            world.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.6f, 0.8f);

            long total = ascentTicksEstimate() * 50L + soarDuration + diveMaxTicks * 50L + 2000L;
            player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, (int) (total / 50), 0, false, false));

            updraft(0);
        } catch (Exception e) {
            remove();
        }
    }

    private long ascentTicksEstimate() {
        return (long) Math.ceil(ascentHeight / Math.max(0.2, ascentSpeed * 0.6)) + 10;
    }

    private void updraft(int pulse) {
        World world = player.getWorld();
        Vector at = player.getLocation().toVector();

        for (Entity entity : GeneralMethods.getEntitiesAroundPoint(player.getLocation(), updraftRadius)) {
            if (!isValidVictim(entity)) continue;

            LivingEntity victim = (LivingEntity) entity;
            if (pulse == 0) {
                DamageHandler.damageEntity(victim, updraftDamage, this);
                victim.setFireTicks(80);
            }

            Vector out = victim.getLocation().toVector().subtract(at);
            out.setY(0);
            if (out.lengthSquared() > 1.0E-6) {
                out.normalize().multiply(0.12);
            } else {
                out = new Vector(0, 0, 0);
            }
            victim.setVelocity(new Vector(out.getX(), 1.0, out.getZ()));
            victim.setFallDistance(0);

            Location l = victim.getLocation().add(0, 1, 0);
            world.spawnParticle(Particle.FLAME, l, 10, 0.3, 0.4, 0.3, 0.08);
        }
    }

    @Override
    public void progress() {
        if (player == null || !player.isOnline() || player.isDead()) {
            remove();
            return;
        }

        ticks++;
        long now = System.currentTimeMillis();

        if (phase != Phase.AFTERMATH && now - startTime > maxTotalMillis) {
            impact();
            return;
        }

        if (phase == Phase.AFTERMATH) {
            progressAftermath();
            return;
        }

        player.setFallDistance(0);
        if (!player.isGliding() && ticks <= 400) {
            player.setGliding(true);
        }

        if (phase == Phase.ASCEND) {
            progressAscend();
        } else if (phase == Phase.SOAR) {
            progressSoar(now);
        } else if (phase == Phase.DIVE) {
            progressDive();
        }

        if (phase != Phase.AFTERMATH) {
            updateWaves(player.getWorld());
        }
    }

    private void progressAscend() {
        phaseTicks++;
        World world = player.getWorld();
        Location loc = player.getLocation();

        double gained = loc.getY() - startY;
        double frac = Math.min(1.0, gained / ascentHeight);

        Vector look = loc.getDirection();
        Vector drift = new Vector(look.getX(), 0, look.getZ());
        if (drift.lengthSquared() > 1.0E-4) {
            drift.normalize().multiply(0.12);
        } else {
            drift = new Vector(0, 0, 0);
        }

        double vy = ascentSpeed * Math.max(0.3, 1.0 - frac * frac * frac);
        Vector target = new Vector(drift.getX(), vy, drift.getZ());
        flightVelocity.add(target.subtract(flightVelocity).multiply(0.4));
        player.setVelocity(flightVelocity.clone());

        drawAscent(world, loc);

        if (phaseTicks == 8) {
            updraft(1);
        }
        if (phaseTicks % 6 == 0) {
            world.playSound(loc, Sound.ENTITY_PHANTOM_FLAP, 0.9f, 0.8f);
        }

        boolean ceiling = GeneralMethods.isSolid(loc.clone().add(0, 2.4, 0).getBlock());
        if (frac >= 0.98 || ceiling || phaseTicks > 50) {
            beginSoar();
        }
    }

    private void beginSoar() {
        phase = Phase.SOAR;
        phaseTicks = 0;
        soarStart = System.currentTimeMillis();
        wasSneaking = player.isSneaking();

        World world = player.getWorld();
        Location loc = player.getLocation().add(0, 1, 0);
        world.spawnParticle(Particle.FLAME, loc, 30, 1.0, 0.6, 1.0, 0.15);
        world.spawnParticle(Particle.END_ROD, loc, 12, 0.8, 0.5, 0.8, 0.12);
        world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.8f, 1.2f);
        world.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.2f, 0.8f);
    }

    private void progressSoar(long now) {
        phaseTicks++;
        World world = player.getWorld();
        Location loc = player.getLocation();
        long elapsed = now - soarStart;

        Vector look = loc.getDirection().normalize();
        Vector flat = new Vector(look.getX(), 0, look.getZ());
        if (flat.lengthSquared() > 1.0E-4) {
            flat.normalize().multiply(soarSpeed);
        } else {
            flat = new Vector(0, 0, 0);
        }
        double vy = Math.max(-0.25, Math.min(0.2, look.getY() * 0.3)) - 0.015;
        Vector target = new Vector(flat.getX(), vy, flat.getZ());
        flightVelocity.add(target.subtract(flightVelocity).multiply(0.18));
        player.setVelocity(flightVelocity.clone());

        if (phaseTicks % 3 == 0) {
            updateLock();
        }

        drawSoar(world, loc);
        drawReticle(world);

        boolean sneaking = player.isSneaking();
        boolean dive = sneaking && !wasSneaking;
        wasSneaking = sneaking;

        if (dive || elapsed > soarDuration) {
            startDive();
            return;
        }

        if (phaseTicks > 6 && player.isOnGround()) {
            impact();
        }
    }

    private void updateLock() {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Vector eyeVec = eye.toVector();

        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;

        for (Entity entity : GeneralMethods.getEntitiesAroundPoint(eye, lockRange)) {
            if (!isValidVictim(entity)) continue;

            LivingEntity living = (LivingEntity) entity;
            Vector to = center(living).subtract(eyeVec);
            double dist = to.length();
            if (dist < 3.0) continue;

            double dot = to.multiply(1.0 / dist).dot(dir);
            if (dot < lockCos) continue;

            double score = (1.0 - dot) + dist * 0.0015;
            if (lockTarget != null && lockTarget.getUniqueId().equals(living.getUniqueId())) {
                score -= 0.02;
            }
            if (score < bestScore) {
                bestScore = score;
                best = living;
            }
        }

        lockTarget = best;
        if (best == null) {
            Location t = GeneralMethods.getTargetedLocation(player, (int) lockRange);
            lockPoint = t != null ? t.toVector() : null;
        } else {
            lockPoint = null;
        }
    }

    public boolean isSoaring() {
        return phase == Phase.SOAR;
    }

    public void startDive() {
        if (phase != Phase.SOAR) return;

        phase = Phase.DIVE;
        phaseTicks = 0;

        World world = player.getWorld();
        Location loc = player.getLocation().add(0, 1, 0);

        updateLock();

        Vector toward = player.getEyeLocation().getDirection().normalize();
        flightVelocity = toward.multiply(0.8);

        world.spawnParticle(Particle.FLAME, loc, 30, 0.8, 0.8, 0.8, 0.2);
        world.spawnParticle(Particle.EXPLOSION_LARGE, loc, 1, 0, 0, 0, 0);
        world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 1.3f);
        world.playSound(loc, Sound.ITEM_TRIDENT_RIPTIDE_3, 1.6f, 1.2f);
    }

    private void progressDive() {
        phaseTicks++;
        World world = player.getWorld();
        Location loc = player.getLocation();
        Vector pos = loc.toVector().add(new Vector(0, 0.9, 0));

        if (lockTarget != null && (lockTarget.isDead() || !lockTarget.isValid() || !lockTarget.getWorld().equals(world))) {
            lockTarget = null;
        }

        Vector goal;
        if (lockTarget != null) {
            goal = center(lockTarget);
        } else if (lockPoint != null) {
            goal = lockPoint.clone();
        } else {
            Vector look = player.getEyeLocation().getDirection().normalize();
            look.setY(Math.min(look.getY(), -0.4));
            goal = pos.clone().add(look.normalize().multiply(30));
        }

        Vector desired = goal.clone().subtract(pos);
        double dist = desired.length();
        if (dist < 1.0E-4) {
            impact();
            return;
        }
        desired.multiply(diveSpeed / dist);

        double blend = lockTarget != null ? Math.min(0.9, diveHoming * 1.6) : diveHoming;
        flightVelocity.add(desired.subtract(flightVelocity).multiply(blend));
        player.setVelocity(flightVelocity.clone());
        player.setFallDistance(0);

        carryGrabbed();
        grabNearby();
        drawDive(world, loc);

        if (phaseTicks % 4 == 0) {
            world.playSound(loc, Sound.ITEM_TRIDENT_RIPTIDE_3, 0.9f, 1.4f);
        }

        Vector ahead = flightVelocity.clone();
        if (ahead.lengthSquared() > 1.0E-6) {
            ahead.normalize().multiply(1.5);
        }
        Block next = loc.clone().add(0, 0.9, 0).add(ahead).getBlock();
        boolean reachedTarget = lockTarget != null && center(lockTarget).distance(pos) < 2.2;

        if (GeneralMethods.isSolid(next) || reachedTarget || (phaseTicks > 4 && player.isOnGround())
                || player.isInWater() || phaseTicks > diveMaxTicks) {
            impact();
        }
    }

    private void grabNearby() {
        if (grabbed.size() >= maxGrabbed) return;

        World world = player.getWorld();
        for (Entity entity : GeneralMethods.getEntitiesAroundPoint(player.getLocation().add(0, 0.9, 0), grabRadius)) {
            if (grabbed.size() >= maxGrabbed) return;
            if (!isValidVictim(entity)) continue;

            LivingEntity victim = (LivingEntity) entity;
            if (grabbed.contains(victim)) continue;

            grabbed.add(victim);
            victim.setNoDamageTicks(0);
            DamageHandler.damageEntity(victim, grabDamage, this);
            victim.setFireTicks(100);

            Location l = victim.getLocation().add(0, victim.getHeight() * 0.5, 0);
            world.spawnParticle(Particle.EXPLOSION_LARGE, l, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.FLAME, l, 14, 0.3, 0.3, 0.3, 0.1);
            world.playSound(l, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.4f, 0.7f);
            world.playSound(l, Sound.ENTITY_BLAZE_HURT, 1.2f, 0.9f);
        }
    }

    private void carryGrabbed() {
        Iterator<LivingEntity> it = grabbed.iterator();
        while (it.hasNext()) {
            LivingEntity e = it.next();
            if (e.isDead() || !e.isValid() || !e.getWorld().equals(player.getWorld())) {
                it.remove();
                continue;
            }
            e.setVelocity(flightVelocity.clone());
            e.setFallDistance(0);
            if (ticks % 5 == 0) {
                e.setFireTicks(60);
            }
        }
    }

    private void drawAscent(World world, Location loc) {
        Vector body = loc.toVector().add(new Vector(0, 0.9, 0));
        Vector look = loc.getDirection();
        Vector flat = new Vector(look.getX(), 0, look.getZ());

        for (int i = 0; i < 6; i++) {
            double a = ticks * 0.6 + i * 1.05;
            Vector p = body.clone().add(new Vector(Math.cos(a) * 0.9, -i * 0.55, Math.sin(a) * 0.9));
            dust(world, p, i % 2 == 0 ? DUST_GOLD : DUST_ORANGE);
            if (i % 2 == 0) {
                jet(world, Particle.FLAME, p, new Vector(0, -1, 0), 0.12);
            }
        }
        puff(world, Particle.FLAME, loc.toVector(), 3, 0.25, 0.05);

        drawFlightWings(world, body.clone().add(new Vector(0, 0.35, 0)), flat, ticks * 0.75, 1.0, 0.0);
    }

    private void drawSoar(World world, Location loc) {
        Vector body = loc.toVector().add(new Vector(0, 0.9, 0));
        Vector dir = flightVelocity.clone();
        dir.setY(0);
        if (dir.lengthSquared() < 1.0E-4) {
            dir = loc.getDirection();
            dir.setY(0);
        }
        if (dir.lengthSquared() < 1.0E-4) {
            dir = new Vector(0, 0, 1);
        }
        dir.normalize();

        drawFlightWings(world, body.clone().add(dir.clone().multiply(-0.1)), dir, ticks * 0.22, 1.35, 0.0);

        Vector[] b = basisOf(dir);
        for (int ribbon = -1; ribbon <= 1; ribbon += 2) {
            for (int k = 1; k <= 6; k++) {
                double wave = Math.sin(ticks * 0.3 - k * 0.7 + ribbon);
                Vector p = body.clone()
                        .add(dir.clone().multiply(-0.6 - k * 0.45))
                        .add(b[1].clone().multiply(ribbon * (0.15 + k * 0.08) + wave * 0.1))
                        .add(new Vector(0, -0.1 * k * 0.3, 0));
                dust(world, p, k < 3 ? DUST_EMBER : (k < 5 ? DUST_ORANGE : DUST_GOLD));
            }
        }
        puff(world, Particle.FLAME, body.clone().add(dir.clone().multiply(-0.7)), 2, 0.12, 0.02);
    }

    private void drawReticle(World world) {
        Vector at;
        if (lockTarget != null) {
            at = lockTarget.getLocation().toVector().add(new Vector(0, 0.15, 0));
        } else if (lockPoint != null) {
            at = lockPoint.clone().add(new Vector(0, 0.2, 0));
        } else {
            return;
        }

        double pulse = 1.0 + 0.25 * Math.sin(ticks * 0.5);
        double radius = (lockTarget != null ? 1.3 : 1.8) * pulse;
        int points = 12;
        for (int i = 0; i < points; i++) {
            double a = i * Math.PI * 2 / points + ticks * 0.12;
            Vector p = at.clone().add(new Vector(Math.cos(a) * radius, 0, Math.sin(a) * radius));
            dust(world, p, lockTarget != null ? DUST_EMBER : DUST_GOLD);
        }
        if (lockTarget != null) {
            Vector head = center(lockTarget).add(new Vector(0, lockTarget.getHeight() * 0.5 + 0.7, 0));
            for (int i = 0; i < 3; i++) {
                dust(world, head.clone().add(new Vector(0, 0.2 * i, 0)), i == 2 ? DUST_WHITE : DUST_EMBER);
            }
        }
    }

    private void drawDive(World world, Location loc) {
        Vector body = loc.toVector().add(new Vector(0, 0.9, 0));
        Vector vel = flightVelocity.clone();
        if (vel.lengthSquared() < 1.0E-6) {
            vel = new Vector(0, -1, 0);
        }
        Vector dir = vel.normalize();
        Vector back = dir.clone().multiply(-1);
        Vector[] b = basisOf(dir);

        drawFlightWings(world, body.clone().add(back.clone().multiply(0.15)), dir, 0, 1.2, 0.85);

        for (int i = 1; i <= 8; i++) {
            double a = ticks * 0.9 + i * 0.8;
            double r = 0.15 + i * 0.07;
            Vector p = body.clone()
                    .add(back.clone().multiply(i * 0.7))
                    .add(b[1].clone().multiply(Math.cos(a) * r))
                    .add(b[2].clone().multiply(Math.sin(a) * r));
            dust(world, p, i < 3 ? DUST_WHITE : (i < 5 ? DUST_GOLD : (i < 7 ? DUST_ORANGE : DUST_EMBER)));
            if (i % 2 == 0) {
                puff(world, Particle.FLAME, p, 1, 0.05, 0.01);
            }
        }
        for (int i = 0; i < 3; i++) {
            jet(world, Particle.FLAME, body, back.clone().add(new Vector(ThreadLocalRandom.current().nextDouble(-0.2, 0.2),
                    ThreadLocalRandom.current().nextDouble(-0.2, 0.2), ThreadLocalRandom.current().nextDouble(-0.2, 0.2))), 0.4);
        }
        if (ticks % 2 == 0) {
            Vector ring = body.clone().add(back.clone().multiply(1.6));
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                dust(world, ring.clone().add(b[1].clone().multiply(Math.cos(a) * 1.1)).add(b[2].clone().multiply(Math.sin(a) * 1.1)), DUST_ORANGE);
            }
        }
    }

    private void updateWaves(World world) {
        Iterator<Wave> it = waves.iterator();
        while (it.hasNext()) {
            Wave w = it.next();
            if (w.delay > 0) {
                w.delay--;
                continue;
            }

            w.radius += w.speed;
            if (w.radius > w.max) {
                it.remove();
                continue;
            }

            drawWave(world, w);
        }
    }

    private void drawWave(World world, Wave w) {
        int n = (int) Math.max(14, Math.min(40, w.radius * 5));
        double fade = 1.0 - w.radius / w.max;

        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            Vector p = w.center.clone().add(new Vector(Math.cos(a) * w.radius, 0.15, Math.sin(a) * w.radius));

            if (i % 2 == 0) {
                jet(world, Particle.FLAME, p, new Vector(0, 1, 0), 0.2 + fade * 0.3);
            }
            if (i % 3 == 0) {
                dust(world, p.clone().add(new Vector(0, 0.4 + fade * 0.5, 0)), DUST_EMBER);
            }
        }
    }

    private double groundY(Location loc) {
        Location probe = loc.clone();
        for (int i = 0; i <= 5; i++) {
            if (GeneralMethods.isSolid(probe.getBlock())) {
                return probe.getBlockY() + 1.0;
            }
            probe.subtract(0, 1, 0);
        }
        return loc.getY();
    }

    private void impact() {
        if (phase == Phase.AFTERMATH) return;

        Location here = player.getLocation().clone();
        World world = here.getWorld();
        double gy = groundY(here);
        Location at = here.clone();
        if (gy < at.getY()) {
            at.setY(gy);
        }

        phase = Phase.AFTERMATH;
        aftermathTicks = 0;
        impact = at.toVector();

        Vector d = flightVelocity != null ? flightVelocity.clone() : new Vector(0, 0, 1);
        d.setY(0);
        if (d.lengthSquared() < 1.0E-4) {
            d = new Vector(0, 0, 1);
        }
        impactDir = d.normalize();

        cracks = new double[8];
        for (int i = 0; i < cracks.length; i++) {
            cracks[i] = i * Math.PI * 2 / cracks.length + ThreadLocalRandom.current().nextDouble(-0.25, 0.25);
        }

        endFlight();

        if (world != null) {
            world.spawnParticle(Particle.EXPLOSION_HUGE, at, 2, impactRadius * 0.15, 0.3, impactRadius * 0.15, 0);
            world.spawnParticle(Particle.FLAME, at, 90, impactRadius * 0.3, 0.5, impactRadius * 0.3, 0.25);
            world.spawnParticle(Particle.LAVA, at, 25, impactRadius * 0.3, 0.4, impactRadius * 0.3, 0);
            world.spawnParticle(Particle.FLASH, at, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, at, 10, impactRadius * 0.25, 0.4, impactRadius * 0.25, 0.04);

            Vector origin = impact.clone().add(new Vector(0, 0.1, 0));
            waves.add(new Wave(origin.clone(), impactRadius / 7.0, impactRadius + 2.0, 0));
            waves.add(new Wave(origin.clone(), impactRadius / 9.0, impactRadius + 2.0, 3));

            world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.6f);
            world.playSound(at, Sound.ENTITY_ENDER_DRAGON_HURT, 2.0f, 0.7f);
            world.playSound(at, Sound.BLOCK_ANVIL_LAND, 1.5f, 0.5f);
        }

        Set<UUID> slammed = new HashSet<>();
        for (LivingEntity g : grabbed) {
            slammed.add(g.getUniqueId());
        }

        for (Entity entity : GeneralMethods.getEntitiesAroundPoint(at, impactRadius)) {
            if (!isValidVictim(entity)) continue;

            LivingEntity victim = (LivingEntity) entity;
            double dist = victim.getLocation().distance(at);
            double mult = Math.max(0.35, 1.0 - dist / impactRadius);
            boolean wasGrabbed = slammed.contains(victim.getUniqueId());

            victim.setNoDamageTicks(0);
            DamageHandler.damageEntity(victim, impactDamage * mult + (wasGrabbed ? slamDamage : 0.0), this);
            victim.setFireTicks(120);

            Vector push = victim.getLocation().toVector().subtract(at.toVector());
            push.setY(0);
            if (push.lengthSquared() < 1.0E-6) {
                push = impactDir.clone();
            }
            push.normalize().multiply(impactKnockback * mult);
            push.setY(wasGrabbed ? 1.0 : Math.max(0.5, 0.4 + mult * 0.5));
            victim.setVelocity(push);
            victim.setFallDistance(0);
        }

        for (LivingEntity g : grabbed) {
            g.setFallDistance(0);
        }
        grabbed.clear();
    }

    private void progressAftermath() {
        World world = player.getWorld();
        aftermathTicks++;

        updateWaves(world);

        if (aftermathTicks <= 14 && cracks != null && aftermathTicks % 2 == 0) {
            double growth = Math.min(1.0, aftermathTicks / 6.0);
            for (double angle : cracks) {
                for (double r = 0.8; r <= impactRadius * growth; r += 1.1) {
                    double wobble = Math.sin(r * 2.1 + angle * 5) * 0.25;
                    Vector p = impact.clone().add(new Vector(
                            Math.cos(angle + wobble * 0.2) * r,
                            0.15,
                            Math.sin(angle + wobble * 0.2) * r));
                    dust(world, p, DUST_DARK);
                    puff(world, Particle.FLAME, p, 1, 0.08, 0.01);
                }
            }
        }

        if (aftermathTicks <= 28) {
            double scale = Math.min(1.0, aftermathTicks / 8.0) * 3.2;
            Vector pos = impact.clone().add(new Vector(0, 1.5 + aftermathTicks * 0.3, 0));
            BirdFx.drawBigBird(world, pos, impactDir.clone().add(new Vector(0, 0.35, 0)), aftermathTicks, scale);
        }

        if (aftermathTicks == 3) {
            world.playSound(impact.toLocation(world), Sound.ENTITY_ENDER_DRAGON_FLAP, 2.5f, 0.7f);
            world.playSound(impact.toLocation(world), Sound.ITEM_TOTEM_USE, 1.4f, 0.6f);
        }
        if (aftermathTicks == 10) {
            world.playSound(impact.toLocation(world), Sound.ENTITY_PHANTOM_SWOOP, 2.0f, 0.6f);
        }

        if (aftermathTicks <= zoneTicks) {
            drawZone(world);
            if (aftermathTicks % zonePulseTicks == 0) {
                pulseZone();
            }
        }

        if (aftermathTicks >= Math.max(zoneTicks, 30)) {
            remove();
        }
    }

    private void drawZone(World world) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double zr = impactRadius * 0.85;
        double fade = 1.0 - (double) aftermathTicks / Math.max(1, zoneTicks);

        int jets = 2 + (int) (4 * fade);
        for (int i = 0; i < jets; i++) {
            double a = random.nextDouble(Math.PI * 2);
            double r = Math.sqrt(random.nextDouble()) * zr;
            Vector p = impact.clone().add(new Vector(Math.cos(a) * r, 0.1, Math.sin(a) * r));
            jet(world, Particle.FLAME, p, new Vector(0, 1, 0), 0.18 + 0.15 * fade);
            if (i % 2 == 0) {
                dust(world, p.clone().add(new Vector(0, 0.5, 0)), DUST_ORANGE);
            }
        }

        if (aftermathTicks % 4 == 0) {
            int n = 20;
            for (int i = 0; i < n; i++) {
                double a = i * Math.PI * 2 / n;
                dust(world, impact.clone().add(new Vector(Math.cos(a) * zr, 0.1, Math.sin(a) * zr)), DUST_EMBER);
            }
        }
    }

    private void pulseZone() {
        Location center = impact.toLocation(player.getWorld());
        double zr = impactRadius * 0.85;

        for (Entity entity : GeneralMethods.getEntitiesAroundPoint(center, zr)) {
            if (!isValidVictim(entity)) continue;

            LivingEntity victim = (LivingEntity) entity;
            if (Math.abs(victim.getLocation().getY() - impact.getY()) > 3.0) continue;

            victim.setNoDamageTicks(0);
            DamageHandler.damageEntity(victim, zoneDamage, this);
            victim.setFireTicks(50);
        }
    }

    private void endFlight() {
        if (!wingsHeld) return;
        wingsHeld = false;

        if (player == null) return;

        try {
            GLIDING.remove(player.getUniqueId());
            player.setGliding(false);
            player.setFallDistance(0);
            player.setVelocity(new Vector(0, 0, 0));
        } catch (Exception ignored) {
        }

        PROTECTED_UNTIL.put(player.getUniqueId(), System.currentTimeMillis() + GUARD_AFTER_MS);
    }

    @Override
    public void remove() {
        endFlight();
        for (LivingEntity g : grabbed) {
            try {
                g.setFallDistance(0);
            } catch (Exception ignored) {
            }
        }
        grabbed.clear();
        waves.clear();
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
        return "PhoenixAscension";
    }

    @Override
    public Element getElement() {
        return PhoenixElement.PHOENIX;
    }

    @Override
    public Location getLocation() {
        return player != null ? player.getLocation() : null;
    }

    @Override
    public void load() {
        PhoenixElement.register();

        FileConfiguration config = ConfigManager.defaultConfig.get();
        config.addDefault(PATH + "Cooldown", 40000);
        config.addDefault(PATH + "Ascent.Height", 20.0);
        config.addDefault(PATH + "Ascent.Speed", 1.05);
        config.addDefault(PATH + "Ascent.UpdraftRadius", 4.5);
        config.addDefault(PATH + "Ascent.UpdraftDamage", 3.0);
        config.addDefault(PATH + "Soar.DurationMillis", 4500);
        config.addDefault(PATH + "Soar.Speed", 0.55);
        config.addDefault(PATH + "Soar.LockRange", 48.0);
        config.addDefault(PATH + "Soar.LockAngleDegrees", 24.0);
        config.addDefault(PATH + "Dive.Speed", 2.3);
        config.addDefault(PATH + "Dive.Homing", 0.22);
        config.addDefault(PATH + "Dive.GrabRadius", 2.4);
        config.addDefault(PATH + "Dive.MaxGrabbed", 3);
        config.addDefault(PATH + "Dive.GrabDamage", 3.0);
        config.addDefault(PATH + "Dive.MaxTicks", 80);
        config.addDefault(PATH + "Impact.Radius", 6.5);
        config.addDefault(PATH + "Impact.Damage", 13.0);
        config.addDefault(PATH + "Impact.SlamDamage", 6.0);
        config.addDefault(PATH + "Impact.Knockback", 1.5);
        config.addDefault(PATH + "Zone.Ticks", 80);
        config.addDefault(PATH + "Zone.DamagePerPulse", 1.5);
        config.addDefault(PATH + "Zone.PulseTicks", 10);
        config.addDefault(PATH + "MaxTotalMillis", 20000);
        config.options().copyDefaults(true);
        ConfigManager.defaultConfig.save();

        cooldown = config.getLong(PATH + "Cooldown");
        ascentHeight = Math.max(5.0, config.getDouble(PATH + "Ascent.Height"));
        ascentSpeed = Math.max(0.3, config.getDouble(PATH + "Ascent.Speed"));
        updraftRadius = Math.max(1.0, config.getDouble(PATH + "Ascent.UpdraftRadius"));
        updraftDamage = config.getDouble(PATH + "Ascent.UpdraftDamage");
        soarDuration = Math.max(1000, config.getLong(PATH + "Soar.DurationMillis"));
        soarSpeed = Math.max(0.1, config.getDouble(PATH + "Soar.Speed"));
        lockRange = Math.max(10.0, config.getDouble(PATH + "Soar.LockRange"));
        lockCos = Math.cos(Math.toRadians(Math.max(5.0, Math.min(80.0, config.getDouble(PATH + "Soar.LockAngleDegrees")))));
        diveSpeed = Math.max(0.8, config.getDouble(PATH + "Dive.Speed"));
        diveHoming = Math.max(0.05, Math.min(0.6, config.getDouble(PATH + "Dive.Homing")));
        grabRadius = Math.max(1.0, config.getDouble(PATH + "Dive.GrabRadius"));
        maxGrabbed = Math.max(0, config.getInt(PATH + "Dive.MaxGrabbed"));
        grabDamage = config.getDouble(PATH + "Dive.GrabDamage");
        diveMaxTicks = Math.max(20, config.getInt(PATH + "Dive.MaxTicks"));
        impactRadius = Math.max(2.0, config.getDouble(PATH + "Impact.Radius"));
        impactDamage = config.getDouble(PATH + "Impact.Damage");
        slamDamage = config.getDouble(PATH + "Impact.SlamDamage");
        impactKnockback = config.getDouble(PATH + "Impact.Knockback");
        zoneTicks = Math.max(0, config.getInt(PATH + "Zone.Ticks"));
        zoneDamage = config.getDouble(PATH + "Zone.DamagePerPulse");
        zonePulseTicks = Math.max(2, config.getInt(PATH + "Zone.PulseTicks"));
        maxTotalMillis = Math.max(8000, config.getLong(PATH + "MaxTotalMillis"));

        if (listener == null) {
            listener = new PhoenixAscensionListener();
            PhoenixAddonLoader.getPlugin().getServer().getPluginManager().registerEvents(listener, PhoenixAddonLoader.getPlugin());
        }
    }

    @Override
    public void stop() {
        for (UUID id : new HashSet<>(GLIDING)) {
            Player p = org.bukkit.Bukkit.getPlayer(id);
            if (p != null) p.setGliding(false);
        }
        GLIDING.clear();
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
        return "Охота феникса. Вы взмываете в небо огненным столбом, подбрасывая врагов вокруг, "
                + "затем парите над полем боя, пока прицел захватывает цель. По Shift вы складываете крылья и "
                + "пикируете кометой: на пути хватаете до трёх врагов и несёте их с собой, а в точке удара "
                + "взрыв раскидывает всех, захваченные получают удар о землю, а выжженная земля продолжает гореть.";
    }

    @Override
    public String getInstructions() {
        return "ЛКМ - взлёт. Наведите прицел на врага, пока парите. Shift или ЛКМ - пикирование. Приземление вне боя безопасно.";
    }

    public static class PhoenixAscensionListener implements Listener {

        @EventHandler
        public void onClick(PlayerInteractEvent event) {
            if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
            if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) return;

            Player player = event.getPlayer();
            if (!player.hasPermission("projectkorra.ability.PhoenixAscension")) return;

            BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
            if (bPlayer == null) return;

            PhoenixAscension existing = CoreAbility.getAbility(player, PhoenixAscension.class);
            if (existing != null) {
                if (existing.isSoaring()) {
                    existing.startDive();
                }
                return;
            }

            if (!bPlayer.canBend(CoreAbility.getAbility(PhoenixAscension.class))) return;

            new PhoenixAscension(player);
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onToggleGlide(EntityToggleGlideEvent event) {
            if (!(event.getEntity() instanceof Player)) return;
            if (event.isGliding()) return;

            if (GLIDING.contains(event.getEntity().getUniqueId())) {
                event.setCancelled(true);
            }
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            PhoenixAscension ascension = CoreAbility.getAbility(event.getPlayer(), PhoenixAscension.class);
            if (ascension != null) {
                ascension.remove();
            }
            GLIDING.remove(event.getPlayer().getUniqueId());
            PROTECTED_UNTIL.remove(event.getPlayer().getUniqueId());
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onDamage(EntityDamageEvent event) {
            if (!(event.getEntity() instanceof Player)) return;

            EntityDamageEvent.DamageCause cause = event.getCause();
            if (cause != EntityDamageEvent.DamageCause.FALL && cause != EntityDamageEvent.DamageCause.FLY_INTO_WALL) return;

            Long until = PROTECTED_UNTIL.get(event.getEntity().getUniqueId());
            if (until == null) return;

            if (until > System.currentTimeMillis()) {
                event.setCancelled(true);
            } else {
                PROTECTED_UNTIL.remove(event.getEntity().getUniqueId());
            }
        }
    }
}
