package de.dorschmic.baukunst.mini;

import de.dorschmic.baukunst.Baukunst;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.InfestedBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;

/** Registers a {@link MiniBlock} for every vanilla full-cube block. */
public final class MiniBlocks {
	private static final List<MiniBlock> ALL = new ArrayList<>();

	private MiniBlocks() {
	}

	public static List<MiniBlock> all() {
		return Collections.unmodifiableList(ALL);
	}

	public static Identifier idFor(Block source) {
		return Baukunst.id("mini_" + BuiltInRegistries.BLOCK.getKey(source).getPath());
	}

	public static void init() {
		List<Block> sources = new ArrayList<>();
		for (Block block : BuiltInRegistries.BLOCK) {
			if (isFullCube(block)) {
				sources.add(block);
			}
		}
		for (Block source : sources) {
			register(source);
		}

		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Baukunst.id("mini_blocks"),
				FabricCreativeModeTab.builder()
						.title(Component.translatable("itemGroup.baukunst.mini_blocks"))
						.icon(() -> new ItemStack(ALL.isEmpty() ? Blocks.STONE : ALL.getFirst()))
						.displayItems((params, output) -> ALL.forEach(output::accept))
						.build());

		Baukunst.LOGGER.info("{} Mini-Blöcke registriert", ALL.size());
	}

	static boolean isFullCube(Block block) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(block);
		if (!"minecraft".equals(id.getNamespace())) {
			return false;
		}
		if (block instanceof EntityBlock || block instanceof InfestedBlock) {
			return false;
		}
		if (!(block.asItem() instanceof BlockItem blockItem) || blockItem.getBlock() != block) {
			return false;
		}
		if (!block.requiredFeatures().isSubsetOf(FeatureFlags.VANILLA_SET)) {
			return false;
		}
		BlockState state = block.defaultBlockState();
		if (state.getRenderShape() != RenderShape.MODEL) {
			return false;
		}
		try {
			// Full-cube outline and something solid to stand on (filters out plants, vines, cobwebs …,
			// but keeps mud or soul sand whose collision box is slightly lower).
			return Block.isShapeFullBlock(state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO))
					&& !state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
		} catch (RuntimeException e) {
			return false;
		}
	}

	private static void register(Block source) {
		Identifier id = idFor(source);
		BlockState sourceState = source.defaultBlockState();
		int light = sourceState.getLightEmission();

		BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
				.setId(ResourceKey.create(Registries.BLOCK, id))
				.mapColor(source.defaultMapColor())
				.sound(sourceState.getSoundType())
				.lightLevel(s -> light)
				.strength(1.0F)
				.noOcclusion()
				.pushReaction(PushReaction.POPPED);
		MiniBlock block = Registry.register(BuiltInRegistries.BLOCK, id, new MiniBlock(source, props));

		Item.Properties itemProps = new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id));
		BlockItem item = new BlockItem(block, itemProps) {
			@Override
			public Component getName(ItemStack stack) {
				// "Mini-<Blockname>" in the player's language, no per-block translations needed.
				return block.getName();
			}
		};
		item.registerBlocks(Item.BY_BLOCK, item);
		Registry.register(BuiltInRegistries.ITEM, id, item);

		ALL.add(block);
	}
}
