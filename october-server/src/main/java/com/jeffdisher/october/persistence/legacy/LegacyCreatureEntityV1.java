package com.jeffdisher.october.persistence.legacy;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.utils.Assert;


/**
 * Reads the V1 version of the CreatureEntity object.
 * Note that this is a duplicate of the V1 version of CreatureEntity with some inlined reader code from CodecHelpers.
 */
public record LegacyCreatureEntityV1(int id
		, EntityType type
		, EntityLocation location
		, EntityLocation velocity
		, byte health
		, byte breath
)
{
	public static LegacyCreatureEntityV1 load(int idToAssign, ByteBuffer buffer)
	{
		Environment env = Environment.getShared();
		int id = idToAssign;
		byte ordinal = buffer.get();
		EntityType type = env.creatures.ENTITY_BY_NUMBER[ordinal];
		EntityLocation location = CodecHelpers.readEntityLocation(buffer);
		EntityLocation velocity = CodecHelpers.readEntityLocation(buffer);
		byte health = buffer.get();
		byte breath = buffer.get();
		
		return new LegacyCreatureEntityV1(id
				, type
				, location
				, velocity
				, health
				, breath
		);
	}

	public void test_writeToBuffer(ByteBuffer buffer)
	{
		// NOTE:  This is just for testing.
		// We just use the logic which was used in CodecHelpers to load this in V1.
		// Note that the ID is not part of the CreatureEntity serialized shape.
		byte ordinal = this.type().number();
		Assert.assertTrue((byte)0 != ordinal);
		EntityLocation location = this.location();
		EntityLocation velocity = this.velocity();
		byte health = this.health();
		byte breath = this.breath();
		
		buffer.put(ordinal);
		CodecHelpers.writeEntityLocation(buffer, location);
		CodecHelpers.writeEntityLocation(buffer, velocity);
		buffer.put(health);
		buffer.put(breath);
	}

	public CreatureEntity toEntity(long currentGameMillis)
	{
		byte yaw = 0;
		byte pitch = 0;
		return new CreatureEntity(this.id
				, this.type
				, this.location
				, this.velocity
				, yaw
				, pitch
				, this.health
				, this.breath
				, this.type.extendedCodec().buildDefault()
				
				, CreatureEntity.createEmptyEphemeral(currentGameMillis)
		);
	}
}
