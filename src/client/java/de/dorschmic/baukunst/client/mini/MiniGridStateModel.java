package de.dorschmic.baukunst.client.mini;

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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/** Renders the contents of a mini grid: each filled cell shows its source block at half size. */
public final class MiniGridStateModel implements BlockStateModel {
	private static final float EPS = 1.0E-4F;

	private static BlockStateModel modelOf(Block block) {
		return Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(block.defaultBlockState());
	}

	private static @Nullable Block[] slots(BlockAndTintGetter level, BlockPos pos) {
		return level.getBlockEntityRenderData(pos) instanceof Block[] slots ? slots : null;
	}

	@Override
	public void collectParts(RandomSource random, List<BlockStateModelPart> output) {
	}

	@Override
	public Material.Baked particleMaterial() {
		return modelOf(Blocks.STONE).particleMaterial();
	}

	@Override
	public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
		Block[] slots = slots(level, pos);
		if (slots != null) {
			for (Block block : slots) {
				if (block != null) {
					return modelOf(block).particleMaterial();
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
		Block[] slots = slots(level, pos);
		if (slots != null) {
			for (Block block : slots) {
				if (block != null) {
					flags |= modelOf(block).materialFlags();
				}
			}
		}
		return flags;
	}

	@Override
	public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
			Predicate<@Nullable Direction> cullTest) {
		Block[] slots = slots(level, pos);
		if (slots == null) {
			return;
		}
		Vector3f tmp = new Vector3f();
		for (int slot = 0; slot < 8; slot++) {
			Block block = slots[slot];
			if (block == null) {
				continue;
			}
			float ox = (slot & 1) * 0.5F;
			float oy = ((slot >> 1) & 1) * 0.5F;
			float oz = ((slot >> 2) & 1) * 0.5F;
			BlockState sourceState = block.defaultBlockState();
			emitter.pushTransform(quad -> {
				for (int i = 0; i < 4; i++) {
					quad.copyPos(i, tmp);
					tmp.mul(0.5F).add(ox, oy, oz);
					quad.pos(i, tmp);
				}
				Direction cull = quad.cullFace();
				if (cull != null) {
					// Only faces that ended up on the outside of the block space can be hidden by a neighbour.
					if (onSide(quad.copyPos(0, null), cull) && cullTest.test(cull)) {
						return false;
					}
					quad.cullFace(null);
				}
				SourceTint.apply(quad, sourceState, level, pos);
				return true;
			});
			modelOf(block).emitQuads(emitter, level, pos, sourceState, random, d -> false);
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
			return new MiniGridStateModel();
		}

		@Override
		public Object visualEqualityGroup(BlockState blockState) {
			return this;
		}
	}
}
