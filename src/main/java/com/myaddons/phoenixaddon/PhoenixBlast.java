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
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;

public class PhoenixBlast extends FireAbility implements AddonAbility {

    private static final String PATH = "ExtraAbilities.Lucifer200134520.Fire.PhoenixBlast.";

    private static long cooldown;
    private static double damage;
    private static double range;
    private static double speed;
    private static double hitRadius;
    private static double explosionRadius;
    private static double explosionDamage;
    private static double homing;
    private static double homingRange;
    private static double birdScale;

    private static Listener listener;

    private Location location;
    private Vector direction;
    private double distanceTraveled;
    private int ticksAlive;
    private boolean exploded;

    public PhoenixBlast(Player player) {
        super(player);

        if (player == null || bPlayer == null) return;
        if (!player.hasPermission("projectkorra.ability.PhoenixBlast")) return;
        if (bPlayer.isOnCooldown(this)) return;
        if (hasAbility(player, PhoenixBlast.class)) return;
        if (GeneralMethods.isRegionProtectedFromBuild(this, player.getLocation())) return;

        Location eye = player.getEyeLocation();
        this.direction = eye.getDirection().normalize();
        this.location = eye.clone().add(direction.clone().multiply(1.2)).add(0, -0.2, 0);
        this.distanceTraveled = 0;
        this.ticksAlive = 0;

        start();

        if (!isStarted() || isRemoved()) return;

        bPlayer.addCooldown(this, cooldown);

        World world = location.getWorld();
        if (world != null) {
            world.playSound(location, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.8f, 1.3f);
            world.playSound(location, Sound.ENTITY_BLAZE_SHOOT, 1.2f, 0.7f);
            world.spawnParticle(Particle.EXPLOSION_LARGE, location, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.FLAME, location, 20, 0.4, 0.4, 0.4, 0.1);
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

        World world = location.getWorld();
        if (world == null) {
            remove();
            return;
        }

        ticksAlive++;
        steer();

        double step = Math.min(0.7, speed);
        int steps = Math.max(1, (int) Math.ceil(speed / step));
        double each = speed / steps;

        for (int s = 0; s < steps; s++) {
            location.add(direction.clone().multiply(each));
            distanceTraveled += each;

            if (distanceTraveled > range) {
                explode(null);
                return;
            }

            if (GeneralMethods.isSolid(location.getBlock()) || GeneralMethods.isRegionProtectedFromBuild(this, location)) {
                location.subtract(direction.clone().multiply(each));
                explode(null);
                return;
            }

            for (Entity entity : GeneralMethods.getEntitiesAroundPoint(location, hitRadius)) {
                if (!isValidVictim(entity)) continue;
                explode((LivingEntity) entity);
                return;
            }
        }

        drawBird(world);
    }

    private void steer() {
        if (homing <= 0) return;

        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        Vector pos = location.toVector();

        for (Entity entity : GeneralMethods.getEntitiesAroundPoint(location, homingRange)) {
            if (!isValidVictim(entity)) continue;

            LivingEntity living = (LivingEntity) entity;
            Vector to = living.getLocation().toVector().add(new Vector(0, living.getHeight() * 0.5, 0)).subtract(pos);
            double dist = to.length();
            if (dist < 1.0E-4) continue;

            double dot = to.clone().multiply(1.0 / dist).dot(direction);
            if (dot < 0.8) continue;

            double score = (1.0 - dot) * 10.0 + dist * 0.05;
            if (score < bestScore) {
                bestScore = score;
                best = living;
            }
        }

        if (best == null) return;

        Vector to = best.getLocation().toVector().add(new Vector(0, best.getHeight() * 0.5, 0)).subtract(pos);
        if (to.lengthSquared() < 1.0E-6) return;
        to.normalize();

        Vector steered = direction.clone().multiply(1.0 - homing).add(to.multiply(homing));
        if (steered.lengthSquared() > 1.0E-6) {
            direction = steered.normalize();
        }
    }

    public void detonateNow() {
        if (exploded) return;
        explode(null);
    }

    private void explode(LivingEntity directHit) {
        if (exploded) return;
        exploded = true;

        World world = location.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.EXPLOSION_HUGE, location, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.FLASH, location, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.FLAME, location, 60, explosionRadius * 0.35, explosionRadius * 0.35, explosionRadius * 0.35, 0.22);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, location, 25, explosionRadius * 0.3, explosionRadius * 0.3, explosionRadius * 0.3, 0.12);
            world.spawnParticle(Particle.LAVA, location, 8, explosionRadius * 0.25, explosionRadius * 0.25, explosionRadius * 0.25, 0);

            Vector center = location.toVector();
            for (int ring = 1; ring <= 2; ring++) {
                double r = explosionRadius * (ring == 1 ? 0.5 : 0.95);
                for (int i = 0; i < 24; i++) {
                    double a = i * Math.PI / 12;
                    Vector p = center.clone().add(new Vector(Math.cos(a) * r, 0.05, Math.sin(a) * r));
                    PhoenixAscension.dust(world, p, (i + ring) % 2 == 0 ? PhoenixAscension.DUST_GOLD : PhoenixAscension.DUST_EMBER);
                }
            }
            BirdFx.drawBigBird(world, center.clone().add(new Vector(0, 0.8, 0)), new Vector(0, 0.5, 1).add(direction.clone().multiply(0.6)), 6, 2.4);

            world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 2.5f, 0.8f);
            world.playSound(location, Sound.ENTITY_ENDER_DRAGON_HURT, 2.0f, 1.0f);
            world.playSound(location, Sound.ITEM_TOTEM_USE, 0.8f, 1.5f);
        }

        for (Entity entity : GeneralMethods.getEntitiesAroundPoint(location, explosionRadius)) {
            if (!isValidVictim(entity)) continue;

            LivingEntity victim = (LivingEntity) entity;
            boolean direct = directHit != null && victim.getUniqueId().equals(directHit.getUniqueId());

            double distance = victim.getLocation().add(0, victim.getHeight() * 0.5, 0).distance(location);
            double mult = Math.max(0.0, 1.0 - distance / explosionRadius);
            if (direct) {
                mult = Math.max(0.7, mult);
            }

            double total = explosionDamage * mult + (direct ? damage : 0.0);
            if (total > 0) {
                victim.setNoDamageTicks(0);
                DamageHandler.damageEntity(victim, total, this);
            }
            victim.setFireTicks(direct ? 160 : 120);

            Vector push = victim.getLocation().toVector().subtract(location.toVector());
            if (push.lengthSquared() < 1.0E-6) {
                push = direction.clone();
            }
            push.setY(0);
            if (push.lengthSquared() < 1.0E-6) {
                push = new Vector(0, 0, 1);
            }
            push.normalize().multiply(1.1 * Math.max(0.3, mult));
            push.setY(0.45 + 0.25 * mult);
            victim.setVelocity(push);
        }

        remove();
    }

    private void drawBird(World world) {
        Vector pos = location.toVector();
        BirdFx.drawBigBird(world, pos, direction, ticksAlive, birdScale);

        Vector tail = pos.clone().subtract(direction.clone().multiply(birdScale * 0.9));
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, tail.getX(), tail.getY(), tail.getZ(), 2, 0.2, 0.2, 0.2, 0.02);
        if (ticksAlive % 3 == 0) {
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, tail.getX(), tail.getY(), tail.getZ(), 1, 0.2, 0.2, 0.2, 0.01);
        }
        if (ticksAlive % 6 == 0) {
            world.playSound(location, Sound.ENTITY_PHANTOM_FLAP, 0.8f, 1.2f);
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
        return "PhoenixBlast";
    }

    @Override
    public Element getElement() {
        return PhoenixElement.PHOENIX;
    }

    @Override
    public Location getLocation() {
        return location != null ? location : (player != null ? player.getLocation() : null);
    }

    @Override
    public void load() {
        PhoenixElement.register();

        FileConfiguration config = ConfigManager.defaultConfig.get();
        config.addDefault(PATH + "Cooldown", 7000);
        config.addDefault(PATH + "Damage", 4.0);
        config.addDefault(PATH + "Range", 45.0);
        config.addDefault(PATH + "Speed", 1.4);
        config.addDefault(PATH + "DirectHitRadius", 1.9);
        config.addDefault(PATH + "ExplosionRadius", 5.0);
        config.addDefault(PATH + "ExplosionDamage", 6.0);
        config.addDefault(PATH + "Homing", 0.06);
        config.addDefault(PATH + "HomingRange", 14.0);
        config.addDefault(PATH + "Visual.BirdScale", 3.0);
        config.options().copyDefaults(true);
        ConfigManager.defaultConfig.save();

        cooldown = config.getLong(PATH + "Cooldown");
        damage = config.getDouble(PATH + "Damage");
        range = Math.max(5.0, config.getDouble(PATH + "Range"));
        speed = Math.max(0.3, config.getDouble(PATH + "Speed"));
        hitRadius = Math.max(0.5, config.getDouble(PATH + "DirectHitRadius"));
        explosionRadius = Math.max(1.0, config.getDouble(PATH + "ExplosionRadius"));
        explosionDamage = config.getDouble(PATH + "ExplosionDamage");
        homing = Math.max(0.0, Math.min(0.5, config.getDouble(PATH + "Homing")));
        homingRange = Math.max(2.0, config.getDouble(PATH + "HomingRange"));
        birdScale = Math.max(1.0, config.getDouble(PATH + "Visual.BirdScale"));

        if (listener == null) {
            listener = new PhoenixBlastListener();
            PhoenixAddonLoader.getPlugin().getServer().getPluginManager().registerEvents(listener, PhoenixAddonLoader.getPlugin());
        }
    }

    @Override
    public void stop() {
        for (PhoenixBlast blast : new ArrayList<>(CoreAbility.getAbilities(PhoenixBlast.class))) {
            blast.remove();
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
        return "Большой огненный феникс летит вперёд и слегка доводится на врага, на которого вы смотрите. "
                + "При столкновении с врагом или стеной он взрывается. Нажмите Shift, чтобы взорвать его в воздухе.";
    }

    @Override
    public String getInstructions() {
        return "ЛКМ - выпустить феникса. Shift - взорвать его в полёте.";
    }

    public static class PhoenixBlastListener implements Listener {

        @EventHandler
        public void onClick(PlayerInteractEvent event) {
            if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
            if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) return;

            Player player = event.getPlayer();
            if (!player.hasPermission("projectkorra.ability.PhoenixBlast")) return;
            if (CoreAbility.hasAbility(player, PhoenixBlast.class)) return;

            BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
            if (bPlayer == null || !bPlayer.canBend(CoreAbility.getAbility(PhoenixBlast.class))) return;

            new PhoenixBlast(player);
        }

        @EventHandler
        public void onSneak(PlayerToggleSneakEvent event) {
            if (!event.isSneaking()) return;

            PhoenixBlast blast = CoreAbility.getAbility(event.getPlayer(), PhoenixBlast.class);
            if (blast != null) {
                blast.detonateNow();
            }
        }
    }
}
