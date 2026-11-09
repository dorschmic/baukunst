package de.dorschmic.baukunst.slab;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.Nullable;

/**
 * Which half of the block a vertical slab occupies.
 * {@link #NONE} means the slab is a normal (horizontal) vanilla slab.
 */
public enum VerticalSlabFacing implements StringRepresentable {
	NONE("none", null),
	NORTH("north", Direction.NORTH),
	EAST("east", Direction.EAST),
	SOUTH("south", Direction.SOUTH),
	WEST("west", Direction.WEST);

	private final String name;
	private final @Nullable Direction direction;

	VerticalSlabFacing(String name, @Nullable Direction direction) {
		this.name = name;
		this.direction = direction;
	}

	public @Nullable Direction direction() {
		return direction;
	}

	public static VerticalSlabFacing of(Direction direction) {
		return switch (direction) {
			case NORTH -> NORTH;
			case EAST -> EAST;
			case SOUTH -> SOUTH;
			case WEST -> WEST;
			default -> NONE;
		};
	}

	@Override
	public String getSerializedName() {
		return name;
	}
}
