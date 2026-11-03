package eredrin.abilities.client;

import com.mojang.blaze3d.platform.InputConstants;
import eredrin.abilities.EredrinAbilities;
import eredrin.abilities.EredrinSet;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = EredrinAbilities.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    public static final KeyMapping OPEN_MENU = new KeyMapping("key.eredrin_abilities.menu",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_ALT, "key.categories.eredrin_abilities");
    public static final KeyMapping USE = new KeyMapping("key.eredrin_abilities.use",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, "key.categories.eredrin_abilities");

    private ClientModEvents() {}

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent e) {
        e.register(OPEN_MENU);
        e.register(USE);
    }

    @SubscribeEvent
    public static void layers(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath(EredrinAbilities.MODID, "selected"),
                ClientModEvents::renderHud);
    }

    private static void renderHud(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !EredrinSet.has(mc.player)) return;
        Component name = ClientState.selected < 0
                ? Component.translatable("ability.eredrin_abilities.none")
                : Component.translatable("ability.eredrin_abilities." + ClientState.selected);
        g.drawCenteredString(mc.font, Component.literal("\u2744 ").append(name),
                g.guiWidth() / 2, g.guiHeight() - 62, 0xA0E8FF);
    }
}
