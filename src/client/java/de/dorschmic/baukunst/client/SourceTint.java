package de.dorschmic.baukunst.client;

import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Our blocks borrow the quads of another block. Tinted quads (grass, leaves …) would look up the
 * tint for *our* block, which has none – so bake the source block's tint into the quad instead.
 */
public final class SourceTint {
	private SourceTint() {
	}

	public static void apply(MutableQuadView quad, BlockState sourceState, BlockAndTintGetter level, BlockPos pos) {
		int tintIndex = quad.tintIndex();
		if (tintIndex < 0) {
			return;
		}
		BlockTintSource tint = Minecraft.getInstance().getBlockColors().getTintSource(sourceState, tintIndex);
		if (tint != null) {
			quad.multiplyColor(0xFF000000 | tint.colorInWorld(sourceState, level, pos));
		}
		quad.tintIndex(-1);
	}
}
