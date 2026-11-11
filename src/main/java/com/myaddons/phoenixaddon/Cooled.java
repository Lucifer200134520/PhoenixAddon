package com.myaddons.phoenixaddon;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.event.AbilityDamageEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class Cooled {

    private static final String PATH = "ExtraAbilities.Lucifer200134520.Fire.Cooled.";
    private static final int PERIOD = 2;

    private static boolean enabled;
    private static int waterTicks;
    private static long debuffMillis;
    private static double damageReduction;
    private static int slownessAmplifier;
    private static boolean obstructView;

    private static final Map<UUID, State> STATES = new HashMap<>();

    private static BukkitTask task;
    private static Listener listener;

    private static class State {
        int inWater;
        long until;
        int tick;
    }

    private Cooled() {
    }

    public static void start(JavaPlugin plugin) {
        stop();

        FileConfiguration config = ConfigManager.defaultConfig.get();
        config.addDefault(PATH + "Enabled", true);
        config.addDefault(PATH + "WaterSeconds", 3.0);
        config.addDefault(PATH + "DebuffSeconds", 20.0);
        config.addDefault(PATH + "DamageReduction", 0.25);
        config.addDefault(PATH + "SlownessAmplifier", 1);
        config.addDefault(PATH + "ObstructView", true);
        config.options().copyDefaults(true);
        ConfigManager.defaultConfig.save();

        enabled = config.getBoolean(PATH + "Enabled");
        waterTicks = (int) Math.max(PERIOD, config.getDouble(PATH + "WaterSeconds") * 20);
        debuffMillis = (long) (config.getDouble(PATH + "DebuffSeconds") * 1000);
        damageReduction = Math.max(0.0, Math.min(0.95, config.getDouble(PATH + "DamageReduction")));
        slownessAmplifier = Math.max(0, Math.min(4, config.getInt(PATH + "SlownessAmplifier")));
        obstructView = config.getBoolean(PATH + "ObstructView");

        if (!enabled) return;

        listener = new CooledListener();
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, Cooled::tick, 20L, PERIOD);
    }

    public static void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
        STATES.clear();
    }

    public static boolean isCooled(Player player) {
        State state = STATES.get(player.getUniqueId());
        return state != null && state.until > System.currentTimeMillis();
    }

    private static boolean isOwner(Player player) {
        if (player.getGameMode() == GameMode.SPECTATOR) return false;
        BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
        if (bPlayer == null || PhoenixElement.PHOENIX == null) return false;
        return bPlayer.hasElement(PhoenixElement.PHOENIX);
    }

    private static void tick() {
        long now = System.currentTimeMillis();

        Iterator<Map.Entry<UUID, State>> it = STATES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, State> entry = it.next();
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null || !p.isOnline()) {
                it.remove();
            }
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isOwner(player)) {
                STATES.remove(player.getUniqueId());
                continue;
            }

            State state = STATES.computeIfAbsent(player.getUniqueId(), id -> new State());
            state.tick++;

            boolean inWater = player.isInWater();
            boolean wasCooled = state.until > now;

            if (inWater) {
                state.inWater += PERIOD;
            } else {
                state.inWater = 0;
            }

            if (inWater && state.inWater >= waterTicks) {
                state.until = now + debuffMillis;

                if (!wasCooled) {
                    announce(player);
                }
            }

            boolean cooled = state.until > now;

            if (cooled) {
                applyDebuff(player, state, now);
            } else if (wasCooled) {
                player.playSound(player.getLocation(), Sound.BLOCK_FIRE_AMBIENT, 1.0f, 1.2f);
                state.until = 0;
            }
        }
    }

    private static void announce(Player player) {
        World world = player.getWorld();
        Location loc = player.getLocation().add(0, 1, 0);

        world.spawnParticle(Particle.CLOUD, loc, 40, 0.5, 0.6, 0.5, 0.08);
        world.spawnParticle(Particle.SMOKE_LARGE, loc, 15, 0.4, 0.5, 0.4, 0.03);
        world.playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, 1.6f, 0.7f);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 1.2f, 0.8f);
    }

    private static void applyDebuff(Player player, State state, long now) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 30, slownessAmplifier, false, false, true));

        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector body = player.getLocation().toVector().add(new Vector(0, 1.0, 0));
        ThreadLocalRandom random = ThreadLocalRandom.current();

        if (obstructView) {
            world.spawnParticle(Particle.CLOUD, eye, 8, 0.35, 0.3, 0.35, 0.01);
            world.spawnParticle(Particle.SMOKE_LARGE, eye, 2, 0.4, 0.3, 0.4, 0.005);
        }

        world.spawnParticle(Particle.CLOUD, player.getLocation().add(0, 1.0, 0), 6, 0.45, 0.7, 0.45, 0.02);
        for (int i = 0; i < 4; i++) {
            Vector p = body.clone().add(new Vector(random.nextDouble(-0.5, 0.5), random.nextDouble(-0.9, 0.9), random.nextDouble(-0.5, 0.5)));
            PhoenixAscension.jet(world, Particle.CLOUD, p, new Vector(0, 1, 0), 0.06);
        }

        if (state.tick % 12 == 0) {
            world.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.4f);
        }
    }

    public static class CooledListener implements Listener {

        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void onAbilityDamage(AbilityDamageEntityEvent event) {
            Player source = event.getSource();
            if (source == null) return;
            if (!isCooled(source)) return;

            event.setDamage(event.getDamage() * (1.0 - damageReduction));
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            STATES.remove(event.getPlayer().getUniqueId());
        }
    }
}
