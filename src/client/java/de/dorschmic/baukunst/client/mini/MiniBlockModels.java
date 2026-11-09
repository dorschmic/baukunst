package de.dorschmic.baukunst.client.mini;

import de.dorschmic.baukunst.mini.MiniBlock;
import de.dorschmic.baukunst.mini.MiniGrid;
import de.dorschmic.baukunst.mini.MiniBlocks;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.world.level.block.state.BlockState;

public final class MiniBlockModels {
	private MiniBlockModels() {
	}

	public static void init() {
		ModelLoadingPlugin.register(context -> {
			MiniGridStateModel.Unbaked grid = new MiniGridStateModel.Unbaked();
			context.registerBlockStateResolver(MiniGrid.BLOCK, resolver ->
					MiniGrid.BLOCK.getStateDefinition().getPossibleStates().forEach(state -> resolver.setModel(state, grid)));
			for (MiniBlock block : MiniBlocks.all()) {
				context.registerBlockStateResolver(block, resolver -> {
					for (BlockState state : block.getStateDefinition().getPossibleStates()) {
						resolver.setModel(state, new MiniBlockStateModel.Unbaked(state));
					}
				});
			}
		});
	}
}
