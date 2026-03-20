package com.jeffdisher.october.persistence;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.data.IOctree;
import com.jeffdisher.october.logic.CreatureIdAssigner;
import com.jeffdisher.october.logic.PassiveIdAssigner;
import com.jeffdisher.october.logic.ScheduledMutation;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.MutationBlockPeriodic;
import com.jeffdisher.october.persistence.legacy.LegacyCreatureEntityV1;
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
		DeserializationContext context = new DeserializationContext(env
			, inBuffer
			, currentGameMillis
			, usePreV8NonStackableDecoding
			, usePreV11DamageDecoding
		);
		
		PackagedCuboid packaged;
		if (StorageVersions.V11 == version)
		{
			// Version 11 is the same as version 12, except it is packaged in the cuboid cluster directories, not flat files.
			CuboidData cuboid = CuboidCodec.readCuboid(address, context);
			
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
		else if (StorageVersions.V6 == version)
		{
			// V6 just adds data so this is to avoid going backward.
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
		else if (StorageVersions.V5 == version)
		{
			// V5 requires that the logic aspect be cleared and all switches be turned off.
			CuboidData cuboid = _readCuboidV5(address, context);
			
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
		else if (StorageVersions.V4 == version)
		{
			// V4 needs to re-write for orientation aspects.
			CuboidData cuboid = _readCuboidPre5(address, context);
			
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
		else if ((StorageVersions.V2 == version) || (StorageVersions.V3 == version))
		{
			// V2 is a subset of V3 so do nothing special - just stops old versions from being broken.
			CuboidData cuboid = _readCuboidPre5(address, context);
			
			// Load any creatures associated with the cuboid.
			List<CreatureEntity> creatures = _readCreaturesV8(context, creatureIdAssigner);
			
			// Now, load any suspended mutations.
			List<ScheduledMutation> pendingMutations = new ArrayList<>();
			Map<BlockAddress, Long> periodicMutations = new HashMap<>();
			_splitMutations(pendingMutations, periodicMutations, context);
			
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
		else if (StorageVersions.V1 == version)
		{
			// The V1 entity is has less data.
			CuboidData cuboid = _readCuboidPre5(address, context);
			
			// Load any creatures associated with the cuboid.
			int creatureCount = inBuffer.getInt();
			List<CreatureEntity> creatures = new ArrayList<>();
			for (int i = 0; i < creatureCount; ++i)
			{
				LegacyCreatureEntityV1 legacy = LegacyCreatureEntityV1.load(creatureIdAssigner.next(), inBuffer);
				CreatureEntity entity = legacy.toEntity(currentGameMillis);
				creatures.add(entity);
			}
			
			// Now, load any suspended mutations.
			List<ScheduledMutation> pendingMutations = new ArrayList<>();
			Map<BlockAddress, Long> periodicMutations = new HashMap<>();
			_splitMutations(pendingMutations, periodicMutations, context);
			
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

	private static CuboidData _readCuboidPre11(CuboidAddress address, DeserializationContext context)
	{
		// Prior to version 11, only aspects up to and including SPECIAL_ITEM_SLOT were included.
		int aspectCount = 11;
		
		CuboidData cuboid = CuboidData.createEmpty(address);
		cuboid.deserializeSomeAspectsFully(context, aspectCount);
		return cuboid;
	}

	private static CuboidData _readCuboidPre8(CuboidAddress address, DeserializationContext context)
	{
		// Prior to version 8, only aspects up to and including MULTI_BLOCK_ROOT were included.
		int aspectCount = 10;
		
		CuboidData cuboid = CuboidData.createEmpty(address);
		cuboid.deserializeSomeAspectsFully(context, aspectCount);
		return cuboid;
	}

	private static CuboidData _readCuboidPre5(CuboidAddress address, DeserializationContext context)
	{
		CuboidData cuboid = CuboidData.createEmpty(address);
		
		// Prior to version 5, only the aspects up to and including LOGIC were included.
		int aspectCount = 7;
		cuboid.deserializeSomeAspectsFully(context, aspectCount);
		
		// This is now a V5 cuboid so convert it to V6.
		_convertCuboid_V5toV6(cuboid);
		return cuboid;
	}

	private static CuboidData _readCuboidV5(CuboidAddress address, DeserializationContext context)
	{
		// Start by just loaded the data, normally.
		CuboidData cuboid = CuboidData.createEmpty(address);
		cuboid.deserializeSomeAspectsFully(context, AspectRegistry.ALL_ASPECTS.length);
		
		_convertCuboid_V5toV6(cuboid);
		
		return cuboid;
	}

	private static void _convertCuboid_V5toV6(CuboidData cuboid)
	{
		// Version 6 changed the definition of the LOGIC layer which requires that it be cleared and all switches and lamps set to "off".
		IOctree<?>[] rawOctrees = cuboid.unsafeDataAccess();
		rawOctrees[AspectRegistry.LOGIC.index()] = AspectRegistry.LOGIC.emptyTreeSupplier().get();
		
		Environment env = Environment.getShared();
		short switchOffNumber = env.items.getItemById("op.switch").number();
		short switchOnNumber = env.items.getItemById("DEPRECATED.op.switch_on").number();
		short lampOffNumber = env.items.getItemById("op.lamp").number();
		short lampOnNumber = env.items.getItemById("DEPRECATED.op.lamp_on").number();
		short gateNumber = env.items.getItemById("op.gate").number();
		short doorOpenNumber = env.items.getItemById("DEPRECATED.op.door_open").number();
		short doubleDoorNumber = env.items.getItemById("op.double_door_base").number();
		short doubleDoorOpenNumber = env.items.getItemById("DEPRECATED.op.double_door_open_base").number();
		short hopperDownNumber = env.items.getItemById("op.hopper").number();
		short hopperNorthNumber = env.items.getItemById("DEPRECATED.op.hopper_north").number();
		short hopperSouthNumber = env.items.getItemById("DEPRECATED.op.hopper_south").number();
		short hopperEastNumber = env.items.getItemById("DEPRECATED.op.hopper_east").number();
		short hopperWestNumber = env.items.getItemById("DEPRECATED.op.hopper_west").number();
		Set<BlockAddress> switches = new HashSet<>();
		Set<BlockAddress> lamps = new HashSet<>();
		Set<BlockAddress> doors = new HashSet<>();
		Set<BlockAddress> doubleDoors = new HashSet<>();
		Map<BlockAddress, FacingDirection> hoppers = new HashMap<>();
		cuboid.walkData(AspectRegistry.BLOCK, new IOctree.IWalkerCallback<>(){
			@Override
			public void visit(BlockAddress base, byte size, Short value)
			{
				if ((switchOnNumber == value)
						|| (lampOnNumber == value)
						|| (doorOpenNumber == value)
						|| (doubleDoorOpenNumber == value)
						|| (hopperDownNumber == value)
						|| (hopperNorthNumber == value)
						|| (hopperSouthNumber == value)
						|| (hopperEastNumber == value)
						|| (hopperWestNumber == value)
				)
				{
					for (byte z = 0; z < size; ++z)
					{
						for (byte y = 0; y < size; ++y)
						{
							for (byte x = 0; x < size; ++x)
							{
								BlockAddress target = base.getRelative(x, y, z);
								if (switchOnNumber == value)
								{
									switches.add(target);
								}
								else if (lampOnNumber == value)
								{
									lamps.add(target);
								}
								else if (doorOpenNumber == value)
								{
									doors.add(target);
								}
								else if (doubleDoorOpenNumber == value)
								{
									doubleDoors.add(target);
								}
								else if (hopperDownNumber == value)
								{
									hoppers.put(target, FacingDirection.DOWN);
								}
								else if (hopperNorthNumber == value)
								{
									hoppers.put(target, FacingDirection.NORTH);
								}
								else if (hopperSouthNumber == value)
								{
									hoppers.put(target, FacingDirection.SOUTH);
								}
								else if (hopperEastNumber == value)
								{
									hoppers.put(target, FacingDirection.EAST);
								}
								else if (hopperWestNumber == value)
								{
									hoppers.put(target, FacingDirection.WEST);
								}
								else
								{
									// Missing case.
									throw Assert.unreachable();
								}
							}
						}
					}
				}
			}
		}, env.special.AIR.item().number());
		for (BlockAddress block : switches)
		{
			cuboid.setData15(AspectRegistry.BLOCK, block, switchOffNumber);
		}
		for (BlockAddress block : lamps)
		{
			cuboid.setData15(AspectRegistry.BLOCK, block, lampOffNumber);
		}
		for (BlockAddress block : doors)
		{
			cuboid.setData15(AspectRegistry.BLOCK, block, gateNumber);
		}
		for (BlockAddress block : doubleDoors)
		{
			cuboid.setData15(AspectRegistry.BLOCK, block, doubleDoorNumber);
		}
		for (Map.Entry<BlockAddress, FacingDirection> elt : hoppers.entrySet())
		{
			BlockAddress address = elt.getKey();
			cuboid.setData15(AspectRegistry.BLOCK, address, hopperDownNumber);
			cuboid.setData7(AspectRegistry.ORIENTATION, address, FacingDirection.directionToByte(elt.getValue()));
		}
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

	private static void _splitMutations(List<ScheduledMutation> out_pendingMutations
			, Map<BlockAddress, Long> out_periodicMutations
			, DeserializationContext context
	)
	{
		for (ScheduledMutation scheduledMutation : CuboidCodec.readMutations(context))
		{
			IMutationBlock mutation = scheduledMutation.mutation();
			if (mutation instanceof MutationBlockPeriodic)
			{
				BlockAddress block = mutation.getAbsoluteLocation().getBlockAddress();
				out_periodicMutations.put(block, scheduledMutation.millisUntilReady());
			}
			else
			{
				out_pendingMutations.add(scheduledMutation);
			}
		}
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
