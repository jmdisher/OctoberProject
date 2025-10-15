package com.jeffdisher.october.net;

import java.nio.ByteBuffer;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.actions.Deprecated_EntityAction;
import com.jeffdisher.october.actions.EntityActionTakeDamageFromEntity;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.mutations.EntityActionType;
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
		EntityActionTakeDamageFromEntity<IMutablePlayerEntity> change = new EntityActionTakeDamageFromEntity<>(BodyPart.HEAD, damage, sourceEntityId);
		
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		EntityActionCodec.serializeToBuffer(buffer, change);
		buffer.flip();
		IEntityAction<IMutablePlayerEntity> read = EntityActionCodec.parseAndSeekContext(new DeserializationContext(ENV
			, buffer
			, 0L
			, false
		));
		Assert.assertTrue(read instanceof EntityActionTakeDamageFromEntity);
		Assert.assertEquals(0, buffer.remaining());
	}

	@Test
	public void deprecatedTakeDamageOther() throws Throwable
	{
		// Note that this IEntityAction is fully deprecated to the point where only the deserializer still exists so we inline the old logic to serialize, here.
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		
		// Write the type.
		EntityActionType type = EntityActionType.DEPRECATED_TAKE_DAMAGE_FROM_OTHER_V4;
		buffer.put((byte) type.ordinal());
		// Write the rest of the action.
		BodyPart target = BodyPart.HEAD;
		int damage = 50;
		// CAUSE_FALL
		byte cause = 3;
		CodecHelpers.writeBodyPart(buffer, target);
		buffer.putInt(damage);
		buffer.put(cause);
		
		buffer.flip();
		IEntityAction<IMutablePlayerEntity> read = EntityActionCodec.parseAndSeekContext(new DeserializationContext(ENV
			, buffer
			, 0L
			, false
		));
		Assert.assertTrue(read instanceof Deprecated_EntityAction);
		Assert.assertEquals(0, buffer.remaining());
	}
}
