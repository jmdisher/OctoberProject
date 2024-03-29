package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Craft;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * A special kind of crafting activity performed by a user.  This is similar to EntityChangeCraft but is specifically
 * related to crafting within a crafting station type of block.  A null operation will merely continue what is already
 * being crafted within the block or a non-null one will be started.
 * Note that this approach also means that multiple users can craft within the block at the same time.  In fact, the
 * reason why this can receive a null operation, while the in-entity crafting can't is to avoid racing with multiple
 * entities within the same tick (since that could result in one entity completing the operation while another starts
 * another one, instead of also helping to complete it).
 */
public class EntityChangeCraftInBlock implements IMutationEntity
{
	public static final MutationEntityType TYPE = MutationEntityType.CRAFT_IN_BLOCK;

	public static EntityChangeCraftInBlock deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation targetBlock = CodecHelpers.readAbsoluteLocation(buffer);
		Craft craft = CodecHelpers.readCraft(buffer);
		long millisToApply = buffer.getLong();
		return new EntityChangeCraftInBlock(targetBlock, craft, millisToApply);
	}


	private final AbsoluteLocation _targetBlock;
	private final Craft _craft;
	private final long _millisToApply;

	public EntityChangeCraftInBlock(AbsoluteLocation targetBlock, Craft craft, long millisToApply)
	{
		_targetBlock = targetBlock;
		_craft = craft;
		_millisToApply = millisToApply;
	}

	@Override
	public long getTimeCostMillis()
	{
		return _millisToApply;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		Environment env = Environment.getShared();
		// Make sure that the block is within range and is a crafting table.
		int absX = Math.abs(_targetBlock.x() - Math.round(newEntity.newLocation.x()));
		int absY = Math.abs(_targetBlock.y() - Math.round(newEntity.newLocation.y()));
		int absZ = Math.abs(_targetBlock.z() - Math.round(newEntity.newLocation.z()));
		boolean isLocationClose = ((absX <= 2) && (absY <= 2) && (absZ <= 2));
		// Note that the cuboid could theoretically not be loaded (although this shouldn't happen in normal clients).
		BlockProxy proxy = context.previousBlockLookUp.apply(_targetBlock);
		boolean isCraftingTable = (null != proxy) && (env.blocks.CRAFTING_TABLE == proxy.getBlock());
		
		boolean didApply = false;
		if (isLocationClose && isCraftingTable)
		{
			// Pass the mutation into the block.
			// (note that we verify that this is valid for the block type in MutationBlockCraft)
			MutationBlockCraft mutation = new MutationBlockCraft(_targetBlock, _craft, _millisToApply);
			context.newMutationSink.accept(mutation);
			didApply = true;
		}
		
		// Account for any movement while we were busy.
		// NOTE:  This is currently wrong as it is only applied in the last part of the operation, not each tick.
		// This will need to be revisited when we change the crafting action.
		boolean didMove = EntityChangeMove.handleMotion(newEntity, context.previousBlockLookUp, _millisToApply);
		
		return didApply || didMove;
	}

	@Override
	public MutationEntityType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeAbsoluteLocation(buffer, _targetBlock);
		CodecHelpers.writeCraft(buffer, _craft);
		buffer.putLong(_millisToApply);
	}
}
