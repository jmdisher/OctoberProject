package com.jeffdisher.october.net;

import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.mutations.EntityChangeIncrementalBlockRepair;
import com.jeffdisher.october.mutations.EntityChangeJump;
import com.jeffdisher.october.mutations.EntityChangeTakeDamageFromEntity;
import com.jeffdisher.october.mutations.Deprecated_EntityChangeTakeDamageFromOther;
import com.jeffdisher.october.mutations.EntityChangeTopLevelMovement;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.IMutablePlayerEntity;


public class TestMutationEntityCodec
{
	@Test
	public void repair() throws Throwable
	{
		AbsoluteLocation location = new AbsoluteLocation(-1, 0, 1);
		EntityChangeIncrementalBlockRepair change = new EntityChangeIncrementalBlockRepair(location);
		
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
		Deprecated_EntityChangeTakeDamageFromOther<IMutablePlayerEntity> change = new Deprecated_EntityChangeTakeDamageFromOther<>(BodyPart.HEAD, damage, Deprecated_EntityChangeTakeDamageFromOther.CAUSE_FALL);
		
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		MutationEntityCodec.serializeToBuffer(buffer, change);
		buffer.flip();
		IMutationEntity<IMutablePlayerEntity> read = MutationEntityCodec.parseAndSeekFlippedBuffer(buffer);
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
		MutationEntityCodec.serializeToBuffer(buffer, action);
		buffer.flip();
		IMutationEntity<IMutablePlayerEntity> read = MutationEntityCodec.parseAndSeekFlippedBuffer(buffer);
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
		MutationEntityCodec.serializeToBuffer(buffer, action);
		buffer.flip();
		read = MutationEntityCodec.parseAndSeekFlippedBuffer(buffer);
		Assert.assertTrue(read instanceof EntityChangeTopLevelMovement);
		Assert.assertTrue(((EntityChangeTopLevelMovement<?>)read).test_getSubAction() instanceof EntityChangeJump);
		Assert.assertEquals(0, buffer.remaining());
	}
}
