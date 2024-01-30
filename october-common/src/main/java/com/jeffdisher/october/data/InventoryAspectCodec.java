package com.jeffdisher.october.data;

import java.nio.ByteBuffer;

import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.Inventory;


public class InventoryAspectCodec implements IAspectCodec<Inventory>
{
	@Override
	public Inventory loadData(ByteBuffer buffer)
	{
		return CodecHelpers.readInventory(buffer);
	}

	@Override
	public void storeData(ByteBuffer buffer, Object object)
	{
		Inventory inv = (Inventory) object;
		CodecHelpers.writeInventory(buffer, inv);
	}
}
