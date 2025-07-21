package com.jeffdisher.october.net;

import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.subactions.EntityChangeIncrementalBlockRepair;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;


public class TestEntitySubActionCodec
{
	@Test
	public void repair() throws Throwable
	{
		AbsoluteLocation location = new AbsoluteLocation(-1, 0, 1);
		EntityChangeIncrementalBlockRepair change = new EntityChangeIncrementalBlockRepair(location);
		
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		EntitySubActionCodec.serializeToBuffer(buffer, change);
		buffer.flip();
		IEntitySubAction<IMutablePlayerEntity> read = EntitySubActionCodec.parseAndSeekFlippedBuffer(buffer);
		Assert.assertTrue(read instanceof EntityChangeIncrementalBlockRepair);
		Assert.assertEquals(0, buffer.remaining());
	}
}
