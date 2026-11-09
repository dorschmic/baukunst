package de.dorschmic.baukunst.test;

import de.dorschmic.baukunst.cluster.ClusterBlockEntity;
import de.dorschmic.baukunst.cluster.Clusters;
import de.dorschmic.baukunst.mini.MiniBlock;
import de.dorschmic.baukunst.mini.MiniBlocks;
import de.dorschmic.baukunst.mini.MiniGrid;
import de.dorschmic.baukunst.mini.MiniGridBlockEntity;
import de.dorschmic.baukunst.shapes.ShapedBlocks;
import de.dorschmic.baukunst.slab.VerticalSlabs;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Copies and pastes our blocks with WorldEdit (only present in dev runs), unrotated and rotated by 90°. */
public class WorldEditClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (!FabricLoader.getInstance().isModLoaded("worldedit")) {
			System.out.println("[WE] WorldEdit not loaded, skipping");
			return;
		}
		net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((message, overlay) ->
				System.out.println("[WE] chat: " + message.getString()));
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode creative @a");
			world.getServer().runOnServer(server -> {
				var player = server.getPlayerList().getPlayers().getFirst();
				server.getPlayerList().op(player.nameAndId());
			});
			context.waitTicks(5);

			BlockPos origin = world.getServer().<BlockPos, RuntimeException>computeOnServer(server -> {
				ServerLevel level = server.overworld();
				BlockPos o = server.getPlayerList().getPlayers().getFirst().blockPosition().offset(10, 5, 10);
				Block dirtStairs = BuiltInRegistries.BLOCK.getValue(ShapedBlocks.idFor(Blocks.DIRT, ShapedBlocks.Kind.STAIRS));
				Block miniStone = BuiltInRegistries.BLOCK.getValue(MiniBlocks.idFor(Blocks.STONE));
				level.setBlockAndUpdate(o, Blocks.OAK_SLAB.defaultBlockState().setValue(VerticalSlabs.VERTICAL, Direction.NORTH));
				level.setBlockAndUpdate(o.east(), dirtStairs.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
				level.setBlockAndUpdate(o.east(2), miniStone.defaultBlockState().setValue(MiniBlock.ROTATION, 0));
				level.setBlockAndUpdate(o.south(), MiniGrid.BLOCK.defaultBlockState());
				MiniGridBlockEntity grid = (MiniGridBlockEntity) level.getBlockEntity(o.south());
				grid.set(MiniGridBlockEntity.slotIndex(0, 0, 0), Blocks.OAK_PLANKS);
				grid.set(MiniGridBlockEntity.slotIndex(1, 0, 0), Blocks.OAK_PLANKS);
				grid.set(MiniGridBlockEntity.slotIndex(0, 1, 0), Blocks.GOLD_BLOCK);
				level.setBlockAndUpdate(o.south().east(), Clusters.BLOCK.defaultBlockState());
				ClusterBlockEntity cluster = (ClusterBlockEntity) level.getBlockEntity(o.south().east());
				cluster.set(0, Blocks.LANTERN.defaultBlockState());
				cluster.set(3, Blocks.END_ROD.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST));
				return o;
			});

			BlockPos copyFrom = origin;
			BlockPos copyTo = origin.offset(2, 0, 1);
			BlockPos plain = origin.offset(0, 0, 10);
			BlockPos rotated = origin.offset(0, 0, 20);
			BlockPos flipped = origin.offset(0, 0, 30);
			tp(context, world, copyFrom);
			cmd(context, world, "/pos1 " + coords(copyFrom));
			cmd(context, world, "/pos2 " + coords(copyTo));
			cmd(context, world, "/copy");
			tp(context, world, plain);
			cmd(context, world, "/paste");
			tp(context, world, rotated);
			cmd(context, world, "/rotate 90");
			cmd(context, world, "/paste");
			tp(context, world, copyFrom);
			cmd(context, world, "/copy");
			tp(context, world, flipped);
			cmd(context, world, "/flip east");
			cmd(context, world, "/paste");
			context.waitTicks(20);

			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				dump("original", level, origin);
				dump("pasted", level, plain);
				// Rotating 90° clockwise around the copy origin: (x, z) -> (-z, x)
				dump("rotated 90", level, rotated);
				dump("flipped", level, flipped);

				// Plain copy & paste keeps everything, including the contents of mini grids and clusters.
				for (BlockPos rel : new BlockPos[] {BlockPos.ZERO, new BlockPos(1, 0, 0), new BlockPos(2, 0, 0),
						new BlockPos(0, 0, 1), new BlockPos(1, 0, 1)}) {
					check(level.getBlockState(origin.offset(rel)).equals(level.getBlockState(plain.offset(rel))), "paste keeps state at " + rel);
				}
				check(level.getBlockEntity(plain.south()) instanceof MiniGridBlockEntity g && g.mask() == 0b111, "paste keeps grid contents");
				check(level.getBlockEntity(plain.south().east()) instanceof ClusterBlockEntity c && c.count() == 2, "paste keeps cluster contents");

				// Rotating by 90° clockwise: (x, z) -> (-z, x).
				check(level.getBlockState(rotated.offset(0, 0, 1)).getValue(StairBlock.FACING) == Direction.EAST, "stairs rotate");
				check(level.getBlockState(rotated.offset(0, 0, 2)).getValue(MiniBlock.ROTATION) == 4, "mini block rotates");
				check(level.getBlockState(rotated).getValue(VerticalSlabs.VERTICAL) == Direction.EAST, "vertical slab rotates north -> east");
				// Grid contents turn along: planks (0,0,0),(1,0,0) -> (1,0,0),(1,0,1); gold (0,1,0) -> (1,1,0).
				MiniGridBlockEntity rg = (MiniGridBlockEntity) level.getBlockEntity(rotated.offset(-1, 0, 0));
				check(rg.mask() == ((1 << 1) | (1 << 5) | (1 << 3)) && rg.get(3) == Blocks.GOLD_BLOCK,
						"grid contents rotate, mask=" + Integer.toBinaryString(rg.mask()));
				// Cluster: lantern quarter 0 -> 1, end rod quarter 3 -> 2 and pointing south instead of east.
				ClusterBlockEntity rc = (ClusterBlockEntity) level.getBlockEntity(rotated.offset(-1, 0, 1));
				check(rc.get(1) != null && rc.get(1).is(Blocks.LANTERN) && rc.get(2) != null
						&& rc.get(2).getValue(BlockStateProperties.FACING) == Direction.SOUTH, "cluster contents rotate");

				// Flip east-west: x -> -x.
				MiniGridBlockEntity fg = (MiniGridBlockEntity) level.getBlockEntity(flipped.offset(0, 0, 1));
				check(fg.mask() == 0b1011 && fg.get(3) == Blocks.GOLD_BLOCK, "grid contents mirror, mask=" + Integer.toBinaryString(fg.mask()));
				ClusterBlockEntity fc = (ClusterBlockEntity) level.getBlockEntity(flipped.offset(-1, 0, 1));
				check(fc.get(1) != null && fc.get(1).is(Blocks.LANTERN) && fc.get(2) != null
						&& fc.get(2).getValue(BlockStateProperties.FACING) == Direction.WEST, "cluster contents mirror");
				check(level.getBlockState(flipped.offset(-1, 0, 0)).getValue(StairBlock.FACING) == Direction.NORTH, "stairs mirror");
			});
		}
	}

	private static void dump(String label, ServerLevel level, BlockPos base) {
		System.out.println("[WE] ---- " + label + " at " + base);
		for (int dy = -8; dy <= 8; dy++) for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				BlockPos p = base.offset(dx, dy, dz);
				BlockState s = level.getBlockState(p);
				if (s.isAir() || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.BEDROCK) || s.is(Blocks.GLASS)) {
					continue;
				}
				String extra = "";
				if (level.getBlockEntity(p) instanceof MiniGridBlockEntity g) {
					StringBuilder b = new StringBuilder(" grid");
					for (int i = 0; i < 8; i++) {
						if (g.get(i) != null) {
							b.append(" ").append(i).append("=").append(BuiltInRegistries.BLOCK.getKey(g.get(i)).getPath());
						}
					}
					extra = b.toString();
				} else if (level.getBlockEntity(p) instanceof ClusterBlockEntity c) {
					extra = " cluster " + c.get(0) + "," + c.get(1) + "," + c.get(2) + "," + c.get(3);
				}
				System.out.println("[WE] (" + dx + "," + dy + "," + dz + ") " + s + extra);
			}
		}
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError("FAILED: " + what);
		}
	}

	private static String coords(BlockPos p) {
		return p.getX() + "," + p.getY() + "," + p.getZ();
	}

	private static void tp(ClientGameTestContext context, TestSingleplayerContext world, BlockPos p) {
		world.getServer().runOnServer(server -> server.overworld().setBlockAndUpdate(p.below(), Blocks.GLASS.defaultBlockState()));
		world.getServer().runCommand("tp @a " + p.getX() + " " + p.getY() + " " + p.getZ());
		context.waitTicks(5);
	}

	/** Runs a WorldEdit command (e.g. "/copy" for //copy) as the player. */
	private static void cmd(ClientGameTestContext context, TestSingleplayerContext world, String command) {
		world.getServer().runOnServer(server -> {
			var player = server.getPlayerList().getPlayers().getFirst();
			server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "/" + command);
		});
		context.waitTicks(5);
	}
}
