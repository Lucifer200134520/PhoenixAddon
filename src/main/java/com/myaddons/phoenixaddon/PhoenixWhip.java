package com.myaddons.phoenixaddon;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.FireAbility;
import com.projectkorra.projectkorra.ability.Ability;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.event.AbilityStartEvent;
import com.projectkorra.projectkorra.util.DamageHandler;
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
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class PhoenixWhip extends FireAbility implements AddonAbility {

    public enum State { EXTENDING, TETHERED, PULLING }

    private static final String PATH = "ExtraAbilities.Lucifer200134520.Fire.PhoenixWhip.";

    private static long cooldown;
    private static double range;
    private static double damage;
    private static double pullDamage;
    private static long tetherDuration;
    private static int maxWhips;
    private static double extendSpeed;
    private static double pullSpeed;

    private static Listener listener;

    private class WhipInstance {
        State state = State.EXTENDING;
        Location head;
        Vector direction;
        LivingEntity target;
        double distanceTraveled = 0;
        long tetherStartTime;

        WhipInstance(Location head, Vector direction) {
            this.head = head.clone();
            this.direction = direction.clone();
        }
    }

    private List<WhipInstance> whips;
    private int whipsShot;
    private boolean cooldownApplied;

    public PhoenixWhip(Player player) {
        super(player);

        if (player == null || bPlayer == null) return;
        if (!player.hasPermission("projectkorra.ability.PhoenixWhip")) return;
        if (bPlayer.isOnCooldown(this)) return;
        if (GeneralMethods.isRegionProtectedFromBuild(this, player.getLocation())) return;

        this.whips = new ArrayList<>();
        this.whipsShot = 0;
        this.cooldownApplied = false;

        start();

        if (!isStarted() || isRemoved()) return;

        shootNewWhip();
    }

    public void shootNewWhip() {
        if (whipsShot >= maxWhips) return;
        whipsShot++;

        Location startLoc = player.getLocation().add(0, 1.2, 0);
        Vector dir = player.getLocation().getDirection().normalize();
        whips.add(new WhipInstance(startLoc, dir));

        World world = player.getWorld();
        if (world != null) {
            world.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.5f);
        }
    }

    @Override
    public void progress() {
        if (player == null || !player.isOnline() || player.isDead()) {
            remove();
            return;
        }

        if (whips.isEmpty() && whipsShot > 0) {
            remove();
            return;
        }

        Iterator<WhipInstance> it = whips.iterator();
        while (it.hasNext()) {
            WhipInstance whip = it.next();
            if (whip.state == State.EXTENDING) progressExtending(whip, it);
            else if (whip.state == State.TETHERED) progressTethered(whip, it);
            else if (whip.state == State.PULLING) progressPulling(whip, it);
        }
    }

    private void progressExtending(WhipInstance whip, Iterator<WhipInstance> it) {
        whip.head.add(whip.direction.clone().multiply(extendSpeed));
        whip.distanceTraveled += extendSpeed;

        if (whip.distanceTraveled > range
                || whip.head.getWorld() == null
                || GeneralMethods.isSolid(whip.head.getBlock())
                || GeneralMethods.isRegionProtectedFromBuild(this, whip.head)) {
            it.remove();
            return;
        }

        World world = whip.head.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.FLAME, whip.head, 5, 0.1, 0.1, 0.1, 0.05);
        }

        for (Entity entity : GeneralMethods.getEntitiesAroundPoint(whip.head, 1.5)) {
            if (!(entity instanceof LivingEntity)) continue;
            if (entity.getUniqueId().equals(player.getUniqueId())) continue;
            if (GeneralMethods.isRegionProtectedFromBuild(this, entity.getLocation())) continue;
            if (isAlreadyGrabbed(entity)) continue;

            whip.target = (LivingEntity) entity;
            whip.state = State.TETHERED;
            whip.tetherStartTime = System.currentTimeMillis();
            DamageHandler.damageEntity(whip.target, damage, this);
            whip.target.setFireTicks(60);

            if (world != null) {
                world.playSound(whip.target.getLocation(), Sound.ENTITY_LEASH_KNOT_PLACE, 1.5f, 1.0f);
            }
            return;
        }

        Location playerHand = player.getLocation().add(0, 1.2, 0);
        drawLine(playerHand, whip.head, false);
    }

    private boolean isAlreadyGrabbed(Entity entity) {
        for (WhipInstance w : whips) {
            if (w.target != null && w.target.getUniqueId().equals(entity.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    private void progressTethered(WhipInstance whip, Iterator<WhipInstance> it) {
        if (!isTargetValid(whip.target)) {
            it.remove();
            return;
        }
        if (System.currentTimeMillis() - whip.tetherStartTime > tetherDuration) {
            it.remove();
            return;
        }

        Location playerHand = player.getLocation().add(0, 1.2, 0);
        Location targetBody = whip.target.getLocation().add(0, 1.0, 0);

        if (!playerHand.getWorld().equals(targetBody.getWorld())) {
            it.remove();
            return;
        }

        double currentDistance = playerHand.distance(targetBody);

        if (currentDistance > range) {
            Vector pullBack = playerHand.toVector().subtract(targetBody.toVector());
            if (pullBack.lengthSquared() > 0.01) {
                pullBack.normalize().multiply(0.5);
                whip.target.setVelocity(whip.target.getVelocity().add(pullBack));
            }
        }

        whip.target.setFireTicks(40);
        drawLine(playerHand, targetBody, false);
    }

    private void progressPulling(WhipInstance whip, Iterator<WhipInstance> it) {
        if (!isTargetValid(whip.target)) {
            it.remove();
            return;
        }

        Location playerLoc = player.getLocation();
        Location targetLoc = whip.target.getLocation();

        if (!playerLoc.getWorld().equals(targetLoc.getWorld())) {
            it.remove();
            return;
        }

        double distance = playerLoc.distance(targetLoc);

        if (distance <= 1.5) {
            whip.target.setVelocity(new Vector(0, 0, 0));
            it.remove();
            return;
        }

        Vector pullVec = playerLoc.toVector().subtract(targetLoc.toVector());
        if (pullVec.lengthSquared() > 0.01) {
            pullVec.normalize().multiply(pullSpeed);
            whip.target.setVelocity(pullVec);
        }
        whip.target.setFallDistance(0);

        Location playerHand = playerLoc.clone().add(0, 1.2, 0);
        drawLine(playerHand, targetLoc.clone().add(0, 1, 0), true);
    }

    private boolean isTargetValid(LivingEntity target) {
        return target != null
                && !target.isDead()
                && target.isValid()
                && target.getWorld().equals(player.getWorld());
    }

    private void drawLine(Location start, Location end, boolean straight) {
        World world = start.getWorld();
        if (world == null || end.getWorld() == null || !world.equals(end.getWorld())) return;

        Vector dir = end.toVector().subtract(start.toVector());
        double dist = dir.length();
        if (dist < 0.01) return;
        dir.normalize();

        for (double d = 0; d < dist; d += 0.5) {
            Location point = start.clone().add(dir.clone().multiply(d));
            if (!straight) {
                double heightSag = Math.sin((d / dist) * Math.PI) * 0.3;
                point.add(0, -heightSag, 0);
            }
            world.spawnParticle(Particle.FLAME, point, 1, 0, 0, 0, 0);
        }
    }

    public void pullTargets() {
        boolean pulled = false;
        for (WhipInstance whip : whips) {
            if (whip.state == State.TETHERED && whip.target != null) {
                whip.state = State.PULLING;
                DamageHandler.damageEntity(whip.target, pullDamage, this);
                pulled = true;
            }
        }
        if (pulled) {
            World world = player.getWorld();
            if (world != null) {
                world.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.5f);
            }
        }
    }

    public boolean isTarget(Player p) {
        if (p == null) return false;
        for (WhipInstance whip : whips) {
            if ((whip.state == State.TETHERED || whip.state == State.PULLING)
                    && whip.target != null
                    && whip.target.getUniqueId().equals(p.getUniqueId())) {
                return true;
            }
        }
        return false;
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
        return "PhoenixWhip";
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
        config.addDefault(PATH + "Cooldown", 8000);
        config.addDefault(PATH + "Range", 12.0);
        config.addDefault(PATH + "InitialDamage", 4.0);
        config.addDefault(PATH + "PullDamage", 2.0);
        config.addDefault(PATH + "TetherDuration", 5000);
        config.addDefault(PATH + "MaxWhips", 3);
        config.addDefault(PATH + "ExtendSpeed", 2.0);
        config.addDefault(PATH + "PullSpeed", 2.0);
        config.options().copyDefaults(true);
        ConfigManager.defaultConfig.save();

        cooldown = config.getLong(PATH + "Cooldown");
        range = config.getDouble(PATH + "Range");
        damage = config.getDouble(PATH + "InitialDamage");
        pullDamage = config.getDouble(PATH + "PullDamage");
        tetherDuration = config.getLong(PATH + "TetherDuration");
        maxWhips = config.getInt(PATH + "MaxWhips");
        extendSpeed = config.getDouble(PATH + "ExtendSpeed");
        pullSpeed = config.getDouble(PATH + "PullSpeed");

        if (listener == null) {
            listener = new PhoenixWhipListener();
            PhoenixAddonLoader.getPlugin().getServer().getPluginManager().registerEvents(listener, PhoenixAddonLoader.getPlugin());
        }
    }

    @Override
    public void stop() {
        for (PhoenixWhip whip : new ArrayList<>(CoreAbility.getAbilities(PhoenixWhip.class))) {
            whip.remove();
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
        return "Создайте извивающийся хлыст из концентрированной плазмы! Выпускайте несколько хлыстов кликом. Связанные враги блокируют магию.";
    }

    @Override
    public String getInstructions() {
        return "ЛКМ для выпуска хлыста. Shift для притягивания всех связанных врагов.";
    }

    public static class PhoenixWhipListener implements Listener {

        @EventHandler
        public void onAbilityStart(AbilityStartEvent event) {
            Ability ability = event.getAbility();
            if (ability == null) return;

            Player caster = ability.getPlayer();
            if (caster == null) return;

            for (PhoenixWhip whip : CoreAbility.getAbilities(PhoenixWhip.class)) {
                if (whip.getPlayer() != null
                        && !whip.getPlayer().getUniqueId().equals(caster.getUniqueId())
                        && whip.isTarget(caster)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        @EventHandler
        public void onClick(PlayerInteractEvent event) {
            if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
            if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) return;

            Player player = event.getPlayer();
            if (!player.hasPermission("projectkorra.ability.PhoenixWhip")) return;

            BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
            if (bPlayer == null || !bPlayer.canBend(CoreAbility.getAbility(PhoenixWhip.class))) return;

            PhoenixWhip existing = CoreAbility.getAbility(player, PhoenixWhip.class);
            if (existing != null) {
                existing.shootNewWhip();
            } else {
                new PhoenixWhip(player);
            }
        }

        @EventHandler
        public void onSneak(PlayerToggleSneakEvent event) {
            if (!event.isSneaking()) return;

            PhoenixWhip whip = CoreAbility.getAbility(event.getPlayer(), PhoenixWhip.class);
            if (whip != null) {
                whip.pullTargets();
            }
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            PhoenixWhip whip = CoreAbility.getAbility(event.getPlayer(), PhoenixWhip.class);
            if (whip != null) {
                whip.remove();
            }
        }
    }
}
