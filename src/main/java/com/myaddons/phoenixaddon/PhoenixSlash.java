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

public class PhoenixSlash extends FireAbility implements AddonAbility {

    private static final String PATH = "ExtraAbilities.Lucifer200134520.Fire.PhoenixSlash.";
    private static final double TILT = Math.toRadians(35);
    private static final long SECOND_MIN_DELAY = 260;

    private static long cooldown;
    private static long singleCooldown;
    private static long comboWindow;
    private static double range;
    private static double waveSpeed;
    private static double halfWidth;
    private static double waveDamage;
    private static double crossDamage;
    private static double knockback;
    private static int maxPierce;
    private static int fireTicks;
    private static int slowTicks;
    private static int slowAmplifier;
    private static int woundTicks;
    private static double woundDamage;
    private static int markTicks;

    private static Listener listener;

    private static class Crescent {
        final int index;
        final Vector dir;
        final Vector axisA;
        final Vector axisN;
        final Set<UUID> hit = new HashSet<>();
        Vector pos;
        double traveled;
        int age;

        Crescent(int index, Vector pos, Vector dir, Vector axisA, Vector axisN) {
            this.index = index;
            this.pos = pos;
            this.dir = dir;
            this.axisA = axisA;
            this.axisN = axisN;
        }
    }

    private static class Wound {
        final LivingEntity entity;
        int ticksLeft;

        Wound(LivingEntity entity, int ticksLeft) {
            this.entity = entity;
            this.ticksLeft = ticksLeft;
        }
    }

    private static class Mark {
        final int slash;
        int expire;

        Mark(int slash, int expire) {
            this.slash = slash;
            this.expire = expire;
        }
    }

    private final List<Crescent> crescents = new ArrayList<>();
    private final Map<UUID, Wound> wounds = new HashMap<>();
    private final Map<UUID, Mark> marks = new HashMap<>();
    private int fired;
    private int tick;
    private long firstFireTime;
    private boolean comboOver;
    private boolean cooldownApplied;
    private boolean active;

    public PhoenixSlash(Player player) {
        super(player);

        if (player == null || bPlayer == null) return;
        if (!bPlayer.canBend(this)) return;
        if (bPlayer.isOnCooldown(this)) return;
        if (hasAbility(player, PhoenixSlash.class)) return;
        if (GeneralMethods.isRegionProtectedFromBuild(this, player.getLocation())) return;

        start();

        if (!isStarted() || isRemoved()) return;

        active = true;
        fire();
    }

    public boolean canFireAgain() {
        return active && !comboOver && fired == 1
                && System.currentTimeMillis() - firstFireTime >= SECOND_MIN_DELAY
                && System.currentTimeMillis() - firstFireTime <= comboWindow;
    }

    public void fire() {
        if (!active || comboOver || fired >= 2) return;

        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();

        Vector right = dir.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1.0E-4) {
            right = new Vector(1, 0, 0);
        } else {
            right.normalize();
        }
        Vector up = right.clone().crossProduct(dir).normalize();

        double tilt = fired == 0 ? TILT : -TILT;
        Vector axisA = right.clone().multiply(Math.cos(tilt)).add(up.clone().multiply(Math.sin(tilt))).normalize();
        Vector axisN = right.clone().multiply(-Math.sin(tilt)).add(up.clone().multiply(Math.cos(tilt))).normalize();

        Vector start = eye.toVector().add(dir.clone().multiply(1.0)).add(new Vector(0, -0.25, 0));
        crescents.add(new Crescent(fired, start, dir, axisA, axisN));

        Vector recoil = new Vector(dir.getX(), 0, dir.getZ());
        if (recoil.lengthSquared() > 1.0E-4) {
            recoil.normalize().multiply(0.28);
            recoil.setY(Math.max(0.0, player.getVelocity().getY()));
            player.setVelocity(recoil);
        }

        world.playSound(eye, Sound.ENTITY_PHANTOM_FLAP, 1.4f, fired == 0 ? 0.8f : 1.0f);
        world.playSound(eye, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.4f, fired == 0 ? 0.7f : 0.9f);
        world.playSound(eye, Sound.ENTITY_BLAZE_SHOOT, 0.8f, fired == 0 ? 1.0f : 1.3f);

        fired++;
        if (fired == 1) {
            firstFireTime = System.currentTimeMillis();
        } else {
            finishCombo(cooldown);
        }
    }

    private void finishCombo(long cd) {
        comboOver = true;
        if (!cooldownApplied && bPlayer != null) {
            cooldownApplied = true;
            bPlayer.addCooldown(this, cd);
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

        tick++;
        World world = player.getWorld();
        long now = System.currentTimeMillis();

        if (!comboOver && fired == 1 && now - firstFireTime > comboWindow) {
            finishCombo(singleCooldown);
        }

        Iterator<Crescent> ci = crescents.iterator();
        while (ci.hasNext()) {
            Crescent c = ci.next();
            if (!advance(world, c)) {
                ci.remove();
            }
        }

        marks.values().removeIf(m -> m.expire < tick);
        tickWounds(world);

        if (comboOver && crescents.isEmpty() && wounds.isEmpty()) {
            remove();
        }
    }

    private boolean advance(World world, Crescent c) {
        c.age++;
        c.pos = c.pos.clone().add(c.dir.clone().multiply(waveSpeed));
        c.traveled += waveSpeed;

        Location loc = c.pos.toLocation(world);
        Location ahead = c.pos.clone().add(c.dir.clone().multiply(0.6)).toLocation(world);

        if (c.traveled > range || GeneralMethods.isSolid(ahead.getBlock())
                || GeneralMethods.isRegionProtectedFromBuild(this, loc)) {
            fizzle(world, c);
            return false;
        }

        drawCrescent(world, c);

        for (Entity entity : GeneralMethods.getEntitiesAroundPoint(loc, halfWidth + 1.2)) {
            if (!isValidVictim(entity)) continue;
            if (c.hit.contains(entity.getUniqueId())) continue;

            LivingEntity victim = (LivingEntity) entity;
            Vector rel = victim.getLocation().toVector().add(new Vector(0, victim.getHeight() * 0.5, 0)).subtract(c.pos);
            double f = rel.dot(c.dir);
            double a = rel.dot(c.axisA);
            double n = rel.dot(c.axisN);

            if (Math.abs(f) > 1.5 + waveSpeed * 0.5) continue;
            if (Math.abs(a) > halfWidth + victim.getWidth() * 0.5) continue;
            if (Math.abs(n) > 1.1 + victim.getHeight() * 0.5) continue;

            c.hit.add(victim.getUniqueId());
            strikeVictim(world, c, victim);

            if (c.hit.size() >= maxPierce) {
                fizzle(world, c);
                return false;
            }
        }

        return true;
    }

    private void strikeVictim(World world, Crescent c, LivingEntity victim) {
        UUID id = victim.getUniqueId();
        Mark mark = marks.get(id);
        Location mid = victim.getLocation().add(0, victim.getHeight() * 0.5, 0);

        victim.setNoDamageTicks(0);

        if (mark != null && mark.slash != c.index) {
            marks.remove(id);
            DamageHandler.damageEntity(victim, waveDamage * 0.5 + crossDamage, this);
            victim.setFireTicks(fireTicks + 40);
            victim.setVelocity(new Vector(c.dir.getX() * 0.25, 0.85, c.dir.getZ() * 0.25));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, slowTicks + 30, slowAmplifier, false, false));
            wounds.put(id, new Wound(victim, woundTicks + 40));
            crossEffect(world, mid.toVector(), c);
            world.playSound(mid, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.5f);
            world.playSound(mid, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.6f, 0.8f);
            world.playSound(mid, Sound.ITEM_TOTEM_USE, 0.6f, 1.6f);
            return;
        }

        DamageHandler.damageEntity(victim, waveDamage, this);
        victim.setFireTicks(fireTicks);

        Vector push = c.dir.clone().multiply(knockback);
        push.setY(0.25);
        victim.setVelocity(push);

        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, slowTicks, slowAmplifier, false, false));
        wounds.put(id, new Wound(victim, woundTicks));
        marks.put(id, new Mark(c.index, tick + markTicks));

        world.spawnParticle(Particle.EXPLOSION_LARGE, mid, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.FLAME, mid, 12, 0.3, 0.3, 0.3, 0.1);
        slashMark(world, mid.toVector(), c.axisA, c.dir, c.index == 0 ? PhoenixAscension.DUST_GOLD : PhoenixAscension.DUST_ORANGE);
        world.playSound(mid, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.3f, 0.9f);
        world.playSound(mid, Sound.ENTITY_BLAZE_HURT, 1.0f, 1.3f);
    }

    private void slashMark(World world, Vector center, Vector axis, Vector dir, Particle.DustOptions color) {
        for (int i = -3; i <= 3; i++) {
            Vector p = center.clone().add(axis.clone().multiply(i * 0.3)).add(dir.clone().multiply(-0.2));
            PhoenixAscension.dust(world, p, color);
        }
    }

    private void crossEffect(World world, Vector center, Crescent c) {
        Vector other = c.axisN.clone();
        Vector slash1 = c.axisA.clone().add(other.clone().multiply(0.55)).normalize();
        Vector slash2 = c.axisA.clone().subtract(other.clone().multiply(0.55)).normalize();
        for (int i = -5; i <= 5; i++) {
            Vector p1 = center.clone().add(slash1.clone().multiply(i * 0.3));
            Vector p2 = center.clone().add(slash2.clone().multiply(i * 0.3));
            PhoenixAscension.dust(world, p1, i % 2 == 0 ? PhoenixAscension.DUST_GOLD : PhoenixAscension.DUST_WHITE);
            PhoenixAscension.dust(world, p2, i % 2 == 0 ? PhoenixAscension.DUST_ORANGE : PhoenixAscension.DUST_WHITE);
        }
        world.spawnParticle(Particle.EXPLOSION_LARGE, center.getX(), center.getY(), center.getZ(), 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.FLAME, center.getX(), center.getY(), center.getZ(), 24, 0.5, 0.5, 0.5, 0.18);
        world.spawnParticle(Particle.FLASH, center.getX(), center.getY(), center.getZ(), 1, 0, 0, 0, 0);
    }

    private void fizzle(World world, Crescent c) {
        world.spawnParticle(Particle.FLAME, c.pos.getX(), c.pos.getY(), c.pos.getZ(), 10, 0.5, 0.5, 0.5, 0.06);
        world.playSound(c.pos.toLocation(world), Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.4f);
    }

    private void drawCrescent(World world, Crescent c) {
        int outer = 11;
        for (int i = 0; i < outer; i++) {
            double u = -1.0 + 2.0 * i / (outer - 1);
            double lat = u * halfWidth;
            double fwd = 1.1 * (1.0 - u * u);
            Vector p = c.pos.clone().add(c.axisA.clone().multiply(lat)).add(c.dir.clone().multiply(fwd));
            Particle.DustOptions color = Math.abs(u) > 0.7 ? PhoenixAscension.DUST_WHITE
                    : (Math.abs(u) > 0.35 ? PhoenixAscension.DUST_GOLD : PhoenixAscension.DUST_ORANGE);
            PhoenixAscension.dust(world, p, color);
            if (i % 5 == 2) {
                PhoenixAscension.jet(world, Particle.FLAME, p, c.dir, 0.12);
            }
        }

        int inner = 7;
        for (int i = 0; i < inner; i++) {
            double u = -0.85 + 1.7 * i / (inner - 1);
            double lat = u * halfWidth;
            double fwd = 0.5 * (1.0 - u * u) - 0.1;
            Vector p = c.pos.clone().add(c.axisA.clone().multiply(lat)).add(c.dir.clone().multiply(fwd));
            PhoenixAscension.dust(world, p, i % 2 == 0 ? PhoenixAscension.DUST_EMBER : PhoenixAscension.DUST_ORANGE);
        }

        if (c.age % 2 == 0) {
            Vector tail = c.pos.clone().add(c.dir.clone().multiply(-0.6));
            PhoenixAscension.puff(world, Particle.FLAME, tail, 2, 0.35, 0.02);
        }
    }

    private void tickWounds(World world) {
        Iterator<Map.Entry<UUID, Wound>> it = wounds.entrySet().iterator();
        while (it.hasNext()) {
            Wound w = it.next().getValue();
            LivingEntity e = w.entity;

            if (e == null || e.isDead() || !e.isValid() || !e.getWorld().equals(world)) {
                it.remove();
                continue;
            }

            w.ticksLeft--;
            if (w.ticksLeft <= 0) {
                it.remove();
                continue;
            }

            Location mid = e.getLocation().add(0, e.getHeight() * 0.55, 0);

            if (w.ticksLeft % 5 == 0) {
                PhoenixAscension.dust(world, mid.toVector().add(new Vector(0.2, 0.1, 0)), PhoenixAscension.DUST_EMBER);
                PhoenixAscension.dust(world, mid.toVector().add(new Vector(-0.2, -0.1, 0.1)), PhoenixAscension.DUST_DARK);
                PhoenixAscension.puff(world, Particle.FLAME, mid.toVector(), 1, 0.2, 0.01);
            }

            if (w.ticksLeft % 20 == 0) {
                e.setNoDamageTicks(0);
                DamageHandler.damageEntity(e, woundDamage, this);
                e.setFireTicks(30);
                world.spawnParticle(Particle.LAVA, mid, 2, 0.2, 0.2, 0.2, 0);
                world.playSound(mid, Sound.BLOCK_FIRE_AMBIENT, 0.6f, 1.6f);
            }
        }
    }

    @Override
    public void remove() {
        if (active) {
            active = false;
            if (!cooldownApplied && bPlayer != null && player != null) {
                cooldownApplied = true;
                bPlayer.addCooldown(this, fired >= 2 ? cooldown : singleCooldown);
            }
        }
        crescents.clear();
        wounds.clear();
        marks.clear();
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
        return "PhoenixSlash";
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
        config.addDefault(PATH + "Wave.Cooldown", 8000);
        config.addDefault(PATH + "Wave.SingleSlashCooldown", 4500);
        config.addDefault(PATH + "Wave.ComboWindowMillis", 1500);
        config.addDefault(PATH + "Wave.Range", 15.0);
        config.addDefault(PATH + "Wave.Speed", 1.2);
        config.addDefault(PATH + "Wave.HalfWidth", 2.3);
        config.addDefault(PATH + "Wave.Damage", 4.0);
        config.addDefault(PATH + "Wave.CrossBonusDamage", 7.0);
        config.addDefault(PATH + "Wave.Knockback", 0.85);
        config.addDefault(PATH + "Wave.MaxPierce", 5);
        config.addDefault(PATH + "Wave.FireTicks", 60);
        config.addDefault(PATH + "Wave.SlowTicks", 50);
        config.addDefault(PATH + "Wave.SlowAmplifier", 1);
        config.addDefault(PATH + "Wound.Ticks", 100);
        config.addDefault(PATH + "Wound.DamagePerSecond", 1.0);
        config.addDefault(PATH + "Wound.MarkTicks", 50);
        config.options().copyDefaults(true);
        ConfigManager.defaultConfig.save();

        cooldown = config.getLong(PATH + "Wave.Cooldown");
        singleCooldown = config.getLong(PATH + "Wave.SingleSlashCooldown");
        comboWindow = Math.max(400, config.getLong(PATH + "Wave.ComboWindowMillis"));
        range = Math.max(4.0, config.getDouble(PATH + "Wave.Range"));
        waveSpeed = Math.max(0.3, config.getDouble(PATH + "Wave.Speed"));
        halfWidth = Math.max(0.8, config.getDouble(PATH + "Wave.HalfWidth"));
        waveDamage = config.getDouble(PATH + "Wave.Damage");
        crossDamage = config.getDouble(PATH + "Wave.CrossBonusDamage");
        knockback = config.getDouble(PATH + "Wave.Knockback");
        maxPierce = Math.max(1, config.getInt(PATH + "Wave.MaxPierce"));
        fireTicks = config.getInt(PATH + "Wave.FireTicks");
        slowTicks = Math.max(0, config.getInt(PATH + "Wave.SlowTicks"));
        slowAmplifier = Math.max(0, Math.min(4, config.getInt(PATH + "Wave.SlowAmplifier")));
        woundTicks = Math.max(20, config.getInt(PATH + "Wound.Ticks"));
        woundDamage = config.getDouble(PATH + "Wound.DamagePerSecond");
        markTicks = Math.max(10, config.getInt(PATH + "Wound.MarkTicks"));

        if (listener == null) {
            listener = new PhoenixSlashListener();
            PhoenixAddonLoader.getPlugin().getServer().getPluginManager().registerEvents(listener, PhoenixAddonLoader.getPlugin());
        }
    }

    @Override
    public void stop() {
        for (PhoenixSlash slash : new ArrayList<>(CoreAbility.getAbilities(PhoenixSlash.class))) {
            slash.remove();
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
        return "1.0";
    }

    @Override
    public String getDescription() {
        return "Косой взмах огненного крыла выпускает полумесяц пламени, который пробивает до пяти врагов. "
                + "Каждый удар поджигает, замедляет и оставляет кровоточащую рану, которая жжёт несколько секунд. "
                + "Второй взмах с другой стороны, попав в уже раненого врага, образует X: мощный взрыв, подбрасывающий цель.";
    }

    @Override
    public String getInstructions() {
        return "ЛКМ - первый взмах. Снова ЛКМ примерно через полсекунды - второй взмах. "
                + "Если оба взмаха попадут в одного врага, получится X-удар.";
    }

    public static class PhoenixSlashListener implements Listener {

        @EventHandler
        public void onClick(PlayerInteractEvent event) {
            if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
            if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) return;

            Player player = event.getPlayer();
            if (!player.hasPermission("projectkorra.ability.PhoenixSlash")) return;

            BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
            if (bPlayer == null) return;
            if (!"PhoenixSlash".equals(bPlayer.getBoundAbilityName())) return;

            PhoenixSlash existing = CoreAbility.getAbility(player, PhoenixSlash.class);
            if (existing != null) {
                if (existing.canFireAgain()) {
                    existing.fire();
                }
                return;
            }

            if (!bPlayer.canBend(CoreAbility.getAbility(PhoenixSlash.class))) return;

            new PhoenixSlash(player);
        }
    }
}
