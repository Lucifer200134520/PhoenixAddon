package com.myaddons.phoenixaddon;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.FireAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.event.AbilityStartEvent;
import com.projectkorra.projectkorra.util.DamageHandler;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class IgniteBlood extends FireAbility implements AddonAbility {

    private static final String PATH = "ExtraAbilities.Lucifer200134520.Fire.IgniteBlood.";

    private static long cooldown;
    private static double range;
    private static long duration;
    private static double explosionDamage;
    private static int blindnessDuration;
    private static Listener listener;
    private static Map<UUID, IgniteBlood> markedTargets = new HashMap<>();

    private LivingEntity target;
    private long startTime;
    private double particleAngle = 0;

    public IgniteBlood(Player player) {
        super(player);

        if (player == null || bPlayer == null) return;
        if (!player.hasPermission("projectkorra.ability.IgniteBlood")) return;
        if (bPlayer.isOnCooldown(this)) return;
        if (hasAbility(player, IgniteBlood.class)) return;

        Entity entityTarget = GeneralMethods.getTargetedEntity(player, range);
        if (!(entityTarget instanceof LivingEntity)) return;
        if (entityTarget.getUniqueId().equals(player.getUniqueId())) return;

        UUID targetId = entityTarget.getUniqueId();
        if (markedTargets.containsKey(targetId)) {
            return;
        }

        this.target = (LivingEntity) entityTarget;
        this.startTime = System.currentTimeMillis();


        if (!isStarted() || isRemoved()) return;

        bPlayer.addCooldown(this, cooldown);
        markedTargets.put(target.getUniqueId(), this);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.2f, 1.8f);
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 0.3f);

        if (target instanceof Player) {
            Player pTarget = (Player) target;
            pTarget.playSound(target.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 0.4f);
        }
    }

    @Override
    public void progress() {
        if (player == null || !player.isOnline()) {
            remove();
            return;
        }

        if (target == null || target.isDead() || !target.isValid() || !target.getWorld().equals(player.getWorld())) {
            remove();
            return;
        }

        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed > duration) {
            expireMarking();
            return;
        }

        double progress = (double) elapsed / duration;

        Location head = target.getLocation().add(0, target.getHeight() + 0.2, 0);
        if (head.getWorld() == null) return;

        particleAngle += 0.4;


        double x = Math.cos(particleAngle) * 0.6;
        double z = Math.sin(particleAngle) * 0.6;

        Location pLoc1 = head.clone().add(x, 0.2, z);
        Location pLoc2 = head.clone().add(-x, -0.2, -z);

        Particle.DustOptions dust = new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 140, 20), 1.2f);
        head.getWorld().spawnParticle(Particle.REDSTONE, pLoc1, 2, 0, 0, 0, 0, dust);
        head.getWorld().spawnParticle(Particle.REDSTONE, pLoc2, 2, 0, 0, 0, 0, dust);

        if (Math.random() < 0.25) {
            head.getWorld().spawnParticle(Particle.FLAME, head, 2, 0.4, 0.15, 0.4, 0.02);
        }

        if (progress > 0.25) {
            int circleParts = 20;
            for (int i = 0; i < circleParts; i++) {
                double angle = (Math.PI * 2 / circleParts) * i + particleAngle * 0.5;
                double px = Math.cos(angle) * (0.8 + progress * 0.6);
                double pz = Math.sin(angle) * (0.8 + progress * 0.6);

                Location circleLoc = head.clone().add(px, -0.3, pz);
                Particle.DustOptions brightDust = new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 205, 80), 1.0f);
                head.getWorld().spawnParticle(Particle.REDSTONE, circleLoc, 1, 0, 0, 0, 0, brightDust);
            }
        }

        if (progress > 0.5) {
            if (Math.random() < 0.4) {
                double angle = Math.random() * Math.PI * 2;
                Location burstLoc = head.clone().add(Math.cos(angle) * 1.2, -0.2, Math.sin(angle) * 1.2);
                Particle.DustOptions fireDust = new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 125, 10), 1.4f);
                head.getWorld().spawnParticle(Particle.REDSTONE, burstLoc, 3, 0.15, 0.15, 0.15, 0, fireDust);
            }

            if (target instanceof Player && Math.random() < 0.12) {
                ((Player) target).playSound(target.getLocation(), Sound.BLOCK_FIRE_AMBIENT, 0.4f, 0.6f);
            }
        }

        if (progress > 0.75) {
            if (Math.random() < 0.6) {
                Location burstLoc = head.clone().add(
                    (Math.random() - 0.5) * 1.8,
                    Math.random() * 0.7,
                    (Math.random() - 0.5) * 1.8
                );
                head.getWorld().spawnParticle(Particle.LAVA, burstLoc, 2, 0.1, 0.1, 0.1, 0);
            }

            if (target instanceof Player && Math.random() < 0.2) {
                ((Player) target).playSound(target.getLocation(), Sound.ENTITY_BLAZE_AMBIENT, 0.6f, 0.5f);
            }
        }
    }

    public void triggerExplosion() {
        if (target == null || target.isDead() || !markedTargets.containsKey(target.getUniqueId())) return;

        Location loc = target.getLocation().add(0, target.getHeight() / 2, 0);
        if (loc.getWorld() == null) return;

        DamageHandler.damageEntity(target, explosionDamage, this);
        target.setFireTicks(120);

        if (target instanceof Player) {
            Player pTarget = (Player) target;
            pTarget.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindnessDuration, 1, false, false));
            pTarget.playSound(pTarget.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.7f);
        }

        loc.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, loc, 8, 0.7, 0.7, 0.7, 0.2);
        loc.getWorld().spawnParticle(Particle.LAVA, loc, 50, 1.0, 1.0, 1.0, 0.2);
        loc.getWorld().spawnParticle(Particle.FLAME, loc, 40, 0.8, 0.8, 0.8, 0.15);
        loc.getWorld().spawnParticle(Particle.REDSTONE, loc, 20, 0.5, 0.5, 0.5, 0,
            new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 100, 0), 1.5f));
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.8f, 1.0f);
        loc.getWorld().playSound(loc, Sound.ENTITY_BLAZE_HURT, 1.2f, 0.6f);

        remove();
    }

    private void expireMarking() {
        remove();
    }

    public LivingEntity getTarget() {
        return target;
    }

    @Override
    public void remove() {
        if (target != null) {
            markedTargets.remove(target.getUniqueId());
        }
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
        return "IgniteBlood";
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
        config.addDefault(PATH + "Cooldown", 10000);
        config.addDefault(PATH + "Range", 16.0);
        config.addDefault(PATH + "Duration", 9000);
        config.addDefault(PATH + "ExplosionDamage", 5.5);
        config.addDefault(PATH + "BlindnessDuration", 80);
        config.options().copyDefaults(true);
        ConfigManager.defaultConfig.save();

        cooldown = config.getLong(PATH + "Cooldown");
        range = config.getDouble(PATH + "Range");
        duration = config.getLong(PATH + "Duration");
        explosionDamage = config.getDouble(PATH + "ExplosionDamage");
        blindnessDuration = config.getInt(PATH + "BlindnessDuration");

        if (listener == null) {
            listener = new IgniteBloodListener();
            PhoenixAddonLoader.getPlugin().getServer().getPluginManager().registerEvents(listener, PhoenixAddonLoader.getPlugin());
        }
    }

    @Override
    public void stop() {
        markedTargets.clear();
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
        return "Пометьте врага меткой кипящей крови (9 сек). Взрывается, если помеченный враг атакует кого-либо или использует способность!";
    }

    @Override
    public String getInstructions() {
        return "ЛКМ на врага - метка активируется при его атаке или способности.";
    }

    public static class IgniteBloodListener implements Listener {

        @EventHandler
        public void onClick(PlayerInteractEvent event) {
            if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
            if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) return;

            Player player = event.getPlayer();
            if (!player.hasPermission("projectkorra.ability.IgniteBlood")) return;

            BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
            if (bPlayer == null || !bPlayer.canBend(CoreAbility.getAbility(IgniteBlood.class))) return;

            new IgniteBlood(player);
        }

        @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
        public void onAbilityCast(AbilityStartEvent event) {
            if (event.getAbility() instanceof Rebirth) return;
            if (event.getAbility() instanceof IgniteBlood) return;

            Player caster = event.getAbility().getPlayer();

            IgniteBlood marked = markedTargets.values().stream()
                .filter(ib -> ib.getTarget() != null && ib.getTarget().getEntityId() == caster.getEntityId())
                .findFirst()
                .orElse(null);

            if (marked != null) {
                marked.triggerExplosion();
            }
        }

        @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
        public void onEntityAttack(EntityDamageByEntityEvent event) {
            if (!(event.getDamager() instanceof LivingEntity)) return;

            LivingEntity attacker = (LivingEntity) event.getDamager();

            IgniteBlood marked = markedTargets.values().stream()
                .filter(ib -> ib.getTarget() != null && ib.getTarget().getEntityId() == attacker.getEntityId())
                .findFirst()
                .orElse(null);

            if (marked != null) {
                marked.triggerExplosion();
            }
        }
    }
}
