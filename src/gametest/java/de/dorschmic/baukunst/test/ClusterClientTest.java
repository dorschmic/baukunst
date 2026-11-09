package de.dorschmic.baukunst.test;

import de.dorschmic.baukunst.cluster.ClusterBlock;
import de.dorschmic.baukunst.cluster.ClusterBlockEntity;
import de.dorschmic.baukunst.cluster.Clusters;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class ClusterClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		check(Clusters.isEligible(Blocks.POPPY) && Clusters.isEligible(Blocks.LANTERN) && Clusters.isEligible(Blocks.TORCH),
				"flowers, lanterns and torches are small blocks");
		check(!Clusters.isEligible(Blocks.STONE) && !Clusters.isEligible(Blocks.CHEST) && !Clusters.isEligible(Blocks.LEVER),
				"full blocks, chests and redstone parts are not");

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set noon");

			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				BlockPos ground = player.blockPosition().offset(0, -1, 5);
				BlockPos pos = ground.above();
				level.setBlockAndUpdate(ground, Blocks.GRASS_BLOCK.defaultBlockState());
				level.setBlockAndUpdate(pos, Blocks.POPPY.defaultBlockState());

				// Second small block onto the flower -> both move into quarters.
				check(use(player, level, Blocks.LANTERN, pos, 0.8, 0.8) == InteractionResult.SUCCESS, "lantern placed into flower");
				check(level.getBlockState(pos).is(Clusters.BLOCK), "cluster formed, got " + level.getBlockState(pos));
				ClusterBlockEntity cluster = (ClusterBlockEntity) level.getBlockEntity(pos);
				check(cluster.count() == 2 && cluster.get(3).is(Blocks.LANTERN) && cluster.get(0).is(Blocks.POPPY),
						"lantern in clicked quarter, poppy diagonally opposite");
				check(level.getBlockState(pos).getValue(ClusterBlock.LIGHT) == 15, "cluster glows like the lantern");

				check(use(player, level, Blocks.TORCH, pos, 0.8, 0.2) == InteractionResult.SUCCESS, "torch added");
				check(use(player, level, Blocks.CORNFLOWER, pos, 0.2, 0.8) == InteractionResult.SUCCESS, "cornflower added");
				check(cluster.count() == 4, "four small blocks");
				check(use(player, level, Blocks.DANDELION, pos, 0.2, 0.2) == InteractionResult.PASS, "no fifth one");
				check(cluster.count() == 4, "still four");
				check(Block.getDrops(level.getBlockState(pos), level, pos, cluster).size() == 4, "drops all four");

				// Breaking takes away one at a time; the last one returns to the middle as a normal block.
				for (int expected = 3; expected >= 2; expected--) {
					boolean whole = PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, level.getBlockState(pos), cluster);
					check(!whole && cluster.count() == expected, "one removed, left " + cluster.count());
				}
				PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, level.getBlockState(pos), cluster);
				BlockState last = level.getBlockState(pos);
				check(!last.is(Clusters.BLOCK) && Clusters.isEligible(last.getBlock()), "last one is a normal block again, got " + last);

				// Candles keep their own vanilla stacking.
				BlockPos candlePos = pos.east(2);
				level.setBlockAndUpdate(candlePos.below(), Blocks.STONE.defaultBlockState());
				level.setBlockAndUpdate(candlePos, Blocks.CANDLE.defaultBlockState());
				check(use(player, level, Blocks.CANDLE, candlePos, 0.8, 0.8) == InteractionResult.PASS, "candle onto candle stays vanilla");
			});

			// Showcase: a little flower bed with lanterns and torches.
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				BlockPos origin = player.blockPosition().offset(30, -1, 30);
				Block[][] beds = {
						{Blocks.POPPY, Blocks.DANDELION, Blocks.BLUE_ORCHID, Blocks.ALLIUM},
						{Blocks.LANTERN, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER, Blocks.TORCH},
						{Blocks.RED_TULIP, Blocks.ORANGE_TULIP, Blocks.WHITE_TULIP, Blocks.PINK_TULIP},
						{Blocks.SOUL_LANTERN, Blocks.LILY_OF_THE_VALLEY, Blocks.AZURE_BLUET, Blocks.BROWN_MUSHROOM},
						{Blocks.FERN, Blocks.SHORT_GRASS, Blocks.OAK_SAPLING, Blocks.CHERRY_SAPLING},
				};
				for (int x = -3; x <= 3; x++) {
					for (int z = -2; z <= 2; z++) {
						level.setBlockAndUpdate(origin.offset(x, 0, z), Blocks.GRASS_BLOCK.defaultBlockState());
						for (int y = 1; y <= 4; y++) {
							level.setBlockAndUpdate(origin.offset(x, y, z), Blocks.AIR.defaultBlockState());
						}
					}
				}
				for (int i = 0; i < beds.length; i++) {
					BlockPos pos = origin.offset(i - 2, 1, 0);
					level.setBlockAndUpdate(pos, Clusters.BLOCK.defaultBlockState());
					ClusterBlockEntity cluster = (ClusterBlockEntity) level.getBlockEntity(pos);
					for (int slot = 0; slot < 4; slot++) {
						cluster.set(slot, beds[i][slot].defaultBlockState());
					}
				}
				player.teleportTo(origin.getX() + 0.5, origin.getY() + 1, origin.getZ() + 3.5);
			});
			context.waitTicks(20);
			world.getConnection().waitForChunksRender();
			BlockPos target = world.getServer().<BlockPos, RuntimeException>computeOnServer(server ->
					server.getPlayerList().getPlayers().getFirst().blockPosition().offset(0, -1, -3));
			context.getInput().lookAt(target);
			context.waitTicks(10);
			context.takeScreenshot("small_block_clusters");
		}
	}

	private static InteractionResult use(ServerPlayer player, ServerLevel level, Block block, BlockPos pos, double x, double z) {
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(block));
		Vec3 hit = new Vec3(pos.getX() + x, pos.getY() + 0.3, pos.getZ() + z);
		return UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND,
				new BlockHitResult(hit, Direction.UP, pos, false));
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError("FAILED: " + what);
		}
	}
}
