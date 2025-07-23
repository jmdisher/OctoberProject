package com.jeffdisher.october.data;

import java.nio.ByteBuffer;

import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.FuelState;


public class FuelledAspectCodec implements IObjectCodec<FuelState>
{
	@Override
	public FuelState loadData(DeserializationContext context)
	{
		return CodecHelpers.readFuelState(context);
	}

	@Override
	public void storeData(ByteBuffer buffer, FuelState object)
	{
		CodecHelpers.writeFuelState(buffer, object);
	}
}
