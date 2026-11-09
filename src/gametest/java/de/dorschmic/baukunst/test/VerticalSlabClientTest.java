package de.dorschmic.baukunst.test;

import de.dorschmic.baukunst.slab.VerticalSlabFacing;
import de.dorschmic.baukunst.slab.VerticalSlabMode;
import de.dorschmic.baukunst.slab.VerticalSlabs;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class VerticalSlabClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set noon");

			// 1) Toggle key switches the mode on the server.
			KeyMapping toggle = KeyMapping.get("key.baukunst.toggle_vertical_slabs");
			check(toggle != null, "toggle key registered");
			context.getInput().pressKey(toggle);
			world.getServer().waitFor(server -> VerticalSlabMode.isEnabled(player(server.overworld()).getUUID()));

			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(level);
				BlockPos base = player.blockPosition().offset(3, 0, 0);
				level.setBlockAndUpdate(base, Blocks.STONE.defaultBlockState());

				// 2) Click the east side of a block -> slab hugs that block (west half).
				BlockPos target = base.east();
				place(level, player, Blocks.OAK_SLAB, base, Direction.EAST, new Vec3(base.getX() + 1, base.getY() + 0.5, base.getZ() + 0.5));
				BlockState s = level.getBlockState(target);
				check(s.is(Blocks.OAK_SLAB) && s.getValue(VerticalSlabs.VERTICAL) == VerticalSlabFacing.WEST
						&& s.getValue(SlabBlock.TYPE) == SlabType.BOTTOM, "side click -> vertical west, got " + s);
				check(s.getShape(level, target).bounds().maxX == 0.5, "vertical shape covers west half");

				// 3) Click the open inner face with the same slab -> merges into a full block.
				place(level, player, Blocks.OAK_SLAB, target, Direction.EAST, new Vec3(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5));
				s = level.getBlockState(target);
				check(s.getValue(SlabBlock.TYPE) == SlabType.DOUBLE && s.getValue(VerticalSlabs.VERTICAL) == VerticalSlabFacing.NONE,
						"merge -> double, got " + s);

				// 4) Click the top of a block near its north edge -> north half.
				BlockPos top = base.above();
				place(level, player, Blocks.STONE_BRICK_SLAB, base, Direction.UP, new Vec3(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.1));
				s = level.getBlockState(top);
				check(s.getValue(VerticalSlabs.VERTICAL) == VerticalSlabFacing.NORTH, "top click near north edge -> north, got " + s);
			});

			// 5) Toggle off -> vanilla behaviour again.
			context.getInput().pressKey(toggle);
			world.getServer().waitFor(server -> !VerticalSlabMode.isEnabled(player(server.overworld()).getUUID()));
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(level);
				BlockPos base = player.blockPosition().offset(3, 0, 3);
				level.setBlockAndUpdate(base, Blocks.STONE.defaultBlockState());
				place(level, player, Blocks.OAK_SLAB, base, Direction.EAST, new Vec3(base.getX() + 1, base.getY() + 0.2, base.getZ() + 0.5));
				BlockState s = level.getBlockState(base.east());
				check(s.getValue(VerticalSlabs.VERTICAL) == VerticalSlabFacing.NONE && s.getValue(SlabBlock.TYPE) == SlabType.BOTTOM,
						"mode off -> normal bottom slab, got " + s);
			});

			// 6) Showcase for a screenshot: a little wall of vertical slabs.
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(level);
				BlockPos origin = player.blockPosition().offset(-4, 0, -6);
				List<Block> slabs = List.<Block>of(Blocks.OAK_SLAB, Blocks.SPRUCE_SLAB, Blocks.STONE_BRICK_SLAB, Blocks.QUARTZ_SLAB,
						Blocks.BRICK_SLAB, Blocks.CHERRY_SLAB, Blocks.SMOOTH_STONE_SLAB, Blocks.MUD_BRICK_SLAB);
				VerticalSlabFacing[] facings = {VerticalSlabFacing.NORTH, VerticalSlabFacing.EAST, VerticalSlabFacing.SOUTH, VerticalSlabFacing.WEST};
				for (int i = 0; i < slabs.size(); i++) {
					for (int y = 0; y < 3; y++) {
						BlockState st = slabs.get(i).defaultBlockState().setValue(VerticalSlabs.VERTICAL, facings[(i + y) % 4]);
						level.setBlockAndUpdate(origin.offset(i, y, 0), st);
					}
				}
				player.teleportTo(origin.getX() + 4.5, origin.getY(), origin.getZ() + 5.5);
			});
			context.waitTicks(5);
			world.getConnection().waitForChunksRender();
			BlockPos lookTarget = world.getServer().<BlockPos, RuntimeException>computeOnServer(server -> player(server.overworld()).blockPosition().offset(0, 1, -6));
			context.getInput().lookAt(lookTarget);
			context.waitTicks(10);
			context.takeScreenshot("vertical_slabs");
		}
	}

	private static ServerPlayer player(ServerLevel level) {
		return level.getServer().getPlayerList().getPlayers().getFirst();
	}

	private static void place(ServerLevel level, ServerPlayer player, Block slab, BlockPos clicked, Direction face, Vec3 hit) {
		ItemStack stack = new ItemStack(slab);
		BlockPlaceContext ctx = new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, stack,
				new BlockHitResult(hit, face, clicked, false));
		((BlockItem) stack.getItem()).place(ctx);
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError("FAILED: " + what);
		}
	}
}
