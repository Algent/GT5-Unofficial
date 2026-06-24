package gregtech.api.items;

import static gregtech.api.enums.GTValues.D1;
import static gregtech.api.enums.Mods.GregTech;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.TCAspects;
import gregtech.api.interfaces.IItemContainer;
import gregtech.api.objects.ItemData;
import gregtech.api.util.GTLog;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTUtility;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

/**
 * A lightweight, subtype-capable item base class.
 *
 * <p>
 * This is the "missing middle" between {@link GTGenericItem} (one class = one item, semantically meaningful but
 * single-meta) and {@link MetaGeneratedItem} (hundreds of metas, but an opaque grab-bag fused to the Materials /
 * {@link OrePrefixes} generation system). With item IDs no longer scarce, the goal here is NOT to pack hundreds of
 * unrelated things into one item; it is to bundle a small, <em>semantically related</em> set of variants — e.g. the
 * ingot / plate / dust of a single material — under one item, behind a class whose name actually means something.
 *
 * <p>
 * It is deliberately {@code abstract}: you are meant to subclass it <em>per concept</em> (e.g.
 * {@code class AdvancedAlloy extends GTVariantItem}). That subclass name is where the semantic value lives. If this
 * were instantiable directly, it would just become {@code MetaGeneratedItem03} under a new name.
 *
 * <p>
 * Localization is resolved at render time (client-side), matching the modern deferred-translation approach, so item
 * names follow the player's current language. Variants opt in to OreDict tags and/or unification {@link ItemData} via
 * the fluent {@link Variant} API.
 */
public abstract class GTVariantItem extends Item {

    /** Forge treats item metadata {@code 32767} as the OreDictionary wildcard, so it can never be a real variant. */
    private static final int OREDICT_WILDCARD = 32767;

    /** Every constructed instance, for cross-cutting passes such as the dev-mode lang-coverage check. */
    private static final List<GTVariantItem> INSTANCES = new ArrayList<>();

    /**
     * Unlocalized base name, e.g. {@code "gt.example_alloy"}. Variant keys are appended:
     * {@code "gt.example_alloy.ingot"}.
     */
    protected final String mName;

    /** Registered variants, keyed by metadata. Linked map: insertion order is preserved for creative/NEI listing. */
    private final Int2ObjectLinkedOpenHashMap<Variant> variants = new Int2ObjectLinkedOpenHashMap<>();

    /** Per-meta icons; lookup-only, so order does not matter here. */
    @SideOnly(Side.CLIENT)
    private Int2ObjectOpenHashMap<IIcon> icons;

    /**
     * @param unlocalizedName concept name without the {@code gt.} prefix, e.g. {@code "example_alloy"}. Used for the
     *                        registry name ({@code gt.example_alloy}), the lang key root, and the texture folder.
     */
    protected GTVariantItem(String unlocalizedName) {
        mName = "gt." + unlocalizedName;
        setHasSubtypes(true);
        setMaxDamage(0);
        setCreativeTab(GregTechAPI.TAB_GREGTECH);
        GameRegistry.registerItem(this, mName, GregTech.ID);
        INSTANCES.add(this);
    }

    /**
     * Dev-mode ({@link gregtech.api.enums.GTValues#D1}) check that every registered variant has a translatable
     * {@code <root>.<key>.name} lang entry, so missing keys surface in the log instead of silently using the hardcoded
     * English fallback. Run once on the client after languages have loaded (see {@code GTClient#onLoadComplete}); it is
     * inherently a no-op on the dedicated server, which loads no lang files. Off unless {@code D1}, to avoid log spam.
     */
    public static void validateLangCoverage() {
        if (!D1) return;
        for (GTVariantItem item : INSTANCES) {
            for (Variant variant : item.variants.values()) {
                String key = item.mName + "." + variant.key + ".name";
                if (!StatCollector.canTranslate(key)) {
                    GTLog.out.println(
                        "[GTVariantItem] Missing lang entry " + key
                            + " (falling back to \""
                            + variant.englishName
                            + "\")");
                }
            }
        }
    }

    /**
     * Registers a variant. Call this from the subclass constructor and keep the returned handle as a field for
     * type-safe access, e.g. {@code plate = add(1, "plate", "Example Alloy Plate");}.
     *
     * @param meta        the metadata value (0..32766; avoid 32767, the OreDict wildcard)
     * @param key         short variant key, e.g. {@code "ingot"}; used for the lang sub-key and default texture name
     * @param englishName fallback display name when no {@code <root>.<key>.name} lang entry exists
     */
    protected Variant add(int meta, String key, String englishName) {
        if (meta < 0 || meta == OREDICT_WILDCARD) {
            throw new IllegalArgumentException("Illegal meta " + meta + " for " + mName);
        }
        if (variants.containsKey(meta)) {
            throw new IllegalArgumentException("Meta " + meta + " already registered for " + mName);
        }
        Variant variant = new Variant(meta, key, englishName);
        variants.put(meta, variant);
        return variant;
    }

    /**
     * @return a stack of the given variant metadata.
     * @throws IllegalArgumentException if {@code meta} is not a registered variant — this is the construction-time
     *                                  factory, so an unknown meta is a programming/recipe error and fails loudly. The
     *                                  render/display paths ({@link #getIconFromDamage},
     *                                  {@link #getItemStackDisplayName}
     *                                  etc.) stay lenient so a stale meta in a loaded save degrades instead of
     *                                  crashing.
     */
    public ItemStack get(int meta, long amount) {
        if (!variants.containsKey(meta)) {
            throw new IllegalArgumentException("No variant registered for meta " + meta + " on " + mName);
        }
        return new ItemStack(this, (int) amount, meta);
    }

    /** @return the furnace fuel value (burn ticks) configured for this meta, or {@code 0} if none / unknown. */
    public int getBurnValue(int meta) {
        Variant variant = variants.get(meta);
        return variant == null ? 0 : variant.burnValue;
    }

    @Override
    public final Item setUnlocalizedName(String name) {
        return this; // name is fixed at construction
    }

    @Override
    public String getUnlocalizedName() {
        return mName;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        Variant variant = variants.get(stack.getItemDamage());
        return variant == null ? mName : mName + "." + variant.key;
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        Variant variant = variants.get(stack.getItemDamage());
        if (variant == null) return super.getItemStackDisplayName(stack);
        String key = mName + "." + variant.key + ".name";
        return StatCollector.canTranslate(key) ? GTUtility.translate(key) : variant.englishName;
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> list, boolean f3h) {
        Variant variant = variants.get(stack.getItemDamage());
        if (variant == null) return;
        String key = mName + "." + variant.key + ".tooltip";
        if (StatCollector.canTranslate(key)) {
            GTUtility.translateMultiline(list, key);
        } else if (variant.englishTooltip != null) {
            GTUtility.translateMultiline(list, variant.englishTooltip);
        }
    }

    @Override
    public void getSubItems(Item item, CreativeTabs tab, List<ItemStack> list) {
        for (Variant variant : variants.values()) {
            if (variant.hidden && !D1) continue; // hidden variants stay obtainable but unlisted, except in dev mode
            list.add(get(variant.meta, 1));
        }
    }

    @Override
    public int getItemStackLimit(ItemStack stack) {
        Variant variant = variants.get(stack.getItemDamage());
        return variant == null ? 64 : variant.maxStackSize;
    }

    @Override
    public boolean doesSneakBypassUse(World world, int x, int y, int z, EntityPlayer player) {
        return true; // match GTGenericItem: sneak-right-click passes through to the block, like other GT items
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        icons = new Int2ObjectOpenHashMap<>();
        for (Variant variant : variants.values()) {
            icons.put(variant.meta, register.registerIcon(GregTech.getResourcePath(mName + "/" + variant.texture)));
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int meta) {
        IIcon icon = icons == null ? null : icons.get(meta);
        return icon != null ? icon : super.getIconFromDamage(meta);
    }

    /**
     * A single metadata variant. Configure it fluently right after {@link #add}; the configuration (OreDict tags,
     * unification data) is applied eagerly, so this is meant to be called during item registration (preInit).
     */
    public final class Variant {

        private final int meta;
        private final String key;
        private final String englishName;
        private String texture;
        private String englishTooltip;
        private int burnValue;
        private int maxStackSize = 64;
        private boolean hidden;

        private Variant(int meta, String key, String englishName) {
            this.meta = meta;
            this.key = key;
            this.englishName = englishName;
            this.texture = key; // default: texture named after the variant key
        }

        /** Override the texture name (relative to {@code assets/gregtech/textures/items/<root>/}). */
        public Variant texture(String textureName) {
            this.texture = textureName;
            return this;
        }

        /** Fallback tooltip when no {@code <root>.<key>.tooltip} lang entry exists. */
        public Variant tooltip(String englishTooltip) {
            this.englishTooltip = englishTooltip;
            return this;
        }

        /** Register one or more raw OreDictionary tags for this variant (e.g. {@code "ingotMixedMetal"}). */
        public Variant oreDict(String... names) {
            for (String name : names) {
                GTOreDictUnificator.registerOre(name, get(1));
            }
            return this;
        }

        /**
         * Make this variant a unification target for a prefix+material pair (e.g. {@code plateAlloy, HV} →
         * {@code plateAlloyAdvanced}). This is the capability {@link GTGenericItem} lacks: recipes using
         * {@code GTOreDictUnificator.get(prefix, material, n)} will resolve to this variant.
         *
         * <p>
         * The target slot is claimed <em>only if vacant</em> (non-overwriting): this registers the OreDict tag and the
         * reverse {@link ItemData}, and becomes the canonical item for {@code prefix.material} unless something else
         * has already explicitly {@code set()} it. That avoids the load-order-dependent stomping a forceful {@code set}
         * would cause.
         */
        public Variant materialData(OrePrefixes prefix, Materials material) {
            GTOreDictUnificator.set(prefix, material, get(1), /* overwrite */ false, /* alreadyRegistered */ false);
            return this;
        }

        /** Register Thaumcraft aspects for this variant (no-op when Thaumcraft is absent). */
        public Variant aspects(TCAspects.TC_AspectStack... aspects) {
            if (GregTechAPI.sThaumcraftCompat != null) {
                GregTechAPI.sThaumcraftCompat.registerThaumcraftAspectsToItem(get(1), Arrays.asList(aspects), false);
            }
            return this;
        }

        /**
         * Furnace fuel value in burn ticks (vanilla coal = 1600, an item smelts in 200). Read back by GT's central
         * fuel handler ({@code GTProxy#getBurnTime}), the same path {@link MetaGeneratedItem#setBurnValue} uses.
         */
        public Variant burnValue(int ticks) {
            this.burnValue = ticks;
            return this;
        }

        /** Maximum stack size for this variant (1..64); defaults to 64. */
        public Variant maxStackSize(int limit) {
            if (limit < 1 || limit > 64) {
                throw new IllegalArgumentException("maxStackSize must be 1..64 for " + mName + "." + key);
            }
            this.maxStackSize = limit;
            return this;
        }

        /**
         * Keep this variant out of the creative/NEI listing while leaving it fully obtainable (recipes, {@link #get},
         * commands). Useful for deprecated or internal metas you must not delete. Still shown in dev mode ({@code D1}).
         */
        public Variant hidden() {
            this.hidden = true;
            return this;
        }

        /**
         * Exclude this variant from OreDict unification, so {@code GTOreDictUnificator.get(...)} never swaps this stack
         * for another item registered under the same tag. The variant keeps any OreDict tags it registers (recipes can
         * still target it); it just won't itself be unified away. Mirrors {@code SubTag.NO_UNIFICATION}.
         */
        public Variant noUnify() {
            GTOreDictUnificator.addToBlacklist(get(1));
            return this;
        }

        /**
         * Also expose this variant through the global {@link IItemContainer} registry (i.e. an {@code ItemList} entry),
         * for interop with the many recipe loaders that look items up via {@code ItemList}. Optional — self-contained
         * concepts can skip it and use the typed {@link Variant} field instead.
         */
        public Variant bind(IItemContainer container) {
            container.set(get(1));
            return this;
        }

        public ItemStack get(long amount) {
            return GTVariantItem.this.get(meta, amount);
        }

        public ItemStack get() {
            return get(1);
        }

        public int meta() {
            return meta;
        }
    }
}
