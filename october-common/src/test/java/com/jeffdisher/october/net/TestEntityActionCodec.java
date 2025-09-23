package com.jeffdisher.october.net;

import java.nio.ByteBuffer;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.actions.Deprecated_EntityChangeTakeDamageFromOther;
import com.jeffdisher.october.actions.EntityChangeTakeDamageFromEntity;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;


public class TestEntityActionCodec
{
	private static Environment ENV;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void takeDamageEntity() throws Throwable
	{
		int damage = 50;
		int sourceEntityId = -2;
		EntityChangeTakeDamageFromEntity<IMutablePlayerEntity> change = new EntityChangeTakeDamageFromEntity<>(BodyPart.HEAD, damage, sourceEntityId);
		
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		EntityActionCodec.serializeToBuffer(buffer, change);
		buffer.flip();
		IEntityAction<IMutablePlayerEntity> read = EntityActionCodec.parseAndSeekContext(new DeserializationContext(ENV
			, buffer
			, false
		));
		Assert.assertTrue(read instanceof EntityChangeTakeDamageFromEntity);
		Assert.assertEquals(0, buffer.remaining());
	}

	@Test
	public void deprecatedTakeDamageOther() throws Throwable
	{
		int damage = 50;
		@SuppressWarnings("deprecation")
		Deprecated_EntityChangeTakeDamageFromOther<IMutablePlayerEntity> change = new Deprecated_EntityChangeTakeDamageFromOther<>(BodyPart.HEAD, damage, Deprecated_EntityChangeTakeDamageFromOther.CAUSE_FALL);
		
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		EntityActionCodec.serializeToBuffer(buffer, change);
		buffer.flip();
		IEntityAction<IMutablePlayerEntity> read = EntityActionCodec.parseAndSeekContext(new DeserializationContext(ENV
			, buffer
			, false
		));
		Assert.assertTrue(read instanceof Deprecated_EntityChangeTakeDamageFromOther);
		Assert.assertEquals(0, buffer.remaining());
	}
}
