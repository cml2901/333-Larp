package eredrin.abilities;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

/** Проверка: надет ли полный сет Эредина из мода Fantasy Armor. */
public final class EredrinSet {
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] NAMES = {
            "eredrin_helmet", "eredrin_chestplate", "eredrin_leggings", "eredrin_boots"};

    private EredrinSet() {}

    public static boolean has(Player player) {
        for (int i = 0; i < SLOTS.length; i++) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(player.getItemBySlot(SLOTS[i]).getItem());
            if (!id.getNamespace().equals("fantasy_armor") || !id.getPath().equals(NAMES[i])) {
                return false;
            }
        }
        return true;
    }
}
