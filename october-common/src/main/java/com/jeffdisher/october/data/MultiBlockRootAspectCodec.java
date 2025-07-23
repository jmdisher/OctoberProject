package com.jeffdisher.october.data;

import java.nio.ByteBuffer;

import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;


public class MultiBlockRootAspectCodec implements IObjectCodec<AbsoluteLocation>
{
	@Override
	public AbsoluteLocation loadData(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		
		// These can be null in the case of over-write.
		return CodecHelpers.readNullableAbsoluteLocation(buffer);
	}

	@Override
	public void storeData(ByteBuffer buffer, AbsoluteLocation object)
	{
		// These can be null in the case of over-write.
		CodecHelpers.writeNullableAbsoluteLocation(buffer, object);
	}
}
