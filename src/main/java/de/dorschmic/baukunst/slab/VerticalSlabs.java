package de.dorschmic.baukunst.slab;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public final class VerticalSlabs {
	/**
	 * Added to every {@code SlabBlock} via mixin: the side of the block a vertical slab hugs.
	 * {@code down} (the default) and {@code up} both mean a normal horizontal slab.
	 *
	 * <p>The values are real {@link Direction}s so that tools like WorldEdit recognise the property
	 * and rotate/flip vertical slabs with the rest of a structure (a vertical flip turns down into up).
	 */
	public static final EnumProperty<Direction> VERTICAL = EnumProperty.create("vertical", Direction.class);

	/** Value of {@link #VERTICAL} for a normal (horizontal) slab. */
	public static final Direction NONE = Direction.DOWN;

	private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

	static {
		SHAPES.put(Direction.NORTH, Block.box(0, 0, 0, 16, 16, 8));
		SHAPES.put(Direction.SOUTH, Block.box(0, 0, 8, 16, 16, 16));
		SHAPES.put(Direction.WEST, Block.box(0, 0, 0, 8, 16, 16));
		SHAPES.put(Direction.EAST, Block.box(8, 0, 0, 16, 16, 16));
	}

	private VerticalSlabs() {
	}

	/** The side a vertical slab hugs, or null for a normal slab. */
	public static @Nullable Direction facing(BlockState state) {
		Direction d = state.getValue(VERTICAL);
		return d.getAxis().isHorizontal() ? d : null;
	}

	public static VoxelShape shape(Direction facing) {
		return SHAPES.get(facing);
	}

	/**
	 * Picks the half of the target block the new vertical slab should occupy.
	 *
	 * @param clickedFace face of the block that was clicked
	 * @param hitX        click position inside the target block, 0..1
	 * @param hitZ        click position inside the target block, 0..1
	 * @param lookDir     horizontal direction the player looks
	 */
	public static Direction facingFor(Direction clickedFace, double hitX, double hitZ, Direction lookDir) {
		if (clickedFace.getAxis().isHorizontal()) {
			// Clicked the side of a block: the slab hugs that block.
			return clickedFace.getOpposite();
		}
		// Clicked top or bottom: use the half the player aimed at.
		double dx = hitX - 0.5;
		double dz = hitZ - 0.5;
		if (Math.abs(dx) < 0.05 && Math.abs(dz) < 0.05) {
			return lookDir;
		}
		if (Math.abs(dx) > Math.abs(dz)) {
			return dx > 0 ? Direction.EAST : Direction.WEST;
		}
		return dz > 0 ? Direction.SOUTH : Direction.NORTH;
	}
}
