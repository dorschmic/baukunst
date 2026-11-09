package de.dorschmic.baukunst.cluster;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** Holds up to four small vanilla blocks side by side (see {@link ClusterBlockEntity}). */
public class ClusterBlock extends Block implements EntityBlock {
	/** Light of the brightest content (a lantern in the cluster still lights up the area). */
	public static final IntegerProperty LIGHT = BlockStateProperties.LEVEL;

	public ClusterBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(LIGHT, 0));
	}

	/** X/Z offset of a quarter's centre relative to the block centre. */
	public static double offsetX(int slot) {
		return (slot & 1) == 0 ? -0.25 : 0.25;
	}

	public static double offsetZ(int slot) {
		return (slot & 2) == 0 ? -0.25 : 0.25;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LIGHT);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ClusterBlockEntity(pos, state);
	}

	private static VoxelShape combined(BlockGetter level, BlockPos pos, boolean collision) {
		if (!(level.getBlockEntity(pos) instanceof ClusterBlockEntity cluster)) {
			return Shapes.block();
		}
		VoxelShape shape = Shapes.empty();
		for (int i = 0; i < 4; i++) {
			BlockState content = cluster.get(i);
			if (content != null) {
				VoxelShape part = collision
						? content.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
						: content.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
				shape = Shapes.or(shape, part.move(offsetX(i), 0, offsetZ(i)));
			}
		}
		return shape;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		VoxelShape shape = combined(level, pos, false);
		return shape.isEmpty() ? Shapes.block() : shape;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return combined(level, pos, true);
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof ClusterBlockEntity cluster) {
			for (int i = 0; i < 4; i++) {
				BlockState content = cluster.get(i);
				if (content != null && content.canSurvive(level, pos)) {
					return true;
				}
			}
			return cluster.count() == 0;
		}
		return true;
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
			Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		return canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
	}

	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		List<ItemStack> drops = new ArrayList<>();
		if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof ClusterBlockEntity cluster) {
			for (int i = 0; i < 4; i++) {
				if (cluster.get(i) != null) {
					drops.add(Clusters.dropFor(cluster.get(i)));
				}
			}
		}
		return drops;
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		if (level.getBlockEntity(pos) instanceof ClusterBlockEntity cluster) {
			for (int i = 0; i < 4; i++) {
				if (cluster.get(i) != null) {
					return new ItemStack(cluster.get(i).getBlock());
				}
			}
		}
		return ItemStack.EMPTY;
	}
}
