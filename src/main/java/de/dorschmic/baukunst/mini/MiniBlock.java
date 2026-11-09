package de.dorschmic.baukunst.mini;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A head-sized (8×8×8 pixel) copy of a full block. Placed like a player head:
 * on the floor in 16 rotations, or hanging on a wall.
 */
public class MiniBlock extends Block {
	public static final IntegerProperty ROTATION = BlockStateProperties.ROTATION_16;
	public static final BooleanProperty WALL = BooleanProperty.create("wall");

	private static final VoxelShape FLOOR_SHAPE = Block.box(4, 0, 4, 12, 8, 12);
	private static final Map<Direction, VoxelShape> WALL_SHAPES = new EnumMap<>(Direction.class);

	static {
		// Key = direction the mini block faces (away from the wall).
		WALL_SHAPES.put(Direction.NORTH, Block.box(4, 4, 8, 12, 12, 16));
		WALL_SHAPES.put(Direction.SOUTH, Block.box(4, 4, 0, 12, 12, 8));
		WALL_SHAPES.put(Direction.EAST, Block.box(0, 4, 4, 8, 12, 12));
		WALL_SHAPES.put(Direction.WEST, Block.box(8, 4, 4, 16, 12, 12));
	}

	private final Block source;

	public MiniBlock(Block source, Properties properties) {
		super(properties);
		this.source = source;
		this.registerDefaultState(this.stateDefinition.any().setValue(ROTATION, 0).setValue(WALL, false));
	}

	/** The full-size block this is a miniature of. */
	public Block source() {
		return source;
	}

	/**
	 * Rotation r means the front faces yaw r × 22.5° (Minecraft yaw: 0 = south, 4 = west,
	 * 8 = north, 12 = east), the same convention as {@code Entity#getYRot()}.
	 */
	public static int rotationFor(Direction direction) {
		return Math.round(direction.toYRot() / 22.5F) & 15;
	}

	/** Direction a wall-mounted mini block faces (away from the wall). */
	public static Direction wallFacing(BlockState state) {
		return switch (state.getValue(ROTATION)) {
			case 4 -> Direction.WEST;
			case 8 -> Direction.NORTH;
			case 12 -> Direction.EAST;
			default -> Direction.SOUTH;
		};
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(ROTATION, WALL);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(WALL) ? WALL_SHAPES.get(wallFacing(state)) : FLOOR_SHAPE;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction face = context.getClickedFace();
		if (face.getAxis().isHorizontal()) {
			return defaultBlockState().setValue(WALL, true).setValue(ROTATION, rotationFor(face));
		}
		// Like a player head: the front looks at the player.
		return defaultBlockState().setValue(WALL, false)
				.setValue(ROTATION, Math.round((context.getRotation() + 180.0F) / 22.5F) & 15);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(ROTATION, rotation.rotate(state.getValue(ROTATION), 16));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.setValue(ROTATION, mirror.mirror(state.getValue(ROTATION), 16));
	}

	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		return List.of(new ItemStack(this));
	}

	@Override
	public MutableComponent getName() {
		return Component.translatable("block.baukunst.mini", source.getName());
	}
}
