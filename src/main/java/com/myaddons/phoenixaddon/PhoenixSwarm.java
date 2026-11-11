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
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class PhoenixSwarm extends FireAbility implements AddonAbility {

    private static final String PATH = "ExtraAbilities.Lucifer200134520.Fire.PhoenixSwarm.";

    private static final double[][] SLOTS = {
            {1.0, -0.2, 0.6, 1.0, 0.15, 0.55},
            {-1.0, -0.2, 0.6, -1.0, 0.15, 0.55},
            {0.7, 1.3, 0.7, 0.5, 1.0, 0.6},
            {-0.7, 1.3, 0.7, -0.5, 1.0, 0.6},
            {1.4, 0.9, 0.8, 1.0, 0.8, 0.5},
            {-1.4, 0.9, 0.8, -1.0, 0.8, 0.5},
            {0.0, 1.8, 0.6, 0.0, 1.0, 0.7},
            {1.1, -0.9, 0.8, 1.0, -0.5, 0.6},
            {-1.1, -0.9, 0.8, -1.0, -0.5, 0.6}
    };

    private static long cooldown;
    private static int birdCount;
    private static double damage;
    private static double speed;
    private static double turnRate;
    private static double hitRadius;
    private static double aimRange;
    private static double orbitRadius;
    private static double orbitSpeed;
    private static int orbitLifetimeTicks;
    private static int maxBirdLife;
    private static double birdScale;
    private static double seekRadius;
    private static int fireTicks;

    private static Listener listener;

    private enum State { FLY, ORBIT }

    private static class Bird {
        final int index;
        Vector pos;
        Vector vel;
        State state = State.FLY;
        double angle;
        int age;
        int flyTicks;
        LivingEntity target;
        int seekCooldown;

        Bird(int index, Vector pos, double angle) {
            this.index = index;
            this.pos = pos;
            this.vel = new Vector(0, 0, 0);
            this.angle = angle;
        }
    }

    private final List<Bird> birds = new ArrayList<>();
    private int tick;
    private int spawned;
    private int orbitStartTick;
    private Vector aimPoint;
    private LivingEntity aimEntity;
    private Vector flatFwd;
    private Vector flatRight;
    private Vector lookRight;
    private Vector lookUp;
    private Vector eyePos;

    public PhoenixSwarm(Player player) {
        super(player);

        if (player == null || bPlayer == null) return;
        if (!player.hasPermission("projectkorra.ability.PhoenixSwarm")) return;
        if (bPlayer.isOnCooldown(this)) return;
        if (hasAbility(player, PhoenixSwarm.class)) return;
        if (GeneralMethods.isRegionProtectedFromBuild(this, player.getLocation())) return;

        this.tick = 0;
        this.spawned = 0;
        this.orbitStartTick = 0;

        start();

        if (!isStarted() || isRemoved()) return;

        bPlayer.addCooldown(this, cooldown);

        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1.2f, 1.3f);
        world.playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.8f, 1.5f);
    }

    @Override
    public void progress() {
        if (player == null || !player.isOnline() || player.isDead()) {
            remove();
            return;
        }

        tick++;
        buildBasis();
        updateAim();

        while (spawned < birdCount) {
            spawnBird();
        }

        if (orbitStartTick > 0 && tick - orbitStartTick > orbitLifetimeTicks) {
            fadeAll();
            remove();
            return;
        }

        Iterator<Bird> it = birds.iterator();
        while (it.hasNext()) {
            Bird bird = it.next();
            bird.age++;
            if (bird.age > maxBirdLife) {
                puff(bird.pos);
                it.remove();
                continue;
            }
            if (!updateBird(bird)) {
                it.remove();
                continue;
            }
            drawBird(bird);
        }

        if (spawned >= birdCount && birds.isEmpty()) {
            remove();
        }
    }

    private void buildBasis() {
        Location eye = player.getEyeLocation();
        eyePos = eye.toVector();

        Vector look = eye.getDirection();
        Vector flat = new Vector(look.getX(), 0, look.getZ());
        if (flat.lengthSquared() < 1.0E-4) {
            flat = flatFwd != null ? flatFwd.clone() : new Vector(0, 0, 1);
        }
        flatFwd = flat.normalize();
        flatRight = new Vector(-flatFwd.getZ(), 0, flatFwd.getX());

        Vector l = look.clone();
        if (l.lengthSquared() < 1.0E-6) {
            l = flatFwd.clone();
        }
        l.normalize();
        Vector r = l.clone().crossProduct(new Vector(0, 1, 0));
        if (r.lengthSquared() < 1.0E-4) {
            r = flatRight.clone();
        } else {
            r.normalize();
        }
        lookRight = r;
        lookUp = r.clone().crossProduct(l).normalize();
    }

    private void updateAim() {
        Location eye = player.getEyeLocation();

        if (tick % 2 == 0 || aimPoint == null) {
            Entity targeted = GeneralMethods.getTargetedEntity(player, aimRange);
            if (targeted instanceof LivingEntity && isValidVictim(targeted)) {
                aimEntity = (LivingEntity) targeted;
            } else {
                aimEntity = null;
            }
        }

        if (aimEntity != null && aimEntity.isValid() && !aimEntity.isDead()) {
            aimPoint = aimEntity.getLocation().add(0, aimEntity.getHeight() * 0.5, 0).toVector();
        } else {
            aimEntity = null;
            Location targeted = GeneralMethods.getTargetedLocation(player, (int) aimRange);
            if (targeted == null) {
                targeted = eye.clone().add(eye.getDirection().multiply(aimRange));
            }
            aimPoint = targeted.toVector();
        }
    }

    private double[] slotFor(int index) {
        return SLOTS[index % SLOTS.length];
    }

    private Vector spawnPosition(int index) {
        double[] slot = slotFor(index);
        double ring = 1.0 + 0.5 * (index / SLOTS.length);

        return eyePos.clone()
                .add(flatRight.clone().multiply(slot[0] * ring))
                .add(new Vector(0, slot[1] * ring - 0.3, 0))
                .subtract(flatFwd.clone().multiply(slot[2] * ring));
    }

    private void spawnBird() {
        int index = spawned;
        Bird bird = new Bird(index, spawnPosition(index), (Math.PI * 2 / Math.max(1, birdCount)) * index);
        birds.add(bird);
        spawned++;
        launch(bird);

        World world = player.getWorld();
        world.spawnParticle(Particle.FLAME, bird.pos.getX(), bird.pos.getY(), bird.pos.getZ(), 6, 0.15, 0.15, 0.15, 0.04);
        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, bird.pos.getX(), bird.pos.getY(), bird.pos.getZ(), 2, 0.2, 0.2, 0.2, 0.01);
        if (index % 3 == 0) {
            world.playSound(bird.pos.toLocation(world), Sound.ENTITY_PHANTOM_FLAP, 0.7f, 1.6f);
        }
        if (index % 4 == 0) {
            world.playSound(bird.pos.toLocation(world), Sound.ENTITY_BLAZE_SHOOT, 0.5f, 1.7f);
        }
    }

    private boolean updateBird(Bird bird) {
        World world = player.getWorld();

        if (bird.state == State.FLY) {
            flyBird(bird);
        }

        if (bird.state == State.ORBIT) {
            orbit(bird);
            if (bird.seekCooldown > 0) {
                bird.seekCooldown--;
            } else {
                bird.seekCooldown = 4;
                LivingEntity found = findTarget();
                if (found != null) {
                    bird.target = found;
                    bird.state = State.FLY;
                    bird.flyTicks = 8;
                }
            }
        }

        Location loc = bird.pos.toLocation(world);
        if (bird.state == State.FLY && bird.flyTicks > 3 && GeneralMethods.isSolid(loc.getBlock())) {
            puff(bird.pos);
            return false;
        }

        return !tryHit(bird, loc);
    }

    private void launch(Bird bird) {
        bird.state = State.FLY;
        bird.flyTicks = 0;

        double[] slot = slotFor(bird.index);
        Vector out = flatRight.clone().multiply(slot[3])
                .add(new Vector(0, slot[4], 0))
                .add(flatFwd.clone().multiply(slot[5]));
        if (out.lengthSquared() < 1.0E-6) {
            out = flatFwd.clone();
        }
        out.normalize();

        Vector toAim = aimPoint.clone().subtract(bird.pos);
        if (toAim.lengthSquared() < 1.0E-6) {
            toAim = flatFwd.clone();
        }
        toAim.normalize();

        Vector initial = out.multiply(0.75).add(toAim.multiply(0.25));
        if (initial.lengthSquared() < 1.0E-6) {
            initial = flatFwd.clone();
        }
        bird.vel = initial.normalize().multiply(speed * 0.8);

        if (aimEntity != null) {
            bird.target = aimEntity;
        }
    }

    private void flyBird(Bird bird) {
        bird.flyTicks++;

        boolean homing = bird.target != null && bird.target.isValid() && !bird.target.isDead()
                && bird.target.getWorld().equals(player.getWorld());
        if (!homing) {
            bird.target = null;
        }

        Vector goal = homing
                ? bird.target.getLocation().add(0, bird.target.getHeight() * 0.5, 0).toVector()
                : aimPoint;

        Vector desired = goal.clone().subtract(bird.pos);
        double dist = desired.length();

        if (!homing && dist < orbitRadius * 1.3) {
            bird.state = State.ORBIT;
            bird.seekCooldown = 6;
            if (orbitStartTick == 0) {
                orbitStartTick = tick;
            }
            return;
        }

        double cruise = homing ? speed * 1.25 : speed;
        if (dist > 1.0E-6) {
            desired.multiply(cruise / dist);
        }

        double ramp = Math.min(1.0, bird.flyTicks / 10.0);
        double blend = Math.min(0.9, turnRate * (0.35 + 0.65 * ramp) * (homing ? 1.5 : 1.0));
        bird.vel.add(desired.subtract(bird.vel).multiply(blend));
        bird.pos.add(bird.vel);
    }

    private void orbit(Bird bird) {
        bird.angle += orbitSpeed;
        double radius = orbitRadius * (1.0 + 0.25 * Math.sin(bird.age * 0.15 + bird.index));

        Vector goal = aimPoint.clone()
                .add(lookRight.clone().multiply(Math.cos(bird.angle) * radius))
                .add(lookUp.clone().multiply(Math.sin(bird.angle) * radius));

        Vector delta = goal.subtract(bird.pos);
        double len = delta.length();
        double maxStep = speed * 1.4;
        if (len > maxStep) {
            delta.multiply(maxStep / len);
        } else {
            delta.multiply(0.7);
        }

        bird.vel = delta.clone();
        bird.pos.add(delta);
    }

    private LivingEntity findTarget() {
        World world = player.getWorld();
        Location center = aimPoint.toLocation(world);

        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;

        for (Entity entity : GeneralMethods.getEntitiesAroundPoint(center, seekRadius)) {
            if (!isValidVictim(entity)) continue;
            double d = entity.getLocation().distanceSquared(center);
            if (d < bestDist) {
                bestDist = d;
                best = (LivingEntity) entity;
            }
        }
        return best;
    }

    private boolean tryHit(Bird bird, Location loc) {
        for (Entity entity : GeneralMethods.getEntitiesAroundPoint(loc, hitRadius)) {
            if (!isValidVictim(entity)) continue;

            LivingEntity victim = (LivingEntity) entity;
            victim.setNoDamageTicks(0);
            DamageHandler.damageEntity(victim, damage, this);
            victim.setFireTicks(fireTicks);

            World world = loc.getWorld();
            if (world != null) {
                world.spawnParticle(Particle.EXPLOSION_LARGE, loc, 1, 0, 0, 0, 0);
                world.spawnParticle(Particle.FLAME, loc, 14, 0.25, 0.25, 0.25, 0.1);
                world.spawnParticle(Particle.LAVA, loc, 2, 0.15, 0.15, 0.15, 0);
                world.playSound(loc, Sound.ENTITY_BLAZE_HURT, 0.8f, 1.6f);
            }
            return true;
        }
        return false;
    }

    private void puff(Vector at) {
        World world = player.getWorld();
        world.spawnParticle(Particle.FLAME, at.getX(), at.getY(), at.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
        world.spawnParticle(Particle.SMOKE_NORMAL, at.getX(), at.getY(), at.getZ(), 4, 0.2, 0.2, 0.2, 0.02);
    }

    private void fadeAll() {
        for (Bird bird : birds) {
            puff(bird.pos);
        }
        birds.clear();
    }

    private void drawBird(Bird bird) {
        Vector fwd = bird.vel.clone();
        if (fwd.lengthSquared() < 1.0E-6) {
            fwd = flatFwd.clone();
        }
        BirdFx.drawFireOval(player.getWorld(), bird.pos, fwd, bird.age, bird.index, birdScale);
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
        return "PhoenixSwarm";
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
        config.addDefault(PATH + "BirdCount", 8);
        config.addDefault(PATH + "Damage", 1.5);
        config.addDefault(PATH + "Speed", 1.1);
        config.addDefault(PATH + "TurnRate", 0.3);
        config.addDefault(PATH + "HitRadius", 1.1);
        config.addDefault(PATH + "AimRange", 40.0);
        config.addDefault(PATH + "OrbitRadius", 2.2);
        config.addDefault(PATH + "OrbitSpeed", 0.22);
        config.addDefault(PATH + "OrbitLifetimeTicks", 36);
        config.addDefault(PATH + "MaxBirdLifeTicks", 70);
        config.addDefault(PATH + "Visual.BirdScale", 1.2);
        config.addDefault(PATH + "SeekRadius", 4.5);
        config.addDefault(PATH + "FireTicks", 40);
        config.options().copyDefaults(true);
        ConfigManager.defaultConfig.save();

        cooldown = config.getLong(PATH + "Cooldown");
        birdCount = Math.max(1, Math.min(24, config.getInt(PATH + "BirdCount")));
        damage = config.getDouble(PATH + "Damage");
        speed = Math.max(0.2, config.getDouble(PATH + "Speed"));
        turnRate = Math.max(0.05, Math.min(1.0, config.getDouble(PATH + "TurnRate")));
        hitRadius = config.getDouble(PATH + "HitRadius");
        aimRange = Math.max(5.0, config.getDouble(PATH + "AimRange"));
        orbitRadius = Math.max(0.5, config.getDouble(PATH + "OrbitRadius"));
        orbitSpeed = config.getDouble(PATH + "OrbitSpeed");
        orbitLifetimeTicks = Math.max(5, config.getInt(PATH + "OrbitLifetimeTicks"));
        maxBirdLife = Math.max(10, config.getInt(PATH + "MaxBirdLifeTicks"));
        birdScale = Math.max(0.5, config.getDouble(PATH + "Visual.BirdScale"));
        seekRadius = config.getDouble(PATH + "SeekRadius");
        fireTicks = config.getInt(PATH + "FireTicks");

        if (listener == null) {
            listener = new PhoenixSwarmListener();
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
        return "Из-за спины мгновенно вылетает стая огненных фениксов: справа, слева, сверху и по диагоналям. "
                + "Каждый феникс - огненный овал в клубах пара. Птицы расходятся веером, затем сходятся на точке прицела "
                + "и наводятся на врага, на которого вы смотрите. Не попавшие кружат вокруг прицела и бросаются на любого, кто в него попадёт.";
    }

    @Override
    public String getInstructions() {
        return "Shift - выпустить стаю. Держите прицел на враге, пока птицы летят и кружат.";
    }

    public static class PhoenixSwarmListener implements Listener {

        @EventHandler
        public void onSneak(PlayerToggleSneakEvent event) {
            if (!event.isSneaking()) return;

            Player player = event.getPlayer();
            if (!player.hasPermission("projectkorra.ability.PhoenixSwarm")) return;

            if (CoreAbility.hasAbility(player, PhoenixSwarm.class)) return;

            BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
            if (bPlayer == null || !bPlayer.canBend(CoreAbility.getAbility(PhoenixSwarm.class))) return;

            new PhoenixSwarm(player);
        }
    }
}
