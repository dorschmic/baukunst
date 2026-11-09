package de.dorschmic.baukunst;

import de.dorschmic.baukunst.cluster.Clusters;
import de.dorschmic.baukunst.mini.MiniBlocks;
import de.dorschmic.baukunst.mixed.MixedSlabs;
import de.dorschmic.baukunst.trapdoor.DoubleTrapdoors;
import de.dorschmic.baukunst.shapes.ShapedBlockTabs;
import de.dorschmic.baukunst.shapes.ShapedBlocks;
import de.dorschmic.baukunst.slab.VerticalModePayload;
import de.dorschmic.baukunst.slab.VerticalSlabMode;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Baukunst implements ModInitializer {
	public static final String MOD_ID = "baukunst";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.serverboundPlay().register(VerticalModePayload.TYPE, VerticalModePayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(VerticalModePayload.TYPE,
				(payload, context) -> VerticalSlabMode.set(context.player().getUUID(), payload.enabled()));

		ServerPlayConnectionEvents.DISCONNECT.register(
				(handler, server) -> VerticalSlabMode.set(handler.getPlayer().getUUID(), false));

		MiniBlocks.init();
		ShapedBlocks.init();
		ShapedBlockTabs.init();
		Clusters.init();
		MixedSlabs.init();
		DoubleTrapdoors.init();
		BlockEntityResync.init();

		LOGGER.info("Baukunst geladen – viel Spaß beim schön Bauen!");
	}
}
