package com.myaddons.phoenixaddon;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.Ability;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.FireAbility;
import com.projectkorra.projectkorra.ability.PassiveAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.event.AbilityStartEvent;
import com.projectkorra.projectkorra.util.DamageHandler;
import com.projectkorra.projectkorra.util.TempBlock;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Rebirth extends FireAbility implements AddonAbility, PassiveAbility {

    public enum State {
        WATCHING,
        ASH,
        RECOVERING
    }

    private static final String PATH = "ExtraAbilities.Lucifer200134520.Fire.Rebirth.";

    private static long cooldown = 600000;
    private static long ashDuration = 5000;
    private static long recoveryDuration = 6000;
    private static long fireResistanceDuration = 8000;
    private static double healthThresholdPercentage = 20.0;
    private static double reviveHealthPercentage = 50.0;
    private static double novaRadius = 4.0;
    private static double novaDamage = 2.0;
    private static double novaKnockback = 0.8;
    private static int novaFireTicks = 60;
    private static double waterLaunchVelocity = 1.1;
    private static double landingRadius = 5.0;
    private static double landingDamage = 5.0;
    private static double landingKnockback = 1.2;
    private static int landingFireTicks = 80;
    private static final long FLIGHT_MIN_MILLIS = 400L;
    private static final long FLIGHT_TIMEOUT_MILLIS = 15000L;

    private static Listener listener;

    /** Игроки, у которых пассивка выключена командой /rebirthtoggle. Хранится в файле и переживает /b reload и рестарт. */
    private static final Set<UUID> DISABLED = ConcurrentHashMap.newKeySet();

    private State state = State.WATCHING;
    private long stateStartTime;
    private Location anchor;
    private float originalWalkSpeed;
    private boolean walkSpeedStored;
    private boolean invisibilityApplied;
    private double shockwaveRadius;
    private int soundStage;
    private boolean launched;
    private boolean landed;
    private long launchTime;
    private final List<TempBlock> embers = new ArrayList<>();

    public Rebirth(Player player) {
        super(player);
    }

    public static boolean isDisabled(UUID id) {
        return DISABLED.contains(id);
    }

    /** Переключает пассивку игрока. Возвращает true, если после переключения она ВКЛЮЧЕНА. */
    public static boolean toggleEnabled(UUID id) {
        boolean nowEnabled;
        if (DISABLED.remove(id)) {
            nowEnabled = true;
        } else {
            DISABLED.add(id);
            nowEnabled = false;
        }
        saveDisabled();
        return nowEnabled;
    }

    private static File getDisabledFile() {
        return new File(PhoenixAddonLoader.getPlugin().getDataFolder(), "rebirth_disabled.yml");
    }

    public static void loadDisabled() {
        DISABLED.clear();
        File file = getDisabledFile();
        if (!file.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String raw : yaml.getStringList("disabled")) {
            try {
                DISABLED.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private static void saveDisabled() {
        File file = getDisabledFile();
        File folder = file.getParentFile();
        if (folder != null && !folder.exists()) {
            folder.mkdirs();
        }

        List<String> ids = new ArrayList<>();
        for (UUID id : DISABLED) {
            ids.add(id.toString());
        }

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("disabled", ids);
        try {
            yaml.save(file);
        } catch (IOException e) {
            PhoenixAddonLoader.getPlugin().getLogger().warning("Не удалось сохранить rebirth_disabled.yml: " + e.getMessage());
        }
    }

    public static Rebirth trigger(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) return null;
        if (DISABLED.contains(player.getUniqueId())) return null;
        if (!player.hasPermission("projectkorra.ability.Rebirth")) return null;

        BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
        if (bPlayer == null) return null;
        if (!bPlayer.hasElement(PhoenixElement.PHOENIX)) return null;
        if (bPlayer.isOnCooldown("Rebirth")) return null;

        Rebirth existing = CoreAbility.getAbility(player, Rebirth.class);
        if (existing != null) {
            if (existing.state != State.WATCHING) return null;
            existing.beginAsh();
            return existing;
        }

        Rebirth ability = new Rebirth(player);
        if (ability.bPlayer == null) return null;

        ability.start();
        if (!ability.isStarted() || ability.isRemoved()) return null;

        ability.beginAsh();
        return ability;
    }

    private void beginAsh() {
        this.state = State.ASH;
        this.stateStartTime = System.currentTimeMillis();
        this.anchor = player.getLocation().clone();
        this.originalWalkSpeed = player.getWalkSpeed();
        this.walkSpeedStored = true;
        this.soundStage = -1;

        player.setVelocity(new Vector(0, 0, 0));
        player.setFallDistance(0);
        player.setFireTicks(0);
        player.setNoDamageTicks(Integer.MAX_VALUE);

        if (!player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, toTicks(ashDuration) + 20, 0, false, false));
            this.invisibilityApplied = true;
        }

        bPlayer.addCooldown(this, cooldown);
        placeEmbers();

        Location loc = anchor.clone().add(0, 1, 0);
        World world = loc.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.FLASH, loc, 2, 0, 0, 0, 0);
            world.spawnParticle(Particle.EXPLOSION_LARGE, loc, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.SMOKE_LARGE, loc, 60, 0.6, 1.0, 0.6, 0.05);
            world.spawnParticle(Particle.FLAME, loc, 40, 0.5, 0.8, 0.5, 0.08);
            world.playSound(loc, Sound.ENTITY_BLAZE_DEATH, 2.0F, 0.6F);
            world.playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, 2.0F, 0.5F);
            world.playSound(loc, Sound.ENTITY_WITHER_SPAWN, 0.6F, 1.8F);
        }


    }

    @Override
    public void progress() {
        if (player == null || !player.isOnline() || player.isDead()) {
            remove();
            return;
        }

        if (state == State.WATCHING) {
            return;
        }

        if (state == State.RECOVERING) {
            drawShockwave();

            if (launched && !landed) {
                trackFlight();
            }

            boolean airborne = launched && !landed
                    && System.currentTimeMillis() - launchTime < FLIGHT_TIMEOUT_MILLIS;

            if (!airborne && System.currentTimeMillis() - stateStartTime >= recoveryDuration) {
                remove();
            }
            return;
        }

        player.setNoDamageTicks(Integer.MAX_VALUE);
        holdInPlace();
        playAshEffects();

        if (System.currentTimeMillis() - stateStartTime >= ashDuration) {
            revive();
        }
    }

    private void holdInPlace() {
        player.setVelocity(new Vector(0, 0, 0));
        player.setFallDistance(0);
        player.setFireTicks(0);
        player.setRemainingAir(player.getMaximumAir());

        Location current = player.getLocation();
        if (current.getWorld() == null || anchor.getWorld() == null) {
            remove();
            return;
        }

        if (!current.getWorld().equals(anchor.getWorld())) {
            remove();
            return;
        }

        if (current.distanceSquared(anchor) > 0.35) {
            Location target = anchor.clone();
            target.setYaw(current.getYaw());
            target.setPitch(current.getPitch());
            player.teleport(target);
        }
    }

    private void playAshEffects() {
        World world = anchor.getWorld();
        if (world == null) return;

        long elapsed = System.currentTimeMillis() - stateStartTime;
        double ratio = Math.min(1.0, (double) elapsed / ashDuration);
        Location center = anchor.clone().add(0, 1, 0);

        drawImplosion(world, center, ratio);
        drawSpiral(world, center, elapsed, ratio);
        drawCore(world, center, ratio);

        if (ratio > 0.55) {
            drawWings(world, center, ratio);
        }

        if (ratio > 0.85) {
            drawCharge(world, center);
        }

        advanceSoundtrack(world, center, ratio);
    }

    private void drawImplosion(World world, Location center, double ratio) {
        int count = (int) (8 + 10 * (1.0 - ratio));
        double distance = 3.5 - 2.0 * ratio;

        for (int i = 0; i < count; i++) {
            double theta = Math.random() * Math.PI * 2;
            double phi = Math.acos(2.0 * Math.random() - 1.0);

            double x = distance * Math.sin(phi) * Math.cos(theta);
            double y = distance * Math.cos(phi) * 0.7;
            double z = distance * Math.sin(phi) * Math.sin(theta);

            Location from = center.clone().add(x, y, z);
            Vector inward = center.toVector().subtract(from.toVector());
            if (inward.lengthSquared() < 0.01) continue;
            inward.normalize().multiply(0.4);

            world.spawnParticle(Particle.ASH, from, 0, inward.getX(), inward.getY(), inward.getZ(), 1);
            if (Math.random() < 0.25) {
                world.spawnParticle(Particle.FLAME, from, 0, inward.getX(), inward.getY(), inward.getZ(), 1);
            }
            if (ratio > 0.4 && Math.random() < 0.15) {
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, from, 0, inward.getX() * 0.5, inward.getY() * 0.5, inward.getZ() * 0.5, 1);
            }
        }
    }

    private void drawSpiral(World world, Location center, long elapsed, double ratio) {
        double baseAngle = elapsed / 55.0;
        double height = 2.6;
        double radius = 1.3 - 0.5 * ratio;

        for (int strand = 0; strand < 2; strand++) {
            double offset = strand * Math.PI;
            for (double t = 0; t <= height; t += 0.2) {
                double angle = baseAngle + offset + t * 2.4;
                double r = radius * (1.0 - t / (height * 2.2));

                Location point = center.clone().add(
                        Math.cos(angle) * r,
                        t - 0.8,
                        Math.sin(angle) * r);

                world.spawnParticle(Particle.FLAME, point, 1, 0, 0, 0, 0);
                if (Math.random() < 0.2) {
                    world.spawnParticle(Particle.FLAME, point, 1, 0, 0, 0, 0);
                }
            }
        }
    }

    private void drawCore(World world, Location center, double ratio) {
        double coreRadius = 0.25 + 0.35 * ratio;
        int density = (int) (5 + 12 * ratio);

        for (int i = 0; i < density; i++) {
            double theta = Math.random() * Math.PI * 2;
            double phi = Math.acos(2.0 * Math.random() - 1.0);

            Location point = center.clone().add(
                    coreRadius * Math.sin(phi) * Math.cos(theta),
                    coreRadius * Math.cos(phi),
                    coreRadius * Math.sin(phi) * Math.sin(theta));

            world.spawnParticle(Particle.LAVA, point, 1, 0, 0, 0, 0);
            if (ratio > 0.6 && Math.random() < 0.2) {
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, point, 1, 0.05, 0.05, 0.05, 0);
            }
        }

        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, center.clone().add(0, 0.6, 0), 2, 0.25, 0.35, 0.25, 0.01);
    }

    private void drawWings(World world, Location center, double ratio) {
        double rad = Math.toRadians(player.getLocation().getYaw());
        Vector right = new Vector(-Math.sin(rad + Math.PI / 2), 0, Math.cos(rad + Math.PI / 2)).normalize();
        Vector back = new Vector(Math.sin(rad), 0, -Math.cos(rad)).normalize();

        double span = 3.2 * ((ratio - 0.55) / 0.45);
        if (span < 0.4) return;

        double flap = Math.sin(System.currentTimeMillis() / 180.0) * 0.35;

        for (double d = 0.35; d <= span; d += 0.28) {
            double lift = Math.sin((d / span) * Math.PI) * 1.5 + flap;
            double sweep = d * 0.45;

            Location rightWing = center.clone()
                    .add(right.clone().multiply(d))
                    .add(back.clone().multiply(sweep))
                    .add(0, lift, 0);

            Location leftWing = center.clone()
                    .add(right.clone().multiply(-d))
                    .add(back.clone().multiply(sweep))
                    .add(0, lift, 0);

            world.spawnParticle(Particle.FLAME, rightWing, 1, 0.03, 0.03, 0.03, 0);
            world.spawnParticle(Particle.FLAME, leftWing, 1, 0.03, 0.03, 0.03, 0);

            if (d > 1.5 && Math.random() < 0.12) {
                world.spawnParticle(Particle.LAVA, rightWing, 1, 0, 0, 0, 0);
                world.spawnParticle(Particle.LAVA, leftWing, 1, 0, 0, 0, 0);
            }

            if (d > 2.0 && ratio > 0.75 && Math.random() < 0.08) {
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, rightWing, 1, 0.02, 0.02, 0.02, 0);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, leftWing, 1, 0.02, 0.02, 0.02, 0);
            }
        }
    }

    private void drawCharge(World world, Location center) {
        for (int i = 0; i < 4; i++) {
            double angle = Math.random() * Math.PI * 2;
            double r = 2.5 + Math.random();
            Location edge = center.clone().add(Math.cos(angle) * r, Math.random() * 2 - 0.5, Math.sin(angle) * r);
            Vector toCenter = center.toVector().subtract(edge.toVector());
            if (toCenter.lengthSquared() < 0.01) continue;
            toCenter.normalize().multiply(1.2);
            world.spawnParticle(Particle.FLAME, edge, 0, toCenter.getX(), toCenter.getY(), toCenter.getZ(), 1);
        }

        if (Math.random() < 0.4) {
            world.playSound(center, Sound.BLOCK_BEACON_AMBIENT, 1.2F, 2.0F);
        }
    }

    private void advanceSoundtrack(World world, Location center, double ratio) {
        int stage = (int) (ratio * 4);
        if (stage <= soundStage) {
            if (Math.random() < 0.12) {
                world.playSound(center, Sound.BLOCK_FIRE_AMBIENT, 1.5F, 0.5F + (float) ratio);
            }
            return;
        }

        soundStage = stage;
        float pitch = 0.5F + stage * 0.25F;
        world.playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, 1.5F, pitch);
        world.playSound(center, Sound.ENTITY_BLAZE_AMBIENT, 1.2F, pitch);
    }

    private void drawShockwave() {
        if (shockwaveRadius > novaRadius * 2.5) return;

        World world = player.getWorld();
        Location center = player.getLocation();
        shockwaveRadius += 0.45;

        int points = (int) (shockwaveRadius * 10);
        if (points <= 0) return;

        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2 / points) * i;
            Location point = center.clone().add(
                    Math.cos(angle) * shockwaveRadius,
                    0.15,
                    Math.sin(angle) * shockwaveRadius);

            world.spawnParticle(Particle.FLAME, point, 1, 0, 0, 0, 0);
            if (i % 4 == 0) {
                world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, point, 1, 0.1, 0.1, 0.1, 0.01);
            }
        }
    }

    private void placeEmbers() {
        World world = anchor.getWorld();
        if (world == null) return;

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue;

                Block block = world.getBlockAt(anchor.getBlockX() + x, anchor.getBlockY(), anchor.getBlockZ() + z);
                if (block.getType() != Material.AIR) continue;
                if (TempBlock.isTempBlock(block)) continue;
                if (!GeneralMethods.isSolid(block.getRelative(BlockFace.DOWN))) continue;
                if (GeneralMethods.isRegionProtectedFromBuild(this, block.getLocation())) continue;

                embers.add(new TempBlock(block, Material.FIRE));
            }
        }
    }

    private void clearEmbers() {
        for (TempBlock tempBlock : embers) {
            tempBlock.revertBlock();
        }
        embers.clear();
    }

    private void revive() {
        clearEmbers();
        restorePlayerState();

        double maxHealth = getMaxHealth();
        double target = maxHealth * (reviveHealthPercentage / 100.0);
        player.setHealth(Math.max(1.0, Math.min(maxHealth, target)));
        player.setFireTicks(0);
        player.setFallDistance(0);
        player.setNoDamageTicks(20);
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, toTicks(fireResistanceDuration), 0, false, false));

        if (isInWater(player)) {
            launchFromWater();
        }

        applyNova();

        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.FLASH, loc.clone().add(0, 1, 0), 4, 0, 0, 0, 0);
            world.spawnParticle(Particle.EXPLOSION_HUGE, loc.clone().add(0, 1, 0), 3, 0.6, 0.6, 0.6, 0);

            for (double y = 0; y <= 14; y += 0.35) {
                double r = 0.5 + Math.sin(y / 14.0 * Math.PI) * 1.4;
                double angle = y * 1.6;
                Location column = loc.clone().add(Math.cos(angle) * r, y, Math.sin(angle) * r);
                world.spawnParticle(Particle.FLAME, column, 2, 0.15, 0.15, 0.15, 0.02);
                if (y % 1.4 < 0.35) {
                    world.spawnParticle(Particle.LAVA, column, 2, 0.3, 0.3, 0.3, 0);
                }
            }

            for (int i = 0; i < 60; i++) {
                double angle = Math.random() * Math.PI * 2;
                Vector out = new Vector(Math.cos(angle), 0.35 + Math.random() * 0.5, Math.sin(angle)).normalize().multiply(1.4);
                world.spawnParticle(Particle.FLAME, loc.clone().add(0, 1, 0), 0, out.getX(), out.getY(), out.getZ(), 1);
            }

            world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 3.0F, 0.6F);
            world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0F, 1.4F);
            world.playSound(loc, Sound.ITEM_TOTEM_USE, 2.0F, 0.9F);
            world.playSound(loc, Sound.ENTITY_PHANTOM_FLAP, 2.0F, 0.7F);
        }

        if (launched) {
            launchTime = System.currentTimeMillis();
            player.setVelocity(new Vector(0, waterLaunchVelocity, 0));
        } else {
            player.setVelocity(new Vector(0, 0.45, 0));
        }

        state = State.RECOVERING;
        stateStartTime = System.currentTimeMillis();
        shockwaveRadius = 0;
    }

    private void applyNova() {
        blast(player.getLocation(), novaRadius, novaDamage, novaKnockback, novaFireTicks);
    }

    private void blast(Location center, double radius, double damage, double knockback, int fireTicks) {
        for (Entity entity : GeneralMethods.getEntitiesAroundPoint(center, radius)) {
            if (!(entity instanceof LivingEntity)) continue;
            if (entity.getUniqueId().equals(player.getUniqueId())) continue;
            if (GeneralMethods.isRegionProtectedFromBuild(this, entity.getLocation())) continue;

            Vector push = entity.getLocation().toVector().subtract(center.toVector());
            if (push.lengthSquared() < 0.01) {
                push = new Vector(0, 1, 0);
            } else {
                push.normalize();
            }

            push.multiply(knockback);
            push.setY(Math.max(0.35, push.getY()));

            entity.setVelocity(entity.getVelocity().add(push));
            entity.setFireTicks(Math.max(entity.getFireTicks(), fireTicks));
            DamageHandler.damageEntity(entity, damage, this);
        }
    }

    private static boolean isWaterBlock(Block block) {
        Material type = block.getType();
        if (type == Material.WATER || type == Material.BUBBLE_COLUMN || type == Material.KELP
                || type == Material.KELP_PLANT || type == Material.SEAGRASS || type == Material.TALL_SEAGRASS) {
            return true;
        }
        if (block.getBlockData() instanceof org.bukkit.block.data.Waterlogged) {
            return ((org.bukkit.block.data.Waterlogged) block.getBlockData()).isWaterlogged();
        }
        return false;
    }

    private static boolean isInWater(Player player) {
        return player.isInWater()
                || isWaterBlock(player.getLocation().getBlock())
                || isWaterBlock(player.getEyeLocation().getBlock());
    }

    /** Выбрасывает игрока из воды: переносит к поверхности и запускает вверх. */
    private void launchFromWater() {
        Location from = player.getLocation();
        World world = from.getWorld();
        if (world == null) return;

        launched = true;
        landed = false;
        launchTime = System.currentTimeMillis();

        int x = from.getBlockX();
        int z = from.getBlockZ();
        int y = from.getBlockY();
        int maxY = world.getMaxHeight() - 2;
        while (y < maxY && isWaterBlock(world.getBlockAt(x, y + 1, z))) {
            y++;
        }

        // y — верхний блок воды; ставим игрока на уровень поверхности, если над ней есть свободное место
        Block above = world.getBlockAt(x, y + 1, z);
        Block above2 = world.getBlockAt(x, y + 2, z);
        if (above.isPassable() && above2.isPassable()) {
            Location surface = new Location(world, from.getX(), y + 1.0, from.getZ(), from.getYaw(), from.getPitch());
            player.teleport(surface);
        }

        player.setFallDistance(0);
        player.setRemainingAir(player.getMaximumAir());

        Location at = player.getLocation().clone().add(0, 0.2, 0);
        world.spawnParticle(Particle.WATER_SPLASH, at, 160, 1.2, 0.3, 1.2, 0.4);
        world.spawnParticle(Particle.WATER_WAKE, at, 80, 1.0, 0.2, 1.0, 0.2);
        world.spawnParticle(Particle.BUBBLE_COLUMN_UP, from.clone().add(0, 1, 0), 60, 0.6, 0.8, 0.6, 0.1);
        world.spawnParticle(Particle.CLOUD, at, 25, 0.8, 0.2, 0.8, 0.1);
        world.playSound(at, Sound.ENTITY_GENERIC_SPLASH, 2.5F, 0.6F);
        world.playSound(at, Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 2.0F, 0.7F);
    }

    /** Пока игрок летит после выброса из воды: огненный след и ожидание приземления. */
    private void trackFlight() {
        World world = player.getWorld();
        Location loc = player.getLocation();

        world.spawnParticle(Particle.FLAME, loc.clone().add(0, 1, 0), 12, 0.35, 0.5, 0.35, 0.03);
        world.spawnParticle(Particle.LAVA, loc.clone().add(0, 0.6, 0), 2, 0.3, 0.3, 0.3, 0);
        world.spawnParticle(Particle.SMOKE_NORMAL, loc.clone().add(0, 0.5, 0), 5, 0.3, 0.3, 0.3, 0.02);

        if (System.currentTimeMillis() - launchTime < FLIGHT_MIN_MILLIS) return;

        Block below = loc.clone().subtract(0, 0.3, 0).getBlock();
        boolean touchedDown = player.isOnGround()
                || GeneralMethods.isSolid(below)
                || isWaterBlock(loc.getBlock());

        if (touchedDown) {
            landed = true;
            landingExplosion();
        }
    }

    /** Огненный взрыв при приземлении после выброса из воды. */
    private void landingExplosion() {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        player.setFallDistance(0);
        player.setFireTicks(0);

        blast(loc, landingRadius, landingDamage, landingKnockback, landingFireTicks);

        Location center = loc.clone().add(0, 0.3, 0);
        world.spawnParticle(Particle.FLASH, center.clone().add(0, 0.7, 0), 3, 0, 0, 0, 0);
        world.spawnParticle(Particle.EXPLOSION_HUGE, center, 3, 0.8, 0.3, 0.8, 0);
        world.spawnParticle(Particle.FLAME, center, 150, landingRadius * 0.35, 0.3, landingRadius * 0.35, 0.18);
        world.spawnParticle(Particle.LAVA, center, 40, landingRadius * 0.3, 0.2, landingRadius * 0.3, 0);
        world.spawnParticle(Particle.SMOKE_LARGE, center, 40, landingRadius * 0.3, 0.4, landingRadius * 0.3, 0.05);

        for (double r = 0.5; r <= landingRadius; r += 0.5) {
            int points = (int) (r * 14);
            for (int i = 0; i < points; i++) {
                double angle = (Math.PI * 2 / points) * i;
                Location point = center.clone().add(Math.cos(angle) * r, 0.1, Math.sin(angle) * r);
                world.spawnParticle(Particle.FLAME, point, 1, 0.05, 0.05, 0.05, 0.02);
                if (i % 5 == 0) {
                    world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, point, 1, 0.1, 0.1, 0.1, 0.01);
                }
            }
        }

        for (double y = 0; y <= 6; y += 0.4) {
            double r = 0.4 + y * 0.25;
            double angle = y * 2.0;
            world.spawnParticle(Particle.FLAME, center.clone().add(Math.cos(angle) * r, y, Math.sin(angle) * r), 3, 0.15, 0.15, 0.15, 0.02);
        }

        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 3.5F, 0.7F);
        world.playSound(loc, Sound.ITEM_FIRECHARGE_USE, 2.5F, 0.6F);
        world.playSound(loc, Sound.ENTITY_BLAZE_SHOOT, 2.0F, 0.5F);
        world.playSound(loc, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 2.0F, 0.9F);
    }

    private void restorePlayerState() {
        if (player == null || !player.isOnline()) {
            walkSpeedStored = false;
            invisibilityApplied = false;
            return;
        }

        if (walkSpeedStored) {
            player.setWalkSpeed(originalWalkSpeed);
            walkSpeedStored = false;
        }

        if (invisibilityApplied) {
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
            invisibilityApplied = false;
        }

        if (state == State.ASH) {
            player.setNoDamageTicks(0);
        }
    }

    private double getMaxHealth() {
        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        return attribute != null ? attribute.getValue() : 20.0;
    }

    private static int toTicks(long millis) {
        return (int) Math.max(1L, millis / 50L);
    }

    public State getState() {
        return state;
    }

    @Override
    public void remove() {
        clearEmbers();
        restorePlayerState();
        super.remove();
    }

    @Override
    public boolean isInstantiable() { return false; }

    @Override
    public boolean isProgressable() { return true; }

    @Override
    public boolean isSneakAbility() { return false; }

    @Override
    public boolean isHarmlessAbility() { return false; }

    @Override
    public long getCooldown() { return cooldown; }

    @Override
    public String getName() { return "Rebirth"; }

    @Override
    public Element getElement() { return PhoenixElement.PHOENIX; }

    @Override
    public Location getLocation() { return player != null ? player.getLocation() : null; }

    @Override
    public void load() {
        PhoenixElement.register();

        FileConfiguration config = ConfigManager.defaultConfig.get();
        config.addDefault(PATH + "Cooldown", 600000);
        config.addDefault(PATH + "AshDuration", 5000);
        config.addDefault(PATH + "RecoveryDuration", 6000);
        config.addDefault(PATH + "FireResistanceDuration", 8000);
        config.addDefault(PATH + "HealthThresholdPercentage", 20.0);
        config.addDefault(PATH + "ReviveHealthPercentage", 50.0);
        config.addDefault(PATH + "NovaRadius", 4.0);
        config.addDefault(PATH + "NovaDamage", 2.0);
        config.addDefault(PATH + "NovaKnockback", 0.8);
        config.addDefault(PATH + "NovaFireTicks", 60);
        config.addDefault(PATH + "WaterLaunchVelocity", 1.1);
        config.addDefault(PATH + "LandingRadius", 5.0);
        config.addDefault(PATH + "LandingDamage", 5.0);
        config.addDefault(PATH + "LandingKnockback", 1.2);
        config.addDefault(PATH + "LandingFireTicks", 80);
        config.options().copyDefaults(true);
        ConfigManager.defaultConfig.save();

        cooldown = config.getLong(PATH + "Cooldown", 600000);
        ashDuration = Math.max(500L, config.getLong(PATH + "AshDuration", 5000));
        recoveryDuration = config.getLong(PATH + "RecoveryDuration", 6000);
        fireResistanceDuration = config.getLong(PATH + "FireResistanceDuration", 8000);
        healthThresholdPercentage = config.getDouble(PATH + "HealthThresholdPercentage", 20.0);
        reviveHealthPercentage = config.getDouble(PATH + "ReviveHealthPercentage", 50.0);
        novaRadius = config.getDouble(PATH + "NovaRadius", 4.0);
        novaDamage = config.getDouble(PATH + "NovaDamage", 2.0);
        novaKnockback = config.getDouble(PATH + "NovaKnockback", 0.8);
        novaFireTicks = config.getInt(PATH + "NovaFireTicks", 60);
        waterLaunchVelocity = config.getDouble(PATH + "WaterLaunchVelocity", 1.1);
        landingRadius = config.getDouble(PATH + "LandingRadius", 5.0);
        landingDamage = config.getDouble(PATH + "LandingDamage", 5.0);
        landingKnockback = config.getDouble(PATH + "LandingKnockback", 1.2);
        landingFireTicks = config.getInt(PATH + "LandingFireTicks", 80);

        if (listener == null) {
            listener = new RebirthListener();
            PhoenixAddonLoader.getPlugin().getServer().getPluginManager().registerEvents(listener, PhoenixAddonLoader.getPlugin());
        }
    }

    @Override
    public void stop() {
        for (Rebirth ability : new ArrayList<>(CoreAbility.getAbilities(Rebirth.class))) {
            ability.remove();
        }

        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
    }

    @Override
    public String getAuthor() { return "Lucifer200134520 & KazharovFAKE"; }

    @Override
    public String getVersion() { return "2.2"; }

    @Override
    public String getDescription() {
        return "Пепел Феникса. Получив смертельный удар, вы рассыпаетесь в пепел: тело замирает на месте и становится полностью неуязвимым, пока внутри кокона собирается новая жизнь. Пепел стягивается спиралью, разворачиваются огненные крылья, и через несколько секунд вы возрождаетесь столбом пламени, который отбрасывает и поджигает всех рядом. Прервать перерождение невозможно. Способность восстанавливается после смерти.";
    }

    @Override
    public String getInstructions() {
        return "Срабатывает автоматически при получении критического урона.";
    }

    public static class RebirthListener implements Listener {

        @EventHandler
        public void onAbilityStart(AbilityStartEvent event) {
            Ability ability = event.getAbility();
            if (ability == null || ability instanceof Rebirth) return;

            Player player = ability.getPlayer();
            if (player == null) return;

            Rebirth rebirth = CoreAbility.getAbility(player, Rebirth.class);
            if (rebirth != null && rebirth.getState() == State.ASH) {
                event.setCancelled(true);
            }
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onDamage(EntityDamageEvent event) {
            if (!(event.getEntity() instanceof Player)) return;

            Player player = (Player) event.getEntity();
            Rebirth rebirth = CoreAbility.getAbility(player, Rebirth.class);

            if (rebirth != null && rebirth.getState() == State.ASH) {
                event.setCancelled(true);
                return;
            }

            if (rebirth != null && rebirth.getState() == State.RECOVERING) {
                if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                    event.setCancelled(true);
                }
                return;
            }

            EntityDamageEvent.DamageCause cause = event.getCause();
            if (cause == EntityDamageEvent.DamageCause.VOID) {
                if (rebirth != null) {
                    rebirth.remove();
                }
                return;
            }

            if (cause == EntityDamageEvent.DamageCause.SUICIDE) return;
            if (event.getFinalDamage() <= 0) return;

            double maxHealth = 20.0;
            AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (attribute != null) {
                maxHealth = attribute.getValue();
            }

            if (player.getHealth() - event.getFinalDamage() > maxHealth * (healthThresholdPercentage / 100.0)) return;

            if (Rebirth.trigger(player) != null) {
                event.setCancelled(true);
            }
        }

        @EventHandler
        public void onHunger(FoodLevelChangeEvent event) {
            if (!(event.getEntity() instanceof Player)) return;

            Rebirth rebirth = CoreAbility.getAbility((Player) event.getEntity(), Rebirth.class);
            if (rebirth != null && rebirth.getState() == State.ASH) {
                event.setCancelled(true);
            }
        }

        @EventHandler
        public void onDeath(PlayerDeathEvent event) {
            Player player = event.getEntity();

            Rebirth rebirth = CoreAbility.getAbility(player, Rebirth.class);
            if (rebirth != null) {
                rebirth.remove();
            }

            BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
            if (bPlayer != null) {
                bPlayer.removeCooldown("Rebirth");
            }
        }

        @EventHandler
        public void onRespawn(PlayerRespawnEvent event) {
            BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(event.getPlayer());
            if (bPlayer != null) {
                bPlayer.removeCooldown("Rebirth");
            }
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            Rebirth rebirth = CoreAbility.getAbility(event.getPlayer(), Rebirth.class);
            if (rebirth != null) {
                rebirth.remove();
            }
        }
    }
}