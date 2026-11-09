package de.dorschmic.baukunst.test;

import de.dorschmic.baukunst.cluster.ClusterBlockEntity;
import de.dorschmic.baukunst.cluster.Clusters;
import de.dorschmic.baukunst.mini.MiniGrid;
import de.dorschmic.baukunst.mini.MiniGridBlockEntity;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Breaks a single mini block / small block with a real left click, like a player does.
 * The client predicts the whole block being destroyed; afterwards it must still show the rest.
 */
public class BreakOneClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode creative @a");
			world.getServer().runCommand("time set noon");

			BlockPos[] positions = world.getServer().<BlockPos[], RuntimeException>computeOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				BlockPos feet = player.blockPosition();
				for (int x = -2; x <= 2; x++) {
					for (int z = 1; z <= 3; z++) {
						for (int y = 0; y <= 2; y++) {
							level.setBlockAndUpdate(feet.offset(x, y, z), Blocks.AIR.defaultBlockState());
						}
						level.setBlockAndUpdate(feet.offset(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState());
					}
				}
				BlockPos gridPos = feet.offset(-1, 0, 2);
				level.setBlockAndUpdate(gridPos, MiniGrid.BLOCK.defaultBlockState());
				MiniGridBlockEntity grid = (MiniGridBlockEntity) level.getBlockEntity(gridPos);
				for (int slot = 0; slot < 8; slot++) {
					grid.set(slot, slot % 2 == 0 ? Blocks.OAK_PLANKS : Blocks.STONE);
				}
				BlockPos clusterPos = feet.offset(1, 0, 2);
				level.setBlockAndUpdate(clusterPos, Clusters.BLOCK.defaultBlockState());
				ClusterBlockEntity cluster = (ClusterBlockEntity) level.getBlockEntity(clusterPos);
				cluster.set(0, Blocks.END_ROD.defaultBlockState());
				cluster.set(1, Blocks.END_ROD.defaultBlockState());
				cluster.set(2, Blocks.TORCH.defaultBlockState());
				player.teleportTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
				return new BlockPos[] {gridPos, clusterPos};
			});
			BlockPos gridPos = positions[0];
			BlockPos clusterPos = positions[1];
			context.waitTicks(10);
			world.getConnection().waitForChunksRender();

			// Mini grid: break the upper front-left cell.
			aim(context, new Vec3(gridPos.getX() + 0.25, gridPos.getY() + 0.75, gridPos.getZ() + 0.1));
			context.getInput().pressKey(options -> options.keyAttack);
			context.waitTicks(20);
			int serverMask = world.getServer().<Integer, RuntimeException>computeOnServer(server ->
					((MiniGridBlockEntity) server.overworld().getBlockEntity(gridPos)).mask());
			int clientMask = context.computeOnClient(mc ->
					mc.level.getBlockEntity(gridPos) instanceof MiniGridBlockEntity g ? g.mask() : -1);
			check(Integer.bitCount(serverMask) == 7, "server: one mini block removed, mask=" + Integer.toBinaryString(serverMask));
			check(clientMask == serverMask, "client still shows the other 7, client mask=" + clientMask
					+ " server mask=" + serverMask);

			// Cluster: break the end rod in the front-left quarter.
			aim(context, new Vec3(clusterPos.getX() + 0.25, clusterPos.getY() + 0.5, clusterPos.getZ() + 0.25));
			context.getInput().pressKey(options -> options.keyAttack);
			context.waitTicks(20);
			int serverCount = world.getServer().<Integer, RuntimeException>computeOnServer(server ->
					((ClusterBlockEntity) server.overworld().getBlockEntity(clusterPos)).count());
			int clientCount = context.computeOnClient(mc ->
					mc.level.getBlockEntity(clusterPos) instanceof ClusterBlockEntity c ? c.count() : -1);
			check(serverCount == 2, "server: one small block removed, left " + serverCount);
			check(clientCount == 2, "client still shows the other 2, client count=" + clientCount);
		}
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
