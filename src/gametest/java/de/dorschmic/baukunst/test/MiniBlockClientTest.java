package de.dorschmic.baukunst.test;

import de.dorschmic.baukunst.mini.MiniBlock;
import de.dorschmic.baukunst.mini.MiniBlocks;
import de.dorschmic.baukunst.mini.MiniGrid;
import de.dorschmic.baukunst.mini.MiniGridBlockEntity;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class MiniBlockClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		check(MiniBlocks.all().size() > 300, "lots of mini blocks registered: " + MiniBlocks.all().size());

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set noon");

			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				Block miniStone = mini(Blocks.STONE);
				BlockPos base = player.blockPosition().offset(0, 0, 5);
				level.setBlockAndUpdate(base, Blocks.STONE.defaultBlockState());

				// Click the top of a block -> placed on the floor.
				place(level, player, miniStone, base, Direction.UP, Vec3.atCenterOf(base).add(0, 0.5, 0));
				BlockState floor = level.getBlockState(base.above());
				check(floor.is(miniStone) && !floor.getValue(MiniBlock.WALL), "floor placement, got " + floor);
				check(floor.getShape(level, base.above()).bounds().maxY == 0.5, "floor shape is half a block high");

				// Click the side of a block -> hangs on the wall, facing away from it.
				place(level, player, miniStone, base, Direction.EAST, Vec3.atCenterOf(base).add(0.5, 0, 0));
				BlockState wall = level.getBlockState(base.east());
				check(wall.is(miniStone) && wall.getValue(MiniBlock.WALL) && MiniBlock.wallFacing(wall) == Direction.EAST,
						"wall placement facing east, got " + wall);

				// Drops itself.
				List<ItemStack> drops = Block.getDrops(floor, level, base.above(), null);
				check(drops.size() == 1 && drops.getFirst().is(miniStone.asItem()), "drops itself, got " + drops);

				check(server.getRecipeManager().byKey(net.minecraft.resources.ResourceKey.create(
						net.minecraft.core.registries.Registries.RECIPE,
						MiniBlocks.idFor(Blocks.STONE).withSuffix("_from_stonecutting"))).isPresent(), "stonecutter recipe exists");
			});

			// Sneak placement: mini blocks snap into a 2x2x2 grid, mixed materials in one block space.
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				BlockPos base = player.blockPosition().offset(6, 0, 0);
				level.setBlockAndUpdate(base, Blocks.STONE.defaultBlockState());
				player.setShiftKeyDown(true);
				useOn(player, mini(Blocks.OAK_PLANKS), base, Direction.UP, new Vec3(base.getX() + 0.25, base.getY() + 1, base.getZ() + 0.25));
				useOn(player, mini(Blocks.GLASS), base, Direction.UP, new Vec3(base.getX() + 0.75, base.getY() + 1, base.getZ() + 0.25));
				BlockPos gridPos = base.above();
				// Click on top of the first mini block -> goes into the cell above it.
				useOn(player, mini(Blocks.GOLD_BLOCK), gridPos, Direction.UP, new Vec3(gridPos.getX() + 0.25, gridPos.getY() + 0.5, gridPos.getZ() + 0.25));
				player.setShiftKeyDown(false);

				check(level.getBlockState(gridPos).is(MiniGrid.BLOCK), "grid block placed");
				MiniGridBlockEntity grid = (MiniGridBlockEntity) level.getBlockEntity(gridPos);
				check(grid.mask() == 0b111, "three cells filled (x0y0z0, x1y0z0, x0y1z0), mask=" + Integer.toBinaryString(grid.mask()));
				check(grid.get(0) == Blocks.OAK_PLANKS && grid.get(1) == Blocks.GLASS && grid.get(2) == Blocks.GOLD_BLOCK, "right materials");
				check(level.getBlockState(gridPos).getShape(level, gridPos).bounds().maxY == 1.0, "shape follows contents");

				List<ItemStack> drops = Block.getDrops(level.getBlockState(gridPos), level, gridPos, grid);
				check(drops.size() == 3, "drops all three, got " + drops);

				// Breaking removes only one mini block while others remain.
				boolean vanillaBreak = net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.invoker()
						.beforeBlockBreak(level, player, gridPos, level.getBlockState(gridPos), grid);
				check(!vanillaBreak && Integer.bitCount(grid.mask()) == 2, "one mini block removed, mask=" + Integer.toBinaryString(grid.mask()));
			});

			// Showcase: a row of mini blocks on the floor, a few on a wall.
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				BlockPos origin = player.blockPosition().offset(-30, 0, -30);
				List<Block> sources = List.<Block>of(Blocks.CARVED_PUMPKIN, Blocks.GRASS_BLOCK, Blocks.OAK_LOG, Blocks.CRAFTING_TABLE,
						Blocks.OAK_LEAVES, Blocks.GLASS, Blocks.SEA_LANTERN, Blocks.DIAMOND_ORE, Blocks.BOOKSHELF, Blocks.TNT);
				for (int i = 0; i < sources.size(); i++) {
					BlockState st = mini(sources.get(i)).defaultBlockState().setValue(MiniBlock.ROTATION, (i * 2) % 16);
					level.setBlockAndUpdate(origin.offset(i - 5, 0, 0), st);
				}
				// Pumpkins facing the camera (south = rotation 0) on floor and wall.
				for (int x = -5; x < 5; x++) {
					level.setBlockAndUpdate(origin.offset(x, 0, -2), Blocks.STONE_BRICKS.defaultBlockState());
					level.setBlockAndUpdate(origin.offset(x, 1, -2), Blocks.STONE_BRICKS.defaultBlockState());
					level.setBlockAndUpdate(origin.offset(x, 2, -2), Blocks.STONE_BRICKS.defaultBlockState());
				}
				Block[] wallSources = {Blocks.CARVED_PUMPKIN, Blocks.SEA_LANTERN, Blocks.GOLD_BLOCK, Blocks.CHERRY_PLANKS};
				for (int i = 0; i < wallSources.length; i++) {
					level.setBlockAndUpdate(origin.offset(-3 + i * 2, 1, -1), mini(wallSources[i]).defaultBlockState()
							.setValue(MiniBlock.WALL, true).setValue(MiniBlock.ROTATION, 0));
				}
				level.setBlockAndUpdate(origin.offset(0, 1, 0), mini(Blocks.CARVED_PUMPKIN).defaultBlockState().setValue(MiniBlock.ROTATION, 0));
				// A 2x2x2 checkerboard and a tiny staircase built from the grid.
				BlockPos gridA = origin.offset(1, 0, 2);
				BlockPos gridB = origin.offset(-2, 0, 2);
				level.setBlockAndUpdate(gridA, MiniGrid.BLOCK.defaultBlockState());
				level.setBlockAndUpdate(gridB, MiniGrid.BLOCK.defaultBlockState());
				MiniGridBlockEntity a = (MiniGridBlockEntity) level.getBlockEntity(gridA);
				MiniGridBlockEntity b = (MiniGridBlockEntity) level.getBlockEntity(gridB);
				for (int slot = 0; slot < 8; slot++) {
					int parity = (slot & 1) ^ ((slot >> 1) & 1) ^ ((slot >> 2) & 1);
					a.set(slot, parity == 0 ? Blocks.QUARTZ_BLOCK : Blocks.BLACKSTONE);
				}
				b.set(MiniGridBlockEntity.slotIndex(0, 0, 0), Blocks.OAK_PLANKS);
				b.set(MiniGridBlockEntity.slotIndex(1, 0, 0), Blocks.OAK_PLANKS);
				b.set(MiniGridBlockEntity.slotIndex(0, 0, 1), Blocks.OAK_PLANKS);
				b.set(MiniGridBlockEntity.slotIndex(1, 0, 1), Blocks.OAK_PLANKS);
				b.set(MiniGridBlockEntity.slotIndex(0, 1, 0), Blocks.OAK_PLANKS);
				b.set(MiniGridBlockEntity.slotIndex(1, 1, 0), Blocks.MOSS_BLOCK);
				player.teleportTo(origin.getX() + 0.5, origin.getY() + 0.6, origin.getZ() + 5.5);
				for (int i = 0; i < 9; i++) {
					player.getInventory().setItem(i, new ItemStack(mini(sources.get(i))));
				}
			});
			context.waitTicks(10);
			world.getConnection().waitForChunksRender();
			BlockPos target = world.getServer().<BlockPos, RuntimeException>computeOnServer(server ->
					server.getPlayerList().getPlayers().getFirst().blockPosition().offset(0, 0, -5));
			context.getInput().lookAt(target);
			context.waitTicks(10);
			context.takeScreenshot("mini_blocks");
		}
	}

	private static Block mini(Block source) {
		return BuiltInRegistries.BLOCK.getValue(MiniBlocks.idFor(source));
	}

	private static void useOn(ServerPlayer player, Block mini, BlockPos clicked, Direction face, Vec3 hit) {
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(mini));
		new ItemStack(mini).getItem().useOn(new net.minecraft.world.item.context.UseOnContext(player, InteractionHand.MAIN_HAND,
				new BlockHitResult(hit, face, clicked, false)));
	}

	private static void place(ServerLevel level, ServerPlayer player, Block block, BlockPos clicked, Direction face, Vec3 hit) {
		ItemStack stack = new ItemStack(block);
		((BlockItem) stack.getItem()).place(new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, stack,
				new BlockHitResult(hit, face, clicked, false)));
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError("FAILED: " + what);
		}
	}
}
