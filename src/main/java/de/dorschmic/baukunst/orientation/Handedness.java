package de.dorschmic.baukunst.orientation;

import net.minecraft.util.StringRepresentable;

/** Whether a block's contents are mirrored. Named like the chest property so WorldEdit swaps it on flips. */
public enum Handedness implements StringRepresentable {
	LEFT("left"),
	RIGHT("right");

	private final String name;

	Handedness(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return name;
	}
}
