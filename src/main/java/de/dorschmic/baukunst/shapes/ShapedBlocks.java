package de.dorschmic.baukunst.shapes;

import de.dorschmic.baukunst.Baukunst;
import de.dorschmic.baukunst.mini.MiniBlocks;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.BlockFamilies;
import net.minecraft.data.BlockFamily;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Adds slabs and stairs for every vanilla building block that does not have them yet.
 * Rendering is done by cutting the source block's model to the slab/stair shape (client side).
 */
public final class ShapedBlocks {
	/** Full blocks that have a function – a slab/stair of them would only be confusing. */
	private static final Set<String> EXCLUDED = Set.of(
			"crafting_table", "tnt", "piston", "sticky_piston", "observer", "note_block", "redstone_lamp", "target",
			"respawn_anchor", "lodestone", "cartography_table", "fletching_table", "smithing_table", "loom", "sponge",
			"wet_sponge", "redstone_block", "magma_block", "slime_block", "honey_block", "budding_amethyst",
			"chorus_flower", "carved_pumpkin", "jack_o_lantern", "bedrock", "reinforced_deepslate", "bookshelf");

	public enum Kind {
		SLAB("slab"), STAIRS("stairs");

		public final String suffix;

		Kind(String suffix) {
			this.suffix = suffix;
		}
	}

	public record Shaped(Block block, Block source, Kind kind) {
	}

	private static final List<Shaped> ALL = new ArrayList<>();

	private ShapedBlocks() {
	}

	public static List<Shaped> all() {
		return Collections.unmodifiableList(ALL);
	}

	public static void init() {
		List<Block> sources = new ArrayList<>();
		for (Block block : BuiltInRegistries.BLOCK) {
			String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
			if (MiniBlocks.isFullCube(block) && !EXCLUDED.contains(path) && !path.contains("copper_bulb")) {
				sources.add(block);
			}
		}
		for (Block source : sources) {
			if (!hasVanilla(source, Kind.SLAB)) {
				register(source, Kind.SLAB);
			}
			if (!hasVanilla(source, Kind.STAIRS)) {
				register(source, Kind.STAIRS);
			}
		}
		Baukunst.LOGGER.info("{} neue Stufen/Treppen registriert", ALL.size());
	}

	/** Does vanilla already have a slab/stair for this block? */
	private static boolean hasVanilla(Block source, Kind kind) {
		BlockFamily family = BlockFamilies.getFamily(source);
		if (family != null && family.getVariants().containsKey(kind == Kind.SLAB ? BlockFamily.Variant.SLAB : BlockFamily.Variant.STAIRS)) {
			return true;
		}
		// Naming fallback: oak_planks -> oak_slab, bricks -> brick_slab, quartz_block -> quartz_slab …
		String path = BuiltInRegistries.BLOCK.getKey(source).getPath();
		List<String> stems = new ArrayList<>(List.of(path));
		for (String suffix : List.of("_planks", "_block", "s")) {
			if (path.endsWith(suffix)) {
				stems.add(path.substring(0, path.length() - suffix.length()));
			}
		}
		for (String stem : stems) {
			if (BuiltInRegistries.BLOCK.containsKey(Identifier.withDefaultNamespace(stem + "_" + kind.suffix))) {
				return true;
			}
		}
		return false;
	}

	public static Identifier idFor(Block source, Kind kind) {
		return Baukunst.id(BuiltInRegistries.BLOCK.getKey(source).getPath() + "_" + kind.suffix);
	}

	private static void register(Block source, Kind kind) {
		Identifier id = idFor(source, kind);
		int light = source.defaultBlockState().getLightEmission();
		BlockBehaviour.Properties props = BlockBehaviour.Properties.ofLegacyCopy(source)
				.setId(ResourceKey.create(Registries.BLOCK, id))
				// The source's functions may read block state properties our block does not have.
				.mapColor(source.defaultMapColor())
				.lightLevel(s -> light);

		String nameKey = "block.baukunst." + kind.suffix;
		Block block = switch (kind) {
			case SLAB -> new SlabBlock(props) {
				@Override
				public net.minecraft.network.chat.MutableComponent getName() {
					return Component.translatable(nameKey, source.getName());
				}
			};
			case STAIRS -> new StairBlock(source.defaultBlockState(), props) {
				@Override
				public net.minecraft.network.chat.MutableComponent getName() {
					return Component.translatable(nameKey, source.getName());
				}
			};
		};
		Registry.register(BuiltInRegistries.BLOCK, id, block);

		BlockItem item = new BlockItem(block, new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id))) {
			@Override
			public Component getName(ItemStack stack) {
				return block.getName();
			}
		};
		item.registerBlocks(Item.BY_BLOCK, item);
		Registry.register(BuiltInRegistries.ITEM, id, item);

		ALL.add(new Shaped(block, source, kind));
	}
}
