package com.jeffdisher.october.data;

import java.nio.ByteBuffer;

import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;


public class MultiBlockRootAspectCodec implements IAspectCodec<AbsoluteLocation>
{
	@Override
	public AbsoluteLocation loadData(ByteBuffer buffer)
	{
		// These can be null in the case of over-write.
		return CodecHelpers.readNullableAbsoluteLocation(buffer);
	}

	@Override
	public void storeData(ByteBuffer buffer, Object object)
	{
		// These can be null in the case of over-write.
		AbsoluteLocation op = (AbsoluteLocation) object;
		CodecHelpers.writeNullableAbsoluteLocation(buffer, op);
	}
}
