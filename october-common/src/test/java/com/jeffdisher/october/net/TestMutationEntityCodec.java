package com.jeffdisher.october.net;

import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.mutations.EntityChangeIncrementalBlockRepair;
import com.jeffdisher.october.mutations.EntityChangeTakeDamageFromEntity;
import com.jeffdisher.october.mutations.EntityChangeTakeDamageFromOther;
import com.jeffdisher.october.mutations.EntityChangeTimeSync;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BodyPart;
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

	@Test
	public void takeDamageEntity() throws Throwable
	{
		int damage = 50;
		int sourceEntityId = -2;
		EntityChangeTakeDamageFromEntity<IMutablePlayerEntity> change = new EntityChangeTakeDamageFromEntity<>(BodyPart.HEAD, damage, sourceEntityId);
		
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		MutationEntityCodec.serializeToBuffer(buffer, change);
		buffer.flip();
		IMutationEntity<IMutablePlayerEntity> read = MutationEntityCodec.parseAndSeekFlippedBuffer(buffer);
		Assert.assertTrue(read instanceof EntityChangeTakeDamageFromEntity);
		Assert.assertEquals(0, buffer.remaining());
	}

	@Test
	public void deprecatedTakeDamageOther() throws Throwable
	{
		int damage = 50;
		@SuppressWarnings("deprecation")
		EntityChangeTakeDamageFromOther<IMutablePlayerEntity> change = new EntityChangeTakeDamageFromOther<>(BodyPart.HEAD, damage, EntityChangeTakeDamageFromOther.CAUSE_FALL);
		
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		MutationEntityCodec.serializeToBuffer(buffer, change);
		buffer.flip();
		IMutationEntity<IMutablePlayerEntity> read = MutationEntityCodec.parseAndSeekFlippedBuffer(buffer);
		Assert.assertTrue(read instanceof EntityChangeTakeDamageFromOther);
		Assert.assertEquals(0, buffer.remaining());
	}

	@Test
	public void timeSync() throws Throwable
	{
		long millisToPass = 67L;
		EntityChangeTimeSync change = new EntityChangeTimeSync(millisToPass);
		
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		MutationEntityCodec.serializeToBuffer(buffer, change);
		buffer.flip();
		IMutationEntity<IMutablePlayerEntity> read = MutationEntityCodec.parseAndSeekFlippedBuffer(buffer);
		Assert.assertTrue(read instanceof EntityChangeTimeSync);
		Assert.assertEquals(0, buffer.remaining());
	}
}
