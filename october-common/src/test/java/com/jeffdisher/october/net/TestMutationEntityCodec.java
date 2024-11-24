package com.jeffdisher.october.net;

import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.mutations.EntityChangeIncrementalBlockRepair;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.IMutablePlayerEntity;


public class TestMutationEntityCodec
{
	@Test
	public void repair() throws Throwable
	{
		AbsoluteLocation location = new AbsoluteLocation(-1, 0, 1);
		short damage = 150;
		EntityChangeIncrementalBlockRepair change = new EntityChangeIncrementalBlockRepair(location, damage);
		
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		MutationEntityCodec.serializeToBuffer(buffer, change);
		buffer.flip();
		IMutationEntity<IMutablePlayerEntity> read = MutationEntityCodec.parseAndSeekFlippedBuffer(buffer);
		Assert.assertTrue(read instanceof EntityChangeIncrementalBlockRepair);
		Assert.assertEquals(0, buffer.remaining());
	}
}
