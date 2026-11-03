package eredrin.abilities;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction.Axis;

/** Серверная логика трёх способностей брони Эредина. */
public final class Abilities {
    public static final int BEHIND = 0;     // тп за спину цели
    public static final int CROSSHAIR = 1;  // тп на прицел
    public static final int SPIKES = 2;     // ледяные шипы

    private static final int COOLDOWN_TICKS = 40; // 2 секунды
    private static final double RANGE = 30.0;
    private static final int EFFECT_TICKS = 30 * 20; // 30 секунд
    private static final int EFFECT_AMPLIFIER = 4;   // уровень V

    private Abilities() {}

    public static void use(ServerPlayer p, int id) {
        if (!EredrinSet.has(p)) return;
        ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
        if (p.getCooldowns().isOnCooldown(chest.getItem())) return;

        boolean ok = switch (id) {
            case BEHIND -> teleportBehind(p);
            case CROSSHAIR -> teleportToCrosshair(p);
            case SPIKES -> iceSpikes(p);
            default -> false;
        };
        if (ok) p.getCooldowns().addCooldown(chest.getItem(), COOLDOWN_TICKS);
    }

    // ------------------------------------------------------------ телепорты

    private static boolean teleportBehind(ServerPlayer p) {
        LivingEntity target = findTarget(p, RANGE);
        if (target == null) return false;
        ServerLevel level = p.serverLevel();

        Vec3 back = Vec3.directionFromRotation(0, target.getYRot()).scale(-1.0);
        for (double dist : new double[]{1.6, 1.2, 0.8}) {
            for (double up : new double[]{0, 1}) {
                Vec3 dest = target.position().add(back.scale(dist)).add(0, up, 0);
                if (isFree(level, p, dest)) {
                    Vec3 from = p.position();
                    frostBurst(level, from, 1.2);
                    p.teleportTo(level, dest.x, dest.y, dest.z, target.getYRot(), 0f);
                    p.fallDistance = 0;
                    frostBurst(level, dest, 1.2);
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean teleportToCrosshair(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(40));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, p));
        if (hit.getType() == HitResult.Type.MISS) return false;

        BlockPos base = hit.getBlockPos().relative(hit.getDirection());
        BlockPos[] candidates = hit.getDirection() == Direction.DOWN
                ? new BlockPos[]{base.below(), base, base.above()}
                : new BlockPos[]{base, base.above(), base.below()};
        for (BlockPos c : candidates) {
            Vec3 dest = Vec3.atBottomCenterOf(c);
            if (isFree(level, p, dest)) {
                frostBurst(level, p.position(), 1.2);
                p.teleportTo(level, dest.x, dest.y, dest.z, p.getYRot(), p.getXRot());
                p.fallDistance = 0;
                frostBurst(level, dest, 1.2);
                return true;
            }
        }
        return false;
    }

    private static boolean isFree(ServerLevel level, ServerPlayer p, Vec3 dest) {
        AABB moved = p.getBoundingBox().move(dest.subtract(p.position()));
        return level.noCollision(p, moved);
    }

    // ------------------------------------------------------------ ледяные шипы

    private static boolean iceSpikes(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        LivingEntity target = findTarget(p, RANGE);
        Vec3 origin = p.position();

        Vec3 dir = Vec3.directionFromRotation(0, p.getYRot());
        double len = 14;
        if (target != null) {
            Vec3 d = target.position().subtract(origin);
            Vec3 flat = new Vec3(d.x, 0, d.z);
            if (flat.lengthSqr() > 1e-6) dir = flat.normalize();
            len = Math.min(flat.length(), RANGE);
        }

        frostBurst(level, origin, 1.6);

        List<Supplier<Vec3>> points = new ArrayList<>();
        final Vec3 fdir = dir;
        for (double d = 1.5; d <= len; d += 1.3) {
            final Vec3 pt = origin.add(fdir.scale(d));
            points.add(() -> pt);
        }
        if (target != null) points.add(target::position); // гарантированный шип под целью

        Set<UUID> alreadyHit = new HashSet<>();
        for (int i = 0; i < points.size(); i++) {
            Supplier<Vec3> pt = points.get(i);
            Scheduler.later(1 + i * 2, () -> spawnSpike(level, p, pt.get(), alreadyHit));
        }
        return true;
    }

    private static void spawnSpike(ServerLevel level, ServerPlayer owner, Vec3 pos, Set<UUID> alreadyHit) {
        double y = groundY(level, pos);

        // эффекты появления шипа
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()),
                pos.x, y + 0.2, pos.z, 25, 0.3, 0.1, 0.3, 0.15);
        level.sendParticles(ParticleTypes.SNOWFLAKE, pos.x, y + 1.0, pos.z, 20, 0.3, 0.8, 0.3, 0.05);
        level.sendParticles(ParticleTypes.CLOUD, pos.x, y + 0.3, pos.z, 6, 0.3, 0.1, 0.3, 0.02);
        level.playSound(null, pos.x, y, pos.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.7f, 1.6f);
        level.playSound(null, pos.x, y, pos.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 0.8f);

        // сам шип: пирамидка из ступенчатых блок-дисплеев, растёт снизу вверх
        double[] sizes = {0.8, 0.6, 0.42, 0.26, 0.12};
        List<Entity> parts = new ArrayList<>();
        for (int k = 0; k < sizes.length; k++) {
            final int kk = k;
            Scheduler.later(Math.max(1, k), () -> {
                Entity e = makeDisplay(level, pos.x, y + kk * 0.6, pos.z, sizes[kk], 0.62,
                        kk == 0 ? "packed_ice" : "blue_ice");
                if (e != null) {
                    level.addFreshEntity(e);
                    parts.add(e);
                }
            });
        }

        // попадание: слепота V и слабость V на 30 секунд, ТОЛЬКО тем, кого задел шип
        AABB box = new AABB(pos.x - 0.7, y - 0.2, pos.z - 0.7, pos.x + 0.7, y + 3.0, pos.z + 0.7);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                en -> en != owner && en.isAlive() && !en.isSpectator())) {
            if (alreadyHit.add(e.getUUID())) onHit(owner, e);
        }

        // шип исчезает через 1.5 секунды с осколками
        Scheduler.later(30, () -> {
            parts.forEach(Entity::discard);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BLUE_ICE.defaultBlockState()),
                    pos.x, y + 1.2, pos.z, 30, 0.3, 0.8, 0.3, 0.12);
            level.playSound(null, pos.x, y, pos.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.6f, 1.2f);
        });
    }

    private static void onHit(ServerPlayer owner, LivingEntity e) {
        e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, EFFECT_TICKS, EFFECT_AMPLIFIER), owner);
        e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EFFECT_TICKS, EFFECT_AMPLIFIER), owner);
        ServerLevel level = (ServerLevel) e.level();
        level.sendParticles(ParticleTypes.SNOWFLAKE, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(),
                40, 0.4, 0.6, 0.4, 0.1);
        level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.PLAYER_HURT_FREEZE,
                SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static Entity makeDisplay(ServerLevel level, double x, double y, double z,
                                      double size, double height, String block) {
        try {
            String snbt = String.format(Locale.ROOT,
                    "{Pos:[%.3fd,%.3fd,%.3fd],block_state:{Name:\"minecraft:%s\"},"
                            + "transformation:{translation:[%.3ff,0f,%.3ff],scale:[%.3ff,%.3ff,%.3ff],"
                            + "left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f]}}",
                    x, y, z, block, -size / 2, -size / 2, size, height, size);
            CompoundTag tag = TagParser.parseTag(snbt);
            Entity e = EntityType.BLOCK_DISPLAY.create(level);
            if (e == null) return null;
            e.load(tag);
            return e;
        } catch (CommandSyntaxException ex) {
            return null;
        }
    }

    private static double groundY(ServerLevel level, Vec3 v) {
        BlockPos.MutableBlockPos m = BlockPos.containing(v.x, v.y + 1, v.z).mutable();
        for (int i = 0; i < 8; i++) {
            BlockState s = level.getBlockState(m);
            if (!s.getCollisionShape(level, m).isEmpty()) {
                return m.getY() + s.getCollisionShape(level, m).max(Axis.Y);
            }
            m.move(0, -1, 0);
        }
        return v.y;
    }

    // ------------------------------------------------------------ общее

    /** Живая цель, на которую смотрит игрок (с учётом блоков на пути). */
    private static LivingEntity findTarget(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult bh = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, p));
        double max = bh.getType() == HitResult.Type.MISS ? range : bh.getLocation().distanceTo(eye);
        Vec3 end2 = eye.add(look.scale(max));
        AABB box = p.getBoundingBox().expandTowards(look.scale(max)).inflate(1.0);
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(p.level(), p, eye, end2, box,
                e -> e instanceof LivingEntity && !e.isSpectator() && e.isPickable());
        return eh != null && eh.getEntity() instanceof LivingEntity le ? le : null;
    }

    /** Белый хлад: снежинки, кольцо, облако и звук льда. */
    private static void frostBurst(ServerLevel level, Vec3 pos, double radius) {
        level.sendParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y + 1.0, pos.z, 70, 0.5, 0.8, 0.5, 0.08);
        level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.8, pos.z, 20, 0.4, 0.6, 0.4, 0.05);
        level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 1.0, pos.z, 15, 0.4, 0.7, 0.4, 0.06);
        for (int i = 0; i < 32; i++) {
            double a = i * Math.PI / 16;
            level.sendParticles(ParticleTypes.SNOWFLAKE, pos.x + Math.cos(a) * radius, pos.y + 0.1,
                    pos.z + Math.sin(a) * radius, 1, 0, 0.05, 0, 0.03);
        }
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8f, 1.4f);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.0f, 0.9f);
    }
}
