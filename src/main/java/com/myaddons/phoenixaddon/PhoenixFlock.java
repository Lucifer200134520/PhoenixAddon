package com.myaddons.phoenixaddon;

import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.ComboAbility;
import com.projectkorra.projectkorra.ability.FireAbility;
import com.projectkorra.projectkorra.ability.util.ComboManager.AbilityInformation;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.util.ClickType;
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
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class PhoenixFlock extends FireAbility implements AddonAbility, ComboAbility {

    private static final String PATH = "ExtraAbilities.Lucifer200134520.Fire.PhoenixFlock.";

    private static long cooldown;
    private static double bigSpeed;
    private static double bigRange;
    private static double bigHitRadius;
    private static double bigDamage;
    private static double bigScale;
    private static int swarmCount;
    private static double swarmDamage;
    private static double swarmSpeed;
    private static double swarmTurnRate;
    private static double swarmScale;
    private static double homingRadius;
    private static double burstRadius;
    private static double burstDamage;
    private static double scatterRadius;
    private static int scatterTicks;
    private static int launchStagger;
    private static int homingTicks;
    private static int fireTicks;

    private enum Phase { BIG, SWARM }

    private static class Bird {
        final int index;
        final Vector slot;
        Vector pos;
        Vector vel;
        int age;
        int launchAt;
        boolean launched;
        LivingEntity target;

        Bird(int index, Vector slot, Vector pos, Vector vel, int launchAt) {
            this.index = index;
            this.slot = slot;
            this.pos = pos;
            this.vel = vel;
            this.launchAt = launchAt;
        }
    }

    private Phase phase;
    private Vector bigPos;
    private Vector bigDir;
    private double traveled;
    private int tick;
    private int swarmTick;
    private Vector anchorFixed;
    private LivingEntity primary;
    private final List<Bird> birds = new ArrayList<>();

    public PhoenixFlock(Player player) {
        super(player);

        if (player == null || bPlayer == null) return;
        if (!bPlayer.canBendIgnoreBinds(this)) return;
        if (bPlayer.isOnCooldown(this)) return;
        if (hasAbility(player, PhoenixFlock.class)) return;
        if (GeneralMethods.isRegionProtectedFromBuild(this, player.getLocation())) return;

        Location eye = player.getEyeLocation();
        this.phase = Phase.BIG;
        this.bigDir = eye.getDirection().normalize();
        this.bigPos = eye.toVector().add(bigDir.clone().multiply(bigScale * 0.9 + 2.0));
        this.traveled = 0;
        this.tick = 0;
        this.swarmTick = 0;

        start();

        if (!isStarted() || isRemoved()) return;

        bPlayer.addCooldown(this, cooldown);

        World world = player.getWorld();
        world.playSound(eye, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 1.0f);
        world.playSound(eye, Sound.ENTITY_BLAZE_SHOOT, 1.8f, 0.6f);
        world.playSound(eye, Sound.ENTITY_PHANTOM_FLAP, 1.8f, 0.6f);
        world.playSound(eye, Sound.ENTITY_ENDER_DRAGON_FLAP, 2.0f, 0.8f);
    }

    @Override
    public void progress() {
        if (player == null || !player.isOnline() || player.isDead()) {
            remove();
            return;
        }

        tick++;

        if (phase == Phase.BIG) {
            progressBig();
        } else {
            progressSwarm();
        }
    }

    private void progressBig() {
        World world = player.getWorld();

        bigPos.add(bigDir.clone().multiply(bigSpeed));
        traveled += bigSpeed;

        Location loc = bigPos.toLocation(world);
        BirdFx.drawBigBird(world, bigPos, bigDir, tick, bigScale);

        if (tick % 5 == 0) {
            world.playSound(loc, Sound.ENTITY_PHANTOM_FLAP, 1.4f, 0.6f);
        }
        if (tick % 14 == 0) {
            world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.6f, 0.9f);
        }

        if (GeneralMethods.isSolid(loc.getBlock()) || GeneralMethods.isRegionProtectedFromBuild(this, loc)) {
            burst(null);
            return;
        }

        if (traveled > bigRange) {
            burst(null);
            return;
        }

        double hit = Math.max(bigHitRadius, bigScale * 0.45);
        for (Entity entity : GeneralMethods.getEntitiesAroundPoint(loc, hit)) {
            if (!isValidVictim(entity)) continue;

            LivingEntity victim = (LivingEntity) entity;
            victim.setNoDamageTicks(0);
            DamageHandler.damageEntity(victim, bigDamage, this);
            victim.setFireTicks(fireTicks);

            burst(victim);
            return;
        }
    }

    private Vector anchor() {
        if (isTargetValid(primary)) {
            return primary.getLocation().toVector().add(new Vector(0, primary.getHeight() * 0.5, 0));
        }
        return anchorFixed;
    }

    private void burst(LivingEntity hit) {
        World world = player.getWorld();
        Location at = bigPos.toLocation(world);

        phase = Phase.SWARM;
        primary = hit;
        swarmTick = 0;
        anchorFixed = hit != null ? hit.getLocation().toVector().add(new Vector(0, hit.getHeight() * 0.5, 0)) : bigPos.clone();

        world.spawnParticle(Particle.EXPLOSION_HUGE, at, 8, bigScale * 0.25, bigScale * 0.2, bigScale * 0.25, 0);
        world.spawnParticle(Particle.FLASH, at, 3, 0.5, 0.5, 0.5, 0);
        world.spawnParticle(Particle.FLAME, at, 220, burstRadius * 0.5, burstRadius * 0.4, burstRadius * 0.5, 0.35);
        world.spawnParticle(Particle.LAVA, at, 50, burstRadius * 0.4, burstRadius * 0.3, burstRadius * 0.4, 0);
        world.spawnParticle(Particle.SMOKE_LARGE, at, 40, burstRadius * 0.5, burstRadius * 0.4, burstRadius * 0.5, 0.05);
        world.spawnParticle(Particle.END_ROD, at, 90, 0.4, 0.4, 0.4, 0.5);

        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 160; i++) {
            Vector dir = new Vector(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
            if (dir.lengthSquared() < 1.0E-6) continue;
            dir.normalize();
            Vector start = bigPos.clone().add(dir.clone().multiply(random.nextDouble() * bigScale * 0.35));
            PhoenixAscension.jet(world, i % 3 == 0 ? Particle.FLAME : Particle.SMOKE_NORMAL, start, dir, 0.5 + random.nextDouble() * 0.6);
            if (i % 4 == 0) {
                PhoenixAscension.dust(world, start, i % 8 == 0 ? PhoenixAscension.DUST_GOLD : PhoenixAscension.DUST_EMBER);
            }
        }

        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.7f);
        world.playSound(at, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 2.5f, 0.8f);
        world.playSound(at, Sound.ENTITY_ENDER_DRAGON_DEATH, 1.6f, 1.6f);
        world.playSound(at, Sound.ENTITY_BLAZE_SHOOT, 2.0f, 0.5f);

        for (Entity entity : GeneralMethods.getEntitiesAroundPoint(at, burstRadius)) {
            if (!isValidVictim(entity)) continue;
            if (entity.equals(hit)) continue;

            LivingEntity victim = (LivingEntity) entity;
            double dist = victim.getLocation().distance(at);
            double dmg = burstDamage * Math.max(0.0, 1.0 - dist / burstRadius);
            if (dmg > 0) {
                victim.setNoDamageTicks(0);
                DamageHandler.damageEntity(victim, dmg, this);
            }
            victim.setFireTicks(fireTicks);
        }

        for (int i = 0; i < swarmCount; i++) {
            double y = 1.0 - 2.0 * (i + 0.5) / swarmCount;
            double r = Math.sqrt(Math.max(0, 1.0 - y * y));
            double phi = i * 2.399963;
            Vector slot = new Vector(Math.cos(phi) * r, y * 0.65, Math.sin(phi) * r).normalize();

            Vector startPos = bigPos.clone().add(slot.clone().multiply(0.8));
            Vector startVel = slot.clone().multiply(0.9);
            birds.add(new Bird(i, slot, startPos, startVel, scatterTicks + i * launchStagger));
        }
    }

    private void progressSwarm() {
        swarmTick++;
        World world = player.getWorld();

        double burstFrame = Math.min(1.0, swarmTick / 8.0);
        if (swarmTick <= 8) {
            Vector c = bigPos;
            double r = burstRadius * burstFrame * 1.3;
            for (int i = 0; i < 48; i++) {
                double a = i * Math.PI * 2 / 48;
                Vector p = c.clone().add(new Vector(Math.cos(a) * r, 0.3, Math.sin(a) * r));
                PhoenixAscension.jet(world, Particle.FLAME, p, new Vector(Math.cos(a), 0.1, Math.sin(a)), 0.4);
                if (i % 3 == 0) {
                    PhoenixAscension.dust(world, p, PhoenixAscension.DUST_ORANGE);
                    PhoenixAscension.puff(world, Particle.SMOKE_LARGE, p, 1, 0.2, 0.02);
                }
            }
        }

        Iterator<Bird> it = birds.iterator();
        while (it.hasNext()) {
            Bird bird = it.next();
            if (!updateBird(bird)) {
                it.remove();
            }
        }

        if (birds.isEmpty()) {
            remove();
        }
    }

    private boolean isTargetValid(LivingEntity e) {
        return e != null && !e.isDead() && e.isValid() && e.getWorld().equals(player.getWorld());
    }

    private LivingEntity findNearest(Vector from) {
        Location center = from.toLocation(player.getWorld());
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;

        for (Entity entity : GeneralMethods.getEntitiesAroundPoint(center, homingRadius)) {
            if (!isValidVictim(entity)) continue;
            double d = entity.getLocation().distanceSquared(center);
            if (d < bestDist) {
                bestDist = d;
                best = (LivingEntity) entity;
            }
        }
        return best;
    }

    private boolean updateBird(Bird bird) {
        World world = player.getWorld();
        bird.age++;

        if (!bird.launched && swarmTick >= bird.launchAt) {
            bird.launched = true;
            bird.age = 0;
            bird.target = isTargetValid(primary) ? primary : findNearest(bird.pos);

            Location loc = bird.pos.toLocation(world);
            world.playSound(loc, Sound.ENTITY_PHANTOM_SWOOP, 1.2f, 1.4f);
            world.spawnParticle(Particle.FLAME, loc, 12, 0.3, 0.3, 0.3, 0.08);
        }

        if (!bird.launched) {
            scatter(bird);
            BirdFx.drawBird(world, bird.pos, bird.vel, swarmTick, bird.index, swarmScale);
            return true;
        }

        if (bird.age > homingTicks) {
            puff(bird.pos.toLocation(world));
            return false;
        }

        if (!isTargetValid(bird.target)) {
            bird.target = isTargetValid(primary) ? primary : findNearest(bird.pos);
        }

        if (bird.target == null) {
            if (bird.age > 12) {
                puff(bird.pos.toLocation(world));
                return false;
            }
            bird.vel.multiply(0.92);
        } else {
            Vector goal = bird.target.getLocation().add(0, bird.target.getHeight() * 0.5, 0).toVector();
            Vector desired = goal.subtract(bird.pos);
            if (desired.lengthSquared() > 1.0E-6) {
                double ramp = Math.min(1.0, 0.4 + bird.age * 0.08);
                desired.normalize().multiply(swarmSpeed * ramp);
                bird.vel.add(desired.subtract(bird.vel).multiply(swarmTurnRate));
            }
        }

        bird.pos.add(bird.vel);
        Location loc = bird.pos.toLocation(world);

        if (GeneralMethods.isSolid(loc.getBlock())) {
            puff(loc);
            return false;
        }

        double hitRadius = Math.max(1.0, swarmScale * 0.6);
        for (Entity entity : GeneralMethods.getEntitiesAroundPoint(loc, hitRadius)) {
            if (!isValidVictim(entity)) continue;

            LivingEntity victim = (LivingEntity) entity;
            victim.setNoDamageTicks(0);
            DamageHandler.damageEntity(victim, swarmDamage, this);
            victim.setFireTicks(fireTicks);

            world.spawnParticle(Particle.EXPLOSION_LARGE, loc, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.FLAME, loc, 25, 0.35, 0.35, 0.35, 0.12);
            world.spawnParticle(Particle.LAVA, loc, 5, 0.25, 0.25, 0.25, 0);
            world.spawnParticle(Particle.SMOKE_LARGE, loc, 6, 0.3, 0.3, 0.3, 0.03);
            world.playSound(loc, Sound.ENTITY_BLAZE_HURT, 0.9f, 1.6f);
            world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.7f);
            return false;
        }

        BirdFx.drawBird(world, bird.pos, bird.vel, bird.age, bird.index, swarmScale);
        return true;
    }

    private void scatter(Bird bird) {
        Vector center = anchor();
        if (center == null) {
            center = bigPos;
        }

        double spin = swarmTick * 0.09;
        double cos = Math.cos(spin);
        double sin = Math.sin(spin);
        Vector dir = new Vector(bird.slot.getX() * cos - bird.slot.getZ() * sin, bird.slot.getY(), bird.slot.getX() * sin + bird.slot.getZ() * cos);

        double bob = Math.sin(swarmTick * 0.2 + bird.index) * 0.4;
        double reach = Math.min(1.0, swarmTick / 6.0);
        Vector slot = center.clone().add(dir.multiply(scatterRadius * reach)).add(new Vector(0, bob, 0));

        Vector pull = slot.subtract(bird.pos).multiply(0.22);
        bird.vel.add(pull);
        bird.vel.multiply(0.72);
        if (bird.vel.length() > 1.2) {
            bird.vel.normalize().multiply(1.2);
        }
        bird.pos.add(bird.vel);

        Vector face = center.clone().subtract(bird.pos);
        if (face.lengthSquared() > 1.0E-6 && bird.vel.lengthSquared() < 0.04) {
            bird.vel = face.normalize().multiply(0.05);
        }
    }

    private void puff(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;
        world.spawnParticle(Particle.FLAME, loc, 14, 0.3, 0.3, 0.3, 0.06);
        world.spawnParticle(Particle.SMOKE_LARGE, loc, 5, 0.3, 0.3, 0.3, 0.02);
    }

    private boolean isValidVictim(Entity e) {
        if (!(e instanceof LivingEntity)) return false;
        if (e instanceof ArmorStand) return false;
        if (e.getUniqueId().equals(player.getUniqueId())) return false;
        if (e instanceof Player && ((Player) e).getGameMode() == GameMode.SPECTATOR) return false;
        if (e.isDead() || !e.isValid()) return false;
        return !GeneralMethods.isRegionProtectedFromBuild(this, e.getLocation());
    }

    @Override
    public void remove() {
        birds.clear();
        super.remove();
    }

    @Override
    public Object createNewComboInstance(Player player) {
        return new PhoenixFlock(player);
    }

    @Override
    public ArrayList<AbilityInformation> getCombination() {
        ArrayList<AbilityInformation> combo = new ArrayList<>();
        combo.add(new AbilityInformation("PhoenixBlast", ClickType.SHIFT_DOWN));
        combo.add(new AbilityInformation("PhoenixBlast", ClickType.SHIFT_UP));
        combo.add(new AbilityInformation("PhoenixBird", ClickType.SHIFT_DOWN));
        return combo;
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
        return "PhoenixFlock";
    }

    @Override
    public Element getElement() {
        return PhoenixElement.PHOENIX;
    }

    @Override
    public Location getLocation() {
        return bigPos != null && player != null ? bigPos.toLocation(player.getWorld()) : null;
    }

    @Override
    public void load() {
        PhoenixElement.register();

        FileConfiguration config = ConfigManager.defaultConfig.get();
        config.addDefault(PATH + "Cooldown", 25000);
        config.addDefault(PATH + "BigSpeed", 1.0);
        config.addDefault(PATH + "BigRange", 45.0);
        config.addDefault(PATH + "BigHitRadius", 1.8);
        config.addDefault(PATH + "BigDamage", 4.0);
        config.addDefault(PATH + "BigScale", 5.0);
        config.addDefault(PATH + "SwarmCount", 9);
        config.addDefault(PATH + "SwarmDamage", 2.0);
        config.addDefault(PATH + "SwarmSpeed", 1.3);
        config.addDefault(PATH + "SwarmTurnRate", 0.35);
        config.addDefault(PATH + "SwarmScale", 2.0);
        config.addDefault(PATH + "HomingRadius", 18.0);
        config.addDefault(PATH + "BurstRadius", 4.0);
        config.addDefault(PATH + "BurstDamage", 3.0);
        config.addDefault(PATH + "ScatterRadius", 6.5);
        config.addDefault(PATH + "ScatterTicks", 22);
        config.addDefault(PATH + "LaunchStaggerTicks", 2);
        config.addDefault(PATH + "HomingTicks", 40);
        config.addDefault(PATH + "FireTicks", 60);
        config.options().copyDefaults(true);
        ConfigManager.defaultConfig.save();

        cooldown = config.getLong(PATH + "Cooldown");
        bigSpeed = Math.max(0.2, config.getDouble(PATH + "BigSpeed"));
        bigRange = config.getDouble(PATH + "BigRange");
        bigHitRadius = config.getDouble(PATH + "BigHitRadius");
        bigDamage = config.getDouble(PATH + "BigDamage");
        bigScale = Math.max(1.0, config.getDouble(PATH + "BigScale"));
        swarmCount = Math.max(1, config.getInt(PATH + "SwarmCount"));
        swarmDamage = config.getDouble(PATH + "SwarmDamage");
        swarmSpeed = Math.max(0.2, config.getDouble(PATH + "SwarmSpeed"));
        swarmTurnRate = Math.max(0.05, Math.min(1.0, config.getDouble(PATH + "SwarmTurnRate")));
        swarmScale = Math.max(0.8, config.getDouble(PATH + "SwarmScale"));
        homingRadius = config.getDouble(PATH + "HomingRadius");
        burstRadius = Math.max(0.5, config.getDouble(PATH + "BurstRadius"));
        burstDamage = config.getDouble(PATH + "BurstDamage");
        scatterRadius = Math.max(2.0, config.getDouble(PATH + "ScatterRadius"));
        scatterTicks = Math.max(6, config.getInt(PATH + "ScatterTicks"));
        launchStagger = Math.max(0, config.getInt(PATH + "LaunchStaggerTicks"));
        homingTicks = Math.max(10, config.getInt(PATH + "HomingTicks"));
        fireTicks = config.getInt(PATH + "FireTicks");
    }

    @Override
    public void stop() {
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
        return "Комбо: огромный феникс летит вперёд. При столкновении с врагом он взрывается, "
                + "из него вылетает рой птиц, они разлетаются вокруг цели, а затем одна за другой бросаются на неё и бьют без промаха.";
    }

    @Override
    public String getInstructions() {
        return "PhoenixBlast (Shift) > PhoenixBlast (отпустить Shift) > PhoenixBird (Shift)";
    }
}
