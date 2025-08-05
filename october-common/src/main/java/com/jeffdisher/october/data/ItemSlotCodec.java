package com.jeffdisher.october.data;

import java.nio.ByteBuffer;

import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.ItemSlot;


public class ItemSlotCodec implements IObjectCodec<ItemSlot>
{
	@Override
	public ItemSlot loadData(DeserializationContext context)
	{
		return CodecHelpers.readSlot(context);
	}

	@Override
	public void storeData(ByteBuffer buffer, ItemSlot object)
	{
		CodecHelpers.writeSlot(buffer, object);
	}
}
