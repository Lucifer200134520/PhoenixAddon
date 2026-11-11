package com.myaddons.phoenixaddon;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.FireAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PhoenixBird extends FireAbility implements AddonAbility {

    private static final String PATH = "ExtraAbilities.Lucifer200134520.Fire.PhoenixBird.";

    private static long cooldown;
    private static long duration;
    private static double flightSpeed;
    private static double maxDistance;
    private static double revealRadius;
    private static int markTicks;
    private static boolean useGlowing;
    private static boolean teleportMode;
    private static double birdScale;
    private static boolean bodyVulnerable;
    private static double bodyHitMaxDamage;
    private static int maxMarked;
    private static double minHeight;

    private static final Set<Material> NATURAL = java.util.EnumSet.of(
            Material.GRASS_BLOCK, Material.DIRT, Material.COARSE_DIRT, Material.PODZOL, Material.MYCELIUM,
            Material.FARMLAND, Material.SAND, Material.RED_SAND, Material.GRAVEL, Material.CLAY,
            Material.STONE, Material.GRANITE, Material.DIORITE, Material.ANDESITE,
            Material.SANDSTONE, Material.RED_SANDSTONE, Material.TERRACOTTA, Material.ORANGE_TERRACOTTA,
            Material.YELLOW_TERRACOTTA, Material.BROWN_TERRACOTTA, Material.RED_TERRACOTTA,
            Material.WHITE_TERRACOTTA, Material.LIGHT_GRAY_TERRACOTTA,
            Material.SNOW_BLOCK, Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE,
            Material.WATER, Material.LAVA, Material.BEDROCK);

    private static final Map<UUID, PhoenixBird> ACTIVE = new HashMap<>();
    private static final Map<UUID, PhoenixBird> DECOYS = new HashMap<>();

    public static boolean isScouting(UUID id) {
        return ACTIVE.containsKey(id);
    }

    private static Listener listener;

    private Location origin;
    private GameMode originalMode;
    private boolean originalAllowFlight;
    private boolean originalFlying;
    private float originalFlySpeed;
    private ArmorStand decoy;
    private long startTime;
    private int age;
    private boolean registered;
    private boolean bodyHit;
    private boolean flightHeld;
    private Vector velocity;
    private final Set<UUID> spotted = new HashSet<>();
    private int visible;

    public PhoenixBird(Player player) {
        super(player);

        if (player == null || bPlayer == null) return;
        if (!bPlayer.canBend(this)) return;
        if (bPlayer.isOnCooldown(this)) return;
        if (hasAbility(player, PhoenixBird.class)) return;
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        if (GeneralMethods.isRegionProtectedFromBuild(this, player.getLocation())) return;
        if (player.getWorld().getEnvironment() != World.Environment.NORMAL) return;
        if (belowGround(player.getWorld(), player.getEyeLocation().toVector())) {
            player.sendMessage("§cПод землёй феникс не взлетит.");
            return;
        }

        start();

        if (!isStarted() || isRemoved()) return;

        bPlayer.addCooldown(this, cooldown);

        this.origin = player.getLocation().clone();
        this.originalMode = player.getGameMode();
        FlightGuard.acquire(player);
        flightHeld = true;
        this.originalAllowFlight = FlightGuard.savedAllowFlight(player);
        this.originalFlying = FlightGuard.savedFlying(player);
        this.originalFlySpeed = FlightGuard.savedFlySpeed(player);
        this.startTime = System.currentTimeMillis();
        this.age = 0;
        this.velocity = player.getEyeLocation().getDirection().normalize().multiply(flightSpeed * 0.6);

        saveRecovery();
        spawnDecoy();

        registered = true;
        ACTIVE.put(player.getUniqueId(), this);

        try {
            player.setGameMode(GameMode.SPECTATOR);
            player.setFlySpeed(0.001f);
        } catch (Throwable t) {
            remove();
            return;
        }

        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        world.playSound(eye, Sound.ENTITY_PHANTOM_FLAP, 1.6f, 1.4f);
        world.playSound(eye, Sound.ENTITY_BLAZE_SHOOT, 1.2f, 1.6f);
        world.playSound(eye, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.8f);
        world.spawnParticle(Particle.FLAME, eye, 24, 0.5, 0.5, 0.5, 0.08);
        world.spawnParticle(Particle.EXPLOSION_LARGE, eye, 1, 0, 0, 0, 0);
    }

    private static NamespacedKey recoveryKey() {
        return new NamespacedKey(PhoenixAddonLoader.getPlugin(), "phoenixbird_recovery");
    }

    private void saveRecovery() {
        try {
            String data = originalMode.name() + "|" + origin.getWorld().getUID() + "|" + origin.getX() + "|" + origin.getY() + "|" + origin.getZ()
                    + "|" + origin.getYaw() + "|" + origin.getPitch() + "|" + originalAllowFlight + "|" + originalFlying + "|" + originalFlySpeed;
            player.getPersistentDataContainer().set(recoveryKey(), PersistentDataType.STRING, data);
        } catch (Throwable ignored) {
        }
    }

    private static void restoreFromRecovery(Player player) {
        try {
            String data = player.getPersistentDataContainer().get(recoveryKey(), PersistentDataType.STRING);
            if (data == null) return;
            player.getPersistentDataContainer().remove(recoveryKey());

            String[] p = data.split("\\|");
            if (p.length < 10) return;

            World world = Bukkit.getWorld(UUID.fromString(p[1]));
            if (world != null) {
                Location back = new Location(world, Double.parseDouble(p[2]), Double.parseDouble(p[3]), Double.parseDouble(p[4]),
                        Float.parseFloat(p[5]), Float.parseFloat(p[6]));
                player.teleport(back);
            }

            boolean allow = Boolean.parseBoolean(p[7]);
            boolean flying = Boolean.parseBoolean(p[8]);
            player.setGameMode(GameMode.valueOf(p[0]));
            player.setFlySpeed(FlightGuard.clampSpeed(Float.parseFloat(p[9])));
            player.setAllowFlight(allow);
            player.setFlying(flying && allow);
            player.setFallDistance(0);
        } catch (Throwable ignored) {
        }
    }

    private void spawnDecoy() {
        try {
            World world = origin.getWorld();
            ArmorStand stand = world.spawn(origin, ArmorStand.class);
            stand.setArms(true);
            stand.setBasePlate(false);
            stand.setGravity(false);
            stand.setInvulnerable(!bodyVulnerable);
            stand.setMarker(false);
            stand.setPersistent(false);
            stand.setCustomName(player.getName());
            stand.setCustomNameVisible(true);

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(player);
                head.setItemMeta(meta);
            }
            stand.setHelmet(head);

            ItemStack chest = player.getInventory().getChestplate();
            ItemStack legs = player.getInventory().getLeggings();
            ItemStack boots = player.getInventory().getBoots();
            stand.setChestplate(chest != null ? chest.clone() : null);
            stand.setLeggings(legs != null ? legs.clone() : null);
            stand.setBoots(boots != null ? boots.clone() : null);

            stand.setRightArmPose(new EulerAngle(Math.toRadians(-8), 0, Math.toRadians(6)));
            stand.setLeftArmPose(new EulerAngle(Math.toRadians(-8), 0, Math.toRadians(-6)));
            stand.setHeadPose(new EulerAngle(Math.toRadians(-12), 0, 0));

            stand.teleport(origin.clone());
            decoy = stand;
            DECOYS.put(stand.getUniqueId(), this);
        } catch (Throwable ignored) {
            decoy = null;
        }
    }

    private boolean isValidTarget(Entity entity) {
        if (!(entity instanceof LivingEntity)) return false;
        if (entity instanceof ArmorStand) return false;
        if (entity.getUniqueId().equals(player.getUniqueId())) return false;
        if (entity instanceof Player && ((Player) entity).getGameMode() == GameMode.SPECTATOR) return false;
        return !entity.isDead() && entity.isValid();
    }

    private boolean isSolidAt(World world, Vector p) {
        int cx = p.getBlockX() >> 4;
        int cz = p.getBlockZ() >> 4;
        if (!world.isChunkLoaded(cx, cz)) return true;
        return world.getBlockAt(p.getBlockX(), p.getBlockY(), p.getBlockZ()).getType().isSolid();
    }

    private int groundTop(World world, int x, int z) {
        int top = world.getHighestBlockYAt(x, z);
        int bottom = Math.max(0, top - 96);
        for (int y = top; y >= bottom; y--) {
            if (NATURAL.contains(world.getBlockAt(x, y, z).getType())) {
                return y;
            }
        }
        return top;
    }

    private boolean belowGround(World world, Vector p) {
        int x = p.getBlockX();
        int z = p.getBlockZ();
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return true;
        return p.getY() < groundTop(world, x, z) + 1 + minHeight;
    }

    private boolean hasLineOfSight(World world, Vector from, Vector to) {
        Vector delta = to.clone().subtract(from);
        double length = delta.length();
        if (length < 1.0) return true;
        delta.multiply(1.0 / length);
        for (double d = 0.8; d < length - 0.8; d += 0.6) {
            if (isSolidAt(world, from.clone().add(delta.clone().multiply(d)))) return false;
        }
        return true;
    }

    @Override
    public void progress() {
        if (player == null || !player.isOnline() || player.isDead()) {
            remove();
            return;
        }

        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed >= duration) {
            remove();
            return;
        }

        World world = player.getWorld();
        if (!world.equals(origin.getWorld())) {
            remove();
            return;
        }

        age++;

        if (player.getGameMode() != GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SPECTATOR);
        }
        if (player.getSpectatorTarget() != null) {
            player.setSpectatorTarget(null);
        }
        player.setFlySpeed(0.001f);

        Location loc = player.getLocation();
        Vector dir = loc.getDirection().normalize();

        if (loc.distance(origin) > maxDistance) {
            remove();
            return;
        }

        Vector eye = player.getEyeLocation().toVector();
        Vector desired = dir.clone().multiply(flightSpeed);

        velocity.add(desired.subtract(velocity).multiply(0.3));

        Vector next = eye.clone().add(velocity);
        if (belowGround(world, next)) {
            velocity.setY(Math.max(velocity.getY(), 0.35));
            if (belowGround(world, eye.clone().add(velocity))) {
                velocity.setX(0);
                velocity.setZ(0);
            }
        }

        if (teleportMode) {
            player.teleport(loc.clone().add(velocity));
        } else {
            player.setVelocity(velocity.clone());
        }

        drawBird(world, eye, dir);
        revealTargets(world, eye);
        drawTether(world);

        if (age % 12 == 0) {
            world.playSound(loc, Sound.ENTITY_PHANTOM_FLAP, 0.7f, 1.5f);
        }
    }

    private void drawBird(World world, Vector eye, Vector dir) {
        Vector up = dir.clone().crossProduct(new Vector(0, 1, 0));
        if (up.lengthSquared() < 1.0E-4) {
            up = new Vector(1, 0, 0);
        }
        up = up.crossProduct(dir).normalize();

        Vector center = eye.clone().subtract(dir.clone().multiply(1.3 * birdScale)).subtract(up.clone().multiply(0.45));
        BirdFx.drawBird(world, center, dir, age, 0, birdScale);
    }

    private void drawTether(World world) {
        if (decoy == null || age % 3 != 0) return;

        Vector from = decoy.getLocation().toVector().add(new Vector(0, 1.2, 0));
        Vector to = player.getEyeLocation().toVector();
        Vector delta = to.clone().subtract(from);
        double length = delta.length();
        if (length < 1.5) return;

        int steps = (int) Math.min(14, length / 1.6);
        for (int i = 1; i <= steps; i++) {
            double f = (double) i / (steps + 1);
            Vector p = from.clone().add(delta.clone().multiply(f));
            p.add(new Vector(0, Math.sin(age * 0.3 + i) * 0.15, 0));
            PhoenixAscension.dust(world, p, i % 2 == 0 ? PhoenixAscension.DUST_GOLD : PhoenixAscension.DUST_ORANGE);
        }
    }

    private void revealTargets(World world, Vector eye) {
        Location center = eye.toLocation(world);
        visible = 0;

        for (Entity entity : GeneralMethods.getEntitiesAroundPoint(center, revealRadius)) {
            if (!isValidTarget(entity)) continue;

            LivingEntity living = (LivingEntity) entity;
            Vector targetEye = living.getEyeLocation().toVector();
            if (!hasLineOfSight(world, eye, targetEye)) continue;
            visible++;

            if (useGlowing) {
                living.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, markTicks, 0, false, false));
            }

            if (visible <= maxMarked) {
                Location top = living.getLocation().add(0, living.getHeight() + 0.5, 0);
                for (int i = 0; i < 3; i++) {
                    double a = age * 0.25 + i * Math.PI * 2 / 3;
                    Vector p = top.toVector().add(new Vector(Math.cos(a) * 0.3, 0, Math.sin(a) * 0.3));
                    PhoenixAscension.dust(world, p, PhoenixAscension.DUST_EMBER);
                }
                if (age % 2 == 0) {
                    PhoenixAscension.puff(world, Particle.FLAME, top.toVector().add(new Vector(0, 0.25, 0)), 1, 0.04, 0.01);
                }
            }

            if (spotted.add(living.getUniqueId())) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.8f);
            }
        }

        if (visible == 0) {
            spotted.clear();
        }
    }

    public void bodyAttacked(double damage) {
        if (bodyHit || !registered) return;
        bodyHit = true;

        final Player owner = player;
        final double dealt = Math.min(bodyHitMaxDamage, Math.max(1.0, damage));
        remove();

        if (owner != null && owner.isOnline()) {
            owner.sendMessage("§cВаше тело атаковали, птица вернулась!");
            Bukkit.getScheduler().runTask(PhoenixAddonLoader.getPlugin(), () -> {
                if (!owner.isOnline() || owner.isDead()) return;
                owner.damage(dealt);
                owner.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 40, 1, false, false));
            });
        }
    }

    @Override
    public void remove() {
        if (registered && player != null) {
            registered = false;
            ACTIVE.remove(player.getUniqueId());

            Location here = null;
            try {
                here = player.getLocation().clone();
            } catch (Throwable ignored) {
            }

            try {
                if (origin != null) {
                    Location back = origin.clone();
                    if (here != null) {
                        back.setYaw(here.getYaw());
                        back.setPitch(here.getPitch());
                    }
                    player.setVelocity(new Vector(0, 0, 0));
                    player.teleport(back);
                }
            } catch (Throwable ignored) {
            }

            try {
                player.setGameMode(originalMode != null ? originalMode : GameMode.SURVIVAL);
                if (flightHeld) {
                    flightHeld = false;
                    FlightGuard.release(player);
                }
                player.setFallDistance(0);
                player.getPersistentDataContainer().remove(recoveryKey());
            } catch (Throwable ignored) {
            }

            try {
                if (decoy != null) {
                    DECOYS.remove(decoy.getUniqueId());
                    decoy.remove();
                    decoy = null;
                }
            } catch (Throwable ignored) {
            }

            try {
                if (here != null && here.getWorld() != null) {
                    here.getWorld().spawnParticle(Particle.FLAME, here, 20, 0.4, 0.4, 0.4, 0.08);
                    here.getWorld().spawnParticle(Particle.SMOKE_NORMAL, here, 6, 0.3, 0.3, 0.3, 0.03);
                    here.getWorld().playSound(here, Sound.ENTITY_BLAZE_DEATH, 0.8f, 1.6f);
                }
            } catch (Throwable ignored) {
            }
        }
        super.remove();
    }

    @Override
    public boolean isSneakAbility() {
        return false;
    }

    @Override
    public boolean isHarmlessAbility() {
        return true;
    }

    @Override
    public long getCooldown() {
        return cooldown;
    }

    @Override
    public String getName() {
        return "PhoenixBird";
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
        config.addDefault(PATH + "Cooldown", 25000);
        config.addDefault(PATH + "Duration", 10000);
        config.addDefault(PATH + "FlightSpeed", 0.85);
        config.addDefault(PATH + "MaxDistanceFromBody", 120.0);
        config.addDefault(PATH + "RevealRadius", 14.0);
        config.addDefault(PATH + "MarkTicks", 120);
        config.addDefault(PATH + "UseGlowing", true);
        config.addDefault(PATH + "MoveMode", "VELOCITY");
        config.addDefault(PATH + "Visual.BirdScale", 1.7);
        config.addDefault(PATH + "BodyVulnerable", true);
        config.addDefault(PATH + "BodyHitMaxDamage", 6.0);
        config.addDefault(PATH + "MaxMarkedTargets", 10);
        config.addDefault(PATH + "MinHeightAboveGround", 1.0);
        config.options().copyDefaults(true);
        ConfigManager.defaultConfig.save();

        cooldown = config.getLong(PATH + "Cooldown");
        duration = config.getLong(PATH + "Duration");
        flightSpeed = Math.max(0.1, config.getDouble(PATH + "FlightSpeed"));
        maxDistance = Math.max(10.0, config.getDouble(PATH + "MaxDistanceFromBody"));
        revealRadius = Math.max(2.0, config.getDouble(PATH + "RevealRadius"));
        markTicks = Math.max(10, config.getInt(PATH + "MarkTicks"));
        useGlowing = config.getBoolean(PATH + "UseGlowing");
        teleportMode = "TELEPORT".equalsIgnoreCase(config.getString(PATH + "MoveMode"));
        birdScale = Math.max(0.5, config.getDouble(PATH + "Visual.BirdScale"));
        bodyVulnerable = config.getBoolean(PATH + "BodyVulnerable");
        bodyHitMaxDamage = Math.max(1.0, config.getDouble(PATH + "BodyHitMaxDamage"));
        maxMarked = Math.max(1, config.getInt(PATH + "MaxMarkedTargets"));
        minHeight = Math.max(0.3, config.getDouble(PATH + "MinHeightAboveGround"));

        if (listener == null) {
            listener = new PhoenixBirdListener();
            PhoenixAddonLoader.getPlugin().getServer().getPluginManager().registerEvents(listener, PhoenixAddonLoader.getPlugin());
        }
    }

    @Override
    public void stop() {
        for (PhoenixBird bird : new ArrayList<>(ACTIVE.values())) {
            bird.remove();
        }
        ACTIVE.clear();
        DECOYS.clear();

        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
    }

    @Override
    public String getAuthor() {
        return "Lucifer200134520 & KazharovFAKE & koral20";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public String getDescription() {
        return "Вы становитесь птицей-разведчиком: камера переходит к ней, вы управляете полётом мышью, "
                + "Существа в зоне прямой видимости подсвечиваются. Птица свободно пролетает сквозь постройки, но не опускается ниже уровня земли: пещеры, шахты и подземелья ей недоступны. Ваше тело остаётся на месте и уязвимо: "
                + "если по нему попадут, птица тут же вернётся. По окончании вы возвращаетесь в своё тело.";
    }

    @Override
    public String getInstructions() {
        return "ЛКМ - запустить птицу. Управляйте мышью. Shift - вернуться досрочно.";
    }

    public static class PhoenixBirdListener implements Listener {

        @EventHandler
        public void onClick(PlayerInteractEvent event) {
            if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
            if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) return;

            Player player = event.getPlayer();
            if (ACTIVE.containsKey(player.getUniqueId())) return;
            if (!player.hasPermission("projectkorra.ability.PhoenixBird")) return;

            BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
            if (bPlayer == null || !bPlayer.canBend(CoreAbility.getAbility(PhoenixBird.class))) return;

            new PhoenixBird(player);
        }

        @EventHandler
        public void onSneak(PlayerToggleSneakEvent event) {
            if (!event.isSneaking()) return;

            PhoenixBird active = ACTIVE.get(event.getPlayer().getUniqueId());
            if (active != null) {
                active.remove();
            }
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onTeleport(PlayerTeleportEvent event) {
            if (!ACTIVE.containsKey(event.getPlayer().getUniqueId())) return;

            if (event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE) {
                event.setCancelled(true);
            }
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onDamage(EntityDamageEvent event) {
            PhoenixBird bird = DECOYS.get(event.getEntity().getUniqueId());
            if (bird == null) return;

            event.setCancelled(true);
            if (bodyVulnerable) {
                bird.bodyAttacked(event.getDamage());
            }
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            PhoenixBird active = ACTIVE.get(event.getPlayer().getUniqueId());
            if (active != null) {
                active.remove();
            }
        }

        @EventHandler
        public void onJoin(PlayerJoinEvent event) {
            if (ACTIVE.containsKey(event.getPlayer().getUniqueId())) return;
            restoreFromRecovery(event.getPlayer());
        }
    }
}
