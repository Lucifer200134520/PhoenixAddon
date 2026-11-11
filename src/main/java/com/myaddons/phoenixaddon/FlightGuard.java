package com.myaddons.phoenixaddon;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class FlightGuard {

    private static final class Saved {
        boolean allowFlight;
        boolean flying;
        float flySpeed;
        int holders;
    }

    private static final Map<UUID, Saved> SAVED = new HashMap<>();

    private FlightGuard() {
    }

    public static void acquire(Player player) {
        UUID id = player.getUniqueId();
        Saved saved = SAVED.get(id);
        if (saved == null) {
            saved = new Saved();
            saved.allowFlight = player.getAllowFlight();
            saved.flying = player.isFlying();
            saved.flySpeed = player.getFlySpeed();
            SAVED.put(id, saved);
        }
        saved.holders++;
        player.setAllowFlight(true);
    }

    public static boolean savedAllowFlight(Player player) {
        Saved saved = SAVED.get(player.getUniqueId());
        return saved != null ? saved.allowFlight : player.getAllowFlight();
    }

    public static boolean savedFlying(Player player) {
        Saved saved = SAVED.get(player.getUniqueId());
        return saved != null ? saved.flying : player.isFlying();
    }

    public static float savedFlySpeed(Player player) {
        Saved saved = SAVED.get(player.getUniqueId());
        return saved != null ? saved.flySpeed : player.getFlySpeed();
    }

    public static void release(Player player) {
        UUID id = player.getUniqueId();
        Saved saved = SAVED.get(id);
        if (saved == null) return;

        saved.holders--;
        if (saved.holders > 0) return;
        SAVED.remove(id);

        if (!player.isOnline()) return;

        player.setFlySpeed(clampSpeed(saved.flySpeed));

        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            return;
        }

        if (saved.allowFlight) {
            player.setAllowFlight(true);
            player.setFlying(saved.flying);
        } else {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    public static float clampSpeed(float speed) {
        return Math.max(-1f, Math.min(1f, speed));
    }
}
