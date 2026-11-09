package de.dorschmic.baukunst.orientation;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * How the contents of a mini grid / small block cluster are turned, read from block state properties that
 * rotation tools understand: {@code facing} (rotate), {@code type} left/right (horizontal flip) and
 * {@code half} bottom/top (vertical flip). WorldEdit, structure blocks etc. only change these properties;
 * the block entity then rearranges its contents to match (see {@link #remap}).
 *
 * <p>Contents are always stored in world orientation. The orientation they were stored with is saved
 * alongside, so that when the state was turned (e.g. pasted rotated) the contents can be turned too.
 *
 * @param turns   clockwise quarter turns from north
 * @param mirror  mirrored east↔west before turning
 * @param flipY   upside down
 */
public record ContentOrientation(int turns, boolean mirror, boolean flipY) {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final EnumProperty<Handedness> MIRROR = EnumProperty.create("type", Handedness.class);
	public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;

	public static final ContentOrientation IDENTITY = new ContentOrientation(0, false, false);

	public static ContentOrientation of(BlockState state) {
		int turns = state.hasProperty(FACING) ? (state.getValue(FACING).get2DDataValue() + 2) % 4 : 0;
		boolean mirror = state.hasProperty(MIRROR) && state.getValue(MIRROR) == Handedness.RIGHT;
		boolean flipY = state.hasProperty(HALF) && state.getValue(HALF) == Half.TOP;
		return new ContentOrientation(turns, mirror, flipY);
	}

	public int encode() {
		return turns | (mirror ? 4 : 0) | (flipY ? 8 : 0);
	}

	public static ContentOrientation decode(int bits) {
		return new ContentOrientation(bits & 3, (bits & 4) != 0, (bits & 8) != 0);
	}

	/** Cell (x, y, z), each 0 or 1, as seen in this orientation; returns {x, y, z}. */
	public int[] apply(int x, int y, int z) {
		if (mirror) {
			x = 1 - x;
		}
		for (int i = 0; i < turns; i++) {
			int nx = 1 - z;
			z = x;
			x = nx;
		}
		if (flipY) {
			y = 1 - y;
		}
		return new int[] {x, y, z};
	}

	/** Turns a content block state (lantern, end rod …) the same way. */
	public BlockState apply(BlockState content) {
		if (mirror) {
			content = content.mirror(Mirror.FRONT_BACK);
		}
		return content.rotate(Rotation.values()[turns]);
	}

	public BlockState undo(BlockState content) {
		content = content.rotate(Rotation.values()[(4 - turns) % 4]);
		return mirror ? content.mirror(Mirror.FRONT_BACK) : content;
	}

	/**
	 * Moves slots stored in orientation {@code from} so that they match orientation {@code to}.
	 *
	 * @param index maps cell coordinates to a slot index
	 */
	public static <T> void remap(T[] slots, ContentOrientation from, ContentOrientation to, CellIndex index,
			java.util.function.UnaryOperator<T> undoFrom, java.util.function.UnaryOperator<T> applyTo) {
		if (from.equals(to)) {
			return;
		}
		T[] old = slots.clone();
		java.util.Arrays.fill(slots, null);
		for (int x = 0; x < 2; x++) {
			for (int y = 0; y < 2; y++) {
				for (int z = 0; z < 2; z++) {
					int[] stored = from.apply(x, y, z);
					int src = index.of(stored[0], stored[1], stored[2]);
					if (src < 0 || old[src] == null) {
						continue;
					}
					int[] target = to.apply(x, y, z);
					slots[index.of(target[0], target[1], target[2])] = applyTo.apply(undoFrom.apply(old[src]));
				}
			}
		}
	}

	@FunctionalInterface
	public interface CellIndex {
		/** Slot index of a cell, or -1 if this container has no such cell. */
		int of(int x, int y, int z);
	}

	/** Rotates the orientation properties of a state (used by {@code Block#rotate}). */
	public static BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	/** Mirrors the orientation properties of a state (used by {@code Block#mirror}). */
	public static BlockState mirror(BlockState state, Mirror mirror) {
		if (mirror == Mirror.NONE) {
			return state;
		}
		return state.setValue(FACING, mirror.mirror(state.getValue(FACING)))
				.setValue(MIRROR, state.getValue(MIRROR) == Handedness.LEFT ? Handedness.RIGHT : Handedness.LEFT);
	}
}
