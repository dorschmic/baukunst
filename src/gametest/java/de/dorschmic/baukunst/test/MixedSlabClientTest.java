package de.dorschmic.baukunst.test;

import de.dorschmic.baukunst.mixed.MixedSlabBlock;
import de.dorschmic.baukunst.mixed.MixedSlabBlockEntity;
import de.dorschmic.baukunst.mixed.MixedSlabs;
import de.dorschmic.baukunst.shapes.ShapedBlocks;
import de.dorschmic.baukunst.slab.VerticalSlabs;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class MixedSlabClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode creative @a");
			world.getServer().runCommand("time set noon");

			BlockPos mixedPos = world.getServer().<BlockPos, RuntimeException>computeOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				BlockPos base = player.blockPosition().offset(0, -1, 4);
				clear(level, base.offset(-4, 0, -1), base.offset(4, 3, 3));

				// 1) Stone brick slab clicked onto the top of an oak bottom slab -> mixed, oak below, stone brick above.
				BlockPos p = base.above();
				level.setBlockAndUpdate(p, Blocks.OAK_SLAB.defaultBlockState());
				check(use(player, level, Blocks.STONE_BRICK_SLAB, p, Direction.UP, new Vec3(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5))
						== InteractionResult.SUCCESS, "stone brick slab placed onto oak slab");
				BlockState s = level.getBlockState(p);
				MixedSlabBlockEntity mixed = (MixedSlabBlockEntity) level.getBlockEntity(p);
				check(s.is(MixedSlabs.BLOCK) && s.getValue(MixedSlabBlock.FACING) == Direction.DOWN
						&& mixed.first() == Blocks.OAK_SLAB && mixed.second() == Blocks.STONE_BRICK_SLAB, "mixed slab oak below / stone brick above, got " + s);
				check(s.getValue(MixedSlabBlock.OPAQUE), "two solid slabs are opaque");
				List<ItemStack> drops = Block.getDrops(s, level, p, mixed);
				check(drops.size() == 2, "drops both slabs, got " + drops);

				// 2) Same slab twice stays the vanilla double slab.
				BlockPos q = base.offset(2, 1, 0);
				level.setBlockAndUpdate(q, Blocks.OAK_SLAB.defaultBlockState());
				check(use(player, level, Blocks.OAK_SLAB, q, Direction.UP, new Vec3(q.getX() + 0.5, q.getY() + 0.5, q.getZ() + 0.5))
						== InteractionResult.PASS, "same slab is left to vanilla");

				// 3) Vertical: glass slab against the open side of a vertical oak slab hugging the north side.
				BlockPos v = base.offset(-2, 1, 0);
				level.setBlockAndUpdate(v, Blocks.OAK_SLAB.defaultBlockState().setValue(VerticalSlabs.VERTICAL, Direction.NORTH));
				Block glassSlab = BuiltInRegistries.BLOCK.getValue(ShapedBlocks.idFor(Blocks.GLASS, ShapedBlocks.Kind.SLAB));
				check(use(player, level, glassSlab, v, Direction.SOUTH, new Vec3(v.getX() + 0.5, v.getY() + 0.5, v.getZ() + 0.5))
						== InteractionResult.SUCCESS, "glass slab placed beside vertical oak slab");
				BlockState vs = level.getBlockState(v);
				check(vs.is(MixedSlabs.BLOCK) && vs.getValue(MixedSlabBlock.FACING) == Direction.NORTH && !vs.getValue(MixedSlabBlock.OPAQUE),
						"vertical mixed slab, see-through, got " + vs);

				// 4) Clicking the block below a top slab fills its free bottom half.
				BlockPos t = base.offset(-4, 1, 0);
				level.setBlockAndUpdate(t, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
				check(use(player, level, Blocks.MOSSY_COBBLESTONE_SLAB, base.offset(-4, 0, 0), Direction.UP,
						new Vec3(t.getX() + 0.5, t.getY(), t.getZ() + 0.5)) == InteractionResult.SUCCESS, "placed into free half via the block below");
				MixedSlabBlockEntity tm = (MixedSlabBlockEntity) level.getBlockEntity(t);
				check(level.getBlockState(t).getValue(MixedSlabBlock.FACING) == Direction.UP && tm.first() == Blocks.SPRUCE_SLAB
						&& tm.second() == Blocks.MOSSY_COBBLESTONE_SLAB, "spruce above, mossy cobblestone below");

				player.teleportTo(p.getX() + 0.5, p.getY(), p.getZ() - 2.5);
				player.setYRot(0);
				return p;
			});
			context.waitTicks(10);
			world.getConnection().waitForChunksRender();

			// 5) Breaking with a real left click removes only the half you look at.
			aim(context, new Vec3(mixedPos.getX() + 0.5, mixedPos.getY() + 0.25, mixedPos.getZ()));
			context.getInput().pressKey(options -> options.keyAttack);
			context.waitTicks(20);
			BlockState left = world.getServer().<BlockState, RuntimeException>computeOnServer(server -> server.overworld().getBlockState(mixedPos));
			check(left.is(Blocks.STONE_BRICK_SLAB) && left.getValue(SlabBlock.TYPE) == SlabType.TOP,
					"oak half broken, stone brick top slab stays, got " + left);
			BlockState clientLeft = context.computeOnClient(mc -> mc.level.getBlockState(mixedPos));
			check(clientLeft.equals(left), "client agrees, got " + clientLeft);

			// Showcase.
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				BlockPos origin = player.blockPosition().offset(-30, 0, -30);
				clear(level, origin.offset(-5, -1, -3), origin.offset(5, 4, 6));
				for (int x = -5; x <= 5; x++) {
					for (int z = -3; z <= 6; z++) {
						level.setBlockAndUpdate(origin.offset(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState());
					}
				}
				Block[][] pairs = {
						{Blocks.OAK_SLAB, Blocks.STONE_BRICK_SLAB}, {Blocks.CHERRY_SLAB, Blocks.QUARTZ_SLAB},
						{Blocks.SPRUCE_SLAB, Blocks.BLACKSTONE_SLAB}, {Blocks.SANDSTONE_SLAB, Blocks.MOSSY_COBBLESTONE_SLAB},
						{Blocks.BRICK_SLAB, Blocks.BIRCH_SLAB}, {Blocks.DARK_OAK_SLAB, Blocks.TUFF_SLAB}};
				Direction[] facings = {Direction.DOWN, Direction.UP, Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH};
				for (int i = 0; i < pairs.length; i++) {
					for (int y = 0; y < 2; y++) {
						BlockPos at = origin.offset(i * 2 - 5, y, 0);
						level.setBlockAndUpdate(at, MixedSlabs.BLOCK.defaultBlockState().setValue(MixedSlabBlock.FACING, facings[(i + y * 2) % 6]));
						((MixedSlabBlockEntity) level.getBlockEntity(at)).set(pairs[i][0], pairs[(i + y) % pairs.length][1]);
					}
				}
				player.teleportTo(origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 5.5);
			});
			context.waitTicks(20);
			world.getConnection().waitForChunksRender();
			BlockPos target = world.getServer().<BlockPos, RuntimeException>computeOnServer(server ->
					server.getPlayerList().getPlayers().getFirst().blockPosition().offset(0, 0, -5));
			context.getInput().lookAt(target);
			context.waitTicks(10);
			context.takeScreenshot("mixed_slabs");
		}
	}

	private static void clear(ServerLevel level, BlockPos from, BlockPos to) {
		for (BlockPos p : BlockPos.betweenClosed(from, to)) {
			level.setBlockAndUpdate(p, p.getY() < from.getY() + 1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
		}
	}

	private static InteractionResult use(ServerPlayer player, ServerLevel level, Block slab, BlockPos clicked, Direction face, Vec3 hit) {
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(slab));
		return UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND,
				new BlockHitResult(hit, face, clicked, false));
	}

	private static void aim(ClientGameTestContext context, Vec3 target) {
		Vec3 eye = context.computeOnClient(mc -> mc.player.getEyePosition());
		Vec3 d = target.subtract(eye);
		float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
		float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
		context.getInput().lookAt(yaw, pitch);
		context.waitTicks(2);
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError("FAILED: " + what);
		}
	}
}
