package com.jeffdisher.october.data;

import java.nio.ByteBuffer;

import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.CraftOperation;


public class CraftingAspectCodec implements IObjectCodec<CraftOperation>
{
	@Override
	public CraftOperation loadData(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		
		CraftOperation toReturn;
		if (context.skipPreV13CraftObjects())
		{
			// The CraftOperation is a long time and a short craft index.
			buffer.getLong();
			buffer.getShort();
			toReturn = null;
		}
		else
		{
			toReturn = CodecHelpers.readCraftOperation(buffer);
		}
		return toReturn;
	}

	@Override
	public void storeData(ByteBuffer buffer, CraftOperation object)
	{
		CodecHelpers.writeCraftOperation(buffer, object);
	}
}
