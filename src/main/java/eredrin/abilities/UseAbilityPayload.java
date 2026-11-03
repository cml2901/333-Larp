package eredrin.abilities;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Клиент -> сервер: "использовать способность №ability". */
public record UseAbilityPayload(int ability) implements CustomPacketPayload {
    public static final Type<UseAbilityPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EredrinAbilities.MODID, "use_ability"));
    public static final StreamCodec<ByteBuf, UseAbilityPayload> CODEC =
            ByteBufCodecs.VAR_INT.map(UseAbilityPayload::new, UseAbilityPayload::ability);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
