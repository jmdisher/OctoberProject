package com.jeffdisher.october.persistence;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.data.IOctree;
import com.jeffdisher.october.logic.CreatureIdAssigner;
import com.jeffdisher.october.logic.PassiveIdAssigner;
import com.jeffdisher.october.logic.ScheduledMutation;
import com.jeffdisher.october.persistence.legacy.LegacyCreatureEntityV8;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.PassiveEntity;
import com.jeffdisher.october.types.PassiveType;
import com.jeffdisher.october.utils.Assert;


/**
 * A utility class to re-write a serialized cuboid from a different version as the latest version.
 */
public class CuboidTranslator
{
	/**
	 * Translates the Cuboid data of version in inBuffer to the latest version in outBuffer.  Note that the version
	 * header is NOT expected in the inBuffer and will NOT be written tot he outBuffer.
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
		// We are only reading these to re-write them so we can use local ID assigners (since the IDs are ephemeral - hence why an assigner is needed).
		CreatureIdAssigner creatureIdAssigner = new CreatureIdAssigner();
		PassiveIdAssigner passiveIdAssigner = new PassiveIdAssigner();
		
		// The game time also doesn't matter so long as is the same for both reading and writing.
		long currentGameMillis = 0L;
		
		// The CuboidAddress is also not part of the serialized shape, just the in-memory shape and is used to give
		// global locations to things like passives and creatures, so that can be anything non-null.
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		
		PackagedCuboid packaged = _readLegacyCuboid(inBuffer
			, version
			, currentGameMillis
			, address
			, creatureIdAssigner
			, passiveIdAssigner
		);
		CuboidCodec.serializeCuboidWithoutVersionHeader(outBuffer, packaged, currentGameMillis);
	}


	private static PackagedCuboid _readLegacyCuboid(ByteBuffer inBuffer
		, int version
		, long currentGameMillis
		, CuboidAddress address
		, CreatureIdAssigner creatureIdAssigner
		, PassiveIdAssigner passiveIdAssigner
	)
	{
		// This cannot be called with up-to-date data.
		Assert.assertTrue(version < StorageVersions.CURRENT);
		
		Environment env = Environment.getShared();
		boolean usePreV8NonStackableDecoding = (version <= StorageVersions.V7);
		boolean usePreV11DamageDecoding = (version <= StorageVersions.V10);
		boolean skipPreV13CraftObjects = (version <= StorageVersions.V12);
		DeserializationContext context = new DeserializationContext(env
			, inBuffer
			, currentGameMillis
			, usePreV8NonStackableDecoding
			, usePreV11DamageDecoding
			, skipPreV13CraftObjects
		);
		
		PackagedCuboid packaged;
		if ((StorageVersions.V13 == version)
			|| (StorageVersions.V14 == version))
		{
			// Version 13 is the same as version 14, but some new data was added.
			// Version 15 added a new aspect, so we need to handle this differently.
			CuboidData cuboid = _readCuboidPre15(address, context);
			
			// Load any creatures associated with the cuboid.
			List<CreatureEntity> creatures = CuboidCodec.readCreatures(context, creatureIdAssigner);
			
			// Now, load any suspended mutations.
			List<ScheduledMutation> pendingMutations = CuboidCodec.readMutations(context);
			// ... and any periodic mutations.
			Map<BlockAddress, Long> periodicMutations = CuboidCodec.readPeriodic(inBuffer);
			
			// Passives are stored much like creatures.
			List<PassiveEntity> passives = CuboidCodec.readPassives(context, passiveIdAssigner);
			
			// This should be fully read (might remove this check if buffer usage changes).
			Assert.assertTrue(!inBuffer.hasRemaining());
			
			packaged = new PackagedCuboid(cuboid
				, creatures
				, pendingMutations
				, periodicMutations
				, passives
			);
		}
		else if ((StorageVersions.V11 == version)
			|| (StorageVersions.V12 == version)
		)
		{
			// Version 11 is the same as version 12, except it is packaged in the cuboid cluster directories, not flat files.
			// Version 12 is the same as version 13, except that the craft objects need to be stripped out (done with DeserializationContext).
			CuboidData cuboid = _readCuboidPre15(address, context);
			
			// Load any creatures associated with the cuboid.
			List<CreatureEntity> creatures = CuboidCodec.readCreatures(context, creatureIdAssigner);
			
			// Now, load any suspended mutations.
			List<ScheduledMutation> pendingMutations = CuboidCodec.readMutations(context);
			// ... and any periodic mutations.
			Map<BlockAddress, Long> periodicMutations = CuboidCodec.readPeriodic(inBuffer);
			
			// Passives are stored much like creatures.
			List<PassiveEntity> passives = CuboidCodec.readPassives(context, passiveIdAssigner);
			
			// This should be fully read (might remove this check if buffer usage changes).
			Assert.assertTrue(!inBuffer.hasRemaining());
			
			packaged = new PackagedCuboid(cuboid
				, creatures
				, pendingMutations
				, periodicMutations
				, passives
			);
		}
		else if ((StorageVersions.V9 == version)
			|| (StorageVersions.V10 == version)
		)
		{
			// Version 10 didn't change anything, just added to it, so we can read with the same logic.
			CuboidData cuboid = _readCuboidPre11(address, context);
			
			// Load any creatures associated with the cuboid.
			List<CreatureEntity> creatures = CuboidCodec.readCreatures(context, creatureIdAssigner);
			
			// Now, load any suspended mutations.
			List<ScheduledMutation> pendingMutations = CuboidCodec.readMutations(context);
			// ... and any periodic mutations.
			Map<BlockAddress, Long> periodicMutations = CuboidCodec.readPeriodic(inBuffer);
			
			// Passives are stored much like creatures.
			List<PassiveEntity> passives = CuboidCodec.readPassives(context, passiveIdAssigner);
			
			// This should be fully read.
			Assert.assertTrue(!inBuffer.hasRemaining());
			
			packaged = new PackagedCuboid(cuboid
				, creatures
				, pendingMutations
				, periodicMutations
				, passives
			);
		}
		else if (StorageVersions.V8 == version)
		{
			CuboidData cuboid = _readCuboidPre11(address, context);
			
			// Load any creatures associated with the cuboid.
			List<CreatureEntity> creatures = _readCreaturesV8(context, creatureIdAssigner);
			
			// Now, load any suspended mutations.
			List<ScheduledMutation> pendingMutations = CuboidCodec.readMutations(context);
			// ... and any periodic mutations.
			Map<BlockAddress, Long> periodicMutations = CuboidCodec.readPeriodic(inBuffer);
			
			// Passives added in V9, extracted from empty item inventory slots.
			List<PassiveEntity> convertedPassives = _convertCuboidPre9(env, currentGameMillis, cuboid, address.getBase(), passiveIdAssigner);
			List<PassiveEntity> passives = (null != convertedPassives)
				? convertedPassives
				: List.of()
			;
			
			// This should be fully read.
			Assert.assertTrue(!inBuffer.hasRemaining());
			
			packaged = new PackagedCuboid(cuboid
				, creatures
				, pendingMutations
				, periodicMutations
				, passives
			);
		}
		else if (StorageVersions.V7 == version)
		{
			CuboidData cuboid = _readCuboidPre8(address, context);
			
			// Load any creatures associated with the cuboid.
			List<CreatureEntity> creatures = _readCreaturesV8(context, creatureIdAssigner);
			
			// Now, load any suspended mutations.
			List<ScheduledMutation> pendingMutations = CuboidCodec.readMutations(context);
			// ... and any periodic mutations.
			Map<BlockAddress, Long> periodicMutations = CuboidCodec.readPeriodic(inBuffer);
			
			// Passives added in V9, extracted from empty item inventory slots.
			List<PassiveEntity> convertedPassives = _convertCuboidPre9(env, currentGameMillis, cuboid, address.getBase(), passiveIdAssigner);
			List<PassiveEntity> passives = (null != convertedPassives)
				? convertedPassives
				: List.of()
			;
			
			// This should be fully read.
			Assert.assertTrue(!inBuffer.hasRemaining());
			
			packaged = new PackagedCuboid(cuboid
				, creatures
				, pendingMutations
				, periodicMutations
				, passives
			);
		}
		else
		{
			throw new RuntimeException("UNSUPPORTED ENTITY STORAGE VERSION:  " + version);
		}
		
		return packaged;
	}

	private static CuboidData _readCuboidPre15(CuboidAddress address, DeserializationContext context)
	{
		// Prior to version 15, only aspects up to and including ENCHANTING were included.
		int aspectCount = AspectRegistry.ENCHANTING.index() + 1;
		
		CuboidData cuboid = CuboidData.createEmpty(address);
		cuboid.deserializeSomeAspectsFully(context, aspectCount);
		_remapBlockChanges(context.env(), cuboid);
		return cuboid;
	}

	private static CuboidData _readCuboidPre11(CuboidAddress address, DeserializationContext context)
	{
		// Prior to version 11, only aspects up to and including SPECIAL_ITEM_SLOT were included.
		int aspectCount = AspectRegistry.SPECIAL_ITEM_SLOT.index() + 1;
		
		CuboidData cuboid = CuboidData.createEmpty(address);
		cuboid.deserializeSomeAspectsFully(context, aspectCount);
		_remapBlockChanges(context.env(), cuboid);
		return cuboid;
	}

	private static CuboidData _readCuboidPre8(CuboidAddress address, DeserializationContext context)
	{
		// Prior to version 8, only aspects up to and including MULTI_BLOCK_ROOT were included.
		int aspectCount = AspectRegistry.MULTI_BLOCK_ROOT.index() + 1;
		
		CuboidData cuboid = CuboidData.createEmpty(address);
		cuboid.deserializeSomeAspectsFully(context, aspectCount);
		_remapBlockChanges(context.env(), cuboid);
		return cuboid;
	}

	private static void _remapBlockChanges(Environment env, CuboidData cuboid)
	{
		short torch = env.items.getItemById("op.torch").number();
		cuboid.walkData(AspectRegistry.BLOCK, (BlockAddress base, byte size, Short value) -> {
			if (value == torch)
			{
				Assert.assertTrue(1 == size);
				cuboid.setData7(AspectRegistry.ORIENTATION, base, FacingDirection.directionToByte(FacingDirection.DOWN));
			}
		}, (short)0);
	}

	private static List<CreatureEntity> _readCreaturesV8(DeserializationContext context, CreatureIdAssigner creatureIdAssigner)
	{
		ByteBuffer buffer = context.buffer();
		int creatureCount = buffer.getInt();
		List<CreatureEntity> creatures = new ArrayList<>();
		for (int i = 0; i < creatureCount; ++i)
		{
			LegacyCreatureEntityV8 legacy = LegacyCreatureEntityV8.load(creatureIdAssigner.next(), buffer);
			CreatureEntity entity = legacy.toEntity(context.currentGameMillis());
			creatures.add(entity);
		}
		return creatures;
	}

	// NOTE:  This will modify input and return the extracted passives or null, if there weren't any and input was unchanged.
	private static List<PassiveEntity> _convertCuboidPre9(Environment env, long currentGameMillis, CuboidData input, AbsoluteLocation baseLocation, PassiveIdAssigner passiveIdAssigner)
	{
		List<BlockAddress> toClear = new ArrayList<>();
		List<PassiveEntity> passives = new ArrayList<>();
		input.walkData(AspectRegistry.INVENTORY, new IOctree.IWalkerCallback<Inventory>() {
			@Override
			public void visit(BlockAddress base, byte size, Inventory value)
			{
				short blockNumber = input.getData15(AspectRegistry.BLOCK, base);
				Block block = env.blocks.fromItem(env.items.ITEMS_BY_TYPE[blockNumber]);
				int inventorySize = env.stations.getNormalInventorySize(block);
				if (0 == inventorySize)
				{
					// This must be an empty inventory so convert its contents to passives.
					PassiveType type = PassiveType.ITEM_SLOT;
					EntityLocation passiveLocation = baseLocation.relativeForBlock(base).toEntityLocation();
					EntityLocation passiveVelocity = new EntityLocation(0.0f, 0.0f, 0.0f);
					for (Integer key : value.sortedKeys())
					{
						ItemSlot slot = value.getSlotForKey(key);
						PassiveEntity passive = new PassiveEntity(passiveIdAssigner.next()
							, type
							, passiveLocation
							, passiveVelocity
							, slot
							, currentGameMillis
						);
						passives.add(passive);
					}
					toClear.add(base);
				}
			}
		}, null);
		
		// Clear out these inventory slots.
		for (BlockAddress address : toClear)
		{
			input.setDataSpecial(AspectRegistry.INVENTORY, address, null);
		}
		return passives.isEmpty()
			? null
			: passives
		;
	}
}
