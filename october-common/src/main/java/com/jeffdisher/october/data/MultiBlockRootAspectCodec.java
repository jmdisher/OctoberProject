package com.jeffdisher.october.data;

import java.nio.ByteBuffer;

import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;


public class MultiBlockRootAspectCodec implements IAspectCodec<AbsoluteLocation>
{
	@Override
	public AbsoluteLocation loadData(ByteBuffer buffer)
	{
		return CodecHelpers.readAbsoluteLocation(buffer);
	}

	@Override
	public void storeData(ByteBuffer buffer, Object object)
	{
		AbsoluteLocation op = (AbsoluteLocation) object;
		CodecHelpers.writeAbsoluteLocation(buffer, op);
	}
}
