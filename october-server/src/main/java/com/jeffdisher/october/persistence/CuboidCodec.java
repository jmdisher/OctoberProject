package com.jeffdisher.october.persistence;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.CreatureIdAssigner;
import com.jeffdisher.october.logic.HeightMapHelpers;
import com.jeffdisher.october.logic.PassiveIdAssigner;
import com.jeffdisher.october.logic.ScheduledMutation;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.PassiveEntity;
import com.jeffdisher.october.utils.Assert;


/**
 * This class just contains the logic to serialize and deserialize a latest-version Cuboid to/from a ByteBuffer.
 * Note that the version header is neither read nor written in these helpers.
 */
public class CuboidCodec
{
	public static void serializeCuboidWithoutVersionHeader(ByteBuffer outBuffer, PackagedCuboid data, long gameTimeMillis)
	{
		// Serialize the entire cuboid into memory and write it out.
		// Data goes in the following order:
		// 1) cuboid data
		// 2) creatures
		// 3) suspended mutations
		// 4) periodic mutations
		// 5) passives
		
		// 1) Write the raw cuboid data.
		IReadOnlyCuboidData cuboid = data.cuboid();
		Object state = cuboid.serializeResumable(null, outBuffer);
		// We currently assume that we just do the write in a single call.
		Assert.assertTrue(null == state);
		
		// 2) Write the creatures.
		List<CreatureEntity> entities = data.creatures();
		outBuffer.putInt(entities.size());
		for (CreatureEntity entity : entities)
		{
			CodecHelpers.writeCreatureEntity(outBuffer, entity, gameTimeMillis);
		}
		
		// 3) Write suspended mutations.
		List<ScheduledMutation> mutationsToWrite = data.pendingMutations().stream().filter((ScheduledMutation scheduled) -> scheduled.mutation().canSaveToDisk()).toList();
		outBuffer.putInt(mutationsToWrite.size());
		for (ScheduledMutation scheduled : mutationsToWrite)
		{
			// Write the parts of the data.
			outBuffer.putLong(scheduled.millisUntilReady());
			MutationBlockCodec.serializeToBuffer(outBuffer, scheduled.mutation());
		}
		
		// 4) Write periodic mutations.
		Map<BlockAddress, Long> periodic = data.periodicMutationMillis();
		outBuffer.putInt(periodic.size());
		for (Map.Entry<BlockAddress, Long> elt : periodic.entrySet())
		{
			BlockAddress block = elt.getKey();
			long millisUntilReady = elt.getValue();
			
			outBuffer.put(block.x());
			outBuffer.put(block.y());
			outBuffer.put(block.z());
			outBuffer.putLong(millisUntilReady);
		}
		
		// 5) Write passive entities.
		List<PassiveEntity> passives = data.passives();
		outBuffer.putInt(passives.size());
		for (PassiveEntity passive : passives)
		{
			CodecHelpers.writePassiveEntity(outBuffer, passive);
		}
		
		// From here, the entire cuboid is serialized into the outBuffer so we can return.
	}

	public static SuspendedCuboid<CuboidData> deserializeCuboidWithoutVersionHeader(ByteBuffer inBuffer
		, CuboidAddress address
		, long currentGameMillis
		, CreatureIdAssigner creatureIdAssigner
		, PassiveIdAssigner passiveIdAssigner
	)
	{
		Environment env = Environment.getShared();
		boolean usePreV8NonStackableDecoding = false;
		boolean usePreV11DamageDecoding = false;
		DeserializationContext context = new DeserializationContext(env
			, inBuffer
			, currentGameMillis
			, usePreV8NonStackableDecoding
			, usePreV11DamageDecoding
		);
		
		CuboidData cuboid = _readCuboid(address, context);
		
		// Load any creatures associated with the cuboid.
		List<CreatureEntity> creatures = _readCreatures(context, creatureIdAssigner);
		
		// Now, load any suspended mutations.
		List<ScheduledMutation> pendingMutations = _readMutations(context);
		// ... and any periodic mutations.
		Map<BlockAddress, Long> periodicMutations = _readPeriodic(inBuffer);
		
		// Passives are stored much like creatures.
		List<PassiveEntity> passives = _readPassives(context, passiveIdAssigner);
		
		// This should be fully read (might remove this check if buffer usage changes).
		Assert.assertTrue(!inBuffer.hasRemaining());
		
		// The height map is ephemeral so it is built here.  Note that building this might be somewhat expensive.
		CuboidHeightMap heightMap = HeightMapHelpers.buildHeightMap(cuboid);
		return new SuspendedCuboid<>(cuboid
			, heightMap
			, creatures
			, pendingMutations
			, periodicMutations
			, passives
		);
	}

	public static List<CreatureEntity> readCreatures(DeserializationContext context, CreatureIdAssigner creatureIdAssigner)
	{
		return _readCreatures(context, creatureIdAssigner);
	}

	public static List<PassiveEntity> readPassives(DeserializationContext context, PassiveIdAssigner passiveIdAssigner)
	{
		return _readPassives(context, passiveIdAssigner);
	}

	public static List<ScheduledMutation> readMutations(DeserializationContext context)
	{
		return _readMutations(context);
	}

	public static Map<BlockAddress, Long> readPeriodic(ByteBuffer buffer)
	{
		return _readPeriodic(buffer);
	}

	public static CuboidData readCuboid(CuboidAddress address, DeserializationContext context)
	{
		return _readCuboid(address, context);
	}


	private static CuboidData _readCuboid(CuboidAddress address, DeserializationContext context)
	{
		CuboidData cuboid = CuboidData.createEmpty(address);
		cuboid.deserializeSomeAspectsFully(context, AspectRegistry.ALL_ASPECTS.length);
		return cuboid;
	}

	private static List<CreatureEntity> _readCreatures(DeserializationContext context, CreatureIdAssigner creatureIdAssigner)
	{
		ByteBuffer buffer = context.buffer();
		int creatureCount = buffer.getInt();
		List<CreatureEntity> creatures = new ArrayList<>();
		for (int i = 0; i < creatureCount; ++i)
		{
			CreatureEntity entity = CodecHelpers.readCreatureEntity(creatureIdAssigner.next(), buffer, context.currentGameMillis());
			creatures.add(entity);
		}
		return creatures;
	}

	private static List<PassiveEntity> _readPassives(DeserializationContext context, PassiveIdAssigner passiveIdAssigner)
	{
		ByteBuffer buffer = context.buffer();
		int passiveCount = buffer.getInt();
		List<PassiveEntity> passives = new ArrayList<>();
		for (int i = 0; i < passiveCount; ++i)
		{
			PassiveEntity entity = CodecHelpers.readPassiveEntity(passiveIdAssigner.next(), context);
			passives.add(entity);
		}
		return passives;
	}

	private static List<ScheduledMutation> _readMutations(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		int mutationCount = buffer.getInt();
		List<ScheduledMutation> suspended = new ArrayList<>();
		for (int i = 0; i < mutationCount; ++i)
		{
			// Read the parts of the suspended data.
			long millisUntilReady = buffer.getLong();
			IMutationBlock mutation = MutationBlockCodec.parseAndSeekContext(context);
			suspended.add(new ScheduledMutation(mutation, millisUntilReady));
		}
		return suspended;
	}

	private static Map<BlockAddress, Long> _readPeriodic(ByteBuffer buffer)
	{
		Map<BlockAddress, Long> periodicMutations = new HashMap<>();
		
		int mutationCount = buffer.getInt();
		for (int i = 0; i < mutationCount; ++i)
		{
			// Read the location.
			byte x = buffer.get();
			byte y = buffer.get();
			byte z = buffer.get();
			BlockAddress block = new BlockAddress(x, y, z);
			
			// Read the millis until ready.
			long millis = buffer.getLong();
			periodicMutations.put(block, millis);
		}
		return periodicMutations;
	}
}
