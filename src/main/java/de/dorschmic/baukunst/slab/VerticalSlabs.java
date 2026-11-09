package de.dorschmic.baukunst.slab;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class VerticalSlabs {
	/** Added to every vanilla {@code SlabBlock} via mixin. */
	public static final EnumProperty<VerticalSlabFacing> VERTICAL = EnumProperty.create("vertical", VerticalSlabFacing.class);

	private static final Map<VerticalSlabFacing, VoxelShape> SHAPES = new EnumMap<>(VerticalSlabFacing.class);

	static {
		SHAPES.put(VerticalSlabFacing.NORTH, Block.box(0, 0, 0, 16, 16, 8));
		SHAPES.put(VerticalSlabFacing.SOUTH, Block.box(0, 0, 8, 16, 16, 16));
		SHAPES.put(VerticalSlabFacing.WEST, Block.box(0, 0, 0, 8, 16, 16));
		SHAPES.put(VerticalSlabFacing.EAST, Block.box(8, 0, 0, 16, 16, 16));
	}

	private VerticalSlabs() {
	}

	public static VoxelShape shape(VerticalSlabFacing facing) {
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
	public static VerticalSlabFacing facingFor(Direction clickedFace, double hitX, double hitZ, Direction lookDir) {
		if (clickedFace.getAxis().isHorizontal()) {
			// Clicked the side of a block: the slab hugs that block.
			return VerticalSlabFacing.of(clickedFace.getOpposite());
		}
		// Clicked top or bottom: use the half the player aimed at.
		double dx = hitX - 0.5;
		double dz = hitZ - 0.5;
		if (Math.abs(dx) < 0.05 && Math.abs(dz) < 0.05) {
			return VerticalSlabFacing.of(lookDir);
		}
		if (Math.abs(dx) > Math.abs(dz)) {
			return dx > 0 ? VerticalSlabFacing.EAST : VerticalSlabFacing.WEST;
		}
		return dz > 0 ? VerticalSlabFacing.SOUTH : VerticalSlabFacing.NORTH;
	}
}
