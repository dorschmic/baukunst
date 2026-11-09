package de.dorschmic.baukunst.slab;

import de.dorschmic.baukunst.Baukunst;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: the player toggled vertical slab mode. */
public record VerticalModePayload(boolean enabled) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<VerticalModePayload> TYPE =
			new CustomPacketPayload.Type<>(Baukunst.id("vertical_slab_mode"));

	public static final StreamCodec<ByteBuf, VerticalModePayload> CODEC =
			ByteBufCodecs.BOOL.map(VerticalModePayload::new, VerticalModePayload::enabled);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
