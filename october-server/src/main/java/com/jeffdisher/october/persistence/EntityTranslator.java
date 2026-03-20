package com.jeffdisher.october.persistence;

import java.nio.ByteBuffer;
import java.util.List;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.logic.ScheduledChange;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.persistence.legacy.LegacyEntityV1;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.utils.Assert;


/**
 * A utility class to re-write a serialized entity from a different version as the latest version.
 */
public class EntityTranslator
{
	/**
	 * Translates the Entity data of version in inBuffer to the latest version in outBuffer.  Note that the version
	 * header is NOT expected in the inBuffer and will NOT be written to the outBuffer.
	 * 
	 * @param outBuffer The destination where the updated version will be written.
	 * @param inBuffer The source where the old version will be read.
	 * @param version The version of the inBuffer data.
	 */
	public static void changeToLatestVersion(ByteBuffer outBuffer
		, ByteBuffer inBuffer
		, int version
	)
	{
		// This cannot be called with up-to-date data.
		Assert.assertTrue(version < StorageVersions.CURRENT);
		
		// The game time doesn't matter so long as is the same for both reading and writing.
		long currentGameMillis = 0L;
		
		SuspendedEntity result = _readLegacyEntity(inBuffer, version, currentGameMillis);
		
		EntityCodec.serializeEntityWithoutVersionHeader(outBuffer, result);
	}


	private static SuspendedEntity _readLegacyEntity(ByteBuffer inBuffer, int version, long currentGameMillis)
	{
		Environment env = Environment.getShared();
		boolean usePreV8NonStackableDecoding = (version <= StorageVersions.V7);
		boolean usePreV11DamageDecoding = (version <= StorageVersions.V10);
		DeserializationContext context = new DeserializationContext(env
			, inBuffer
			, currentGameMillis
			, usePreV8NonStackableDecoding
			, usePreV11DamageDecoding
		);
		
		SuspendedEntity result;
		if ((StorageVersions.V11 == version)
			|| (StorageVersions.V10 == version)
		)
		{
			// Do nothing special - just stops old versions from being broken.
			Entity entity = CodecHelpers.readEntityDisk(context);
			
			// Now, load any suspended changes.
			List<ScheduledChange> suspended = EntityCodec.readSuspendedMutations(context);
			result = new SuspendedEntity(entity, suspended);
		}
		else if ((StorageVersions.V2 == version)
				|| (StorageVersions.V3 == version)
				|| (StorageVersions.V4 == version)
				|| (StorageVersions.V5 == version)
				|| (StorageVersions.V6 == version)
				|| (StorageVersions.V7 == version)
				|| (StorageVersions.V8 == version)
				|| (StorageVersions.V9 == version)
		)
		{
			// These versions used a different on-disk entity shape.
			Entity entity = _readEntityPre10(context);
			
			// Now, load any suspended changes.
			List<ScheduledChange> suspended = EntityCodec.readSuspendedMutations(context);
			result = new SuspendedEntity(entity, suspended);
		}
		else if (StorageVersions.V1 == version)
		{
			// The V1 entity is has less data.
			// Read the legacy data.
			LegacyEntityV1 legacy = LegacyEntityV1.load(context);
			Entity entity = legacy.toEntity();
			
			// Now, load any suspended changes.
			List<ScheduledChange> suspended = EntityCodec.readSuspendedMutations(context);
			result = new SuspendedEntity(entity, suspended);
		}
		else
		{
			throw new RuntimeException("UNSUPPORTED ENTITY STORAGE VERSION:  " + version);
		}
		return result;
	}

	private static Entity _readEntityPre10(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		int id = buffer.getInt();
		boolean isCreativeMode = CodecHelpers.readBoolean(buffer);
		EntityLocation location = CodecHelpers.readEntityLocation(buffer);
		EntityLocation velocity = CodecHelpers.readEntityLocation(buffer);
		byte yaw = buffer.get();
		byte pitch = buffer.get();
		Inventory inventory = CodecHelpers.readInventory(context);
		int[] hotbar = new int[Entity.HOTBAR_SIZE];
		for (int i = 0; i < hotbar.length; ++i)
		{
			hotbar[i] = buffer.getInt();
		}
		int hotbarIndex = buffer.getInt();
		NonStackableItem[] armour = new NonStackableItem[BodyPart.values().length];
		for (int i = 0; i < armour.length; ++i)
		{
			armour[i] = CodecHelpers.readNonStackableItem(context);
		}
		// We ignore localCraftOperation as it is now ephemeral.
		CodecHelpers.readCraftOperation(buffer);
		byte health = buffer.get();
		byte food = buffer.get();
		byte breath = buffer.get();
		// We ignore int energyDeficit as it is now ephemeral.
		buffer.getInt();
		EntityLocation spawn = CodecHelpers.readEntityLocation(buffer);
		
		return new Entity(id
			, isCreativeMode
			, location
			, velocity
			, yaw
			, pitch
			, inventory
			, hotbar
			, hotbarIndex
			, armour
			, health
			, food
			, breath
			, spawn
			
			, Entity.EMPTY_SHARED
			, Entity.EMPTY_LOCAL
		);
	}
}
