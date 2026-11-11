package com.myaddons.phoenixaddon;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.FireAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.util.DamageHandler;
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
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class PhoenixCrush extends FireAbility implements AddonAbility {

    private static final String PATH = "ExtraAbilities.Lucifer200134520.Fire.PhoenixCrush.";

    private static long cooldown;
    private static long chargeDuration;
    private static long explosionDuration;
    private static double maxRadius;
    private static double coreDamage;
    private static double waveDamage;
    private static double pullStrength;
    private static int fireTicksDuration;
    private static boolean allowTeleportation;

    private static final int MAX_PARTICLES_PER_TICK = 500;

    private static Listener listener;

    private enum Phase { CHARGING, IMPLODING, EXPLODING, DONE }

    private Phase phase;
    private long phaseStartTime;
    private Location epicenter;
    private Location ground;
    private Vector facing;
    private double chargeRadius;
    private Set<Entity> damagedEntities;
    private int soundStage;
    private int tick;

    public PhoenixCrush(Player player) {
        super(player);

        if (player == null || bPlayer == null) return;
        if (!player.hasPermission("projectkorra.ability.PhoenixCrush")) return;
        if (bPlayer.isOnCooldown(this)) return;
        if (hasAbility(player, PhoenixCrush.class)) return;
        if (GeneralMethods.isRegionProtectedFromBuild(this, player.getLocation())) return;

        this.phase = Phase.CHARGING;
        this.phaseStartTime = System.currentTimeMillis();
        this.epicenter = player.getLocation().clone().add(0, 2.2, 0);
        this.ground = findGround(player.getLocation());
        this.chargeRadius = 0;
        this.damagedEntities = new HashSet<>();
        this.soundStage = 0;
        this.tick = 0;

        Vector look = player.getLocation().getDirection().clone();
        look.setY(0);
        if (look.lengthSquared() < 1.0E-4) {
            look = new Vector(0, 0, 1);
        }
        this.facing = look.normalize();

        start();

        if (!isStarted() || isRemoved()) {
            return;
        }

        bPlayer.addCooldown(this, cooldown);

        World world = player.getWorld();
        if (world != null) {
            world.playSound(epicenter, Sound.BLOCK_BEACON_ACTIVATE, 2.5f, 0.5f);
            world.playSound(epicenter, Sound.ENTITY_WITHER_SPAWN, 2.0f, 0.8f);
            world.playSound(epicenter, Sound.ENTITY_BLAZE_SHOOT, 2.0f, 0.5f);
        }
    }

    @Override
    public void progress() {
        if (player == null || !player.isOnline() || player.isDead() || epicenter == null || epicenter.getWorld() == null) {
            remove();
            return;
        }

        tick++;
        long elapsed = System.currentTimeMillis() - phaseStartTime;

        switch (phase) {
            case CHARGING:
                if (elapsed >= chargeDuration) {
                    startImplosion();
                } else {
                    progressCharging(elapsed);
                }
                break;

            case IMPLODING:
                if (elapsed >= 1000) {
                    startExplosion();
                } else {
                    progressImploding(elapsed);
                }
                break;

            case EXPLODING:
                if (elapsed >= explosionDuration) {
                    phase = Phase.DONE;
                    remove();
                } else {
                    progressExploding(elapsed);
                }
                break;

            case DONE:
                remove();
                break;
        }
    }

    private Location findGround(Location from) {
        for (int i = 0; i <= 8; i++) {
            Block block = from.clone().subtract(0, i, 0).getBlock();
            if (block.getType().isSolid()) {
                return new Location(from.getWorld(), from.getX(), block.getY() + 1.0, from.getZ());
            }
        }
        return from.clone();
    }

    private static Vector sphere(int i, int n, double rotation) {
        double y = 1.0 - 2.0 * (i + 0.5) / n;
        double r = Math.sqrt(Math.max(0, 1.0 - y * y));
        double phi = i * 2.399963 + rotation;
        return new Vector(Math.cos(phi) * r, y, Math.sin(phi) * r);
    }

    private boolean isValidVictim(Entity entity) {
        if (!(entity instanceof LivingEntity)) return false;
        if (entity instanceof ArmorStand) return false;
        if (entity.getUniqueId().equals(player.getUniqueId())) return false;
        return !GeneralMethods.isRegionProtectedFromBuild(this, entity.getLocation());
    }

    private void progressCharging(long elapsed) {
        double progress = (double) elapsed / chargeDuration;
        chargeRadius = 2.0 + 7.0 * progress;

        ground = findGround(player.getLocation());
        epicenter = player.getLocation().clone().add(0, 2.2 + progress * 2.0 + Math.sin(tick * 0.2) * 0.12, 0);

        player.setVelocity(new Vector(0, 0, 0));
        player.setFallDistance(0);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 20, 3, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 20, 2, false, false));

        World world = epicenter.getWorld();

        drawRuneCircle(world, progress);
        drawCore(world, progress);
        drawStreams(world, progress);

        if (progress > 0.1) {
            PhoenixAscension.drawWings(world, player.getLocation().toVector().add(new Vector(0, 1.3, 0)),
                    facing, tick * 0.22, 0.4 + progress * 0.9, 0.4);
        }

        playChargingSounds(world, progress);

        pullNearbyEntities(chargeRadius * 1.5, pullStrength * 0.3);
    }

    private void drawRuneCircle(World world, double progress) {
        double r = 1.5 + 6.5 * progress;
        double rotation = tick * 0.05;
        Vector center = ground.toVector().add(new Vector(0, 0.1, 0));
        int n = (int) Math.max(24, r * 7);

        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            Vector outer = center.clone().add(new Vector(Math.cos(a) * r, 0, Math.sin(a) * r));
            PhoenixAscension.dust(world, outer, PhoenixAscension.DUST_GOLD);

            if (i % 2 == 0) {
                Vector inner = center.clone().add(new Vector(Math.cos(a) * r * 0.72, 0, Math.sin(a) * r * 0.72));
                PhoenixAscension.dust(world, inner, PhoenixAscension.DUST_EMBER);
            }
            if (i % 6 == 0) {
                PhoenixAscension.jet(world, Particle.FLAME, outer, new Vector(0, 1, 0), 0.2 + progress * 0.3);
            }
        }

        Vector[] tips = new Vector[8];
        for (int k = 0; k < 8; k++) {
            double a = rotation + k * Math.PI * 2 / 8;
            tips[k] = center.clone().add(new Vector(Math.cos(a) * r, 0, Math.sin(a) * r));
        }

        for (int k = 0; k < 8; k++) {
            Vector from = tips[k];
            Vector to = tips[(k + 3) % 8];
            double length = from.distance(to);
            int steps = (int) Math.max(2, length / 0.8);

            for (int s = 0; s <= steps; s++) {
                Vector p = from.clone().add(to.clone().subtract(from).multiply(s / (double) steps));
                PhoenixAscension.dust(world, p, PhoenixAscension.DUST_ORANGE);
                if (s % 3 == 0) {
                    PhoenixAscension.puff(world, Particle.FLAME, p, 1, 0.03, 0.005);
                }
            }
        }
    }

    private void drawCore(World world, double progress) {
        Vector c = epicenter.toVector();
        double r0 = 0.5 + progress * 0.6;
        double rotation = tick * 0.15;

        for (int i = 0; i < 12; i++) {
            Vector p = c.clone().add(sphere(i, 12, rotation).multiply(r0));
            PhoenixAscension.dust(world, p, PhoenixAscension.DUST_WHITE);
        }
        for (int i = 0; i < 16; i++) {
            Vector p = c.clone().add(sphere(i, 16, -rotation * 0.7).multiply(r0 * 1.5));
            PhoenixAscension.dust(world, p, PhoenixAscension.DUST_ORANGE);
        }
        for (int i = 0; i < 12; i++) {
            Vector p = c.clone().add(sphere(i, 12, rotation * 0.4).multiply(r0 * 2.1));
            PhoenixAscension.puff(world, Particle.FLAME, p, 1, 0.03, 0.005);
            if (i % 4 == 0) {
                PhoenixAscension.puff(world, Particle.LAVA, p, 1, 0.05, 0);
            }
        }

        if (progress > 0.3) {
            PhoenixAscension.puff(world, Particle.END_ROD, c, 2, r0, 0.02);
        }

        if (progress > 0.25) {
            double ringR = r0 * 2.8;
            for (int ring = 0; ring < 3; ring++) {
                double tilt = ring * Math.PI / 3;
                double spin = tick * (0.12 + ring * 0.04) * (ring % 2 == 0 ? 1 : -1);

                for (int i = 0; i < 12; i++) {
                    double a = spin + i * Math.PI * 2 / 12;
                    double x = Math.cos(a) * ringR;
                    double y = Math.sin(a) * ringR;
                    Vector p = c.clone().add(new Vector(
                            x * Math.cos(tilt),
                            y,
                            x * Math.sin(tilt)));
                    PhoenixAscension.dust(world, p, ring == 1 ? PhoenixAscension.DUST_GOLD : PhoenixAscension.DUST_EMBER);
                }
            }
        }
    }

    private void drawStreams(World world, double progress) {
        double r = 1.5 + 6.5 * progress;
        Vector base = ground.toVector().add(new Vector(0, 0.2, 0));
        Vector core = epicenter.toVector();

        for (int stream = 0; stream < 6; stream++) {
            double angle = stream * Math.PI * 2 / 6 + tick * 0.03;

            for (int k = 0; k < 8; k++) {
                double t = (k / 8.0 + tick * 0.04) % 1.0;
                double curve = angle + t * 2.2;
                double radius = r * (1.0 - t);

                Vector p = new Vector(
                        base.getX() + Math.cos(curve) * radius,
                        base.getY() + (core.getY() - base.getY()) * t * t,
                        base.getZ() + Math.sin(curve) * radius);

                PhoenixAscension.puff(world, Particle.FLAME, p, 1, 0.03, 0.005);
                if (k % 2 == 0) {
                    PhoenixAscension.dust(world, p, t < 0.5 ? PhoenixAscension.DUST_EMBER : PhoenixAscension.DUST_GOLD);
                }
            }
        }
    }

    private void playChargingSounds(World world, double progress) {
        int stage = (int) (progress * 5);
        if (stage > soundStage) {
            soundStage = stage;
            float pitch = 0.5f + stage * 0.2f;
            world.playSound(epicenter, Sound.BLOCK_BEACON_POWER_SELECT, 2.5f, pitch);
            world.playSound(epicenter, Sound.ENTITY_BLAZE_AMBIENT, 2.0f, pitch);
        }

        if (tick % 12 == 0) {
            world.playSound(epicenter, Sound.BLOCK_BEACON_AMBIENT, 1.8f, 0.5f + (float) progress);
        }
        if (tick % 7 == 0) {
            world.playSound(epicenter, Sound.BLOCK_FIRE_AMBIENT, 1.8f, 0.5f + (float) progress);
        }
    }

    private void startImplosion() {
        phase = Phase.IMPLODING;
        phaseStartTime = System.currentTimeMillis();
        ground = findGround(player.getLocation());

        World world = epicenter.getWorld();
        if (world != null) {
            world.playSound(epicenter, Sound.ENTITY_WITHER_DEATH, 2.5f, 0.5f);
            world.playSound(epicenter, Sound.ENTITY_ENDER_DRAGON_DEATH, 2.5f, 0.8f);
            world.playSound(epicenter, Sound.BLOCK_PORTAL_TRAVEL, 1.5f, 1.4f);

            world.spawnParticle(Particle.EXPLOSION_HUGE, epicenter, 3, 0.5, 0.5, 0.5, 0);
            world.spawnParticle(Particle.FLASH, epicenter, 4, 1, 1, 1, 0);
        }
    }

    private void progressImploding(long elapsed) {
        double progress = (double) elapsed / 1000.0;
        double eased = progress * progress;
        World world = epicenter.getWorld();
        Vector c = epicenter.toVector();

        player.setVelocity(new Vector(0, 0, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 20, 2, false, false));

        double shell = 11.0 * (1.0 - eased) + 0.6;
        double rotation = tick * 0.25;

        for (int i = 0; i < 70; i++) {
            Vector p = c.clone().add(sphere(i, 70, rotation).multiply(shell));
            Vector toCenter = c.clone().subtract(p);
            if (toCenter.lengthSquared() > 1.0E-6) {
                toCenter.normalize();
            }

            PhoenixAscension.jet(world, Particle.FLAME, p, toCenter, 0.35);
            if (i % 2 == 0) {
                PhoenixAscension.dust(world, p, PhoenixAscension.DUST_EMBER);
            }
            if (i % 5 == 0) {
                PhoenixAscension.jet(world, Particle.SOUL_FIRE_FLAME, p, toCenter, 0.4);
            }
        }

        double groundRadius = maxRadius * (1.0 - eased) + 1.0;
        Vector gc = ground.toVector().add(new Vector(0, 0.2, 0));
        int n = (int) Math.max(20, Math.min(60, groundRadius * 5));

        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n + rotation * 0.2;
            Vector p = gc.clone().add(new Vector(Math.cos(a) * groundRadius, 0, Math.sin(a) * groundRadius));
            Vector inward = new Vector(-Math.cos(a), 0.15, -Math.sin(a));
            PhoenixAscension.jet(world, Particle.FLAME, p, inward, 0.5);
            if (i % 2 == 0) {
                PhoenixAscension.dust(world, p.clone().add(new Vector(0, 0.3, 0)), PhoenixAscension.DUST_ORANGE);
            }
        }

        PhoenixAscension.puff(world, Particle.END_ROD, c, 8 + (int) (progress * 14), 0.5, 0.05);
        for (int i = 0; i < 10; i++) {
            PhoenixAscension.dust(world, c.clone().add(sphere(i, 10, rotation).multiply(0.7)), PhoenixAscension.DUST_WHITE);
        }

        if (progress > 0.75) {
            world.spawnParticle(Particle.FLASH, epicenter, 1, 1, 1, 1, 0);
        }

        pullNearbyEntities(maxRadius, pullStrength * (1 + progress * 2));

        if (progress > 0.5 && allowTeleportation) {
            for (Entity entity : GeneralMethods.getEntitiesAroundPoint(epicenter, 5.0)) {
                if (!isValidVictim(entity)) continue;
                if (damagedEntities.contains(entity)) continue;

                entity.teleport(epicenter);
                entity.setVelocity(new Vector(0, 0, 0));
            }
        }
    }

    private void startExplosion() {
        phase = Phase.EXPLODING;
        phaseStartTime = System.currentTimeMillis();

        World world = epicenter.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.EXPLOSION_HUGE, epicenter, 60, 0, 0, 0, 0);
            world.spawnParticle(Particle.FLASH, epicenter, 25, 0, 0, 0, 0);
            world.spawnParticle(Particle.END_ROD, epicenter, 250, 0, 0, 0, 0.6);

            world.playSound(epicenter, Sound.ENTITY_GENERIC_EXPLODE, 4.0f, 0.5f);
            world.playSound(epicenter, Sound.ENTITY_ENDER_DRAGON_GROWL, 4.0f, 0.6f);
            world.playSound(epicenter, Sound.ENTITY_WITHER_BREAK_BLOCK, 4.0f, 0.4f);
            world.playSound(epicenter, Sound.BLOCK_END_PORTAL_SPAWN, 3.0f, 0.8f);
        }

        damageCore();
    }

    private void progressExploding(long elapsed) {
        double progress = (double) elapsed / explosionDuration;
        double radius = maxRadius * progress;

        drawExplosionWave(radius, progress);
        damageWave(radius);
        createFirePillars(radius, progress);
    }

    private void drawExplosionWave(double radius, double progress) {
        World world = epicenter.getWorld();
        if (world == null) return;

        int points = Math.min((int) (radius * 8), MAX_PARTICLES_PER_TICK / 10);

        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2 / points) * i;
            Location point = epicenter.clone().add(
                    Math.cos(angle) * radius,
                    0,
                    Math.sin(angle) * radius
            );

            world.spawnParticle(Particle.FLAME, point, 6, 0.4, 0.4, 0.4, 0.25);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, point, 4, 0.3, 0.3, 0.3, 0.15);
            world.spawnParticle(Particle.EXPLOSION_LARGE, point, 1, 0, 0, 0, 0);

            if (i % 3 == 0) {
                world.spawnParticle(Particle.LAVA, point, 3, 0.6, 0.6, 0.6, 0);
            }
        }

        world.spawnParticle(Particle.END_ROD, epicenter, Math.min(60, MAX_PARTICLES_PER_TICK / 10), radius * 0.5, radius * 0.5, radius * 0.5, 0.4);
    }

    private void createFirePillars(double radius, double progress) {
        World world = epicenter.getWorld();
        if (world == null) return;

        if (Math.random() < 0.4) {
            double angle = Math.random() * Math.PI * 2;
            double distance = Math.random() * radius;

            Location pillarBase = epicenter.clone().add(
                    Math.cos(angle) * distance,
                    -2,
                    Math.sin(angle) * distance
            );

            for (double y = 0; y < 18; y += 0.5) {
                Location point = pillarBase.clone().add(0, y, 0);
                world.spawnParticle(Particle.FLAME, point, 4, 0.4, 0.1, 0.4, 0.08);
                if (y % 2 < 0.5) {
                    world.spawnParticle(Particle.LAVA, point, 2, 0.3, 0.1, 0.3, 0);
                }
            }
        }
    }

    private void pullNearbyEntities(double radius, double strength) {
        for (Entity entity : GeneralMethods.getEntitiesAroundPoint(epicenter, radius)) {
            if (!isValidVictim(entity)) continue;

            Vector pull = epicenter.toVector().subtract(entity.getLocation().toVector());
            if (pull.lengthSquared() < 1.0E-6) continue;

            pull.normalize().multiply(strength);
            entity.setVelocity(entity.getVelocity().add(pull));
        }
    }

    private void damageCore() {
        for (Entity entity : GeneralMethods.getEntitiesAroundPoint(epicenter, 10.0)) {
            if (!isValidVictim(entity)) continue;

            double distance = entity.getLocation().distance(epicenter);
            double damageMult = Math.max(0, 1.0 - (distance / 10.0));
            double finalDamage = coreDamage * damageMult;

            DamageHandler.damageEntity(entity, finalDamage, this);
            entity.setFireTicks(fireTicksDuration);
            damagedEntities.add(entity);

            Vector push = entity.getLocation().toVector().subtract(epicenter.toVector());
            if (push.lengthSquared() < 1.0E-6) {
                push = new Vector(0, 0, 0);
            } else {
                push.normalize();
            }
            push.setY(1.8);
            entity.setVelocity(push.multiply(3.5));
        }
    }

    private void damageWave(double radius) {
        for (Entity entity : GeneralMethods.getEntitiesAroundPoint(epicenter, radius + 6)) {
            if (!isValidVictim(entity)) continue;
            if (damagedEntities.contains(entity)) continue;

            Location loc = entity.getLocation();
            double dx = loc.getX() - epicenter.getX();
            double dz = loc.getZ() - epicenter.getZ();
            double dy = loc.getY() - epicenter.getY();
            double distance = Math.sqrt(dx * dx + dz * dz);

            if (dy < -10 || dy > 10) continue;

            if (distance > radius - 2.5 && distance < radius + 2.5) {
                DamageHandler.damageEntity(entity, waveDamage, this);
                entity.setFireTicks(fireTicksDuration);
                damagedEntities.add(entity);

                Vector push = new Vector(dx, 0, dz);
                if (push.lengthSquared() < 1.0E-6) {
                    push = new Vector(0, 0, 0);
                } else {
                    push.normalize();
                }
                push.setY(1.0);
                entity.setVelocity(push.multiply(2.5));
            }
        }
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
        return "PhoenixCrush";
    }

    @Override
    public Element getElement() {
        return PhoenixElement.PHOENIX;
    }

    @Override
    public Location getLocation() {
        return epicenter != null ? epicenter : (player != null ? player.getLocation() : null);
    }

    @Override
    public void load() {
        PhoenixElement.register();

        FileConfiguration config = ConfigManager.defaultConfig.get();
        config.addDefault(PATH + "Cooldown", 60000);
        config.addDefault(PATH + "ChargeDuration", 3000);
        config.addDefault(PATH + "ExplosionDuration", 2000);
        config.addDefault(PATH + "MaxRadius", 28.0);
        config.addDefault(PATH + "CoreDamage", 35.0);
        config.addDefault(PATH + "WaveDamage", 18.0);
        config.addDefault(PATH + "PullStrength", 2.5);
        config.addDefault(PATH + "FireTicksDuration", 350);
        config.addDefault(PATH + "AllowTeleportation", false);
        config.options().copyDefaults(true);
        ConfigManager.defaultConfig.save();

        cooldown = config.getLong(PATH + "Cooldown");
        chargeDuration = config.getLong(PATH + "ChargeDuration");
        explosionDuration = Math.max(500, config.getLong(PATH + "ExplosionDuration"));
        maxRadius = config.getDouble(PATH + "MaxRadius");
        coreDamage = config.getDouble(PATH + "CoreDamage");
        waveDamage = config.getDouble(PATH + "WaveDamage");
        pullStrength = config.getDouble(PATH + "PullStrength");
        fireTicksDuration = config.getInt(PATH + "FireTicksDuration");
        allowTeleportation = config.getBoolean(PATH + "AllowTeleportation");

        if (listener == null) {
            listener = new PhoenixCrushListener();
            PhoenixAddonLoader.getPlugin().getServer().getPluginManager().registerEvents(listener, PhoenixAddonLoader.getPlugin());
        }
    }

    @Override
    public void stop() {
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
        return "Под вами вспыхивает огненный круг, над головой рождается мини-звезда и стягивает к себе врагов. "
                + "Затем коллапс и чудовищный взрыв: ударная волна по земле и огромный феникс, взмывающий в небо.";
    }

    @Override
    public String getInstructions() {
        return "ЛКМ для создания. Заряжается 3 сек, затем взрывается.";
    }

    public static class PhoenixCrushListener implements Listener {

        @EventHandler
        public void onClick(PlayerInteractEvent event) {
            if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
            if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) return;

            Player player = event.getPlayer();
            if (!player.hasPermission("projectkorra.ability.PhoenixCrush")) return;

            BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
            if (bPlayer == null || !bPlayer.canBend(CoreAbility.getAbility(PhoenixCrush.class))) return;

            new PhoenixCrush(player);
        }
    }
}
