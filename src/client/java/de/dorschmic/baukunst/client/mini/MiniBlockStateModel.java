package de.dorschmic.baukunst.client.mini;

import de.dorschmic.baukunst.mini.MiniBlock;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Renders the model of the source block at half size, rotated like a player head.
 * Works for every block without extra model files: the source block's quads are
 * transformed on the fly while the chunk mesh is built.
 */
public final class MiniBlockStateModel implements BlockStateModel {
	private final BlockState sourceState;
	private final Matrix4f transform;
	private final Matrix4f normalTransform;

	private MiniBlockStateModel(BlockState sourceState, Matrix4f transform) {
		this.sourceState = sourceState;
		this.transform = transform;
		this.normalTransform = new Matrix4f(transform).invert().transpose();
	}

	public static MiniBlockStateModel create(BlockState miniState) {
		MiniBlock block = (MiniBlock) miniState.getBlock();
		int rotation = miniState.getValue(MiniBlock.ROTATION);
		// Facing yaw (0 = south, 90 = west, 180 = north, 270 = east). Block models face north.
		float yaw = rotation * 22.5F;
		float turn = (float) Math.toRadians(-(yaw - 180.0F));

		Vector3f center;
		if (miniState.getValue(MiniBlock.WALL)) {
			Direction facing = MiniBlock.wallFacing(miniState);
			center = new Vector3f(0.5F - 0.25F * facing.getStepX(), 0.5F, 0.5F - 0.25F * facing.getStepZ());
		} else {
			center = new Vector3f(0.5F, 0.25F, 0.5F);
		}

		Matrix4f m = new Matrix4f()
				.translate(center)
				.rotateY(turn)
				.scale(0.5F)
				.translate(-0.5F, -0.5F, -0.5F);
		return new MiniBlockStateModel(block.source().defaultBlockState(), m);
	}

	private BlockStateModel sourceModel() {
		return Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(sourceState);
	}

	@Override
	public void collectParts(RandomSource random, List<BlockStateModelPart> output) {
		// Only rendered through the Fabric renderer path (emitQuads).
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
		Vector3f tmp = new Vector3f();
		emitter.pushTransform(quad -> {
			transformQuad(quad, tmp);
			int tintIndex = quad.tintIndex();
			if (tintIndex >= 0) {
				BlockTintSource tint = Minecraft.getInstance().getBlockColors().getTintSource(sourceState, tintIndex);
				if (tint != null) {
					quad.multiplyColor(0xFF000000 | tint.colorInWorld(sourceState, level, pos));
				}
				quad.tintIndex(-1);
			}
			return true;
		});
		// The mini block never touches neighbours fully, so nothing is culled.
		source.emitQuads(emitter, level, pos, sourceState, random, direction -> false);
		emitter.popTransform();
	}

	private void transformQuad(MutableQuadView quad, Vector3f tmp) {
		for (int i = 0; i < 4; i++) {
			quad.copyPos(i, tmp);
			transform.transformPosition(tmp);
			quad.pos(i, tmp);
			if (quad.hasNormal(i)) {
				quad.copyNormal(i, tmp);
				normalTransform.transformDirection(tmp).normalize();
				quad.normal(i, tmp);
			}
		}
		quad.cullFace(null);
		Vector3f n = new Vector3f(quad.faceNormal());
		quad.nominalFace(Direction.getApproximateNearest(n.x, n.y, n.z));
		quad.ambientOcclusion(TriState.FALSE);
	}

	/** Unbaked form handed to Fabric's model loading for each mini block state. */
	public record Unbaked(BlockState miniState) implements BlockStateModel.UnbakedRoot {
		@Override
		public void resolveDependencies(ResolvableModel.Resolver resolver) {
			// The source block's model is loaded by vanilla anyway.
		}

		@Override
		public BlockStateModel bake(BlockState blockState, ModelBaker modelBakery) {
			return create(miniState);
		}

		@Override
		public Object visualEqualityGroup(BlockState blockState) {
			return this;
		}
	}
}
