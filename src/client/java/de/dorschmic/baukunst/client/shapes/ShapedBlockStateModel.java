package de.dorschmic.baukunst.client.shapes;

import de.dorschmic.baukunst.client.SourceTint;
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
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/** Renders a slab/stair (any state, incl. vertical slabs) by cutting the source block's model to its shape. */
public final class ShapedBlockStateModel implements BlockStateModel {
	private final BlockState sourceState;
	private final List<AABB> boxes;

	public ShapedBlockStateModel(BlockState sourceState, BlockState shapedState) {
		this.sourceState = sourceState;
		this.boxes = shapedState.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs();
	}

	private BlockStateModel sourceModel() {
		return Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(sourceState);
	}

	@Override
	public void collectParts(RandomSource random, List<BlockStateModelPart> output) {
	}

	@Override
	public Material.Baked particleMaterial() {
		return sourceModel().particleMaterial();
	}

	@Override
	public int materialFlags() {
		return sourceModel().materialFlags();
	}

	@Override
	public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
			Predicate<@Nullable Direction> cullTest) {
		BlockStateModel source = sourceModel();
		long seed = random.nextLong();
		for (AABB box : boxes) {
			emitter.pushTransform(quad -> {
				if (!QuadClipper.clip(quad, box)) {
					return false;
				}
				Direction cull = quad.cullFace();
				if (cull != null && cullTest.test(cull)) {
					return false;
				}
				SourceTint.apply(quad, sourceState, level, pos);
				return true;
			});
			random.setSeed(seed);
			source.emitQuads(emitter, level, pos, sourceState, random, direction -> false);
			emitter.popTransform();
		}
	}

	public record Unbaked(BlockState sourceState, BlockState shapedState) implements BlockStateModel.UnbakedRoot {
		@Override
		public void resolveDependencies(ResolvableModel.Resolver resolver) {
		}

		@Override
		public BlockStateModel bake(BlockState blockState, ModelBaker modelBakery) {
			return new ShapedBlockStateModel(sourceState, shapedState);
		}

		@Override
		public Object visualEqualityGroup(BlockState blockState) {
			return this;
		}
	}
}
