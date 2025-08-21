package com.jeffdisher.october.net;

import java.nio.ByteBuffer;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.actions.Deprecated_EntityChangeTakeDamageFromOther;
import com.jeffdisher.october.actions.EntityChangeTakeDamageFromEntity;
import com.jeffdisher.october.actions.EntityChangeTopLevelMovement;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.subactions.EntityChangeJump;
import com.jeffdisher.october.subactions.EntitySubActionRequestSwapSpecialSlot;
import com.jeffdisher.october.subactions.EntitySubActionTravelViaBlock;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.EntityLocation;
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

	@Test
	public void topLevel() throws Throwable
	{
		EntityLocation newLocation = new EntityLocation(0.5f, 0.0f, 0.0f);
		EntityLocation newVelocity = new EntityLocation(5.0f, 0.0f, 0.0f);
		EntityChangeTopLevelMovement<IMutablePlayerEntity> action = new EntityChangeTopLevelMovement<>(newLocation
			, newVelocity
			, EntityChangeTopLevelMovement.Intensity.WALKING
			, (byte)0
			, (byte)0
			, null
		);
		
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		EntityActionCodec.serializeToBuffer(buffer, action);
		buffer.flip();
		IEntityAction<IMutablePlayerEntity> read = EntityActionCodec.parseAndSeekContext(new DeserializationContext(ENV
			, buffer
			, false
		));
		Assert.assertTrue(read instanceof EntityChangeTopLevelMovement);
		Assert.assertNull(((EntityChangeTopLevelMovement<?>)read).test_getSubAction());
		Assert.assertEquals(0, buffer.remaining());
		
		EntityChangeJump<IMutablePlayerEntity> jump = new EntityChangeJump<>();
		action = new EntityChangeTopLevelMovement<>(newLocation
			, newVelocity
			, EntityChangeTopLevelMovement.Intensity.WALKING
			, (byte)5
			, (byte)6
			, jump
		);
		
		buffer = ByteBuffer.allocate(1024);
		EntityActionCodec.serializeToBuffer(buffer, action);
		buffer.flip();
		read = EntityActionCodec.parseAndSeekContext(new DeserializationContext(ENV
			, buffer
			, false
		));
		Assert.assertTrue(read instanceof EntityChangeTopLevelMovement);
		Assert.assertTrue(((EntityChangeTopLevelMovement<?>)read).test_getSubAction() instanceof EntityChangeJump);
		Assert.assertEquals(0, buffer.remaining());
	}

	@Test
	public void swapped() throws Throwable
	{
		AbsoluteLocation target = new AbsoluteLocation(-10, 6, 0);
		boolean sendAll = true;
		EntitySubActionRequestSwapSpecialSlot change = new EntitySubActionRequestSwapSpecialSlot(target, sendAll);
		EntityChangeTopLevelMovement<IMutablePlayerEntity> action = new EntityChangeTopLevelMovement<>(target.toEntityLocation()
			, new EntityLocation(0.0f, 0.0f, 0.0f)
			, EntityChangeTopLevelMovement.Intensity.STANDING
			, (byte)0
			, (byte)0
			, change
		);
		
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		EntityActionCodec.serializeToBuffer(buffer, action);
		buffer.flip();
		IEntityAction<IMutablePlayerEntity> read = EntityActionCodec.parseAndSeekContext(new DeserializationContext(ENV
			, buffer
			, false
		));
		Assert.assertTrue(read instanceof EntityChangeTopLevelMovement);
		Assert.assertEquals(0, buffer.remaining());
	}

	@Test
	public void travel() throws Throwable
	{
		AbsoluteLocation target = new AbsoluteLocation(-10, 6, 0);
		EntitySubActionTravelViaBlock change = new EntitySubActionTravelViaBlock(target);
		EntityChangeTopLevelMovement<IMutablePlayerEntity> action = new EntityChangeTopLevelMovement<>(target.toEntityLocation()
			, new EntityLocation(0.0f, 0.0f, 0.0f)
			, EntityChangeTopLevelMovement.Intensity.STANDING
			, (byte)0
			, (byte)0
			, change
		);
		
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		EntityActionCodec.serializeToBuffer(buffer, action);
		buffer.flip();
		IEntityAction<IMutablePlayerEntity> read = EntityActionCodec.parseAndSeekContext(new DeserializationContext(ENV
			, buffer
			, false
		));
		Assert.assertTrue(read instanceof EntityChangeTopLevelMovement);
		Assert.assertEquals(0, buffer.remaining());
	}
}
