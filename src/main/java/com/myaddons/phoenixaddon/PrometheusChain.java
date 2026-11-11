package com.myaddons.phoenixaddon;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.FireAbility;
import com.projectkorra.projectkorra.ability.PassiveAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.event.AbilityStartEvent;
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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PrometheusChain extends FireAbility implements AddonAbility {

    private static final String PATH = "ExtraAbilities.Lucifer200134520.Fire.PrometheusChain.";

    private static long cooldown;
    private static double range;
    private static double chainSpeed;
    private static double grabDamage;

    private static double linkRadius;
    private static int maxTargets;
    private static int linkDelayTicks;
    private static long swingDuration;
    private static double holdDistance;
    private static double linkSpacing;
    private static double follow;

    private static double hitRadius;
    private static double minSwingSpeed;
    private static double explosionDamage;
    private static double explosionRadius;
    private static double explosionKnockback;
    private static int hitCooldownTicks;
    private static double heldDamagePerHit;
    private static double throwSpeed;

    private static final Particle.DustOptions FIRE_DUST =
            new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 145, 20), 1.3f);
    private static final Particle.DustOptions ORANGE_DUST =
            new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 195, 55), 1.2f);

    private static final Set<UUID> HELD_NOW = new HashSet<>();

    public static boolean isHeld(UUID id) {
        return HELD_NOW.contains(id);
    }

    private static Listener listener;

    private enum Phase { EXTENDING, SWINGING }

    private static class Held {
        final LivingEntity entity;
        final boolean hadAI;
        final boolean hadGravity;
        Vector pos;
        Vector prev;
        double speed;

        Held(LivingEntity entity) {
            this.entity = entity;
            this.hadAI = !(entity instanceof Player) && entity.hasAI();
            this.hadGravity = entity.hasGravity();
            this.pos = entity.getLocation().toVector().add(new Vector(0, entity.getHeight() * 0.5, 0));
            this.prev = this.pos.clone();
            this.speed = 0;
        }
    }

    private Phase phase;
    private Location origin;
    private Location chainEnd;
    private Vector direction;
    private double distanceTraveled;
    private Vector handle;

    private final List<Held> held = new ArrayList<>();
    private final Map<UUID, Integer> hitUntil = new HashMap<>();
    private int tick;
    private int linkTimer;
    private boolean gathering;
    private long swingStart;

    public PrometheusChain(Player player) {
        super(player);

        if (player == null || bPlayer == null) return;
        if (!player.hasPermission("projectkorra.ability.PrometheusChain")) return;
        if (bPlayer.isOnCooldown(this)) return;
        if (hasAbility(player, PrometheusChain.class)) return;
        if (GeneralMethods.isRegionProtectedFromBuild(this, player.getLocation())) return;

        this.phase = Phase.EXTENDING;
        this.origin = player.getEyeLocation().clone();
        this.chainEnd = origin.clone();
        this.direction = origin.getDirection().normalize();
        this.distanceTraveled = 0;
        this.tick = 0;

        start();

        if (!isStarted() || isRemoved()) return;

        bPlayer.addCooldown(this, cooldown);

        World world = player.getWorld();
        world.playSound(origin, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.6f);
        world.playSound(origin, Sound.ITEM_TRIDENT_THROW, 1.5f, 0.8f);
        world.playSound(origin, Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.5f);
    }

    @Override
    public void progress() {
        if (player == null || !player.isOnline() || player.isDead()) {
            remove();
            return;
        }

        tick++;
        origin = player.getEyeLocation().clone();

        if (phase == Phase.EXTENDING) {
            progressExtending();
        } else {
            progressSwinging();
        }
    }

    private Location handLocation() {
        Vector dir = origin.getDirection().normalize();
        Vector right = dir.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1.0E-4) {
            right = new Vector(1, 0, 0);
        } else {
            right.normalize();
        }
        return origin.clone().add(right.multiply(0.35)).add(dir.multiply(0.45)).add(0, -0.4, 0);
    }

    private void progressExtending() {
        double step = Math.min(0.9, chainSpeed);
        int steps = Math.max(1, (int) Math.ceil(chainSpeed / step));
        double each = chainSpeed / steps;

        for (int s = 0; s < steps; s++) {
            chainEnd.add(direction.clone().multiply(each));
            distanceTraveled += each;

            if (distanceTraveled > range || chainEnd.getWorld() == null) {
                drawLine(handLocation(), chainEnd);
                remove();
                return;
            }

            if (GeneralMethods.isSolid(chainEnd.getBlock()) || GeneralMethods.isRegionProtectedFromBuild(this, chainEnd)) {
                World world = chainEnd.getWorld();
                world.spawnParticle(Particle.FLAME, chainEnd, 12, 0.2, 0.2, 0.2, 0.08);
                world.playSound(chainEnd, Sound.BLOCK_CHAIN_PLACE, 1.0f, 0.6f);
                drawLine(handLocation(), chainEnd);
                remove();
                return;
            }

            for (Entity entity : GeneralMethods.getEntitiesAroundPoint(chainEnd, 1.6)) {
                if (!isValidVictim(entity)) continue;

                drawLine(handLocation(), chainEnd);
                capture((LivingEntity) entity);
                phase = Phase.SWINGING;
                swingStart = System.currentTimeMillis();
                gathering = held.size() < maxTargets;
                linkTimer = 0;
                return;
            }
        }

        drawLine(handLocation(), chainEnd);
    }

    private void capture(LivingEntity target) {
        if (!HELD_NOW.add(target.getUniqueId())) return;

        Held h = new Held(target);
        held.add(h);

        if (!(target instanceof Player)) {
            target.setAI(false);
        }
        target.setGravity(false);

        DamageHandler.damageEntity(target, grabDamage, this);
        target.setFireTicks(100);

        World world = target.getWorld();
        Location loc = target.getLocation().add(0, target.getHeight() * 0.5, 0);

        world.spawnParticle(Particle.EXPLOSION_LARGE, loc, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.FLAME, loc, 24, 0.4, 0.4, 0.4, 0.12);
        world.spawnParticle(Particle.LAVA, loc, 4, 0.3, 0.3, 0.3, 0);

        world.playSound(loc, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.6f, 0.6f);
        world.playSound(loc, Sound.BLOCK_CHAIN_PLACE, 1.6f, 0.7f);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.4f);
    }

    private boolean captureNext() {
        Held last = held.get(held.size() - 1);
        Location from = last.entity.getLocation().add(0, last.entity.getHeight() * 0.5, 0);

        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;

        for (Entity entity : GeneralMethods.getEntitiesAroundPoint(from, linkRadius)) {
            if (!isValidVictim(entity)) continue;
            if (heldOf(entity) != null) continue;

            double d = entity.getLocation().distance(last.entity.getLocation());
            if (d <= linkRadius && d < bestDist) {
                bestDist = d;
                best = (LivingEntity) entity;
            }
        }

        if (best == null) return false;

        Location to = best.getLocation().add(0, best.getHeight() * 0.5, 0);
        drawLine(from, to);
        from.getWorld().playSound(from, Sound.ENTITY_BLAZE_SHOOT, 1.2f, 0.6f);

        capture(best);
        return true;
    }

    private void progressSwinging() {
        if (System.currentTimeMillis() - swingStart > swingDuration) {
            remove();
            return;
        }

        Iterator<Held> it = held.iterator();
        while (it.hasNext()) {
            Held h = it.next();
            if (h.entity.isDead() || !h.entity.isValid() || !h.entity.getWorld().equals(player.getWorld())) {
                restoreState(h);
                it.remove();
            }
        }

        if (held.isEmpty()) {
            remove();
            return;
        }

        if (gathering) {
            linkTimer++;
            if (linkTimer >= linkDelayTicks) {
                linkTimer = 0;
                if (held.size() >= maxTargets || !captureNext()) {
                    gathering = false;
                }
            }
        }

        updateHeld();
        applyPositions();
        drawRope();
        checkCollisions();

    }

    private void updateHeld() {
        Vector look = origin.getDirection();
        if (look.lengthSquared() < 1.0E-6) {
            look = new Vector(0, 0, 1);
        }
        look.normalize();

        handle = handLocation().toVector();

        double total = holdDistance + Math.max(0, held.size() - 1) * linkSpacing;
        double free = total;
        for (double d = 1.0; d <= total; d += 0.5) {
            if (GeneralMethods.isSolid(origin.clone().add(look.clone().multiply(d)).getBlock())) {
                free = Math.max(1.2, d - 0.7);
                break;
            }
        }
        double scale = Math.min(1.0, free / total);

        World world = origin.getWorld();
        Vector base = origin.toVector();
        double maxStep = 2.5;

        for (int i = 0; i < held.size(); i++) {
            Held h = held.get(i);
            double d = (holdDistance + i * linkSpacing) * scale;
            Vector target = base.clone().add(look.clone().multiply(d)).add(new Vector(0, -0.3, 0));

            h.prev = h.pos.clone();
            Vector move = target.subtract(h.pos).multiply(follow);
            double len = move.length();
            if (len > maxStep) {
                move.multiply(maxStep / len);
            }
            h.pos.add(move);

            if (world != null && GeneralMethods.isSolid(h.pos.toLocation(world).getBlock())) {
                h.pos = h.prev.clone();
            }
            h.speed = h.pos.distance(h.prev);
        }
    }

    private void applyPositions() {
        World world = origin.getWorld();
        if (world == null) return;

        for (Held h : held) {
            LivingEntity e = h.entity;
            Location cur = e.getLocation();
            Vector feet = h.pos.clone().subtract(new Vector(0, e.getHeight() * 0.5, 0));

            if (e instanceof Player) {
                Vector pull = feet.subtract(cur.toVector());
                double pullLength = pull.length();
                if (pullLength > 2.5) {
                    pull.multiply(2.5 / pullLength);
                }
                e.setVelocity(pull);
            } else {
                Location loc = feet.toLocation(world);
                loc.setYaw(cur.getYaw());
                loc.setPitch(cur.getPitch());
                e.setVelocity(new Vector(0, 0, 0));
                e.teleport(loc);
            }

            e.setFallDistance(0);
            if (tick % 10 == 0) {
                e.setFireTicks(60);
            }
        }
    }

    private void checkCollisions() {
        List<Vector> points = new ArrayList<>();
        List<Double> speeds = new ArrayList<>();

        for (int i = 0; i < held.size(); i++) {
            Held h = held.get(i);
            points.add(h.pos);
            speeds.add(h.speed);

            if (i + 1 < held.size()) {
                Held next = held.get(i + 1);
                points.add(h.pos.clone().add(next.pos).multiply(0.5));
                speeds.add((h.speed + next.speed) * 0.5);
            }
        }

        for (int i = 0; i < points.size(); i++) {
            if (speeds.get(i) < minSwingSpeed) continue;

            Location p = points.get(i).toLocation(origin.getWorld());
            if (p.distance(origin) < 1.6) continue;

            for (Entity entity : GeneralMethods.getEntitiesAroundPoint(p, hitRadius)) {
                if (!isValidVictim(entity)) continue;
                if (heldOf(entity) != null) continue;
                if (hitUntil.getOrDefault(entity.getUniqueId(), 0) > tick) continue;

                explode(entity.getLocation().add(0, entity.getHeight() * 0.5, 0), (LivingEntity) entity);
                return;
            }
        }
    }

    private void explode(Location at, LivingEntity directHit) {
        World world = at.getWorld();
        if (world == null) return;

        world.spawnParticle(Particle.EXPLOSION_HUGE, at, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.FLAME, at, 60, explosionRadius * 0.4, explosionRadius * 0.4, explosionRadius * 0.4, 0.2);
        world.spawnParticle(Particle.LAVA, at, 10, explosionRadius * 0.3, explosionRadius * 0.3, explosionRadius * 0.3, 0);
        world.spawnParticle(Particle.REDSTONE, at, 25, explosionRadius * 0.4, explosionRadius * 0.4, explosionRadius * 0.4, 0, FIRE_DUST);
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8;
            PhoenixAscension.dust(world, at.toVector().add(new Vector(Math.cos(a) * explosionRadius * 0.8, 0.1, Math.sin(a) * explosionRadius * 0.8)),
                    i % 2 == 0 ? PhoenixAscension.DUST_GOLD : PhoenixAscension.DUST_ORANGE);
        }

        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 2.5f, 0.8f);
        world.playSound(at, Sound.ENTITY_BLAZE_HURT, 1.5f, 0.6f);
        world.playSound(at, Sound.BLOCK_ANVIL_LAND, 1.2f, 0.5f);

        for (Entity entity : GeneralMethods.getEntitiesAroundPoint(at, explosionRadius)) {
            if (!isValidVictim(entity)) continue;
            if (heldOf(entity) != null) continue;

            LivingEntity victim = (LivingEntity) entity;
            double dmg;
            if (victim.equals(directHit)) {
                dmg = explosionDamage;
            } else {
                double dist = victim.getLocation().distance(at);
                dmg = explosionDamage * 0.6 * Math.max(0, 1.0 - dist / explosionRadius);
            }

            if (dmg > 0) {
                victim.setNoDamageTicks(0);
                DamageHandler.damageEntity(victim, dmg, this);
            }
            victim.setFireTicks(100);

            Vector push = victim.getLocation().toVector().subtract(at.toVector());
            if (push.lengthSquared() < 1.0E-6) {
                push = origin.getDirection().clone();
            }
            push.normalize().multiply(explosionKnockback);
            push.setY(Math.max(0.35, push.getY()));
            victim.setVelocity(push);

            hitUntil.put(victim.getUniqueId(), tick + hitCooldownTicks);
        }

        if (heldDamagePerHit > 0) {
            for (Held h : held) {
                h.entity.setNoDamageTicks(0);
                DamageHandler.damageEntity(h.entity, heldDamagePerHit, this);
            }
        }
    }

    private boolean isValidVictim(Entity e) {
        if (!(e instanceof LivingEntity)) return false;
        if (e instanceof ArmorStand) return false;
        if (e.getUniqueId().equals(player.getUniqueId())) return false;
        if (e instanceof Player && ((Player) e).getGameMode() == GameMode.SPECTATOR) return false;
        if (e.isDead() || !e.isValid()) return false;
        if (heldOf(e) == null && HELD_NOW.contains(e.getUniqueId())) return false;
        if (PhoenixSnare.isCaught(e.getUniqueId())) return false;
        return !GeneralMethods.isRegionProtectedFromBuild(this, e.getLocation());
    }

    private Held heldOf(Entity e) {
        for (Held h : held) {
            if (h.entity.getUniqueId().equals(e.getUniqueId())) return h;
        }
        return null;
    }

    private int linkCounter;

    private void drawLinks(World world, Vector from, Vector to, double waveAmount) {
        Vector seg = to.clone().subtract(from);
        double len = seg.length();
        if (len < 0.05) return;

        Vector dir = seg.clone().multiply(1.0 / len);
        Vector side = dir.clone().crossProduct(new Vector(0, 1, 0));
        if (side.lengthSquared() < 1.0E-4) {
            side = new Vector(1, 0, 0);
        } else {
            side.normalize();
        }
        Vector lift = side.clone().crossProduct(dir).normalize();

        int links = Math.max(1, (int) Math.round(len / 0.55));
        for (int l = 0; l < links; l++) {
            double t = (l + 0.5) / links;
            Vector c = from.clone().add(seg.clone().multiply(t));
            if (waveAmount > 0) {
                c.add(lift.clone().multiply(Math.sin(tick * 0.5 + (linkCounter + l) * 0.6) * waveAmount));
            }

            Vector off = (((linkCounter + l) & 1) == 0 ? side : lift).clone().multiply(0.1);
            world.spawnParticle(Particle.REDSTONE, c.getX() + off.getX(), c.getY() + off.getY(), c.getZ() + off.getZ(), 1, 0, 0, 0, 0, FIRE_DUST);
            world.spawnParticle(Particle.REDSTONE, c.getX() - off.getX(), c.getY() - off.getY(), c.getZ() - off.getZ(), 1, 0, 0, 0, 0, ORANGE_DUST);
            if ((linkCounter + l) % 4 == 0) {
                world.spawnParticle(Particle.FLAME, c.getX(), c.getY(), c.getZ(), 1, 0, 0, 0, 0.002);
            }
        }
        linkCounter += links;
    }

    private void drawRope() {
        World world = origin.getWorld();
        if (world == null || handle == null) return;

        linkCounter = 0;
        Vector prev = handle;
        for (Held h : held) {
            drawLinks(world, prev, h.pos, 0.0);
            prev = h.pos;
        }

        Held tip = held.get(held.size() - 1);
        Location tipLoc = tip.pos.toLocation(world);
        if (tip.speed >= minSwingSpeed) {
            world.spawnParticle(Particle.FLAME, tipLoc, 4, 0.15, 0.15, 0.15, 0.04);
            if (tick % 6 == 0) {
                world.playSound(tipLoc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.6f);
                world.playSound(tipLoc, Sound.BLOCK_CHAIN_PLACE, 0.8f, 1.4f);
            }
        }
    }

    private void drawLine(Location start, Location end) {
        World world = start.getWorld();
        if (world == null || !world.equals(end.getWorld())) return;

        linkCounter = 0;
        drawLinks(world, start.toVector(), end.toVector(), 0.05);
    }

    private void restoreState(Held h) {
        LivingEntity e = h.entity;
        if (e == null) return;
        HELD_NOW.remove(e.getUniqueId());
        try {
            if (!(e instanceof Player)) {
                e.setAI(h.hadAI);
            }
            e.setGravity(e instanceof Player || h.hadGravity);
            e.setFallDistance(0);
        } catch (Throwable ignored) {
        }
    }

    private void releaseAll(boolean throwForward) {
        Vector throwDir = player != null ? player.getEyeLocation().getDirection().normalize() : null;

        for (Held h : held) {
            LivingEntity e = h.entity;
            if (e == null) continue;

            HELD_NOW.remove(e.getUniqueId());

            try {
                if (!(e instanceof Player)) {
                    e.setAI(h.hadAI);
                }
                e.setGravity(e instanceof Player || h.hadGravity);
                e.setFallDistance(0);

                if (throwForward && throwDir != null) {
                    e.setVelocity(throwDir.clone().multiply(throwSpeed));
                } else {
                    e.setVelocity(new Vector(0, 0, 0));
                }
            } catch (Throwable ignored) {
            }
        }
        held.clear();
    }

    public void throwAndFinish() {
        if (phase != Phase.SWINGING) return;

        if (player != null) {
            World world = player.getWorld();
            world.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.6f, 0.8f);
            world.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 1.6f, 0.6f);
        }
        releaseAll(true);
        remove();
    }

    @Override
    public void remove() {
        releaseAll(false);
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
        return "PrometheusChain";
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
        config.addDefault(PATH + "Cooldown", 35000);
        config.addDefault(PATH + "Range", 20.0);
        config.addDefault(PATH + "ChainSpeed", 2.5);
        config.addDefault(PATH + "GrabDamage", 3.0);

        config.addDefault(PATH + "LinkRadius", 7.0);
        config.addDefault(PATH + "MaxTargets", 6);
        config.addDefault(PATH + "LinkDelayTicks", 4);
        config.addDefault(PATH + "SwingDuration", 12000);
        config.addDefault(PATH + "HoldDistance", 3.5);
        config.addDefault(PATH + "LinkSpacing", 2.2);
        config.addDefault(PATH + "Follow", 0.75);

        config.addDefault(PATH + "HitRadius", 1.6);
        config.addDefault(PATH + "MinSwingSpeed", 0.5);
        config.addDefault(PATH + "ExplosionDamage", 14.0);
        config.addDefault(PATH + "ExplosionRadius", 3.5);
        config.addDefault(PATH + "ExplosionKnockback", 1.2);
        config.addDefault(PATH + "HitCooldownTicks", 15);
        config.addDefault(PATH + "HeldDamagePerHit", 1.0);
        config.addDefault(PATH + "ThrowSpeed", 1.8);
        config.options().copyDefaults(true);
        ConfigManager.defaultConfig.save();

        cooldown = config.getLong(PATH + "Cooldown");
        range = config.getDouble(PATH + "Range");
        chainSpeed = config.getDouble(PATH + "ChainSpeed");
        grabDamage = config.getDouble(PATH + "GrabDamage");

        linkRadius = config.getDouble(PATH + "LinkRadius");
        maxTargets = Math.max(1, config.getInt(PATH + "MaxTargets"));
        linkDelayTicks = Math.max(1, config.getInt(PATH + "LinkDelayTicks"));
        swingDuration = config.getLong(PATH + "SwingDuration");
        holdDistance = config.getDouble(PATH + "HoldDistance");
        linkSpacing = Math.max(0.5, config.getDouble(PATH + "LinkSpacing"));
        follow = Math.max(0.2, Math.min(1.0, config.getDouble(PATH + "Follow")));

        hitRadius = config.getDouble(PATH + "HitRadius");
        minSwingSpeed = config.getDouble(PATH + "MinSwingSpeed");
        explosionDamage = config.getDouble(PATH + "ExplosionDamage");
        explosionRadius = config.getDouble(PATH + "ExplosionRadius");
        explosionKnockback = config.getDouble(PATH + "ExplosionKnockback");
        hitCooldownTicks = Math.max(1, config.getInt(PATH + "HitCooldownTicks"));
        heldDamagePerHit = config.getDouble(PATH + "HeldDamagePerHit");
        throwSpeed = config.getDouble(PATH + "ThrowSpeed");

        if (listener == null) {
            listener = new PrometheusChainListener();
            PhoenixAddonLoader.getPlugin().getServer().getPluginManager().registerEvents(listener, PhoenixAddonLoader.getPlugin());
        }
    }

    @Override
    public void stop() {
        for (PrometheusChain chain : new ArrayList<>(CoreAbility.getAbilities(PrometheusChain.class))) {
            chain.remove();
        }
        HELD_NOW.clear();
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
        return "Огненная цепь хватает существо, а затем тянется к ближайшим и собирает из них живой канат. "
                + "Захваченные висят на жёстко удерживаемом расстоянии перед вами и следуют за прицелом. Размахивайте ими камерой как булавой: при ударе по врагу канат взрывается и наносит огромный урон. "
                + "Пока канат у вас в руках, другие способности недоступны.";
    }

    @Override
    public String getInstructions() {
        return "ЛКМ - выпустить цепь. После захвата управляйте канатом камерой как хлыстом: резко махните, и конец каната ударит с щелчком. "
                + "Shift - бросить захваченных вперёд и закончить.";
    }

    public static class PrometheusChainListener implements Listener {

        @EventHandler
        public void onClick(PlayerInteractEvent event) {
            if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
            if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) return;

            Player player = event.getPlayer();
            if (!player.hasPermission("projectkorra.ability.PrometheusChain")) return;

            if (CoreAbility.hasAbility(player, PrometheusChain.class)) return;

            BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
            if (bPlayer == null || !bPlayer.canBend(CoreAbility.getAbility(PrometheusChain.class))) return;

            new PrometheusChain(player);
        }

        @EventHandler
        public void onSneak(PlayerToggleSneakEvent event) {
            if (!event.isSneaking()) return;

            PrometheusChain chain = CoreAbility.getAbility(event.getPlayer(), PrometheusChain.class);
            if (chain != null) {
                chain.throwAndFinish();
            }
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onAbilityStart(AbilityStartEvent event) {
            if (!(event.getAbility() instanceof CoreAbility)) return;

            CoreAbility started = (CoreAbility) event.getAbility();
            if (started instanceof PrometheusChain) return;
            if (started instanceof PassiveAbility) return;

            Player owner = started.getPlayer();
            if (owner == null) return;

            if (CoreAbility.hasAbility(owner, PrometheusChain.class)) {
                event.setCancelled(true);
            }
        }
    }
}
