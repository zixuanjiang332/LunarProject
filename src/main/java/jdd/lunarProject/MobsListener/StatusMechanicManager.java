package jdd.lunarProject.MobsListener;

import io.lumine.mythic.bukkit.MythicBukkit;
import jdd.lunarProject.LunarProject;
import jdd.lunarProject.Tool.CombatVariableUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class StatusMechanicManager extends BukkitRunnable implements Listener {
    private static final long RUPTURE_HIT_COOLDOWN_MS = 600L;
    private static final long RUPTURE_EXPIRE_DELAY_MS = 5_000L;
    private static final long PLAYER_MOVE_WINDOW_MS = 500L;

    private static final Map<UUID, Long> ruptureHitCooldowns = new HashMap<>();
    private static final Map<UUID, Integer> observedRuptureApplicationMarkers = new HashMap<>();
    private static final Map<UUID, Long> ruptureExpireAt = new HashMap<>();
    private static final Map<UUID, Long> playerLastMoveAt = new HashMap<>();

    @Override
    public void run() {
        long now = System.currentTimeMillis();

        // Rupture expiration now refreshes only when the effect is newly applied.
        // We watch a dedicated application marker authored by Mythic instead of
        // the rupture intensity itself, so hit-driven -1 consumption never restarts
        // the 5s countdown.
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity livingEntity : world.getLivingEntities()) {
                if (!CombatVariableUtil.isManaged(livingEntity)) {
                    continue;
                }

                int intensity = CombatVariableUtil.getInt(livingEntity, CombatVariableUtil.VAR_RUPTURE_INTENSITY, 0);
                int applicationMarker = CombatVariableUtil.getInt(livingEntity, CombatVariableUtil.VAR_RUPTURE_APPLICATION_MARKER, 0);
                UUID uuid = livingEntity.getUniqueId();
                Integer previousMarker = observedRuptureApplicationMarkers.get(uuid);

                if (intensity <= 0) {
                    observedRuptureApplicationMarkers.remove(uuid);
                    ruptureExpireAt.remove(uuid);
                    continue;
                }

                if (previousMarker == null || previousMarker != applicationMarker) {
                    trackRuptureApplication(livingEntity, applicationMarker, now);
                }
            }
        }

        Iterator<Map.Entry<UUID, Long>> iterator = ruptureExpireAt.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            if (entry.getValue() > now) {
                continue;
            }

            LivingEntity entity = resolveLivingEntity(entry.getKey());
            iterator.remove();

            if (entity == null || !entity.isValid() || entity.isDead()) {
                observedRuptureApplicationMarkers.remove(entry.getKey());
                continue;
            }

            int intensity = CombatVariableUtil.getInt(entity, CombatVariableUtil.VAR_RUPTURE_INTENSITY, 0);
            if (intensity <= 0) {
                observedRuptureApplicationMarkers.remove(entry.getKey());
                continue;
            }

            MythicBukkit.inst().getAPIHelper().castSkill(entity, "System_Execute_Rupture_Expiration");
            observedRuptureApplicationMarkers.remove(entry.getKey());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamaged(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        if (event.getFinalDamage() <= 0.0) {
            return;
        }
        if (event.getDamager().getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        if (CombatVariableUtil.isStatusDamage(event.getDamager())) {
            return;
        }

        int intensity = CombatVariableUtil.getInt(victim, CombatVariableUtil.VAR_RUPTURE_INTENSITY, 0);
        if (intensity <= 0) {
            return;
        }

        long now = System.currentTimeMillis();
        long nextAllowedTime = ruptureHitCooldowns.getOrDefault(victim.getUniqueId(), 0L);
        if (now < nextAllowedTime) {
            return;
        }

        ruptureHitCooldowns.put(victim.getUniqueId(), now + RUPTURE_HIT_COOLDOWN_MS);

        // Players take rupture as an independent DOT tick, while mobs take it as
        // a gluttony-tagged status hit that still enters the normal multiplier chain.
        if (victim instanceof Player) {
            CombatVariableUtil.applySelfStatusDamage(
                    victim,
                    intensity,
                    CombatVariableUtil.ATTACK_TYPE_RUPTURE_DOT,
                    CombatVariableUtil.ATTACK_SIN_NONE
            );
        } else {
            CombatVariableUtil.applySelfStatusDamage(
                    victim,
                    intensity,
                    CombatVariableUtil.ATTACK_TYPE_RUPTURE,
                    CombatVariableUtil.ATTACK_SIN_GLUTTONY
            );
        }

        // Rupture spends one stack after the current hit's bonus damage has been
        // fully resolved. This keeps the current proc using the pre-hit intensity
        // while still refreshing the 10s expiration off the post-proc change.
        int remainingIntensity = Math.max(0, intensity - 1);
        CombatVariableUtil.setInt(victim, CombatVariableUtil.VAR_RUPTURE_INTENSITY, remainingIntensity);
        if (remainingIntensity <= 0) {
            observedRuptureApplicationMarkers.remove(victim.getUniqueId());
            ruptureExpireAt.remove(victim.getUniqueId());
        }
        MythicBukkit.inst().getAPIHelper().castSkill(victim, "System_Apply_Rupture_Damage_Effect");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }

        if (!event.getFrom().getWorld().equals(event.getTo().getWorld())) {
            playerLastMoveAt.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
            return;
        }

        double deltaX = event.getTo().getX() - event.getFrom().getX();
        double deltaY = event.getTo().getY() - event.getFrom().getY();
        double deltaZ = event.getTo().getZ() - event.getFrom().getZ();
        double distanceSquared = (deltaX * deltaX) + (deltaY * deltaY) + (deltaZ * deltaZ);
        if (distanceSquared > 0.0001D) {
            playerLastMoveAt.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
        }
    }

    public static boolean isPlayerMoving(Player player) {
        long lastMoveAt = playerLastMoveAt.getOrDefault(player.getUniqueId(), 0L);
        return System.currentTimeMillis() - lastMoveAt <= PLAYER_MOVE_WINDOW_MS;
    }

    private void trackRuptureApplication(LivingEntity entity, int applicationMarker, long now) {
        UUID uuid = entity.getUniqueId();
        int intensity = CombatVariableUtil.getInt(entity, CombatVariableUtil.VAR_RUPTURE_INTENSITY, 0);
        if (intensity <= 0) {
            observedRuptureApplicationMarkers.remove(uuid);
            ruptureExpireAt.remove(uuid);
            return;
        }

        observedRuptureApplicationMarkers.put(uuid, applicationMarker);
        ruptureExpireAt.put(uuid, now + RUPTURE_EXPIRE_DELAY_MS);
    }

    private LivingEntity resolveLivingEntity(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            return player;
        }

        Entity entity = Bukkit.getEntity(uuid);
        if (entity instanceof LivingEntity livingEntity) {
            return livingEntity;
        }

        return MythicBukkit.inst().getMobManager().getActiveMob(uuid)
                .map(activeMob -> activeMob.getEntity().getBukkitEntity())
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast)
                .orElse(null);
    }
}
