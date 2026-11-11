package com.myaddons.phoenixaddon;

import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.ComboAbility;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.FireAbility;
import com.projectkorra.projectkorra.ability.PassiveAbility;
import com.projectkorra.projectkorra.ability.util.ComboManager.AbilityInformation;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.event.AbilityStartEvent;
import com.projectkorra.projectkorra.util.ClickType;
import com.projectkorra.projectkorra.util.DamageHandler;
import com.projectkorra.projectkorra.util.MovementHandler;
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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PhoenixSnare extends FireAbility implements AddonAbility, ComboAbility {

    private static final String PATH = "ExtraAbilities.Lucifer200134520.Fire.PhoenixSnare.";

    private static long cooldown;
    private static double targetRange;
    private static final int birdCount = 4;
    private static double ringRadius;
    private static double birdSpeed;
    private static int spawnIntervalTicks;
    private static int gatherTimeoutTicks;
    private static int lashTicks;
    private static long stunDuration;
    private static double lashDamage;
    private static double stunDamagePerSecond;
    private static int escapeCount;
    private static int fireTicks;

    private static final Particle.DustOptions FIRE_DUST =
            new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 140, 15), 1.1f);

    private static final Map<UUID, PhoenixSnare> VICTIMS = new HashMap<>();

    private static Listener listener;

    public static boolean isCaught(UUID id) {
        return VICTIMS.containsKey(id);
    }

    private enum Phase { GATHER, LASH, STUN }

    private static class Bird {
        final int index;
        Vector pos;
        Vector vel;
        int age;

        Bird(int index, Vector pos, Vector vel) {
            this.index = index;
            this.pos = pos;
            this.vel = vel;
        }
    }

    private Phase phase;
    private LivingEntity target;
    private final List<Bird> birds = new ArrayList<>();
    private int tick;
    private int spawned;
    private int lashTick;
    private double ringAngle;
    private double radiusScale;
    private long stunStart;
    private int escapes;
    private int escapeStep;
    private boolean aiChanged;
    private boolean released;
    private MovementHandler movementHandler;

    public PhoenixSnare(Player player) {
        super(player);

        if (player == null || bPlayer == null) return;
        if (!bPlayer.canBendIgnoreBinds(this)) return;
        if (bPlayer.isOnCooldown(this)) return;
        if (hasAbility(player, PhoenixSnare.class)) return;

        Entity targeted = GeneralMethods.getTargetedEntity(player, targetRange);
        if (!isValidVictim(targeted)) return;
        if (VICTIMS.containsKey(targeted.getUniqueId())) return;

        this.target = (LivingEntity) targeted;
        this.phase = Phase.GATHER;
        this.tick = 0;
        this.spawned = 0;
        this.lashTick = 0;
        this.ringAngle = 0;
        this.radiusScale = 1.0;
        this.escapes = 0;
        this.escapeStep = 0;

        start();

        if (!isStarted() || isRemoved()) return;

        bPlayer.addCooldown(this, cooldown);

        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.6f, 0.9f);
        world.playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1.4f, 1.2f);
    }

    @Override
    public void progress() {
        if (player == null || !player.isOnline() || player.isDead()) {
            remove();
            return;
        }

        if (target == null || target.isDead() || !target.isValid() || !target.getWorld().equals(player.getWorld())) {
            remove();
            return;
        }

        tick++;

        switch (phase) {
            case GATHER:
                progressGather();
                break;
            case LASH:
                progressLash();
                break;
            case STUN:
                progressStun();
                break;
        }
    }

    private Vector targetCenter() {
        return target.getLocation().toVector().add(new Vector(0, target.getHeight() * 0.5, 0));
    }

    private Vector slotPos(int index) {
        double angle = ringAngle + index * (Math.PI * 2 / birdCount);
        double r = ringRadius * radiusScale;
        Vector c = targetCenter();
        return new Vector(
                c.getX() + Math.cos(angle) * r,
                c.getY() + 0.4 + Math.sin(angle * 2 + index) * 0.7,
                c.getZ() + Math.sin(angle) * r);
    }

    private void spawnBird() {
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection().normalize();

        Vector right = look.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1.0E-4) {
            right = new Vector(1, 0, 0);
        } else {
            right.normalize();
        }
        Vector up = right.clone().crossProduct(look).normalize();

        double side = ((spawned % 3) - 1) * 0.9;
        double lift = 0.2 + (spawned % 2) * 0.6;

        Vector pos = eye.toVector()
                .subtract(look.clone().multiply(1.1))
                .add(right.multiply(side))
                .add(up.multiply(lift));

        birds.add(new Bird(spawned, pos, look.clone().multiply(0.5)));

        World world = player.getWorld();
        Location loc = pos.toLocation(world);
        world.spawnParticle(Particle.FLAME, loc, 4, 0.15, 0.15, 0.15, 0.03);
        world.playSound(loc, Sound.ENTITY_PHANTOM_FLAP, 0.9f, 1.5f);
        spawned++;
    }

    private boolean updateBirds() {
        World world = player.getWorld();
        boolean allClose = spawned >= birdCount;

        for (Bird bird : birds) {
            bird.age++;
            Vector delta = slotPos(bird.index).subtract(bird.pos);
            double len = delta.length();

            Vector vel = delta.clone().multiply(0.3);
            if (vel.length() > birdSpeed) {
                vel.normalize().multiply(birdSpeed);
            }
            bird.vel = vel;
            bird.pos.add(vel);

            if (len > 1.4) {
                allClose = false;
            }

            Vector facing = vel.lengthSquared() < 1.0E-4 ? targetCenter().subtract(bird.pos) : vel;
            BirdFx.drawFireOval(world, bird.pos, facing, bird.age, bird.index, 0.75);
        }

        return allClose;
    }

    private void progressGather() {
        ringAngle += 0.09;

        if (spawned < birdCount && tick % spawnIntervalTicks == 0) {
            spawnBird();
        }

        boolean ready = updateBirds();

        if (ready || tick > gatherTimeoutTicks) {
            phase = Phase.LASH;
            lashTick = 0;
            player.getWorld().playSound(target.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.6f, 0.6f);
            player.getWorld().playSound(target.getLocation(), Sound.ITEM_TRIDENT_THROW, 1.4f, 0.7f);
        }
    }

    private void progressLash() {
        ringAngle += 0.09;
        radiusScale += (0.75 - radiusScale) * 0.1;
        lashTick++;

        updateBirds();

        double progress = Math.min(1.0, lashTick / (double) lashTicks);
        drawWhips(progress);

        if (lashTick >= lashTicks) {
            beginStun();
        }
    }

    private void beginStun() {
        phase = Phase.STUN;
        stunStart = System.currentTimeMillis();
        escapes = 0;
        escapeStep = 0;

        target.setNoDamageTicks(0);
        DamageHandler.damageEntity(target, lashDamage, this);
        target.setFireTicks(fireTicks);

        if (!(target instanceof Player) && target.hasAI()) {
            target.setAI(false);
            aiChanged = true;
        }

        try {
            movementHandler = new MovementHandler(target, this);
            movementHandler.stop("§cВас опутали огненные хлысты!");
        } catch (Exception ignored) {
            movementHandler = null;
        }

        if (target instanceof Player) {
            VICTIMS.put(target.getUniqueId(), this);
            sendHint();
        }

        World world = target.getWorld();
        Location loc = target.getLocation().add(0, target.getHeight() * 0.5, 0);
        world.spawnParticle(Particle.EXPLOSION_LARGE, loc, 2, 0.4, 0.4, 0.4, 0);
        world.spawnParticle(Particle.FLAME, loc, 20, 0.4, 0.5, 0.4, 0.08);
        world.playSound(loc, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.8f, 0.6f);
        world.playSound(loc, Sound.BLOCK_CHAIN_PLACE, 1.8f, 0.5f);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.4f);
    }

    private void progressStun() {
        ringAngle += 0.05;
        radiusScale += (0.75 - radiusScale) * 0.1;

        if (System.currentTimeMillis() - stunStart >= stunDuration) {
            finish();
            return;
        }

        updateBirds();
        drawWhips(1.0);

        target.setVelocity(new Vector(0, Math.min(0, target.getVelocity().getY()), 0));

        if (tick % 20 == 0 && stunDamagePerSecond > 0) {
            DamageHandler.damageEntity(target, stunDamagePerSecond, this);
            target.setFireTicks(fireTicks);
        }

        if (tick % 10 == 0 && target instanceof Player) {
            sendHint();
        }
    }

    private void drawWhips(double progress) {
        World world = player.getWorld();
        Vector center = targetCenter();

        for (Bird bird : birds) {
            Vector dir = center.clone().subtract(bird.pos);
            double length = dir.length();
            if (length < 0.01) continue;
            dir.normalize();

            int points = Math.max(2, (int) Math.min(10, length * 2.0));
            int shown = Math.max(1, (int) Math.round(points * progress));
            for (int i = 0; i <= shown; i++) {
                double t = (double) i / points;
                Vector p = bird.pos.clone().add(dir.clone().multiply(length * t));
                if (i % 3 == 0) {
                    world.spawnParticle(Particle.FLAME, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0.003);
                } else {
                    world.spawnParticle(Particle.REDSTONE, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0, FIRE_DUST);
                }
            }
        }
    }

    public void onVictimSneak(boolean sneaking) {
        if (phase != Phase.STUN) return;

        if (sneaking) {
            if (escapeStep == 0) {
                escapeStep = 1;
            }
        } else if (escapeStep == 1) {
            escapeStep = 2;
        }

        sendHint();
    }

    public void onVictimClick() {
        if (phase != Phase.STUN) return;

        if (escapeStep == 2) {
            escapes++;
            escapeStep = 0;

            World world = target.getWorld();
            Location loc = target.getLocation().add(0, target.getHeight() * 0.5, 0);
            world.spawnParticle(Particle.FLAME, loc, 8, 0.4, 0.4, 0.4, 0.06);
            world.playSound(loc, Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f + escapes * 0.08f);

            if (escapes >= escapeCount) {
                breakFree();
                return;
            }
        } else {
            escapeStep = 0;
        }

        sendHint();
    }

    private void sendHint() {
        if (!(target instanceof Player)) return;

        String hint;
        switch (escapeStep) {
            case 1:
                hint = "отпустите Shift";
                break;
            case 2:
                hint = "нажмите ЛКМ";
                break;
            default:
                hint = "нажмите Shift";
                break;
        }

        String text = "§6Вырывайтесь! §f[" + escapes + "/" + escapeCount + "] §e" + hint;
        try {
            ((Player) target).spigot().sendMessage(
                    net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    new net.md_5.bungee.api.chat.TextComponent(text));
        } catch (Throwable ignored) {
        }
    }

    private void breakFree() {
        World world = target.getWorld();
        Location loc = target.getLocation().add(0, target.getHeight() * 0.5, 0);
        world.spawnParticle(Particle.EXPLOSION_LARGE, loc, 3, 0.6, 0.6, 0.6, 0);
        world.spawnParticle(Particle.FLAME, loc, 30, 0.7, 0.7, 0.7, 0.12);
        world.playSound(loc, Sound.ITEM_SHIELD_BREAK, 1.8f, 0.8f);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.5f);
        finish();
    }

    private void finish() {
        World world = player.getWorld();
        for (Bird bird : birds) {
            Location loc = bird.pos.toLocation(world);
            world.spawnParticle(Particle.FLAME, loc, 3, 0.15, 0.15, 0.15, 0.03);
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc, 2, 0.2, 0.2, 0.2, 0.01);
        }
        remove();
    }

    private void releaseTarget() {
        if (released) return;
        released = true;

        if (movementHandler != null) {
            try {
                movementHandler.reset();
            } catch (Exception ignored) {
            }
            movementHandler = null;
        }

        if (target != null) {
            if (aiChanged && target.isValid()) {
                target.setAI(true);
            }
            VICTIMS.remove(target.getUniqueId());
        }
        birds.clear();
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
        releaseTarget();
        super.remove();
    }

    @Override
    public Object createNewComboInstance(Player player) {
        return new PhoenixSnare(player);
    }

    @Override
    public ArrayList<AbilityInformation> getCombination() {
        ArrayList<AbilityInformation> combo = new ArrayList<>();
        combo.add(new AbilityInformation("PrometheusChain", ClickType.SHIFT_DOWN));
        combo.add(new AbilityInformation("PrometheusChain", ClickType.SHIFT_UP));
        combo.add(new AbilityInformation("PhoenixSlash", ClickType.SHIFT_DOWN));
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
        return "PhoenixSnare";
    }

    @Override
    public Element getElement() {
        return PhoenixElement.PHOENIX;
    }

    @Override
    public Location getLocation() {
        return target != null ? target.getLocation() : (player != null ? player.getLocation() : null);
    }

    @Override
    public void load() {
        PhoenixElement.register();

        FileConfiguration config = ConfigManager.defaultConfig.get();
        config.addDefault(PATH + "Cooldown", 40000);
        config.addDefault(PATH + "TargetRange", 30.0);
        config.addDefault(PATH + "RingRadius", 2.6);
        config.addDefault(PATH + "BirdSpeed", 1.2);
        config.addDefault(PATH + "SpawnIntervalTicks", 2);
        config.addDefault(PATH + "GatherTimeoutTicks", 80);
        config.addDefault(PATH + "LashTicks", 10);
        config.addDefault(PATH + "StunDuration", 7000);
        config.addDefault(PATH + "LashDamage", 4.0);
        config.addDefault(PATH + "StunDamagePerSecond", 1.0);
        config.addDefault(PATH + "EscapeCount", 7);
        config.addDefault(PATH + "FireTicks", 40);
        config.options().copyDefaults(true);
        ConfigManager.defaultConfig.save();

        cooldown = config.getLong(PATH + "Cooldown");
        targetRange = Math.max(5.0, config.getDouble(PATH + "TargetRange"));
        ringRadius = Math.max(1.0, config.getDouble(PATH + "RingRadius"));
        birdSpeed = Math.max(0.2, config.getDouble(PATH + "BirdSpeed"));
        spawnIntervalTicks = Math.max(1, config.getInt(PATH + "SpawnIntervalTicks"));
        gatherTimeoutTicks = Math.max(10, config.getInt(PATH + "GatherTimeoutTicks"));
        lashTicks = Math.max(1, config.getInt(PATH + "LashTicks"));
        stunDuration = config.getLong(PATH + "StunDuration");
        lashDamage = config.getDouble(PATH + "LashDamage");
        stunDamagePerSecond = config.getDouble(PATH + "StunDamagePerSecond");
        escapeCount = Math.max(1, config.getInt(PATH + "EscapeCount"));
        fireTicks = config.getInt(PATH + "FireTicks");

        if (listener == null) {
            listener = new PhoenixSnareListener();
            PhoenixAddonLoader.getPlugin().getServer().getPluginManager().registerEvents(listener, PhoenixAddonLoader.getPlugin());
        }
    }

    @Override
    public void stop() {
        for (PhoenixSnare snare : new ArrayList<>(VICTIMS.values())) {
            snare.remove();
        }
        VICTIMS.clear();

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
        return "Комбо: птицы окружают врага в прицеле и одновременно опутывают его огненными хлыстами. "
                + "Враг оглушён на 7 секунд. Чтобы вырваться раньше, ему нужно семь раз повторить: "
                + "нажать Shift, отпустить Shift, нажать ЛКМ.";
    }

    @Override
    public String getInstructions() {
        return "PrometheusChain (Shift) > PrometheusChain (отпустить Shift) > PhoenixSlash (Shift), цель в прицеле";
    }

    public static class PhoenixSnareListener implements Listener {

        @EventHandler
        public void onSneak(PlayerToggleSneakEvent event) {
            PhoenixSnare snare = VICTIMS.get(event.getPlayer().getUniqueId());
            if (snare != null) {
                snare.onVictimSneak(event.isSneaking());
            }
        }

        @EventHandler
        public void onClick(PlayerInteractEvent event) {
            if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
            if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) return;

            PhoenixSnare snare = VICTIMS.get(event.getPlayer().getUniqueId());
            if (snare != null) {
                snare.onVictimClick();
            }
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            PhoenixSnare snare = VICTIMS.get(event.getPlayer().getUniqueId());
            if (snare != null) {
                snare.remove();
            }
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onAbilityStart(AbilityStartEvent event) {
            if (!(event.getAbility() instanceof CoreAbility)) return;

            CoreAbility started = (CoreAbility) event.getAbility();
            if (started instanceof PassiveAbility) return;

            Player owner = started.getPlayer();
            if (owner == null) return;

            if (VICTIMS.containsKey(owner.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }
}
