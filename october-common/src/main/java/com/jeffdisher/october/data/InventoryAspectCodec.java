package com.jeffdisher.october.data;

import java.nio.ByteBuffer;

import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.Inventory;


public class InventoryAspectCodec implements IObjectCodec<Inventory>
{
	@Override
	public Inventory loadData(DeserializationContext context)
	{
		return CodecHelpers.readInventory(context);
	}

	@Override
	public void storeData(ByteBuffer buffer, Inventory object)
	{
		CodecHelpers.writeInventory(buffer, object);
	}
}
