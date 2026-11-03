package eredrin.abilities.client;

import eredrin.abilities.EredrinAbilities;
import eredrin.abilities.EredrinSet;
import eredrin.abilities.UseAbilityPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = EredrinAbilities.MODID, value = Dist.CLIENT)
public final class ClientGameEvents {
    private ClientGameEvents() {}

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        while (ClientModEvents.OPEN_MENU.consumeClick()) {
            if (mc.screen == null && EredrinSet.has(mc.player)) {
                mc.setScreen(new RadialScreen());
            }
        }
        while (ClientModEvents.USE.consumeClick()) {
            if (mc.screen == null && ClientState.selected >= 0 && EredrinSet.has(mc.player)) {
                PacketDistributor.sendToServer(new UseAbilityPayload(ClientState.selected));
            }
        }
    }
}
