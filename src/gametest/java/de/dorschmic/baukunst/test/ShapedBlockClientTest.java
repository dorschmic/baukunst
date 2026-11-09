package de.dorschmic.baukunst.test;

import de.dorschmic.baukunst.shapes.ShapedBlocks;
import de.dorschmic.baukunst.slab.VerticalSlabs;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

public class ShapedBlockClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		check(ShapedBlocks.all().size() > 300, "lots of slabs/stairs: " + ShapedBlocks.all().size());
		check(!BuiltInRegistries.BLOCK.containsKey(ShapedBlocks.idFor(Blocks.STONE, ShapedBlocks.Kind.SLAB)), "no duplicate stone slab");
		check(!BuiltInRegistries.BLOCK.containsKey(ShapedBlocks.idFor(Blocks.OAK_PLANKS, ShapedBlocks.Kind.SLAB)), "no duplicate oak slab");

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set noon");

			// Creative inventory: new slab/stairs follow right after their source block.
			context.runOnClient(mc -> {
				CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), false, mc.level.registryAccess());
				List<ItemStack> natural = new ArrayList<>(BuiltInRegistries.CREATIVE_MODE_TAB.getValueOrThrow(CreativeModeTabs.NATURAL_BLOCKS).getDisplayItems());
				int dirt = indexOf(natural, Blocks.DIRT);
				check(dirt >= 0 && natural.get(dirt + 1).is(shaped(Blocks.DIRT, ShapedBlocks.Kind.SLAB).asItem())
						&& natural.get(dirt + 2).is(shaped(Blocks.DIRT, ShapedBlocks.Kind.STAIRS).asItem()), "dirt slab/stairs after dirt");
				check(BuiltInRegistries.CREATIVE_MODE_TAB.stream().noneMatch(tab ->
						BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab).getNamespace().equals("baukunst")
								&& tab.getDisplayItems().stream().anyMatch(s -> s.is(shaped(Blocks.DIRT, ShapedBlocks.Kind.SLAB).asItem()))),
						"not in an own tab");
			});

			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				Block glassSlab = shaped(Blocks.GLASS, ShapedBlocks.Kind.SLAB);
				check(glassSlab.defaultBlockState().hasProperty(VerticalSlabs.VERTICAL), "new slabs can be vertical");

				Block stoneStairs = shaped(Blocks.DEEPSLATE, ShapedBlocks.Kind.STAIRS);
				check(stoneStairs.defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE), "deepslate stairs need a pickaxe");
				check(shaped(Blocks.DIRT, ShapedBlocks.Kind.SLAB).defaultBlockState().is(BlockTags.MINEABLE_WITH_SHOVEL), "dirt slab: shovel");
				check(shaped(Blocks.DIRT, ShapedBlocks.Kind.SLAB).defaultBlockState().is(BlockTags.SLABS), "in #slabs");

				BlockPos p = server.getPlayerList().getPlayers().getFirst().blockPosition().offset(3, 0, 3);
				BlockState dbl = shaped(Blocks.DIRT, ShapedBlocks.Kind.SLAB).defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE);
				level.setBlockAndUpdate(p, dbl);
				List<ItemStack> drops = Block.getDrops(dbl, level, p, null);
				check(drops.size() == 1 && drops.getFirst().getCount() == 2, "double slab drops 2, got " + drops);
				check(server.getRecipeManager().byKey(net.minecraft.resources.ResourceKey.create(Registries.RECIPE,
						ShapedBlocks.idFor(Blocks.DIRT, ShapedBlocks.Kind.SLAB).withSuffix("_from_stonecutting"))).isPresent(), "recipe");
			});

			// Showcase.
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				var player = server.getPlayerList().getPlayers().getFirst();
				BlockPos o = player.blockPosition().offset(-30, 0, 30);
				Block[] sources = {Blocks.GRASS_BLOCK, Blocks.OAK_LOG, Blocks.GLASS, Blocks.OAK_LEAVES, Blocks.DIRT, Blocks.SNOW_BLOCK,
						Blocks.TERRACOTTA, Blocks.GOLD_BLOCK};
				for (int i = 0; i < sources.length; i++) {
					BlockPos col = o.offset(i * 2 - 7, 0, 0);
					level.setBlockAndUpdate(col, shaped(sources[i], ShapedBlocks.Kind.SLAB).defaultBlockState());
					level.setBlockAndUpdate(col.north(), shaped(sources[i], ShapedBlocks.Kind.STAIRS).defaultBlockState()
							.setValue(StairBlock.FACING, Direction.SOUTH));
					level.setBlockAndUpdate(col.north().east(), shaped(sources[i], ShapedBlocks.Kind.STAIRS).defaultBlockState()
							.setValue(StairBlock.FACING, Direction.WEST));
					level.setBlockAndUpdate(col.north(2), shaped(sources[i], ShapedBlocks.Kind.SLAB).defaultBlockState()
							.setValue(VerticalSlabs.VERTICAL, Direction.NORTH));
					level.setBlockAndUpdate(col.north(2).above(), shaped(sources[i], ShapedBlocks.Kind.SLAB).defaultBlockState()
							.setValue(SlabBlock.TYPE, SlabType.TOP));
				}
				player.teleportTo(o.getX() + 0.5, o.getY() + 1.5, o.getZ() + 4.5);
				for (int i = 0; i < 8; i++) {
					player.getInventory().setItem(i, new ItemStack(shaped(sources[i], i % 2 == 0 ? ShapedBlocks.Kind.SLAB : ShapedBlocks.Kind.STAIRS)));
				}
			});
			context.waitTicks(10);
			world.getConnection().waitForChunksRender();
			BlockPos target = world.getServer().<BlockPos, RuntimeException>computeOnServer(server ->
					server.getPlayerList().getPlayers().getFirst().blockPosition().offset(0, -1, -5));
			context.getInput().lookAt(target);
			context.waitTicks(10);
			context.takeScreenshot("slabs_and_stairs");
		}
	}

	private static Block shaped(Block source, ShapedBlocks.Kind kind) {
		return BuiltInRegistries.BLOCK.getValue(ShapedBlocks.idFor(source, kind));
	}

	private static int indexOf(List<ItemStack> stacks, Block block) {
		for (int i = 0; i < stacks.size(); i++) {
			if (stacks.get(i).is(block.asItem())) {
				return i;
			}
		}
		return -1;
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError("FAILED: " + what);
		}
	}
}
