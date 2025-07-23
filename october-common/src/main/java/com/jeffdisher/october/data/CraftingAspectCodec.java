package com.jeffdisher.october.data;

import java.nio.ByteBuffer;

import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.CraftOperation;


public class CraftingAspectCodec implements IAspectCodec<CraftOperation>
{
	@Override
	public CraftOperation loadData(ByteBuffer buffer)
	{
		return CodecHelpers.readCraftOperation(buffer);
	}

	@Override
	public void storeData(ByteBuffer buffer, CraftOperation object)
	{
		CodecHelpers.writeCraftOperation(buffer, object);
	}
}
