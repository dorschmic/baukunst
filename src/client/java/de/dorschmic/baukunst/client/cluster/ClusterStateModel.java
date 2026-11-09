package de.dorschmic.baukunst.client.cluster;

import de.dorschmic.baukunst.client.SourceTint;
import de.dorschmic.baukunst.cluster.ClusterBlock;
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
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/** Renders the small blocks of a cluster, each with its own model, moved to the centre of its quarter. */
public final class ClusterStateModel implements BlockStateModel {
	private static final float EPS = 1.0E-4F;

	private static BlockStateModel modelOf(BlockState state) {
		return Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
	}

	private static @Nullable BlockState[] slots(BlockAndTintGetter level, BlockPos pos) {
		return level.getBlockEntityRenderData(pos) instanceof BlockState[] slots ? slots : null;
	}

	@Override
	public void collectParts(RandomSource random, List<BlockStateModelPart> output) {
	}

	@Override
	public Material.Baked particleMaterial() {
		return modelOf(Blocks.POPPY.defaultBlockState()).particleMaterial();
	}

	@Override
	public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
		BlockState[] slots = slots(level, pos);
		if (slots != null) {
			for (BlockState content : slots) {
				if (content != null) {
					return modelOf(content).particleMaterial(level, pos, content);
				}
			}
		}
		return particleMaterial();
	}

	@Override
	public int materialFlags() {
		return 0;
	}

	@Override
	public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
		int flags = 0;
		BlockState[] slots = slots(level, pos);
		if (slots != null) {
			for (BlockState content : slots) {
				if (content != null) {
					flags |= modelOf(content).materialFlags(level, pos, content, random);
				}
			}
		}
		return flags;
	}

	@Override
	public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
			Predicate<@Nullable Direction> cullTest) {
		BlockState[] slots = slots(level, pos);
		if (slots == null) {
			return;
		}
		Vector3f tmp = new Vector3f();
		for (int slot = 0; slot < 4; slot++) {
			BlockState content = slots[slot];
			if (content == null) {
				continue;
			}
			float ox = (float) ClusterBlock.offsetX(slot);
			float oz = (float) ClusterBlock.offsetZ(slot);
			emitter.pushTransform(quad -> {
				for (int i = 0; i < 4; i++) {
					quad.copyPos(i, tmp);
					tmp.add(ox, 0, oz);
					quad.pos(i, tmp);
				}
				Direction cull = quad.cullFace();
				if (cull != null) {
					// Only faces still lying on the outside of the block space can be hidden by a neighbour.
					if (onSide(quad.copyPos(0, null), cull) && cullTest.test(cull)) {
						return false;
					}
					quad.cullFace(null);
				}
				SourceTint.apply(quad, content, level, pos);
				return true;
			});
			modelOf(content).emitQuads(emitter, level, pos, content, random, d -> false);
			emitter.popTransform();
		}
	}

	private static boolean onSide(Vector3f p, Direction side) {
		float target = side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0F : 0.0F;
		float v = switch (side.getAxis()) {
			case X -> p.x;
			case Y -> p.y;
			case Z -> p.z;
		};
		return Math.abs(v - target) < EPS;
	}

	public static final class Unbaked implements BlockStateModel.UnbakedRoot {
		@Override
		public void resolveDependencies(ResolvableModel.Resolver resolver) {
		}

		@Override
		public BlockStateModel bake(BlockState blockState, ModelBaker modelBakery) {
			return new ClusterStateModel();
		}

		@Override
		public Object visualEqualityGroup(BlockState blockState) {
			return this;
		}
	}
}
