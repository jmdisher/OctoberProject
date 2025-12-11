package com.jeffdisher.october.data;

import java.nio.ByteBuffer;

import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.EnchantingOperation;


public class EnchantingAspectCodec implements IObjectCodec<EnchantingOperation>
{
	@Override
	public EnchantingOperation loadData(DeserializationContext context)
	{
		return CodecHelpers.readEnchantingOperation(context);
	}

	@Override
	public void storeData(ByteBuffer buffer, EnchantingOperation object)
	{
		CodecHelpers.writeEnchantingOperation(buffer, object);
	}
}
