package de.dorschmic.baukunst.shapes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/** Puts the new slabs/stairs right next to their source block in the vanilla creative tabs. */
public final class ShapedBlockTabs {
	private ShapedBlockTabs() {
	}

	public static void init() {
		Map<Block, List<Block>> bySource = new LinkedHashMap<>();
		for (ShapedBlocks.Shaped shaped : ShapedBlocks.all()) {
			bySource.computeIfAbsent(shaped.source(), b -> new ArrayList<>()).add(shaped.block());
		}

		CreativeModeTabEvents.MODIFY_OUTPUT_ALL.register((tab, output) -> {
			if (tab.getType() != CreativeModeTab.Type.CATEGORY || isOwnTab(tab)) {
				return;
			}
			for (Map.Entry<Block, List<Block>> entry : bySource.entrySet()) {
				Item sourceItem = entry.getKey().asItem();
				if (output.getDisplayStacks().stream().anyMatch(stack -> stack.is(sourceItem))) {
					output.insertAfter(sourceItem, entry.getValue().stream().map(ItemStack::new).toList());
				}
			}
		});
	}

	private static boolean isOwnTab(CreativeModeTab tab) {
		var key = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
		return key != null && "baukunst".equals(key.getNamespace());
	}
}
