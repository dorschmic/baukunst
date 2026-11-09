package de.dorschmic.baukunst.mixed;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** A full block made of two different slabs (see {@link MixedSlabBlockEntity}). */
public class MixedSlabBlock extends Block implements EntityBlock {
	/** Side of the first slab; the second one is on the opposite side. */
	public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
	/** False if one of the slabs is see-through (glass, leaves …): then neighbours must not hide their faces. */
	public static final BooleanProperty OPAQUE = BooleanProperty.create("opaque");

	public MixedSlabBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN).setValue(OPAQUE, true));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, OPAQUE);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MixedSlabBlockEntity(pos, state);
	}

	@Override
	protected VoxelShape getOcclusionShape(BlockState state) {
		return state.getValue(OPAQUE) ? Shapes.block() : Shapes.empty();
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state) {
		return !state.getValue(OPAQUE);
	}

	@Override
	protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
		// Mining speed and tool of the half the player is looking at.
		if (level.getBlockEntity(pos) instanceof MixedSlabBlockEntity mixed) {
			BlockState half = MixedSlabs.targetedHalf(player, pos, mixed);
			return half.getDestroyProgress(player, level, pos);
		}
		return super.getDestroyProgress(state, player, level, pos);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
	}

	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof MixedSlabBlockEntity mixed) {
			return List.of(new ItemStack(mixed.first()), new ItemStack(mixed.second()));
		}
		return List.of();
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return level.getBlockEntity(pos) instanceof MixedSlabBlockEntity mixed ? new ItemStack(mixed.first()) : ItemStack.EMPTY;
	}
}
