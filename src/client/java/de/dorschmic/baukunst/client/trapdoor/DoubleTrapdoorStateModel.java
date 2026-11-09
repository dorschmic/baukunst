package de.dorschmic.baukunst.client.trapdoor;

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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Renders both trapdoors of a double trapdoor with their own models. */
public final class DoubleTrapdoorStateModel implements BlockStateModel {
	private static BlockStateModel modelOf(BlockState state) {
		return Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
	}

	private static BlockState @Nullable [] panels(BlockAndTintGetter level, BlockPos pos) {
		return level.getBlockEntityRenderData(pos) instanceof BlockState[] panels ? panels : null;
	}

	@Override
	public void collectParts(RandomSource random, List<BlockStateModelPart> output) {
	}

	@Override
	public Material.Baked particleMaterial() {
		return modelOf(Blocks.SPRUCE_TRAPDOOR.defaultBlockState()).particleMaterial();
	}

	@Override
	public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
		BlockState[] panels = panels(level, pos);
		return panels == null ? particleMaterial() : modelOf(panels[0]).particleMaterial(level, pos, panels[0]);
	}

	@Override
	public int materialFlags() {
		return 0;
	}

	@Override
	public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
		int flags = 0;
		BlockState[] panels = panels(level, pos);
		if (panels != null) {
			for (BlockState panel : panels) {
				flags |= modelOf(panel).materialFlags(level, pos, panel, random);
			}
		}
		return flags;
	}

	@Override
	public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
			Predicate<@Nullable Direction> cullTest) {
		BlockState[] panels = panels(level, pos);
		if (panels == null) {
			return;
		}
		for (BlockState panel : panels) {
			emitter.pushTransform(quad -> {
				SourceTint.apply(quad, panel, level, pos);
				return true;
			});
			modelOf(panel).emitQuads(emitter, level, pos, panel, random, cullTest);
			emitter.popTransform();
		}
	}

	public static final class Unbaked implements BlockStateModel.UnbakedRoot {
		@Override
		public void resolveDependencies(ResolvableModel.Resolver resolver) {
		}

		@Override
		public BlockStateModel bake(BlockState blockState, ModelBaker modelBakery) {
			return new DoubleTrapdoorStateModel();
		}

		@Override
		public Object visualEqualityGroup(BlockState blockState) {
			return this;
		}
	}
}
