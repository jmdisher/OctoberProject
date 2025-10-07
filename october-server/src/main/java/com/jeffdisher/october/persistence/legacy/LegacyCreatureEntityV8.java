package com.jeffdisher.october.persistence.legacy;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;


/**
 * Reads the V8 version of the CreatureEntity object.
 * The key difference in this version is that it has no per-type extended data.
 */
public record LegacyCreatureEntityV8(int id
	, EntityType type
	, EntityLocation location
	, EntityLocation velocity
	, byte yaw
	, byte pitch
	, byte health
	, byte breath
)
{
	public static LegacyCreatureEntityV8 load(int idToAssign, ByteBuffer buffer)
	{
		Environment env = Environment.getShared();
		int id = idToAssign;
		byte ordinal = buffer.get();
		EntityType type = env.creatures.ENTITY_BY_NUMBER[ordinal];
		EntityLocation location = CodecHelpers.readEntityLocation(buffer);
		EntityLocation velocity = CodecHelpers.readEntityLocation(buffer);
		byte yaw = buffer.get();
		byte pitch = buffer.get();
		byte health = buffer.get();
		byte breath = buffer.get();
		
		return new LegacyCreatureEntityV8(id
			, type
			, location
			, velocity
			, yaw
			, pitch
			, health
			, breath
		);
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
