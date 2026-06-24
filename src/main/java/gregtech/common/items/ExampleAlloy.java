package gregtech.common.items;

import gregtech.api.enums.TCAspects;
import gregtech.api.items.GTVariantItem;

/**
 * Proof-of-concept usage of {@link GTVariantItem}, doubling as a feature menu: one item, one item ID, several
 * semantically related variants, each demonstrating a different slice of the fluent {@link GTVariantItem.Variant} API.
 *
 * <p>
 * Compare with the alternatives:
 * <ul>
 * <li>{@code MetaGeneratedItem03}: would scatter these across an opaque shared item where membership means
 * nothing.</li>
 * <li>{@code GTGenericItem}: would need one class (and one item ID) per variant, and could not unify.</li>
 * </ul>
 *
 * <p>
 * The unification-target hook ({@code materialData}) and the {@code ItemList} binding hook ({@code bind}) are shown for
 * real in {@link AdvancedAlloy} instead, to keep this class clash-free (it claims no real prefix+material).
 */
public final class ExampleAlloy extends GTVariantItem {

    public final Variant ingot;
    public final Variant plate;
    public final Variant dust;
    public final Variant nugget;

    public ExampleAlloy() {
        super("example_alloy");

        // spotless:off

        // (1) Minimal: a display name + one OreDict tag + a literal tooltip.
        //     Texture defaults to the variant key -> gt.example_alloy/ingot.png.
        ingot = add(0, "ingot", "Example Alloy Ingot")
            .oreDict("ingotExampleAlloy")
            .tooltip("Proof-of-concept bundled variant");

        // (2) Composition: several hooks chained — multiple OreDict tags (varargs) + multiple Thaumcraft aspects.
        plate = add(1, "plate", "Example Alloy Plate")
            .oreDict("plateExampleAlloy", "plateAnyExampleAlloy")
            .aspects(
                new TCAspects.TC_AspectStack(TCAspects.METALLUM, 1L),
                new TCAspects.TC_AspectStack(TCAspects.ORDO, 1L));

        // (3) Lang-key tooltip path: no literal tooltip set here, so the tooltip (if any) comes from the
        //     gt.example_alloy.dust.tooltip lang key when present; otherwise the item simply has no tooltip line.
        dust = add(2, "dust", "Example Alloy Dust")
            .oreDict("dustExampleAlloy");

        // (4) Texture reuse + furnace fuel: share an existing icon via .texture(), and make it burnable
        //     (read back by GTProxy's fuel handler). The burn value here is purely illustrative.
        nugget = add(3, "nugget", "Example Alloy Nugget")
            .oreDict("nuggetExampleAlloy")
            .texture("dust")   // reuse gt.example_alloy/dust.png instead of needing a nugget.png
            .burnValue(200);   // smelts exactly one item, like a tiny lump of fuel

        // spotless:on
    }
}
