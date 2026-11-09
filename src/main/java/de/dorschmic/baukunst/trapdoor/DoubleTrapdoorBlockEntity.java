package de.dorschmic.baukunst.trapdoor;

import de.dorschmic.baukunst.orientation.ContentOrientation;
import net.fabricmc.fabric.api.blockgetter.v2.RenderDataBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The two trapdoors of a double trapdoor, stored as their full block states (side, facing, half) in world
 * orientation. When the block is turned (WorldEdit, structures), both states are turned along.
 */
public class DoubleTrapdoorBlockEntity extends BlockEntity implements RenderDataBlockEntity {
	private final BlockState[] panels = {
			DoubleTrapdoors.panelState(Blocks.SPRUCE_TRAPDOOR.defaultBlockState(), net.minecraft.core.Direction.DOWN),
			DoubleTrapdoors.panelState(Blocks.SPRUCE_TRAPDOOR.defaultBlockState(), net.minecraft.core.Direction.UP)};

	public DoubleTrapdoorBlockEntity(BlockPos pos, BlockState state) {
		super(DoubleTrapdoors.BLOCK_ENTITY, pos, state);
	}

	public BlockState get(int i) {
		return panels[i];
	}

	public void set(BlockState first, BlockState second) {
		panels[0] = first;
		panels[1] = second;
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("orientation", ContentOrientation.of(getBlockState()).encode());
		output.store("first", BlockState.CODEC, panels[0]);
		output.store("second", BlockState.CODEC, panels[1]);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		input.read("first", BlockState.CODEC).filter(DoubleTrapdoors::isTrapdoor).ifPresent(s -> panels[0] = s);
		input.read("second", BlockState.CODEC).filter(DoubleTrapdoors::isTrapdoor).ifPresent(s -> panels[1] = s);
		ContentOrientation stored = input.getInt("orientation").map(ContentOrientation::decode)
				.orElse(ContentOrientation.of(getBlockState()));
		remap(stored, ContentOrientation.of(getBlockState()));
		if (level != null && level.isClientSide()) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
		}
	}

	@Override
	public void setBlockState(BlockState state) {
		ContentOrientation before = ContentOrientation.of(getBlockState());
		super.setBlockState(state);
		ContentOrientation after = ContentOrientation.of(state);
		if (!before.equals(after)) {
			remap(before, after);
			setChanged();
		}
	}

	/** Turns both trapdoors from one orientation of the block to another. */
	private void remap(ContentOrientation from, ContentOrientation to) {
		if (from.equals(to)) {
			return;
		}
		for (int i = 0; i < 2; i++) {
			BlockState s = from.undo(panels[i]);
			if (from.flipY() != to.flipY()) {
				s = s.setValue(TrapDoorBlock.HALF, s.getValue(TrapDoorBlock.HALF) == Half.TOP ? Half.BOTTOM : Half.TOP);
			}
			panels[i] = to.apply(s);
		}
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveCustomOnly(registries);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public Object getRenderData() {
		return panels.clone();
	}
}
