package com.llamastudio.zoltraak;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.joml.Vector3f;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Logic cac skill (chuyen tu MythicMobs zoltraak_skills.yml). */
public final class Spells {

    public static final double BEAM_RANGE = 50.0;
    public static final double BARRAGE_RANGE = 20.0;
    public static final int BARRAGE_COOLDOWN_TICKS = 80; // 4 giay

    private static final DustParticleOptions PURPLE = new DustParticleOptions(new Vector3f(0.73f, 0.0f, 1.0f), 1.3f);

    private static final Map<UUID, Long> BARRAGE_READY_AT = new HashMap<>();

    private Spells() {}

    private record Trace(Vec3 pos, LivingEntity entity, boolean hit) {}

    // ------------------------------------------------------------------ beam

    public static void castBeam(ServerLevel level, Player player) {
        Trace trace = trace(level, player, BEAM_RANGE);

        Vec3 look = player.getLookAngle();
        Vec3 start = player.getEyePosition().add(look.scale(1.2)).add(0, -0.3, 0);
        drawBeam(level, start, trace.pos());

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PUFFER_FISH_BLOW_OUT, SoundSource.PLAYERS, 2.0f, 1.5f);
        Scheduler.later(5, () -> zoltraakSound(level, player.position()));

        if (trace.hit()) {
            impact(level, player, trace.pos(), 1.2, false);
        }
    }

    // --------------------------------------------------------------- barrage

    /** @return true neu skill duoc tung ra. */
    public static boolean castBarrage(ServerLevel level, Player player) {
        long now = level.getGameTime();
        Long readyAt = BARRAGE_READY_AT.get(player.getUUID());
        if (readyAt != null && readyAt > now) {
            String seconds = String.format("%.1f", (readyAt - now) / 20.0);
            player.displayClientMessage(Component.translatable("message.zoltraak.cooldown", seconds), true);
            return false;
        }

        Trace trace = trace(level, player, BARRAGE_RANGE);
        if (trace.entity() == null) {
            player.displayClientMessage(Component.translatable("message.zoltraak.aim"), true);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 2.0f, 1.0f);
            return false;
        }

        BARRAGE_READY_AT.put(player.getUUID(), now + BARRAGE_COOLDOWN_TICKS);

        LivingEntity target = trace.entity();
        Vec3 center = target.position().add(0, target.getBbHeight() * 0.5, 0);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PUFFER_FISH_BLOW_OUT, SoundSource.PLAYERS, 2.0f, 1.5f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2.0f, 2.0f);

        // 5 tia bay tu xung quanh nguoi choi ve phia muc tieu: {forward, up, side, delay}
        double[][] origins = {
                {-1.0, 0.5, 0.0, 0},
                {-0.8, 1.2, 1.5, 2},
                {-0.5, 0.0, -1.5, 4},
                {-1.1, 1.8, 2.5, 6},
                {-1.3, 0.8, -2.5, 8}
        };
        Vec3 eye = player.getEyePosition();
        Vec3 forward = new Vec3(player.getLookAngle().x, 0, player.getLookAngle().z);
        forward = forward.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : forward.normalize();
        Vec3 right = forward.cross(new Vec3(0, 1, 0)).normalize();

        for (double[] o : origins) {
            Vec3 origin = eye.add(forward.scale(o[0])).add(right.scale(o[2])).add(0, o[1], 0);
            Scheduler.later((int) o[3] + 1, () -> drawBeam(level, origin, center));
        }

        // Sat thuong: 4 lan, cach nhau 2 tick, bat dau sau 3 tick
        for (int i = 0; i < 4; i++) {
            Scheduler.later(3 + i * 2, () -> impact(level, player, center, 2.0, true));
        }
        return true;
    }

    // --------------------------------------------------------------- helpers

    private static Trace trace(Level level, Player player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(range));

        BlockHitResult blockHit = level.clip(
                new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        boolean hitBlock = blockHit.getType() != HitResult.Type.MISS;
        Vec3 stop = hitBlock ? blockHit.getLocation() : end;

        AABB box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, player, eye, stop, box,
                e -> !e.isSpectator() && e.isPickable() && e instanceof LivingEntity && e != player);

        if (entityHit != null && entityHit.getEntity() instanceof LivingEntity living) {
            return new Trace(entityHit.getLocation(), living, true);
        }
        return new Trace(stop, null, hitBlock);
    }

    private static void drawBeam(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        if (length < 0.01) {
            return;
        }
        double stepSize = 0.4;
        Vec3 step = delta.scale(stepSize / length);
        int steps = (int) (length / stepSize);
        Vec3 p = from;
        for (int i = 0; i <= steps; i++) {
            level.sendParticles(PURPLE, p.x, p.y, p.z, 2, 0.04, 0.04, 0.04, 0.0);
            if (i % 3 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
            }
            p = p.add(step);
        }
    }

    private static void impact(ServerLevel level, Player player, Vec3 pos, double radius, boolean ignoreIFrames) {
        level.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 2, 0.3, 0.3, 0.3, 0.0);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.x, pos.y, pos.z, 3, 0.3, 0.3, 0.3, 0.05);
        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 1.0f, 1.5f);

        float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        AABB area = new AABB(pos, pos).inflate(radius);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != player && e.isAlive() && !e.isSpectator())) {
            if (ignoreIFrames) {
                victim.invulnerableTime = 0;
            }
            victim.hurt(level.damageSources().playerAttack(player), damage);
            // Slowness level 99 trong 10 tick (nhu ban goc)
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 98, false, false), player);
        }
        zoltraakSound(level, pos);
    }

    private static void zoltraakSound(ServerLevel level, Vec3 pos) {
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2.0f, 2.0f);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 2.0f, 2.0f);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 2.0f, 2.0f);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 0.5f, 2.0f);
    }
}
