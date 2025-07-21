package com.jeffdisher.october.actions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.mutations.EntityActionType;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


public class Deprecated_EntityActionItemsRequestPush implements IEntityAction<IMutablePlayerEntity>
{
	public static final EntityActionType TYPE = EntityActionType.DEPRECATED_ITEMS_REQUEST_PUSH;

	public static Deprecated_EntityActionItemsRequestPush deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation blockLocation = CodecHelpers.readAbsoluteLocation(buffer);
		int localInventoryId = buffer.getInt();
		Assert.assertTrue(localInventoryId > 0);
		int count = buffer.getInt();
		Assert.assertTrue(count > 0);
		byte inventoryAspect = buffer.get();
		return new Deprecated_EntityActionItemsRequestPush(blockLocation, localInventoryId, count, inventoryAspect);
	}


	private final AbsoluteLocation _blockLocation;
	private final int _localInventoryId;
	private final int _count;
	private final byte _inventoryAspect;

	@Deprecated
	public Deprecated_EntityActionItemsRequestPush(AbsoluteLocation blockLocation, int localInventoryId, int count, byte inventoryAspect)
	{
		Assert.assertTrue(localInventoryId > 0);
		Assert.assertTrue(count > 0);
		
		_blockLocation = blockLocation;
		_localInventoryId = localInventoryId;
		_count = count;
		_inventoryAspect = inventoryAspect;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		// Not used.
		return true;
	}

	@Override
	public EntityActionType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeAbsoluteLocation(buffer, _blockLocation);
		buffer.putInt(_localInventoryId);
		buffer.putInt(_count);
		buffer.put(_inventoryAspect);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// This has a block reference.
		return false;
	}

	@Override
	public String toString()
	{
		return "Push " + _count + " items of local inventory key " + _localInventoryId + " to " + _blockLocation + " (inventory aspect " + _inventoryAspect + ")";
	}
}
