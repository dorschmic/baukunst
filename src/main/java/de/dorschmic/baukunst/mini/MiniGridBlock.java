package de.dorschmic.baukunst.mini;

import de.dorschmic.baukunst.orientation.ContentOrientation;
import de.dorschmic.baukunst.orientation.Handedness;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** Block that holds up to 8 mini blocks in a 2×2×2 grid (see {@link MiniGridBlockEntity}). */
public class MiniGridBlock extends Block implements EntityBlock {
	private static final VoxelShape[] SHAPES = new VoxelShape[256];

	static {
		for (int mask = 0; mask < 256; mask++) {
			VoxelShape shape = Shapes.empty();
			for (int slot = 0; slot < 8; slot++) {
				if ((mask & (1 << slot)) != 0) {
					double x = (slot & 1) * 8, y = ((slot >> 1) & 1) * 8, z = ((slot >> 2) & 1) * 8;
					shape = Shapes.or(shape, Block.box(x, y, z, x + 8, y + 8, z + 8));
				}
			}
			SHAPES[mask] = shape.optimize();
		}
	}

	public MiniGridBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any()
				.setValue(ContentOrientation.FACING, Direction.NORTH)
				.setValue(ContentOrientation.MIRROR, Handedness.LEFT)
				.setValue(ContentOrientation.HALF, Half.BOTTOM));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		// Only describe how the contents are turned, so rotation tools (WorldEdit, structures) can turn them.
		builder.add(ContentOrientation.FACING, ContentOrientation.MIRROR, ContentOrientation.HALF);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return ContentOrientation.rotate(state, rotation);
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return ContentOrientation.mirror(state, mirror);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MiniGridBlockEntity(pos, state);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (level.getBlockEntity(pos) instanceof MiniGridBlockEntity grid) {
			int mask = grid.mask();
			return mask == 0 ? Shapes.block() : SHAPES[mask];
		}
		return Shapes.block();
	}

	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		List<ItemStack> drops = new ArrayList<>();
		if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof MiniGridBlockEntity grid) {
			for (int i = 0; i < 8; i++) {
				Block source = grid.get(i);
				MiniBlock mini = source == null ? null : MiniBlocks.forSource(source);
				if (mini != null) {
					drops.add(new ItemStack(mini));
				}
			}
		}
		return drops;
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		if (level.getBlockEntity(pos) instanceof MiniGridBlockEntity grid) {
			for (int i = 0; i < 8; i++) {
				MiniBlock mini = grid.get(i) == null ? null : MiniBlocks.forSource(grid.get(i));
				if (mini != null) {
					return new ItemStack(mini);
				}
			}
		}
		return ItemStack.EMPTY;
	}
}
