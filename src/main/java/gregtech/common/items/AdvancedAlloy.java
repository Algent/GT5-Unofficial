package gregtech.common.items;

import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.TCAspects;
import gregtech.api.items.GTVariantItem;

/**
 * A "real" {@link GTVariantItem} demo: the IC2 Advanced Alloy chain, as the kind of concept class
 * ({@code AdvancedAlloy.java}) the team wants instead of opaque MetaGenerated slots.
 *
 * <p>
 * Two semantically related variants under one item ID:
 * <ul>
 * <li><b>ingot</b> — the Mixed Metal Ingot precursor; a plain crafting intermediate tagged
 * {@code ingotMixedMetal}.</li>
 * <li><b>plate</b> — the Advanced Alloy itself, claimed as {@code plateAlloy.HV} (OreDict {@code plateAlloyAdvanced}).
 * Because it becomes the unification target, every existing recipe that outputs
 * {@code GTOreDictUnificator.get(plateAlloy, HV, n)} resolves to this plate with no recipe edits.</li>
 * </ul>
 */
public final class AdvancedAlloy extends GTVariantItem {

    public static AdvancedAlloy INSTANCE;

    public final Variant ingot;
    public final Variant plate;

    public AdvancedAlloy() {
        super("advanced_alloy");

        // spotless:off
        ingot = add(0, "ingot", "Mixed Metal Ingot")
            .oreDict("ingotMixedMetal")
            .aspects(new TCAspects.TC_AspectStack(TCAspects.METALLUM, 3L))
            .bind(ItemList.AdvancedAlloy_Ingot);

        plate = add(1, "plate", "Advanced Alloy")
            .materialData(OrePrefixes.plateAlloy, Materials.HV)
            .aspects(
                new TCAspects.TC_AspectStack(TCAspects.METALLUM, 4L),
                new TCAspects.TC_AspectStack(TCAspects.VACUOS, 2L))
            .bind(ItemList.AdvancedAlloy_Plate);
        // spotless:on
    }
}
