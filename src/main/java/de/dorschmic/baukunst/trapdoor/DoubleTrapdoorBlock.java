package de.dorschmic.baukunst.trapdoor;

import de.dorschmic.baukunst.orientation.ContentOrientation;
import de.dorschmic.baukunst.orientation.Handedness;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** Two trapdoors on opposite sides of one block space. There is no room to open them. */
public class DoubleTrapdoorBlock extends Block implements EntityBlock {
	public DoubleTrapdoorBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any()
				.setValue(ContentOrientation.FACING, Direction.NORTH)
				.setValue(ContentOrientation.MIRROR, Handedness.LEFT)
				.setValue(ContentOrientation.HALF, Half.BOTTOM));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		// Only describe how the trapdoors are turned, so rotation tools (WorldEdit, structures) can turn them.
		builder.add(ContentOrientation.FACING, ContentOrientation.MIRROR, ContentOrientation.HALF);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new DoubleTrapdoorBlockEntity(pos, state);
	}

	private static VoxelShape combined(BlockGetter level, BlockPos pos, boolean collision) {
		if (!(level.getBlockEntity(pos) instanceof DoubleTrapdoorBlockEntity doors)) {
			return Shapes.block();
		}
		VoxelShape shape = Shapes.empty();
		for (int i = 0; i < 2; i++) {
			BlockState panel = doors.get(i);
			shape = Shapes.or(shape, collision
					? panel.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
					: panel.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
		}
		return shape;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return combined(level, pos, false);
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return combined(level, pos, true);
	}

	@Override
	protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof DoubleTrapdoorBlockEntity doors) {
			return doors.get(DoubleTrapdoors.targetedPanel(player, pos, doors)).getDestroyProgress(player, level, pos);
		}
		return super.getDestroyProgress(state, player, level, pos);
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
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof DoubleTrapdoorBlockEntity doors) {
			return List.of(new ItemStack(doors.get(0).getBlock()), new ItemStack(doors.get(1).getBlock()));
		}
		return List.of();
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return level.getBlockEntity(pos) instanceof DoubleTrapdoorBlockEntity doors
				? new ItemStack(doors.get(0).getBlock()) : ItemStack.EMPTY;
	}
}
