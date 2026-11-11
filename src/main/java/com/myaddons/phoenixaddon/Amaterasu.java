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
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Amaterasu extends FireAbility implements AddonAbility {

    private static final String PATH = "ExtraAbilities.Lucifer200134520.Fire.Amaterasu.";

    private static final Particle.DustOptions BLACK = new Particle.DustOptions(Color.fromRGB(14, 10, 18), 1.7f);
    private static final Particle.DustOptions EMBER = new Particle.DustOptions(Color.fromRGB(50, 38, 62), 1.3f);

    private static long cooldown;
    private static double range;
    private static long duration;
    private static double damagePerTick;
    private static double rampPerTick;
    private static long tickInterval;
    private static double maxDamageLimit;
    private static double spreadRadius;
    private static int spreadIntervalTicks;
    private static int maxFlames;
    private static boolean groundFlames;
    private static int patchLifetime;
    private static int patchIntervalTicks;
    private static double selfDamage;
    private static boolean antiHeal;
    private static double detonateFraction;
    private static double detonateRadius;
    private static double detonateSplash;

    private static final Map<UUID, Flame> ACTIVE = new HashMap<>();

    private static Listener listener;

    private static class Flame {
        final LivingEntity entity;
        final int generation;
        final long start;
        final long lifetime;
        long lastDamage;
        double dealt;
        double current;
        int age;
        int lastSpread;
        int lastPatch;

        Flame(LivingEntity entity, int generation, long lifetime, double current) {
            this.entity = entity;
            this.generation = generation;
            this.start = System.currentTimeMillis();
            this.lifetime = lifetime;
            this.lastDamage = start;
            this.current = current;
        }
    }

    private static class Patch {
        final Vector pos;
        int ticksLeft;

        Patch(Vector pos, int ticksLeft) {
            this.pos = pos;
            this.ticksLeft = ticksLeft;
        }
    }

    private final List<Flame> flames = new ArrayList<>();
    private final List<Patch> patches = new ArrayList<>();
    private int tick;
    private long castTime;
    private boolean detonated;

    public Amaterasu(Player player) {
        super(player);

        if (player == null || bPlayer == null) return;
        if (!player.hasPermission("projectkorra.ability.Amaterasu")) return;
        if (bPlayer.isOnCooldown(this)) return;
        if (hasAbility(player, Amaterasu.class)) return;
        if (GeneralMethods.isRegionProtectedFromBuild(this, player.getLocation())) return;

        Entity targetEntity = GeneralMethods.getTargetedEntity(player, range);
        if (!(targetEntity instanceof LivingEntity)) return;
        if (!isValidVictim(targetEntity)) return;
        if (ACTIVE.containsKey(targetEntity.getUniqueId())) return;

        start();

        if (!isStarted() || isRemoved()) return;

        bPlayer.addCooldown(this, cooldown);
        castTime = System.currentTimeMillis();

        LivingEntity target = (LivingEntity) targetEntity;
        World world = player.getWorld();

        drawBeam(world, player.getEyeLocation().toVector(), target.getLocation().toVector().add(new Vector(0, target.getHeight() * 0.5, 0)));
        ignite(target, 0, duration);

        if (selfDamage > 0) {
            player.setHealth(Math.max(1.0, player.getHealth() - selfDamage));
            Vector eye = player.getEyeLocation().toVector();
            Vector right = player.getEyeLocation().getDirection().crossProduct(new Vector(0, 1, 0));
            if (right.lengthSquared() > 1.0E-4) {
                right.normalize();
                for (int side = -1; side <= 1; side += 2) {
                    for (int i = 0; i < 3; i++) {
                        Vector p = eye.clone().add(right.clone().multiply(side * 0.17)).add(new Vector(0, -0.1 - i * 0.12, 0))
                                .add(player.getEyeLocation().getDirection().multiply(0.25));
                        PhoenixAscension.dust(world, p, PhoenixAscension.DUST_DARK);
                    }
                }
            }
        }

        world.playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1.5f, 0.5f);
        world.playSound(target.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 1.0f, 0.8f);
    }

    private boolean isValidVictim(Entity e) {
        if (!(e instanceof LivingEntity)) return false;
        if (e instanceof ArmorStand) return false;
        if (e.getUniqueId().equals(player.getUniqueId())) return false;
        if (e instanceof Player && ((Player) e).getGameMode() == GameMode.SPECTATOR) return false;
        if (e.isDead() || !e.isValid()) return false;
        return !GeneralMethods.isRegionProtectedFromBuild(this, e.getLocation());
    }

    private void ignite(LivingEntity entity, int generation, long lifetime) {
        if (ACTIVE.containsKey(entity.getUniqueId())) return;
        if (flames.size() >= maxFlames) return;

        Flame flame = new Flame(entity, generation, lifetime, damagePerTick);
        flames.add(flame);
        ACTIVE.put(entity.getUniqueId(), flame);

        World world = entity.getWorld();
        Location loc = entity.getLocation().add(0, entity.getHeight() * 0.5, 0);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 14, 0.3, 0.5, 0.3, 0.05);
        world.spawnParticle(Particle.REDSTONE, loc, 12, 0.35, 0.5, 0.35, 0, BLACK);
        world.playSound(loc, Sound.ENTITY_BLAZE_SHOOT, 0.6f, 0.5f);
    }

    private void drawBeam(World world, Vector from, Vector to) {
        Vector delta = to.clone().subtract(from);
        double length = delta.length();
        if (length < 0.5) return;
        int points = (int) Math.min(36, length * 1.5);
        for (int i = 1; i < points; i++) {
            double f = (double) i / points;
            Vector p = from.clone().add(delta.clone().multiply(f));
            PhoenixAscension.dust(world, p, i % 3 == 0 ? EMBER : BLACK);
        }
    }

    @Override
    public void progress() {
        if (player == null || !player.isOnline()) {
            remove();
            return;
        }

        tick++;
        long now = System.currentTimeMillis();

        Iterator<Flame> it = flames.iterator();
        while (it.hasNext()) {
            Flame flame = it.next();
            if (!progressFlame(flame, now)) {
                ACTIVE.remove(flame.entity.getUniqueId(), flame);
                it.remove();
            }
        }

        Iterator<Patch> pi = patches.iterator();
        while (pi.hasNext()) {
            Patch patch = pi.next();
            if (!progressPatch(patch)) {
                pi.remove();
            }
        }

        if (tick % 8 == 0 && !flames.isEmpty()) {
            drawTears();
        }

        if (flames.isEmpty() && patches.isEmpty()) {
            remove();
        }
    }

    private void drawTears() {
        World world = player.getWorld();
        Location eyeLoc = player.getEyeLocation();
        Vector dir = eyeLoc.getDirection().normalize();
        Vector right = dir.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1.0E-4) return;
        right.normalize();
        for (int side = -1; side <= 1; side += 2) {
            Vector p = eyeLoc.toVector().add(right.clone().multiply(side * 0.17)).add(dir.clone().multiply(0.25))
                    .add(new Vector(0, -0.1 - (tick % 24) * 0.01, 0));
            PhoenixAscension.dust(world, p, PhoenixAscension.DUST_DARK);
        }
    }

    private boolean progressFlame(Flame flame, long now) {
        LivingEntity entity = flame.entity;
        if (entity == null || !entity.isValid() || entity.isDead()) return false;
        if (GeneralMethods.isRegionProtectedFromBuild(this, entity.getLocation())) return false;
        if (now - flame.start > flame.lifetime || flame.dealt >= maxDamageLimit) {
            fadeOut(entity);
            return false;
        }

        flame.age++;
        World world = entity.getWorld();
        Location loc = entity.getLocation().add(0, entity.getHeight() * 0.5, 0);
        double h = Math.max(0.6, entity.getHeight());

        world.spawnParticle(Particle.REDSTONE, loc, 5, 0.3, h * 0.35, 0.3, 0, BLACK);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 3, 0.2, h * 0.3, 0.2, 0.01);
        if (flame.age % 2 == 0) {
            world.spawnParticle(Particle.SMOKE_NORMAL, loc.clone().add(0, h * 0.4, 0), 1, 0.2, 0.2, 0.2, 0.01);
        }
        if (flame.age % 6 == 0) {
            world.spawnParticle(Particle.SQUID_INK, loc, 1, 0.3, 0.4, 0.3, 0.01);
        }
        if (flame.age % 12 == 0) {
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc.clone().add(0, h * 0.6, 0), 1, 0.15, 0.2, 0.15, 0.01);
        }

        if (entity.isInWater() || entity.getFireTicks() < 10) {
            entity.setFireTicks(30);
        }

        if (now - flame.lastDamage >= tickInterval) {
            flame.lastDamage = now;
            double amount = Math.min(flame.current, maxDamageLimit - flame.dealt);
            if (amount > 0) {
                entity.setNoDamageTicks(0);
                DamageHandler.damageEntity(entity, amount, this);
                flame.dealt += amount;
                flame.current += rampPerTick;
                if (flame.age % 3 == 0) {
                    world.playSound(loc, Sound.BLOCK_FIRE_AMBIENT, 0.7f, 0.3f);
                }
            }
        }

        if (flame.generation < 2 && tick - flame.lastSpread >= spreadIntervalTicks) {
            flame.lastSpread = tick;
            spreadFrom(flame);
        }

        if (groundFlames && entity.isOnGround() && patches.size() < 18 && tick - flame.lastPatch >= patchIntervalTicks) {
            flame.lastPatch = tick;
            patches.add(new Patch(entity.getLocation().toVector().add(new Vector(0, 0.1, 0)), patchLifetime));
        }

        return true;
    }

    private void fadeOut(LivingEntity entity) {
        World world = entity.getWorld();
        Location loc = entity.getLocation().add(0, entity.getHeight() * 0.5, 0);
        world.spawnParticle(Particle.SMOKE_NORMAL, loc, 8, 0.3, 0.4, 0.3, 0.03);
        world.playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 0.6f);
    }

    private void spreadFrom(Flame flame) {
        Location from = flame.entity.getLocation().add(0, flame.entity.getHeight() * 0.5, 0);

        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity entity : GeneralMethods.getEntitiesAroundPoint(from, spreadRadius)) {
            if (!isValidVictim(entity)) continue;
            if (ACTIVE.containsKey(entity.getUniqueId())) continue;
            double d = entity.getLocation().distanceSquared(from);
            if (d < bestDist) {
                bestDist = d;
                best = (LivingEntity) entity;
            }
        }
        if (best == null) return;

        drawBeam(from.getWorld(), from.toVector(), best.getLocation().toVector().add(new Vector(0, best.getHeight() * 0.5, 0)));
        ignite(best, flame.generation + 1, Math.max(2000L, flame.lifetime - (System.currentTimeMillis() - flame.start) / 2));
    }

    private boolean progressPatch(Patch patch) {
        patch.ticksLeft--;
        if (patch.ticksLeft <= 0) return false;

        World world = player.getWorld();

        if (tick % 2 == 0) {
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, patch.pos.getX(), patch.pos.getY(), patch.pos.getZ(), 2, 0.35, 0.05, 0.35, 0.01);
            PhoenixAscension.dust(world, patch.pos.clone().add(new Vector(0, 0.25, 0)), BLACK);
        }

        if (patch.ticksLeft % 8 == 0) {
            Location at = patch.pos.toLocation(world);
            for (Entity entity : GeneralMethods.getEntitiesAroundPoint(at, 1.3)) {
                if (!isValidVictim(entity)) continue;
                if (ACTIVE.containsKey(entity.getUniqueId())) continue;
                ignite((LivingEntity) entity, 2, Math.max(2500L, duration / 2));
            }
        }
        return true;
    }

    public void detonate() {
        if (detonated || flames.isEmpty()) return;
        if (System.currentTimeMillis() - castTime < 900) return;
        detonated = true;

        World world = player.getWorld();
        List<Flame> snapshot = new ArrayList<>(flames);
        List<Vector> centers = new ArrayList<>();

        for (Flame flame : snapshot) {
            LivingEntity entity = flame.entity;
            if (entity == null || !entity.isValid() || entity.isDead()) continue;

            double left = Math.max(0.0, maxDamageLimit - flame.dealt);
            double burst = left * detonateFraction;
            if (burst > 0) {
                entity.setNoDamageTicks(0);
                DamageHandler.damageEntity(entity, burst, this);
                flame.dealt += burst;
            }
            centers.add(entity.getLocation().toVector().add(new Vector(0, entity.getHeight() * 0.5, 0)));
        }

        for (Vector center : centers) {
            Location at = center.toLocation(world);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, at, 40, detonateRadius * 0.3, detonateRadius * 0.3, detonateRadius * 0.3, 0.12);
            world.spawnParticle(Particle.REDSTONE, at, 30, detonateRadius * 0.35, detonateRadius * 0.35, detonateRadius * 0.35, 0, BLACK);
            world.spawnParticle(Particle.EXPLOSION_LARGE, at, 1, 0, 0, 0, 0);
            for (int i = 0; i < 16; i++) {
                double a = i * Math.PI / 8;
                PhoenixAscension.dust(world, center.clone().add(new Vector(Math.cos(a) * detonateRadius * 0.7, 0, Math.sin(a) * detonateRadius * 0.7)), EMBER);
            }
            world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.5f);
            world.playSound(at, Sound.ENTITY_WITHER_HURT, 0.8f, 0.6f);

            for (Entity entity : GeneralMethods.getEntitiesAroundPoint(at, detonateRadius)) {
                if (!isValidVictim(entity)) continue;
                if (ACTIVE.containsKey(entity.getUniqueId())) continue;

                LivingEntity victim = (LivingEntity) entity;
                victim.setNoDamageTicks(0);
                DamageHandler.damageEntity(victim, detonateSplash, this);

                Vector push = victim.getLocation().toVector().subtract(center);
                push.setY(0);
                if (push.lengthSquared() > 1.0E-6) {
                    push.normalize().multiply(0.6);
                } else {
                    push = new Vector(0, 0, 0);
                }
                push.setY(0.35);
                victim.setVelocity(push);

                ignite(victim, 2, Math.max(2500L, duration / 2));
            }
        }

        for (Flame flame : snapshot) {
            flame.entity.setFireTicks(60);
            ACTIVE.remove(flame.entity.getUniqueId(), flame);
        }
        flames.clear();
    }

    @Override
    public void remove() {
        for (Flame flame : flames) {
            ACTIVE.remove(flame.entity.getUniqueId(), flame);
        }
        flames.clear();
        patches.clear();
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
        return "Amaterasu";
    }

    @Override
    public Element getElement() {
        return PhoenixElement.PHOENIX;
    }

    @Override
    public Location getLocation() {
        if (!flames.isEmpty() && flames.get(0).entity != null) {
            return flames.get(0).entity.getLocation();
        }
        return player != null ? player.getLocation() : null;
    }

    @Override
    public void load() {
        PhoenixElement.register();

        FileConfiguration config = ConfigManager.defaultConfig.get();
        config.addDefault(PATH + "Cooldown", 30000);
        config.addDefault(PATH + "Range", 20.0);
        config.addDefault(PATH + "Duration", 7000);
        config.addDefault(PATH + "Flame.DamageStart", 1.0);
        config.addDefault(PATH + "Flame.DamageRamp", 0.15);
        config.addDefault(PATH + "Flame.IntervalMillis", 500);
        config.addDefault(PATH + "Flame.MaxDamagePerFlame", 20.0);
        config.addDefault(PATH + "Spread.Radius", 3.0);
        config.addDefault(PATH + "Spread.IntervalTicks", 30);
        config.addDefault(PATH + "Spread.MaxFlames", 4);
        config.addDefault(PATH + "Ground.Enabled", true);
        config.addDefault(PATH + "Ground.PatchLifetimeTicks", 70);
        config.addDefault(PATH + "Ground.PatchIntervalTicks", 12);
        config.addDefault(PATH + "SelfDamage", 1.0);
        config.addDefault(PATH + "BlockHealing", true);
        config.addDefault(PATH + "Detonate.DamageFraction", 0.5);
        config.addDefault(PATH + "Detonate.Radius", 3.5);
        config.addDefault(PATH + "Detonate.SplashDamage", 3.0);
        config.options().copyDefaults(true);
        ConfigManager.defaultConfig.save();

        cooldown = config.getLong(PATH + "Cooldown");
        range = config.getDouble(PATH + "Range");
        duration = Math.max(1500, config.getLong(PATH + "Duration"));
        damagePerTick = config.getDouble(PATH + "Flame.DamageStart");
        rampPerTick = config.getDouble(PATH + "Flame.DamageRamp");
        tickInterval = Math.max(100, config.getLong(PATH + "Flame.IntervalMillis"));
        maxDamageLimit = config.getDouble(PATH + "Flame.MaxDamagePerFlame");
        spreadRadius = Math.max(0.5, config.getDouble(PATH + "Spread.Radius"));
        spreadIntervalTicks = Math.max(5, config.getInt(PATH + "Spread.IntervalTicks"));
        maxFlames = Math.max(1, config.getInt(PATH + "Spread.MaxFlames"));
        groundFlames = config.getBoolean(PATH + "Ground.Enabled");
        patchLifetime = Math.max(10, config.getInt(PATH + "Ground.PatchLifetimeTicks"));
        patchIntervalTicks = Math.max(4, config.getInt(PATH + "Ground.PatchIntervalTicks"));
        selfDamage = Math.max(0.0, config.getDouble(PATH + "SelfDamage"));
        antiHeal = config.getBoolean(PATH + "BlockHealing");
        detonateFraction = Math.max(0.0, Math.min(1.0, config.getDouble(PATH + "Detonate.DamageFraction")));
        detonateRadius = Math.max(1.0, config.getDouble(PATH + "Detonate.Radius"));
        detonateSplash = config.getDouble(PATH + "Detonate.SplashDamage");

        if (listener == null) {
            listener = new AmaterasuListener();
            PhoenixAddonLoader.getPlugin().getServer().getPluginManager().registerEvents(listener, PhoenixAddonLoader.getPlugin());
        }
    }

    @Override
    public void stop() {
        for (Amaterasu ability : new ArrayList<>(CoreAbility.getAbilities(Amaterasu.class))) {
            ability.remove();
        }
        ACTIVE.clear();
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
        return "Вы поджигаете врага взглядом чёрным пламенем: оно не гаснет в воде, усиливается с каждой секундой, "
                + "блокирует лечение, перекидывается на соседей и оставляет на земле чёрные очаги. "
                + "Повторное применение взорвёт пламя и добавит урона. Взгляд обжигает и вас.";
    }

    @Override
    public String getInstructions() {
        return "Наведите взгляд на врага, зажмите Shift и нажмите ЛКМ. Пока пламя горит, Shift + ЛКМ ещё раз - детонация.";
    }

    public static class AmaterasuListener implements Listener {

        @EventHandler
        public void onClick(PlayerInteractEvent event) {
            if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
            if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) return;

            Player player = event.getPlayer();
            if (!player.isSneaking()) return;
            if (!player.hasPermission("projectkorra.ability.Amaterasu")) return;

            BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
            if (bPlayer == null) return;
            if (!"Amaterasu".equals(bPlayer.getBoundAbilityName())) return;

            Amaterasu existing = CoreAbility.getAbility(player, Amaterasu.class);
            if (existing != null) {
                existing.detonate();
                return;
            }

            if (!bPlayer.canBend(CoreAbility.getAbility(Amaterasu.class))) return;

            new Amaterasu(player);
        }

        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void onHeal(EntityRegainHealthEvent event) {
            if (!antiHeal) return;
            if (ACTIVE.containsKey(event.getEntity().getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }
}
