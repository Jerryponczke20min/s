package pl.radar;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;

/**
 * Anarchiczny set I  = 4x netherite, kazdy element ma enchanty.
 * Anarchiczny set II = 4x netherite + fioletowe zdobienie (Amethyst trim) na wszystkich elementach
 *                      + lepsze enchanty (Protection IV+ i min. TIER2_MIN_ENCH enchantow na element).
 */
public class AnarchyAnalyzer {
    public static final EquipmentSlot[] ARMOR = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    public static final int TIER2_MIN_ENCH = 5;

    public static void analyze(PlayerData d) {
        int minEnch = Integer.MAX_VALUE, minProt = Integer.MAX_VALUE;
        for (EquipmentSlot slot : ARMOR) {
            ItemStack st = d.player.getEquippedStack(slot);
            String path = Registries.ITEM.getId(st.getItem()).getPath();
            if (path.startsWith("diamond_")) d.diamondPieces++;
            else if (path.startsWith("netherite_")) d.netheritePieces++;

            var trim = st.get(DataComponentTypes.TRIM);
            if (trim != null) {
                var key = trim.material().getKey();
                if (key.isPresent() && key.get().getValue().getPath().equals("amethyst")) d.purplePieces++;
            }

            ItemEnchantmentsComponent c = EnchantmentHelper.getEnchantments(st);
            int ench = c.getSize();
            int prot = 0;
            for (RegistryEntry<Enchantment> e : c.getEnchantments()) {
                if (e.matchesKey(Enchantments.PROTECTION)) prot = c.getLevel(e);
            }
            d.totalEnch += ench;
            minEnch = Math.min(minEnch, ench);
            minProt = Math.min(minProt, prot);
        }
        d.minEnch = minEnch == Integer.MAX_VALUE ? 0 : minEnch;
        d.minProt = minProt == Integer.MAX_VALUE ? 0 : minProt;

        if (d.netheritePieces == 4 && d.minEnch >= 1) d.tier = 1;
        if (d.netheritePieces == 4 && d.purplePieces == 4
            && d.minEnch >= TIER2_MIN_ENCH && d.minProt >= 4) d.tier = 2;
    }
}
