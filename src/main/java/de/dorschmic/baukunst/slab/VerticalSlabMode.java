package de.dorschmic.baukunst.slab;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks which players currently place slabs vertically (toggled with a key). */
public final class VerticalSlabMode {
	private static final Set<UUID> ENABLED = ConcurrentHashMap.newKeySet();

	private VerticalSlabMode() {
	}

	public static boolean isEnabled(UUID player) {
		return ENABLED.contains(player);
	}

	public static void set(UUID player, boolean enabled) {
		if (enabled) {
			ENABLED.add(player);
		} else {
			ENABLED.remove(player);
		}
	}
}
