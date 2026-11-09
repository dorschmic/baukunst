package de.dorschmic.baukunst.mini;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

public class MiniBlockItem extends BlockItem {
	private final MiniBlock mini;

	public MiniBlockItem(MiniBlock block, Properties properties) {
		super(block, properties);
		this.mini = block;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player != null && player.isSecondaryUseActive()) {
			// Sneaking: snap into the 2×2×2 grid instead of placing like a player head.
			return MiniGrid.place(context, mini);
		}
		return super.useOn(context);
	}

	@Override
	public Component getName(ItemStack stack) {
		// "Mini-<Blockname>" in the player's language, no per-block translations needed.
		return mini.getName();
	}
}
