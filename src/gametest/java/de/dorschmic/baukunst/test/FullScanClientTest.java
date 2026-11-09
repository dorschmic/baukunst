package de.dorschmic.baukunst.test;

import de.dorschmic.baukunst.cluster.ClusterBlock;
import de.dorschmic.baukunst.cluster.ClusterBlockEntity;
import de.dorschmic.baukunst.cluster.Clusters;
import de.dorschmic.baukunst.mini.MiniBlock;
import de.dorschmic.baukunst.mini.MiniBlocks;
import de.dorschmic.baukunst.mini.MiniGrid;
import de.dorschmic.baukunst.mini.MiniGridBlockEntity;
import de.dorschmic.baukunst.mixed.MixedSlabBlock;
import de.dorschmic.baukunst.mixed.MixedSlabBlockEntity;
import de.dorschmic.baukunst.mixed.MixedSlabs;
import de.dorschmic.baukunst.shapes.ShapedBlocks;
import de.dorschmic.baukunst.slab.VerticalSlabMode;
import de.dorschmic.baukunst.slab.VerticalSlabs;
import de.dorschmic.baukunst.trapdoor.DoubleTrapdoorBlockEntity;
import de.dorschmic.baukunst.trapdoor.DoubleTrapdoors;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A full pass over the mod, played like a player does: real right / left clicks aimed at exact points,
 * creative and survival. After every step, server and client must agree on the block, its hitbox and
 * its collision box.
 */
public class FullScanClientTest implements FabricClientGameTest {
	private ClientGameTestContext context;
	private TestSingleplayerContext world;
	private final List<String> passed = new ArrayList<>();
	private BlockPos origin;

	@Override
	public void runTest(ClientGameTestContext context) {
		this.context = context;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			this.world = world;
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("gamerule doDaylightCycle false");
			world.getServer().runCommand("gamemode creative @a");
			origin = server(s -> player(s).blockPosition());
			// A clean stone floor with room above.
			server(s -> {
				ServerLevel level = s.overworld();
				for (BlockPos p : BlockPos.betweenClosed(origin.offset(-12, -1, -12), origin.offset(12, 4, 12))) {
					level.setBlock(p, p.getY() == origin.getY() - 1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
				return null;
			});
			context.waitTicks(10);
			world.getConnection().waitForChunksRender();

			verticalSlabs();
			shapedBlocks();
			miniBlocks();
			clusters();
			mixedSlabs();
			doubleTrapdoors();
			survival();

			System.out.println("[SCAN] " + passed.size() + " checks passed:");
			passed.forEach(p -> System.out.println("[SCAN]   ok  " + p));
		}
	}

	// ---------------------------------------------------------------- scenarios

	private void verticalSlabs() {
		BlockPos wall = station(-9, -9);
		setBlock(wall, Blocks.STONE.defaultBlockState());
		toggleVertical(true);

		// Side of a block -> the slab hugs it.
		rightClick(Blocks.OAK_SLAB, face(wall, Direction.SOUTH));
		BlockPos slab = wall.south();
		expect(slab, s -> s.is(Blocks.OAK_SLAB) && s.getValue(VerticalSlabs.VERTICAL) == Direction.NORTH, "vertical slab hugs the wall");
		expectShape(slab, new AABB(0, 0, 0, 1, 1, 0.5), 0.5, "vertical slab hitbox = north half");
		expectCollision(slab, 0.5, "vertical slab collision = north half");

		// Open side -> two of them merge to a full block.
		rightClick(Blocks.OAK_SLAB, new Vec3(slab.getX() + 0.5, slab.getY() + 0.5, slab.getZ() + 0.5));
		expect(slab, s -> s.getValue(SlabBlock.TYPE) == SlabType.DOUBLE && VerticalSlabs.facing(s) == null, "two vertical slabs merge");
		expectShape(slab, new AABB(0, 0, 0, 1, 1, 1), 1.0, "merged slab is a full block");
		leftClick(center(slab));
		expect(slab, BlockState::isAir, "creative break removes it");

		// Top of a block near the east edge -> east half.
		BlockPos floor = wall.east(2).below();
		rightClick(Blocks.SPRUCE_SLAB, new Vec3(floor.getX() + 0.9, floor.getY() + 1, floor.getZ() + 0.5));
		expect(floor.above(), s -> s.is(Blocks.SPRUCE_SLAB) && s.getValue(VerticalSlabs.VERTICAL) == Direction.EAST,
				"top click near east edge -> east half");
		expectShape(floor.above(), new AABB(0.5, 0, 0, 1, 1, 1), 0.5, "east half hitbox");

		// Mode off -> normal slabs again.
		toggleVertical(false);
		BlockPos floor2 = wall.west(2).below();
		rightClick(Blocks.SPRUCE_SLAB, new Vec3(floor2.getX() + 0.9, floor2.getY() + 1, floor2.getZ() + 0.5));
		expect(floor2.above(), s -> s.is(Blocks.SPRUCE_SLAB) && VerticalSlabs.facing(s) == null && s.getValue(SlabBlock.TYPE) == SlabType.BOTTOM,
				"mode off: normal bottom slab");
		expectShape(floor2.above(), new AABB(0, 0, 0, 1, 0.5, 1), 0.5, "bottom slab hitbox");
	}

	private void shapedBlocks() {
		BlockPos floor = station(-3, -9).below();
		Block dirtStairs = shaped(Blocks.DIRT, ShapedBlocks.Kind.STAIRS);
		Block glassSlab = shaped(Blocks.GLASS, ShapedBlocks.Kind.SLAB);

		rightClick(dirtStairs, new Vec3(floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 0.5));
		expect(floor.above(), s -> s.is(dirtStairs) && s.getValue(StairBlock.FACING) == Direction.NORTH, "dirt stairs placed facing away from the player");
		expectShape(floor.above(), null, 0.75, "stairs hitbox (3/4 block)");
		expectCollision(floor.above(), 0.75, "stairs collision");

		BlockPos floor2 = floor.east(2);
		rightClick(glassSlab, new Vec3(floor2.getX() + 0.5, floor2.getY() + 1, floor2.getZ() + 0.5));
		expect(floor2.above(), s -> s.is(glassSlab) && s.getValue(SlabBlock.TYPE) == SlabType.BOTTOM, "glass slab placed");
		expectShape(floor2.above(), new AABB(0, 0, 0, 1, 0.5, 1), 0.5, "glass slab hitbox");
		rightClick(glassSlab, new Vec3(floor2.getX() + 0.5, floor2.getY() + 1.5, floor2.getZ() + 0.5));
		expect(floor2.above(), s -> s.getValue(SlabBlock.TYPE) == SlabType.DOUBLE, "two glass slabs -> double");

		leftClick(center(floor.above()));
		leftClick(center(floor2.above()));
		expect(floor.above(), BlockState::isAir, "stairs broken");
		expect(floor2.above(), BlockState::isAir, "double slab broken");
	}

	private void miniBlocks() {
		BlockPos floor = station(3, -9).below();
		Block miniStone = mini(Blocks.STONE);

		// Floor and wall placement.
		rightClick(miniStone, new Vec3(floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 0.5));
		expect(floor.above(), s -> s.is(miniStone) && !s.getValue(MiniBlock.WALL), "mini block on the floor");
		expectShape(floor.above(), null, -1, "mini block hitbox exists");
		check(clientShape(floor.above()).bounds().maxY <= 0.5, "mini block is at most half a block high");
		BlockPos wall = floor.east(3).above();
		setBlock(wall, Blocks.STONE.defaultBlockState());
		rightClick(miniStone, face(wall, Direction.SOUTH));
		expect(wall.south(), s -> s.is(miniStone) && s.getValue(MiniBlock.WALL), "mini block on a wall");

		// Sneak: 2x2x2 grid, mixed materials.
		BlockPos gridFloor = floor.west(3);
		BlockPos grid = gridFloor.above();
		context.getInput().holdKey(o -> o.keyShift);
		context.waitTicks(3);
		rightClick(mini(Blocks.OAK_PLANKS), new Vec3(grid.getX() + 0.25, grid.getY(), grid.getZ() + 0.25));
		rightClick(mini(Blocks.GLASS), new Vec3(grid.getX() + 0.75, grid.getY(), grid.getZ() + 0.25));
		rightClick(mini(Blocks.GOLD_BLOCK), new Vec3(grid.getX() + 0.25, grid.getY() + 0.5, grid.getZ() + 0.25));
		context.getInput().releaseKey(o -> o.keyShift);
		context.waitTicks(3);
		expect(grid, s -> s.is(MiniGrid.BLOCK), "sneak placement makes a mini grid");
		expectGridMask(grid, 0b111, "grid: planks, glass, gold in the right cells");
		expectShape(grid, null, 3 * 0.125, "grid hitbox = 3 cells");
		expectCollision(grid, 3 * 0.125, "grid collision = 3 cells");

		// Breaking takes one cell at a time.
		leftClick(new Vec3(grid.getX() + 0.75, grid.getY() + 0.25, grid.getZ() + 0.25));
		expectGridMask(grid, 0b101, "breaking the glass cell leaves planks + gold");
		expectShape(grid, null, 2 * 0.125, "grid hitbox shrinks");
		leftClick(new Vec3(grid.getX() + 0.25, grid.getY() + 0.75, grid.getZ() + 0.25));
		expectGridMask(grid, 0b001, "breaking the gold cell leaves planks");
		leftClick(new Vec3(grid.getX() + 0.25, grid.getY() + 0.25, grid.getZ() + 0.25));
		expect(grid, BlockState::isAir, "breaking the last cell removes the grid");
	}

	private void clusters() {
		BlockPos ground = station(9, -9).below();
		setBlock(ground, Blocks.GRASS_BLOCK.defaultBlockState());
		BlockPos pos = ground.above();
		rightClick(Blocks.POPPY, new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
		expect(pos, s -> s.is(Blocks.POPPY), "poppy placed normally");

		// Lantern onto the poppy, aimed at its south-east part -> quarter 3, poppy moves to quarter 0.
		rightClick(Blocks.LANTERN, new Vec3(pos.getX() + 0.6, pos.getY() + 0.3, pos.getZ() + 0.6875));
		expect(pos, s -> s.is(Clusters.BLOCK) && s.getValue(ClusterBlock.LIGHT) == 15, "cluster formed and glows");
		expectCluster(pos, 2, "cluster has poppy + lantern");
		expectShape(pos, null, -1, "cluster hitbox exists");
		check(clientShape(pos).bounds().getXsize() > 0.5, "cluster hitbox spans two quarters");

		rightClick(Blocks.TORCH, new Vec3(pos.getX() + 0.75, pos.getY() + 0.2, pos.getZ() + 0.8));
		expectCluster(pos, 3, "torch added");

		// Break the lantern, then the torch: the poppy returns to the middle as a normal block.
		leftClick(new Vec3(pos.getX() + 0.75, pos.getY() + 0.2, pos.getZ() + 0.75));
		expectCluster(pos, 2, "one small block broken");
		BlockState before = clientState(pos);
		for (int i = 0; i < 2 && server(s -> s.overworld().getBlockState(pos)).is(Clusters.BLOCK); i++) {
			ClusterBlockEntity c = server(s -> (ClusterBlockEntity) s.overworld().getBlockEntity(pos));
			int slot = c.get(3) != null && !c.get(3).is(Blocks.POPPY) ? 3 : c.get(1) != null ? 1 : 2;
			leftClick(new Vec3(pos.getX() + (slot & 1) * 0.5 + 0.25, pos.getY() + 0.2, pos.getZ() + (slot >> 1) * 0.5 + 0.25));
		}
		expect(pos, s -> s.is(Blocks.POPPY), "last one is a normal poppy again (was " + before + ")");
	}

	private void mixedSlabs() {
		BlockPos floor = station(-9, -3).below();
		BlockPos pos = floor.above();
		rightClick(Blocks.OAK_SLAB, new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
		rightClick(Blocks.STONE_BRICK_SLAB, new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
		expect(pos, s -> s.is(MixedSlabs.BLOCK) && s.getValue(MixedSlabBlock.FACING) == Direction.DOWN, "oak + stone brick -> mixed slab");
		expectMixed(pos, Blocks.OAK_SLAB, Blocks.STONE_BRICK_SLAB, "mixed slab: oak below, stone brick above");
		expectShape(pos, new AABB(0, 0, 0, 1, 1, 1), 1.0, "mixed slab hitbox = full block");
		expectCollision(pos, 1.0, "mixed slab collision = full block");
		leftClick(new Vec3(pos.getX() + 0.5, pos.getY() + 0.75, pos.getZ()));
		expect(pos, s -> s.is(Blocks.OAK_SLAB) && s.getValue(SlabBlock.TYPE) == SlabType.BOTTOM,
				"breaking the top half leaves the oak bottom slab");
		expectShape(pos, new AABB(0, 0, 0, 1, 0.5, 1), 0.5, "remaining slab hitbox");

		// Side by side with vertical slabs, glass makes it see-through.
		BlockPos wall = pos.east(3);
		setBlock(wall, Blocks.STONE.defaultBlockState());
		toggleVertical(true);
		rightClick(Blocks.OAK_SLAB, face(wall, Direction.SOUTH));
		rightClick(shaped(Blocks.GLASS, ShapedBlocks.Kind.SLAB), center(wall.south()));
		toggleVertical(false);
		expect(wall.south(), s -> s.is(MixedSlabs.BLOCK) && s.getValue(MixedSlabBlock.FACING) == Direction.NORTH
				&& !s.getValue(MixedSlabBlock.OPAQUE), "vertical mixed slab, see-through");
		leftClick(new Vec3(wall.getX() + 0.5, wall.getY() + 0.5, wall.getZ() + 1.75));
		expect(wall.south(), s -> s.is(Blocks.OAK_SLAB) && s.getValue(VerticalSlabs.VERTICAL) == Direction.NORTH,
				"breaking the glass half leaves the vertical oak slab");
	}

	private void doubleTrapdoors() {
		BlockPos floor = station(-3, -3).below();
		BlockPos pos = floor.above();

		// Floor + ceiling.
		rightClick(Blocks.SPRUCE_TRAPDOOR, new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
		expect(pos, s -> s.is(Blocks.SPRUCE_TRAPDOOR) && !s.getValue(TrapDoorBlock.OPEN) && s.getValue(TrapDoorBlock.HALF) == Half.BOTTOM,
				"spruce trapdoor on the floor");
		rightClick(Blocks.SPRUCE_TRAPDOOR, new Vec3(pos.getX() + 0.5, pos.getY() + 3 / 16.0, pos.getZ() + 0.5));
		expect(pos, s -> s.is(DoubleTrapdoors.BLOCK), "second trapdoor on its inner face -> double trapdoor");
		expectTrapdoors(pos, Direction.DOWN, Direction.UP, "one on the floor, one at the ceiling");
		expectShape(pos, new AABB(0, 0, 0, 1, 1, 1), 2 * 3 / 16.0, "hitbox = two thin panels");
		expectCollision(pos, 2 * 3 / 16.0, "collision = two thin panels");

		// It cannot be opened.
		rightClickEmpty(new Vec3(pos.getX() + 0.5, pos.getY() + 3 / 16.0, pos.getZ() + 0.5));
		expectTrapdoors(pos, Direction.DOWN, Direction.UP, "right click does not open it");

		leftClick(new Vec3(pos.getX() + 0.5, pos.getY() + 13 / 16.0, pos.getZ() + 0.5));
		expect(pos, s -> s.is(Blocks.SPRUCE_TRAPDOOR) && s.getValue(TrapDoorBlock.HALF) == Half.BOTTOM && !s.getValue(TrapDoorBlock.OPEN),
				"breaking the top one leaves the floor trapdoor");

		// Two walls: open trapdoor on the north side, oak one added on the south side.
		BlockPos wallPos = pos.east(3);
		BlockState open = Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN, true);
		for (Direction f : Direction.Plane.HORIZONTAL) {
			if (DoubleTrapdoors.sideOf(open.setValue(TrapDoorBlock.FACING, f)) == Direction.NORTH) {
				open = open.setValue(TrapDoorBlock.FACING, f);
			}
		}
		setBlock(wallPos, open);
		rightClick(Blocks.OAK_TRAPDOOR, new Vec3(wallPos.getX() + 0.5, wallPos.getY() + 0.5, wallPos.getZ() + 3 / 16.0));
		expect(wallPos, s -> s.is(DoubleTrapdoors.BLOCK), "trapdoor against an open trapdoor -> double trapdoor");
		expectTrapdoors(wallPos, Direction.NORTH, Direction.SOUTH, "spruce north, oak south");
		expectShape(wallPos, new AABB(0, 0, 0, 1, 1, 1), 2 * 3 / 16.0, "wall hitbox = two thin panels");

		// Via the block below a ceiling trapdoor.
		BlockPos ceiling = pos.west(3);
		setBlock(ceiling, Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP));
		rightClick(Blocks.SPRUCE_TRAPDOOR, new Vec3(ceiling.getX() + 0.5, ceiling.getY(), ceiling.getZ() + 0.5));
		expectTrapdoors(ceiling, Direction.UP, Direction.DOWN, "clicking the floor below a ceiling trapdoor fills the floor side");
	}

	private void survival() {
		world.getServer().runCommand("gamemode survival @a");
		server(s -> {
			player(s).getInventory().clearContent();
			return null;
		});
		BlockPos pos = station(3, -3);
		server(s -> {
			ServerLevel level = s.overworld();
			level.setBlockAndUpdate(pos, MixedSlabs.BLOCK.defaultBlockState());
			((MixedSlabBlockEntity) level.getBlockEntity(pos)).set(Blocks.OAK_SLAB, Blocks.STONE_SLAB);
			level.setBlockAndUpdate(pos.west(2), DoubleTrapdoors.BLOCK.defaultBlockState());
			return null;
		});
		context.waitTicks(5);
		give(Items.AIR);

		// Oak half by hand: breaks after a while and drops the oak slab, the stone half stays.
		mine(new Vec3(pos.getX() + 0.5, pos.getY() + 0.25, pos.getZ() + 1), 120);
		expect(pos, s -> s.is(Blocks.STONE_SLAB) && s.getValue(SlabBlock.TYPE) == SlabType.TOP, "survival: oak half broken by hand");
		check(dropped(pos, Blocks.OAK_SLAB.asItem()), "survival: oak slab dropped");
		passed.add("survival: oak slab dropped");

		// Stone by hand: no drop (needs a pickaxe), like vanilla.
		mine(new Vec3(pos.getX() + 0.5, pos.getY() + 0.75, pos.getZ() + 1), 400);
		expect(pos, BlockState::isAir, "survival: stone half broken by hand");
		check(!dropped(pos, Blocks.STONE_SLAB.asItem()), "survival: stone slab needs a pickaxe to drop");
		passed.add("survival: stone slab without pickaxe drops nothing");

		// Double trapdoor: one at a time, each drops.
		BlockPos doors = pos.west(2);
		mine(new Vec3(doors.getX() + 0.5, doors.getY() + 3 / 16.0, doors.getZ() + 0.3), 150);
		expect(doors, s -> s.is(Blocks.SPRUCE_TRAPDOOR) && s.getValue(TrapDoorBlock.HALF) == Half.TOP, "survival: floor trapdoor broken, ceiling one stays");
		check(dropped(doors, Blocks.SPRUCE_TRAPDOOR.asItem()), "survival: trapdoor dropped");
		passed.add("survival: trapdoor dropped");
		world.getServer().runCommand("gamemode creative @a");
	}

	// ---------------------------------------------------------------- helpers

	/** Teleports the player 3 blocks south of the returned spot (feet level), looking north. */
	private BlockPos station(int dx, int dz) {
		BlockPos spot = origin.offset(dx, 0, dz);
		server(s -> {
			player(s).teleportTo(s.overworld(), spot.getX() + 0.5, spot.getY(), spot.getZ() + 3.5, java.util.Set.of(), 180, 20, true);
			return null;
		});
		context.waitTicks(5);
		return spot;
	}

	private void toggleVertical(boolean on) {
		boolean now = server(s -> VerticalSlabMode.isEnabled(player(s).getUUID()));
		if (now != on) {
			context.getInput().pressKey(KeyMapping.get("key.baukunst.toggle_vertical_slabs"));
			world.getServer().waitFor(s -> VerticalSlabMode.isEnabled(player(s).getUUID()) == on);
		}
	}

	private void give(Item item) {
		server(s -> {
			player(s).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
			return null;
		});
		context.waitTicks(3);
	}

	private void rightClick(Block block, Vec3 target) {
		give(block.asItem());
		aim(target);
		context.getInput().pressKey((Options o) -> o.keyUse);
		context.waitTicks(5);
	}

	private void rightClickEmpty(Vec3 target) {
		give(Items.AIR);
		aim(target);
		context.getInput().pressKey((Options o) -> o.keyUse);
		context.waitTicks(5);
	}

	private void leftClick(Vec3 target) {
		give(Items.AIR);
		aim(target);
		context.getInput().pressKey((Options o) -> o.keyAttack);
		context.waitTicks(10);
	}

	private void mine(Vec3 target, int ticks) {
		aim(target);
		context.getInput().holdKeyFor((Options o) -> o.keyAttack, ticks);
		context.waitTicks(10);
	}

	private void aim(Vec3 target) {
		Vec3 eye = context.computeOnClient(mc -> mc.player.getEyePosition());
		Vec3 d = target.subtract(eye);
		float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
		float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
		context.getInput().lookAt(yaw, pitch);
		context.waitTicks(2);
	}

	private void setBlock(BlockPos pos, BlockState state) {
		server(s -> s.overworld().setBlockAndUpdate(pos, state));
		context.waitTicks(3);
	}

	private void expect(BlockPos pos, java.util.function.Predicate<BlockState> ok, String what) {
		context.waitTicks(5);
		BlockState serverState = server(s -> s.overworld().getBlockState(pos));
		BlockState clientState = clientState(pos);
		check(ok.test(serverState), what + " – server has " + serverState);
		check(serverState.equals(clientState), what + " – client has " + clientState + ", server " + serverState);
		passed.add(what);
	}

	/** Checks the outline shape on server and client: same boxes, expected bounds (optional) and volume (-1 = any). */
	private void expectShape(BlockPos pos, AABB bounds, double volume, String what) {
		VoxelShape serverShape = server(s -> s.overworld().getBlockState(pos).getShape(s.overworld(), pos));
		VoxelShape clientShape = clientShape(pos);
		check(!serverShape.isEmpty(), what + " – empty hitbox");
		check(serverShape.toAabbs().equals(clientShape.toAabbs()), what + " – client hitbox " + clientShape.toAabbs()
				+ " differs from server " + serverShape.toAabbs());
		if (bounds != null) {
			check(close(serverShape.bounds(), bounds), what + " – bounds " + serverShape.bounds());
		}
		if (volume >= 0) {
			check(Math.abs(volume(serverShape) - volume) < 1.0E-4, what + " – volume " + volume(serverShape) + " expected " + volume);
		}
		passed.add(what);
	}

	private void expectCollision(BlockPos pos, double volume, String what) {
		VoxelShape serverShape = server(s -> s.overworld().getBlockState(pos).getCollisionShape(s.overworld(), pos, CollisionContext.empty()));
		VoxelShape clientShape = context.computeOnClient(mc -> mc.level.getBlockState(pos).getCollisionShape(mc.level, pos, CollisionContext.empty()));
		check(serverShape.toAabbs().equals(clientShape.toAabbs()), what + " – client collision differs");
		check(Math.abs(volume(serverShape) - volume) < 1.0E-4, what + " – volume " + volume(serverShape) + " expected " + volume);
		passed.add(what);
	}

	private void expectGridMask(BlockPos pos, int mask, String what) {
		context.waitTicks(5);
		int serverMask = server(s -> s.overworld().getBlockEntity(pos) instanceof MiniGridBlockEntity g ? g.mask() : -1);
		int clientMask = context.computeOnClient(mc -> mc.level.getBlockEntity(pos) instanceof MiniGridBlockEntity g ? g.mask() : -1);
		check(serverMask == mask, what + " – server mask " + Integer.toBinaryString(serverMask));
		check(clientMask == mask, what + " – client mask " + Integer.toBinaryString(clientMask));
		passed.add(what);
	}

	private void expectCluster(BlockPos pos, int count, String what) {
		context.waitTicks(5);
		int serverCount = server(s -> s.overworld().getBlockEntity(pos) instanceof ClusterBlockEntity c ? c.count() : -1);
		int clientCount = context.computeOnClient(mc -> mc.level.getBlockEntity(pos) instanceof ClusterBlockEntity c ? c.count() : -1);
		check(serverCount == count && clientCount == count, what + " – server " + serverCount + ", client " + clientCount);
		passed.add(what);
	}

	private void expectMixed(BlockPos pos, Block first, Block second, String what) {
		Function<BlockGetter, String> read = level -> level.getBlockEntity(pos) instanceof MixedSlabBlockEntity m
				? m.first() + "/" + m.second() : "none";
		String serverMixed = server(s -> read.apply(s.overworld()));
		String clientMixed = context.computeOnClient(mc -> read.apply(mc.level));
		check(serverMixed.equals(first + "/" + second) && clientMixed.equals(serverMixed), what + " – server " + serverMixed + ", client " + clientMixed);
		passed.add(what);
	}

	private void expectTrapdoors(BlockPos pos, Direction a, Direction b, String what) {
		context.waitTicks(5);
		Function<BlockGetter, String> read = level -> level.getBlockEntity(pos) instanceof DoubleTrapdoorBlockEntity d
				? DoubleTrapdoors.sideOf(d.get(0)) + "/" + DoubleTrapdoors.sideOf(d.get(1)) : "none";
		String serverSides = server(s -> read.apply(s.overworld()));
		String clientSides = context.computeOnClient(mc -> read.apply(mc.level));
		check(serverSides.equals(a + "/" + b) && clientSides.equals(serverSides), what + " – server " + serverSides + ", client " + clientSides);
		passed.add(what);
	}

	private boolean dropped(BlockPos pos, Item item) {
		// On the ground, or already picked up by the player.
		return server(s -> !s.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3),
				e -> e.getItem().is(item)).isEmpty() || player(s).getInventory().contains(new ItemStack(item)));
	}

	private BlockState clientState(BlockPos pos) {
		return context.computeOnClient(mc -> mc.level.getBlockState(pos));
	}

	private VoxelShape clientShape(BlockPos pos) {
		return context.computeOnClient(mc -> mc.level.getBlockState(pos).getShape(mc.level, pos));
	}

	private static double volume(VoxelShape shape) {
		return shape.toAabbs().stream().mapToDouble(b -> b.getXsize() * b.getYsize() * b.getZsize()).sum();
	}

	private static boolean close(AABB a, AABB b) {
		double e = 1.0E-4;
		return Math.abs(a.minX - b.minX) < e && Math.abs(a.minY - b.minY) < e && Math.abs(a.minZ - b.minZ) < e
				&& Math.abs(a.maxX - b.maxX) < e && Math.abs(a.maxY - b.maxY) < e && Math.abs(a.maxZ - b.maxZ) < e;
	}

	private static Vec3 center(BlockPos pos) {
		return Vec3.atCenterOf(pos);
	}

	/** A point in the middle of a block face. */
	private static Vec3 face(BlockPos pos, Direction side) {
		return Vec3.atCenterOf(pos).add(side.getStepX() * 0.5, side.getStepY() * 0.5, side.getStepZ() * 0.5);
	}

	private static Block shaped(Block source, ShapedBlocks.Kind kind) {
		return BuiltInRegistries.BLOCK.getValue(ShapedBlocks.idFor(source, kind));
	}

	private static Block mini(Block source) {
		return BuiltInRegistries.BLOCK.getValue(MiniBlocks.idFor(source));
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private <T> T server(Function<MinecraftServer, T> f) {
		return world.getServer().<T, RuntimeException>computeOnServer(f::apply);
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError("FAILED: " + what);
		}
	}

	private static final class Items {
		static final Item AIR = net.minecraft.world.item.Items.AIR;
	}
}
