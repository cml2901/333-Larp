package eredrin.abilities.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.client.gui.screens.Screen;
import org.joml.Matrix4f;

/** Радиальное меню на 3 способности. Наведи мышь на сектор и кликни; выбранная способность выполняется на X. */
public class RadialScreen extends Screen {
    private static final int COUNT = 3;
    private static final float INNER = 28f;
    private static final float OUTER = 95f;

    public RadialScreen() {
        super(Component.empty());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0x66000000);
    }

    private double centerAngle(int i) {
        return -90 + i * 120.0;
    }

    private int hovered(double mx, double my) {
        double dx = mx - width / 2.0, dy = my - height / 2.0;
        double dist = Math.hypot(dx, dy);
        if (dist < INNER || dist > OUTER + 60) return -1;
        double ang = Math.toDegrees(Math.atan2(dy, dx));
        int best = -1;
        double bestDiff = 999;
        for (int i = 0; i < COUNT; i++) {
            double diff = Math.abs(Mth.wrapDegrees(ang - centerAngle(i)));
            if (diff < bestDiff) {
                bestDiff = diff;
                best = i;
            }
        }
        return best;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        int cx = width / 2, cy = height / 2;
        int hov = hovered(mx, my);

        for (int i = 0; i < COUNT; i++) {
            int color = 0xAA14305A;                         // тёмно-синий
            if (i == ClientState.selected) color = 0xCC3A8FD0; // выбран
            if (i == hov) color = 0xDDB8F0FF;                 // под курсором (белый хлад)
            double c = centerAngle(i);
            drawSector(g, cx, cy, c - 58, c + 58, color);

            double rad = Math.toRadians(c);
            int ix = (int) (cx + Math.cos(rad) * (INNER + OUTER) / 2);
            int iy = (int) (cy + Math.sin(rad) * (INNER + OUTER) / 2);
            g.drawCenteredString(font, String.valueOf(i + 1), ix, iy - 4, 0xFFFFFF);

            int lx = (int) (cx + Math.cos(rad) * (OUTER + 38));
            int ly = (int) (cy + Math.sin(rad) * (OUTER + 22));
            g.drawCenteredString(font, Component.translatable("ability.eredrin_abilities." + i), lx, ly - 4,
                    i == hov ? 0xFFFFFF : 0xA0E8FF);
        }

        Component center = hov >= 0
                ? Component.translatable("ability.eredrin_abilities." + hov)
                : Component.translatable("ability.eredrin_abilities.hint");
        g.drawCenteredString(font, center, cx, cy - 4, 0xFFFFFF);
    }

    private void drawSector(GuiGraphics g, int cx, int cy, double a0, double a1, int argb) {
        Matrix4f m = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP,
                DefaultVertexFormat.POSITION_COLOR);
        int steps = 24;
        for (int s = 0; s <= steps; s++) {
            double a = Math.toRadians(a0 + (a1 - a0) * s / steps);
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            b.addVertex(m, cx + cos * INNER, cy + sin * INNER, 0).setColor(argb);
            b.addVertex(m, cx + cos * OUTER, cy + sin * OUTER, 0).setColor(argb);
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int hov = hovered(mx, my);
        if (hov >= 0) {
            ClientState.selected = hov;
            onClose();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (ClientModEvents.OPEN_MENU.matches(key, scan)) {
            onClose();
            return true;
        }
        if (key >= 49 && key < 49 + COUNT) { // клавиши 1..3
            ClientState.selected = key - 49;
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
