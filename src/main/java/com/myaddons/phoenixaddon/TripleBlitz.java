package com.myaddons.phoenixaddon;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.FireAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

public class TripleBlitz extends FireAbility implements AddonAbility, Listener {

    private static final String PATH = "ExtraAbilities.Lucifer_ZeIIDriS.Fire.TripleBlitz.";
    private static final int MAX_CHARGES = 3;

    private static long fullCooldown = 12000;
    private static long partialCooldown = 6000;
    private static long timeWindow = 1500;
    private static long delayBetweenDashes = 400;
    private static double dashPower = 3.0;

    private int charges;
    private long lastDashTime;
    private boolean cooldownApplied;

    public TripleBlitz(Player player) {
        super(player);

        if (player == null || bPlayer == null) return;
        if (!player.hasPermission("projectkorra.ability.TripleBlitz")) return;
        if (bPlayer.isOnCooldown(this)) return;

        this.charges = MAX_CHARGES;
        this.lastDashTime = System.currentTimeMillis();

        start();
        performDash();
    }

    public void performDash() {
        if (charges <= 0) return;
        if (charges < MAX_CHARGES && System.currentTimeMillis() < lastDashTime + delayBetweenDashes) return;

        Vector direction = player.getLocation().getDirection().normalize();

        if (Double.isNaN(direction.getX()) || Double.isNaN(direction.getY()) || Double.isNaN(direction.getZ())) {
            remove();
            return;
        }

        direction.multiply(dashPower);

        if (direction.getY() < 0.2) {
            direction.setY(0.2);
        }

        player.setVelocity(new Vector(0, 0, 0));
        player.setVelocity(direction);
        player.setFallDistance(0);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.5f);

        Particle.DustOptions yellowDust = new Particle.DustOptions(Color.YELLOW, 1.2F);
        player.getWorld().spawnParticle(Particle.REDSTONE, player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0, yellowDust);

        charges--;
        lastDashTime = System.currentTimeMillis();

        if (charges <= 0) {
            remove();
        }
    }

    private void applyCooldown() {
        if (cooldownApplied || bPlayer == null) return;
        cooldownApplied = true;

        if (charges <= 0) {
            bPlayer.addCooldown(this, fullCooldown);
        } else if (charges < MAX_CHARGES) {
            bPlayer.addCooldown(this, partialCooldown);
        }
    }

    @Override
    public void remove() {
        applyCooldown();
        super.remove();
    }

    @Override
    public void progress() {
        if (player == null || !player.isOnline() || player.isDead()) {
            remove();
            return;
        }

        if (!bPlayer.canBendIgnoreCooldowns(this)) {
            remove();
            return;
        }

        if (System.currentTimeMillis() > lastDashTime + timeWindow) {
            remove();
        }
    }

    @EventHandler
    public void onClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) return;

        Player p = event.getPlayer();
        if (!p.hasPermission("projectkorra.ability.TripleBlitz")) return;

        BendingPlayer bendingPlayer = BendingPlayer.getBendingPlayer(p);
        if (bendingPlayer == null) return;
        if (!getName().equalsIgnoreCase(bendingPlayer.getBoundAbilityName())) return;

        TripleBlitz existing = CoreAbility.getAbility(p, TripleBlitz.class);
        if (existing != null) {
            existing.performDash();
        } else if (bendingPlayer.canBend(CoreAbility.getAbility(TripleBlitz.class))) {
            new TripleBlitz(p);
        }
    }

    @Override
    public void load() {
        PhoenixElement.register();
        PhoenixAddonLoader.getPlugin().getServer().getPluginManager().registerEvents(this, PhoenixAddonLoader.getPlugin());

        FileConfiguration config = ConfigManager.defaultConfig.get();
        config.addDefault(PATH + "FullCooldown", 12000);
        config.addDefault(PATH + "PartialCooldown", 6000);
        config.addDefault(PATH + "TimeWindow", 1500);
        config.addDefault(PATH + "DelayBetweenDashes", 400);
        config.addDefault(PATH + "DashPower", 3.0);
        config.options().copyDefaults(true);
        ConfigManager.defaultConfig.save();

        fullCooldown = config.getLong(PATH + "FullCooldown", 12000);
        partialCooldown = config.getLong(PATH + "PartialCooldown", 6000);
        timeWindow = config.getLong(PATH + "TimeWindow", 1500);
        delayBetweenDashes = config.getLong(PATH + "DelayBetweenDashes", 400);
        dashPower = config.getDouble(PATH + "DashPower", 3.0);
    }

    @Override
    public void stop() {
        HandlerList.unregisterAll(this);
    }

    @Override
    public String getDescription() {
        return "Пропустите через себя энергию феникса, чтобы совершить серию молниеносных рывков! Эта способность позволяет вам сделать до трёх рывков вперёд. Если вы используете все 3 заряда, способность уйдёт в полное время перезарядки. Однако, если вы остановитесь после первого или второго рывка, время перезарядки будет значительно короче.";
    }

    @Override
    public String getInstructions() {
        return "Нажмите Левый клик (ЛКМ) для рывка. Можно использовать до 3 раз подряд.";
    }

    @Override
    public boolean isHarmlessAbility() { return false; }

    @Override
    public boolean isSneakAbility() { return false; }

    @Override
    public String getAuthor() { return "Lucifer200134520 & ZeIIDriS"; }

    @Override
    public String getVersion() { return "1.1"; }

    @Override
    public String getName() { return "TripleBlitz"; }

    @Override
    public Element getElement() { return PhoenixElement.PHOENIX; }

    @Override
    public Location getLocation() { return player != null ? player.getLocation() : null; }

    @Override
    public long getCooldown() { return fullCooldown; }
}