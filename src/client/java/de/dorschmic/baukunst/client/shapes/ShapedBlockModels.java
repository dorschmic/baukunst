package de.dorschmic.baukunst.client.shapes;

import de.dorschmic.baukunst.Baukunst;
import de.dorschmic.baukunst.shapes.ShapedBlocks;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.world.level.block.state.BlockState;

public final class ShapedBlockModels {
	private ShapedBlockModels() {
	}

	public static void init() {
		ItemModels.ID_MAPPER.put(Baukunst.id("shaped_block"), ShapedItemModel.Unbaked.MAP_CODEC);

		ModelLoadingPlugin.register(context -> {
			for (ShapedBlocks.Shaped shaped : ShapedBlocks.all()) {
				BlockState sourceState = shaped.source().defaultBlockState();
				context.registerBlockStateResolver(shaped.block(), resolver -> {
					for (BlockState state : shaped.block().getStateDefinition().getPossibleStates()) {
						resolver.setModel(state, new ShapedBlockStateModel.Unbaked(sourceState, state));
					}
				});
			}
		});
	}
}
