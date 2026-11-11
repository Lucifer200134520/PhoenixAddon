package com.myaddons.phoenixaddon;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.FireAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Cauterize extends FireAbility implements AddonAbility {

    private static final String PATH = "ExtraAbilities.Lucifer200134520.Fire.Cauterize.";

    private static long cooldown;
    private static double healthCost;
    private static double healAmount;
    private static int regenerationLevel;
    private static long regenerationDuration;
    private static long fireResistanceDuration;
    private static double damageReduction;

    private static Listener listener;
    private static Map<UUID, Long> activeEffects = new HashMap<>();

    private long startTime;
    private boolean applied;

    public Cauterize(Player player) {
        super(player);

        if (player == null || bPlayer == null) return;
        if (!player.hasPermission("projectkorra.ability.Cauterize")) return;
        if (bPlayer.isOnCooldown(this)) return;

        double maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        double currentHealth = player.getHealth();


        if (currentHealth - healthCost < 4.0) {
            player.sendMessage("§cНедостаточно здоровья: нужно минимум " + (healthCost + 4.0) + " HP");
            return;
        }

        start();

        if (!isStarted() || isRemoved()) return;

        this.startTime = System.currentTimeMillis();
        this.applied = false;

        player.setHealth(currentHealth - healthCost);

        Location loc = player.getLocation().add(0, 1, 0);
        if (loc.getWorld() != null) {
            loc.getWorld().spawnParticle(Particle.LAVA, loc, 6, 0.5, 0.5, 0.5, 0);
            loc.getWorld().spawnParticle(Particle.FLAME, loc, 18, 0.5, 0.6, 0.5, 0.06);
            loc.getWorld().playSound(loc, Sound.ENTITY_BLAZE_SHOOT, 1.2f, 0.4f);
            loc.getWorld().playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, 1.2f, 1.2f);
        }

        bPlayer.addCooldown(this, cooldown);
    }

    @Override
    public void progress() {
        if (player == null || !player.isOnline() || player.isDead()) {
            remove();
            return;
        }

        if (!applied) {
            applyEffect();
            applied = true;
        }

        if (System.currentTimeMillis() - startTime > regenerationDuration) {
            remove();
        }
    }

    private void applyEffect() {
        double currentHealth = player.getHealth();
        double maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        double newHealth = Math.min(maxHealth, currentHealth + healAmount);
        player.setHealth(newHealth);

        player.setFireTicks(0);
        player.removePotionEffect(PotionEffectType.POISON);

        int regenTicks = (int) (regenerationDuration / 50);
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, regenTicks, regenerationLevel - 1, false, true));

        int fireResTicks = (int) (fireResistanceDuration / 50);
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, fireResTicks, 0, false, true));

        activeEffects.put(player.getUniqueId(), System.currentTimeMillis() + regenerationDuration);

        Location loc = player.getLocation().add(0, 1, 0);
        if (loc.getWorld() != null) {
            loc.getWorld().spawnParticle(Particle.FLAME, loc, 12, 0.4, 0.6, 0.4, 0.05);
            loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
        }

    }

    @Override
    public void remove() {
        if (player != null) {
            activeEffects.remove(player.getUniqueId());
        }
        super.remove();
    }

    public static double getActiveDamageReduction(Player player) {
        if (activeEffects.containsKey(player.getUniqueId())) {
            long expiryTime = activeEffects.get(player.getUniqueId());
            if (System.currentTimeMillis() < expiryTime) {
                return damageReduction;
            } else {
                activeEffects.remove(player.getUniqueId());
            }
        }
        return 0.0;
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
        return "Cauterize";
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
        config.addDefault(PATH + "Cooldown", 45000);
        config.addDefault(PATH + "HealthCost", 3.0);
        config.addDefault(PATH + "HealAmount", 2.0);
        config.addDefault(PATH + "RegenerationLevel", 2);
        config.addDefault(PATH + "RegenerationDuration", 6000);
        config.addDefault(PATH + "FireResistanceDuration", 8000);
        config.addDefault(PATH + "DamageReduction", 0.15);
        config.options().copyDefaults(true);
        ConfigManager.defaultConfig.save();

        cooldown = config.getLong(PATH + "Cooldown");
        healthCost = config.getDouble(PATH + "HealthCost");
        healAmount = config.getDouble(PATH + "HealAmount");
        regenerationLevel = config.getInt(PATH + "RegenerationLevel");
        regenerationDuration = config.getLong(PATH + "RegenerationDuration");
        fireResistanceDuration = config.getLong(PATH + "FireResistanceDuration");
        damageReduction = config.getDouble(PATH + "DamageReduction");

        if (listener == null) {
            listener = new CauterizeListener();
            PhoenixAddonLoader.getPlugin().getServer().getPluginManager().registerEvents(listener, PhoenixAddonLoader.getPlugin());
        }
    }

    @Override
    public void stop() {
        activeEffects.clear();
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
        return "Прижгите раны огнём: вы теряете " + (int) healthCost + " HP, затем получаете " + (int) healAmount
                + " HP, регенерацию " + regenerationLevel + " на " + (regenerationDuration / 1000) + " сек, "
                + (int) Math.round(damageReduction * 100) + "% защиты от урона, иммунитет к огню и очищение от яда.";
    }

    @Override
    public String getInstructions() {
        return "Зажмите Shift и нажмите ЛКМ для активации.";
    }

    public static class CauterizeListener implements Listener {

        @EventHandler
        public void onClick(PlayerInteractEvent event) {
            if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
            if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) return;

            Player player = event.getPlayer();
            if (!player.isSneaking()) return;
            if (!player.hasPermission("projectkorra.ability.Cauterize")) return;

            BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
            if (bPlayer == null || !bPlayer.canBend(CoreAbility.getAbility(Cauterize.class))) return;

            new Cauterize(player);
        }

        @EventHandler
        public void onDamage(EntityDamageEvent event) {
            if (!(event.getEntity() instanceof Player)) return;

            Player player = (Player) event.getEntity();
            double reduction = Cauterize.getActiveDamageReduction(player);

            if (reduction > 0) {
                double newDamage = event.getDamage() * (1.0 - reduction);
                event.setDamage(newDamage);

                Location loc = player.getLocation().add(0, 1, 0);
                if (loc.getWorld() != null) {
                    loc.getWorld().spawnParticle(Particle.FLAME, loc, 4, 0.4, 0.4, 0.4, 0.03);
                    loc.getWorld().playSound(loc, Sound.ENTITY_BLAZE_AMBIENT, 0.3f, 1.5f);
                }
            }
        }
    }
}
