package de.dorschmic.baukunst.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.dorschmic.baukunst.Baukunst;
import de.dorschmic.baukunst.slab.VerticalModePayload;
import de.dorschmic.baukunst.slab.VerticalSlabMode;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class BaukunstClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Baukunst.id("baukunst"));

	private static KeyMapping toggleVerticalKey;
	private static boolean verticalMode = false;

	@Override
	public void onInitializeClient() {
		toggleVerticalKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.baukunst.toggle_vertical_slabs", InputConstants.KEY_V, CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleVerticalKey.consumeClick()) {
				verticalMode = !verticalMode;
				sync(client);
				if (client.player != null) {
					client.player.sendOverlayMessage(Component.translatable(verticalMode
							? "message.baukunst.vertical_slabs.on"
							: "message.baukunst.vertical_slabs.off"));
				}
			}
		});

		// Re-send the current mode whenever we join a world/server.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> sync(client));
	}

	private static void sync(Minecraft client) {
		if (client.player == null) {
			return;
		}
		// Local copy for client-side placement prediction.
		VerticalSlabMode.set(client.player.getUUID(), verticalMode);
		if (ClientPlayNetworking.canSend(VerticalModePayload.TYPE)) {
			ClientPlayNetworking.send(new VerticalModePayload(verticalMode));
		}
	}
}
