package de.dorschmic.baukunst.client.mixed;

import de.dorschmic.baukunst.client.SourceTint;
import de.dorschmic.baukunst.mixed.MixedSlabBlock;
import de.dorschmic.baukunst.mixed.MixedSlabs;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Renders both halves of a mixed slab block with the models of the two slabs. */
public final class MixedSlabStateModel implements BlockStateModel {
	private static BlockStateModel modelOf(BlockState state) {
		return Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
	}

	/** Slab states of both halves, or null while the block entity data has not arrived yet. */
	private static BlockState @Nullable [] halves(BlockAndTintGetter level, BlockPos pos, BlockState state) {
		if (!(level.getBlockEntityRenderData(pos) instanceof Block[] slabs) || slabs.length != 2) {
			return null;
		}
		Direction side = state.getValue(MixedSlabBlock.FACING);
		return new BlockState[] {MixedSlabs.halfState(slabs[0], side), MixedSlabs.halfState(slabs[1], side.getOpposite())};
	}

	@Override
	public void collectParts(RandomSource random, List<BlockStateModelPart> output) {
	}

	@Override
	public Material.Baked particleMaterial() {
		return modelOf(Blocks.OAK_SLAB.defaultBlockState()).particleMaterial();
	}

	@Override
	public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
		BlockState[] halves = halves(level, pos, state);
		return halves == null ? particleMaterial() : modelOf(halves[0]).particleMaterial(level, pos, halves[0]);
	}

	@Override
	public int materialFlags() {
		return 0;
	}

	@Override
	public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
		int flags = 0;
		BlockState[] halves = halves(level, pos, state);
		if (halves != null) {
			for (BlockState half : halves) {
				flags |= modelOf(half).materialFlags(level, pos, half, random);
			}
		}
		return flags;
	}

	@Override
	public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
			Predicate<@Nullable Direction> cullTest) {
		BlockState[] halves = halves(level, pos, state);
		if (halves == null) {
			return;
		}
		for (BlockState half : halves) {
			emitter.pushTransform(quad -> {
				SourceTint.apply(quad, half, level, pos);
				return true;
			});
			modelOf(half).emitQuads(emitter, level, pos, half, random, cullTest);
			emitter.popTransform();
		}
	}

	public static final class Unbaked implements BlockStateModel.UnbakedRoot {
		@Override
		public void resolveDependencies(ResolvableModel.Resolver resolver) {
		}

		@Override
		public BlockStateModel bake(BlockState blockState, ModelBaker modelBakery) {
			return new MixedSlabStateModel();
		}

		@Override
		public Object visualEqualityGroup(BlockState blockState) {
			return this;
		}
	}
}
