package com.jeffdisher.october.data;

import java.nio.ByteBuffer;

import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.FuelState;


public class FueledAspectCodec implements IAspectCodec<FuelState>
{
	@Override
	public FuelState loadData(ByteBuffer buffer)
	{
		return CodecHelpers.readFuelState(buffer);
	}

	@Override
	public void storeData(ByteBuffer buffer, Object object)
	{
		FuelState op = (FuelState) object;
		CodecHelpers.writeFuelState(buffer, op);
	}
}
