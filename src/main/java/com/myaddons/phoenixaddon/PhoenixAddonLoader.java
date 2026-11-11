package com.myaddons.phoenixaddon;

import com.myaddons.phoenixaddon.commands.RebirthToggleCommand;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.ComboAbility;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.PassiveAbility;
import com.projectkorra.projectkorra.event.AbilityStartEvent;
import com.projectkorra.projectkorra.ability.util.ComboManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PhoenixAddonLoader extends JavaPlugin {

    private static final String ABILITY_PACKAGE = "com.myaddons.phoenixaddon";

    private static PhoenixAddonLoader plugin;

    private final List<AddonAbility> loadedAbilities = new ArrayList<>();
    private final List<String> comboNames = new ArrayList<>();

    /** Способность-маркер: если её нет в реестре ProjectKorra, значит /b reload сбросил наши способности. */
    private static final String MARKER_ABILITY = "PhoenixNova";

    private BukkitTask watchdog;
    private boolean registering;

    @Override
    public void onEnable() {
        plugin = this;

        if (Bukkit.getPluginManager().getPlugin("ProjectKorra") == null) {
            getLogger().severe("ProjectKorra не найден! Отключаю PhoenixAddon.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        Rebirth.loadDisabled();

        Bukkit.getPluginManager().registerEvents(new SafetyListener(), this);

        if (getCommand("rebirthtoggle") != null) {
            getCommand("rebirthtoggle").setExecutor(new RebirthToggleCommand());
        }

        Bukkit.getScheduler().runTaskLater(this, () -> {
            registerAbilities();
            startWatchdog();
        }, 20L);
    }

    /**
     * /b reload в ProjectKorra останавливает все AddonAbility (stop()), полностью очищает реестр
     * способностей и ComboManager, а затем заново сканирует только папку abilities/.
     * Способности, зарегистрированные через registerPluginAbilities (как у нас), при этом теряются,
     * а load() больше не вызывается, поэтому слушатели остаются отписанными.
     * Следим за реестром и при потере регистрируем всё заново.
     */
    private void startWatchdog() {
        if (watchdog != null) {
            watchdog.cancel();
        }
        watchdog = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (registering) return;
            if (CoreAbility.getAbility(MARKER_ABILITY) == null) {
                registerAbilities();
            }
        }, 10L, 10L);
    }

    private void registerAbilities() {
        registering = true;
        try {
            // чистим остатки прошлой регистрации (важно при повторной регистрации после /b reload)
            loadedAbilities.clear();
            for (String name : comboNames) {
                try {
                    ComboManager.getComboAbilities().remove(name);
                    ComboManager.getDescriptions().remove(name);
                    ComboManager.getInstructions().remove(name);
                    ComboManager.getAuthors().remove(name);
                } catch (Throwable ignored) {
                }
            }
            comboNames.clear();

            PhoenixElement.register();

            CoreAbility.registerPluginAbilities(this, ABILITY_PACKAGE);

            for (CoreAbility ability : new ArrayList<>(CoreAbility.getAbilities())) {
                if (!(ability instanceof AddonAbility)) continue;
                if (!ability.getClass().getName().startsWith(ABILITY_PACKAGE + ".")) continue;
                if (ability.getClass().getClassLoader() != getClass().getClassLoader()) continue;

                AddonAbility addon = (AddonAbility) ability;
                try {
                    addon.load();
                    if (ability instanceof ComboAbility) {
                        ComboAbility combo = (ComboAbility) ability;
                        String name = ability.getName();
                        ComboManager.getComboAbilities().put(name, new ComboManager.ComboAbilityInfo(name, combo.getCombination(), combo));
                        ComboManager.getDescriptions().put(name, ability.getDescription());
                        ComboManager.getInstructions().put(name, ability.getInstructions());
                        ComboManager.getAuthors().put(name, addon.getAuthor());
                        comboNames.add(name);
                    }
                    loadedAbilities.add(addon);
                } catch (Throwable t) {
                    getLogger().severe("Не удалось загрузить способность " + ability.getName() + ": " + t);
                    t.printStackTrace();
                }
            }

            Cooled.start(this);

        } catch (Throwable e) {
            getLogger().severe("Ошибка загрузки способностей: " + e.getMessage());
            e.printStackTrace();
        } finally {
            registering = false;
        }
    }

    @Override
    public void onDisable() {
        if (watchdog != null) {
            watchdog.cancel();
            watchdog = null;
        }
        Cooled.stop();
        for (AddonAbility addon : loadedAbilities) {
            try {
                addon.stop();
            } catch (Throwable ignored) {
            }
        }
        loadedAbilities.clear();
        for (String name : comboNames) {
            try {
                ComboManager.getComboAbilities().remove(name);
                ComboManager.getDescriptions().remove(name);
                ComboManager.getInstructions().remove(name);
                ComboManager.getAuthors().remove(name);
            } catch (Throwable ignored) {
            }
        }
        comboNames.clear();
        getLogger().info("PhoenixAddon выгружен.");
    }

    public static PhoenixAddonLoader getPlugin() {
        return plugin;
    }

    public static final class SafetyListener implements Listener {

        private static void sanitize(Player player) {
            try {
                if (!player.hasGravity()) {
                    player.setGravity(true);
                }
                if (player.getWalkSpeed() < 0.02f) {
                    player.setWalkSpeed(0.2f);
                }
                if (player.getFlySpeed() < 0.005f) {
                    player.setFlySpeed(0.1f);
                }
            } catch (Throwable ignored) {
            }
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onAbilityStart(AbilityStartEvent event) {
            if (!(event.getAbility() instanceof CoreAbility)) return;

            CoreAbility started = (CoreAbility) event.getAbility();
            if (started instanceof PassiveAbility) return;

            Player owner = started.getPlayer();
            if (owner == null) return;

            UUID id = owner.getUniqueId();
            if (PrometheusChain.isHeld(id) || PhoenixSnare.isCaught(id) || PhoenixBird.isScouting(id)) {
                event.setCancelled(true);
            }
        }

        @EventHandler
        public void onJoin(PlayerJoinEvent event) {
            sanitize(event.getPlayer());
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (event.getPlayer().isOnline()) {
                    sanitize(event.getPlayer());
                }
            }, 40L);
        }

        @EventHandler
        public void onRespawn(PlayerRespawnEvent event) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (event.getPlayer().isOnline()) {
                    sanitize(event.getPlayer());
                }
            });
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            if (!event.getPlayer().hasGravity()) {
                try {
                    event.getPlayer().setGravity(true);
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
