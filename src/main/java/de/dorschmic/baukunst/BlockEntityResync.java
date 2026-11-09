package de.dorschmic.baukunst;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * When a player breaks only one part of a multi-part block (mini grid, small block cluster), the client has
 * already predicted the whole block gone and dropped its block entity. Block entity data sent before the
 * server acknowledges that prediction is thrown away, and the client then restores the block with an empty
 * block entity – it turns invisible. So send the contents once more a few ticks later.
 */
public final class BlockEntityResync {
	private static final int DELAY_TICKS = 3;

	private record Pending(ServerLevel level, BlockPos pos, int dueTick) {
	}

	private static final List<Pending> PENDING = new ArrayList<>();

	private BlockEntityResync() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (PENDING.isEmpty()) {
				return;
			}
			int now = server.getTickCount();
			PENDING.removeIf(p -> {
				if (p.dueTick() > now) {
					return false;
				}
				// Re-sends the block state and the block entity data to every player tracking the chunk.
				p.level().getChunkSource().blockChanged(p.pos());
				return true;
			});
		});
	}

	public static void schedule(ServerLevel level, BlockPos pos) {
		PENDING.add(new Pending(level, pos.immutable(), level.getServer().getTickCount() + DELAY_TICKS));
	}
}
