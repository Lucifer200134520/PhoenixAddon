package com.myaddons.phoenixaddon;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.FireAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.util.DamageHandler;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class PhoenixBeam extends FireAbility implements AddonAbility {

    private static final String PATH = "ExtraAbilities.Lucifer200134520.Fire.PhoenixBeam.";

    private static long cooldown;
    private static double range;
    private static double baseDamage;
    private static double maxDamageMultiplier;
    private static double multiplierGrowth;
    private static long maxDuration;
    private static long damageInterval;
    private static int maxBounces;
    private static double hitRadius;
    private static int slowAmplifier;

    private static Listener listener;

    private long startTime;
    private long lastDamageTime;
    private double currentMultiplier;
    private boolean cooldownApplied;
    private int soundTicks;

    public PhoenixBeam(Player player) {
        super(player);

        if (player == null || bPlayer == null) return;
        if (!player.hasPermission("projectkorra.ability.PhoenixBeam")) return;
        if (bPlayer.isOnCooldown(this)) return;
        if (hasAbility(player, PhoenixBeam.class)) return;
        if (GeneralMethods.isRegionProtectedFromBuild(this, player.getLocation())) return;

        this.startTime = System.currentTimeMillis();
        this.lastDamageTime = 0;
        this.currentMultiplier = 1.0;
        this.cooldownApplied = false;
        this.soundTicks = 0;

        start();

        if (!isStarted() || isRemoved()) return;

        Location loc = player.getEyeLocation();
        World world = loc.getWorld();
        if (world != null) {
            world.playSound(loc, Sound.ENTITY_BLAZE_SHOOT, 1.6f, 1.6f);
            world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 2.0f);
        }
    }

    @Override
    public void progress() {
        if (player == null || !player.isOnline() || player.isDead()) {
            remove();
            return;
        }

        long elapsed = System.currentTimeMillis() - startTime;

        if (!player.isSneaking() || !bPlayer.canBendIgnoreBindsCooldowns(this) || elapsed > maxDuration) {
            remove();
            return;
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 10, slowAmplifier - 1, false, false, false));

        if (currentMultiplier < maxDamageMultiplier) {
            currentMultiplier = Math.min(maxDamageMultiplier, currentMultiplier + multiplierGrowth);
        }

        soundTicks++;
        if (soundTicks % 6 == 0) {
            Location loc = player.getLocation();
            World world = loc.getWorld();
            if (world != null) {
                world.playSound(loc, Sound.BLOCK_FIRE_AMBIENT, 0.7f, 1.9f);
                if (currentMultiplier > 1.7) {
                    world.playSound(loc, Sound.ENTITY_GUARDIAN_ATTACK, 0.2f, 1.6f);
                }
            }
        }

        drawAndProcessBeam();
    }

    private void drawAndProcessBeam() {
        Location currentLoc = player.getEyeLocation();
        World world = currentLoc.getWorld();
        if (world == null) return;

        Vector currentDir = currentLoc.getDirection().normalize();
        if (!isValidVector(currentDir)) return;

        Set<LivingEntity> hitEntities = new HashSet<>();
        boolean canDamage = System.currentTimeMillis() - lastDamageTime >= damageInterval;
        int bounces = 0;
        double step = 0.5;
        double traveled = 0;

        int red = 255;
        int green = (int) (120 + (currentMultiplier - 1.0) * 70);
        int blue = (int) (currentMultiplier > 1.8 ? 60 : 20);
        Particle.DustOptions dustOptions = new Particle.DustOptions(
                Color.fromRGB(red, Math.min(255, green), blue),
                (float) (0.9 + (currentMultiplier * 0.5)));

        while (traveled <= range && bounces <= maxBounces) {
            Location nextLoc = currentLoc.clone().add(currentDir.clone().multiply(step));

            if (nextLoc.getWorld() == null || !nextLoc.getChunk().isLoaded()) break;


            if (GeneralMethods.isSolid(nextLoc.getBlock())) {
                if (GeneralMethods.isRegionProtectedFromBuild(this, nextLoc)) break;

                bounces++;
                if (bounces > maxBounces) break;

                Vector reflection = calculateReflection(currentDir, currentLoc.clone(), nextLoc);
                if (!isValidVector(reflection)) break;

                currentDir = reflection.normalize();

                world.playSound(currentLoc, Sound.BLOCK_GLASS_BREAK, 0.7f, 1.9f);
                world.spawnParticle(Particle.END_ROD, currentLoc, 12, 0.3, 0.3, 0.3, 0.1);
                world.spawnParticle(Particle.FLAME, currentLoc, 15, 0.4, 0.4, 0.4, 0.08);

                currentLoc = nextLoc;
                traveled += step;
                continue;
            }

            currentLoc = nextLoc;
            traveled += step;

            world.spawnParticle(Particle.REDSTONE, currentLoc, 3, 0.1, 0.1, 0.1, 0, dustOptions);
            world.spawnParticle(Particle.FLAME, currentLoc, 2, 0.08, 0.08, 0.08, 0.01);

            if (currentMultiplier > 1.6 && Math.random() < 0.25) {
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, currentLoc, 2, 0.06, 0.06, 0.06, 0.01);
            }
            if (currentMultiplier > 2.0 && Math.random() < 0.2) {
                world.spawnParticle(Particle.LAVA, currentLoc, 1, 0.04, 0.04, 0.04, 0);
            }


            if (!canDamage) continue;

            for (Entity entity : GeneralMethods.getEntitiesAroundPoint(currentLoc, hitRadius)) {
                if (!(entity instanceof LivingEntity)) continue;
                if (entity.getUniqueId().equals(player.getUniqueId())) continue;

                LivingEntity target = (LivingEntity) entity;
                if (hitEntities.contains(target)) continue;
                if (GeneralMethods.isRegionProtectedFromBuild(this, target.getLocation())) continue;

                hitEntities.add(target);

                double finalDamage = baseDamage * currentMultiplier;
                DamageHandler.damageEntity(target, finalDamage, this);
                target.setFireTicks(90);

                Location hitLoc = target.getLocation().add(0, target.getHeight() / 2, 0);
                World targetWorld = target.getWorld();
                targetWorld.spawnParticle(Particle.EXPLOSION_LARGE, hitLoc, 3, 0.3, 0.3, 0.3, 0);
                targetWorld.spawnParticle(Particle.LAVA, hitLoc, 12, 0.5, 0.5, 0.5, 0.08);
                targetWorld.spawnParticle(Particle.FLAME, hitLoc, 20, 0.4, 0.4, 0.4, 0.1);
                targetWorld.playSound(hitLoc, Sound.ENTITY_BLAZE_HURT, 0.9f, 1.3f);
            }
        }

        if (canDamage && !hitEntities.isEmpty()) {
            lastDamageTime = System.currentTimeMillis();
        }
    }

    private Vector calculateReflection(Vector direction, Location from, Location to) {
        Vector diff = to.toVector().subtract(from.toVector());


        double absX = Math.abs(diff.getX());
        double absY = Math.abs(diff.getY());
        double absZ = Math.abs(diff.getZ());

        Vector reflected = direction.clone();

        if (absX > absY && absX > absZ) {
            reflected.setX(-reflected.getX());
        } else if (absY > absX && absY > absZ) {
            reflected.setY(-reflected.getY());
        } else {
            reflected.setZ(-reflected.getZ());
        }

        return reflected.normalize();
    }

    private boolean isValidVector(Vector v) {
        return v != null && v.lengthSquared() > 0.001 &&
               Double.isFinite(v.getX()) && Double.isFinite(v.getY()) && Double.isFinite(v.getZ());
    }

    private void applyCooldown() {
        if (cooldownApplied || bPlayer == null) return;
        cooldownApplied = true;
        bPlayer.addCooldown(this, cooldown);
    }

    @Override
    public void remove() {
        applyCooldown();
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
        return "PhoenixBeam";
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
        config.addDefault(PATH + "Cooldown", 14000);
        config.addDefault(PATH + "Range", 28.0);
        config.addDefault(PATH + "BaseDamage", 2.5);
        config.addDefault(PATH + "MaxMultiplier", 2.8);
        config.addDefault(PATH + "MultiplierGrowth", 0.025);
        config.addDefault(PATH + "MaxDuration", 6000);
        config.addDefault(PATH + "DamageInterval", 600);
        config.addDefault(PATH + "MaxBounces", 4);
        config.addDefault(PATH + "HitRadius", 1.8);
        config.addDefault(PATH + "SlowAmplifier", 2);
        config.options().copyDefaults(true);
        ConfigManager.defaultConfig.save();

        cooldown = config.getLong(PATH + "Cooldown");
        range = config.getDouble(PATH + "Range");
        baseDamage = config.getDouble(PATH + "BaseDamage");
        maxDamageMultiplier = config.getDouble(PATH + "MaxMultiplier");
        multiplierGrowth = config.getDouble(PATH + "MultiplierGrowth");
        maxDuration = config.getLong(PATH + "MaxDuration");
        damageInterval = config.getLong(PATH + "DamageInterval");
        maxBounces = config.getInt(PATH + "MaxBounces");
        hitRadius = config.getDouble(PATH + "HitRadius");
        slowAmplifier = Math.max(1, Math.min(5, config.getInt(PATH + "SlowAmplifier")));

        if (listener == null) {
            listener = new PhoenixBeamListener();
            PhoenixAddonLoader.getPlugin().getServer().getPluginManager().registerEvents(listener, PhoenixAddonLoader.getPlugin());
        }
    }

    @Override
    public void stop() {
        for (PhoenixBeam beam : new ArrayList<>(CoreAbility.getAbilities(PhoenixBeam.class))) {
            beam.remove();
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
        return "Сконцентрируйте солнечную плазму! Луч усиливается со временем, отражается от стен (макс 4 раза).";
    }

    @Override
    public String getInstructions() {
        return "Зажмите Shift для стрельбы лучом. Геометрия помогает поражать врагов за углами!";
    }

    public static class PhoenixBeamListener implements Listener {

        @EventHandler
        public void onSneak(PlayerToggleSneakEvent event) {
            if (!event.isSneaking()) return;

            Player player = event.getPlayer();
            if (!player.hasPermission("projectkorra.ability.PhoenixBeam")) return;

            BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
            if (bPlayer == null || !bPlayer.canBend(CoreAbility.getAbility(PhoenixBeam.class))) return;

            new PhoenixBeam(player);
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            PhoenixBeam beam = CoreAbility.getAbility(event.getPlayer(), PhoenixBeam.class);
            if (beam != null) {
                beam.remove();
            }
        }
    }
}
