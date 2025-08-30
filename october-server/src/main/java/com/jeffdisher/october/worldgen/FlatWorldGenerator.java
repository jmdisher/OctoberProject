package com.jeffdisher.october.worldgen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.OrientationAspect;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.logic.CreatureIdAssigner;
import com.jeffdisher.october.logic.HeightMapHelpers;
import com.jeffdisher.october.logic.ScheduledMutation;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.persistence.SuspendedCuboid;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.utils.CuboidGenerator;


/**
 * A relatively simple world generator, designed to include the basic block types supported.
 * We also drop other miscellaneous items to make testing easier in the 0,0,0 cuboid.
 */
public class FlatWorldGenerator implements IWorldGenerator
{
	public static final AbsoluteLocation BASE = new AbsoluteLocation(-8, -8, -1);
	public static final AbsoluteLocation PORTAL_NORTH = new AbsoluteLocation(-5, 1002, -1);
	public static final AbsoluteLocation PORTAL_SOUTH = new AbsoluteLocation(5, -1002, -1);
	public static final AbsoluteLocation PORTAL_EAST = new AbsoluteLocation(1002, 5, -1);
	public static final AbsoluteLocation PORTAL_WEST = new AbsoluteLocation(-1002, -5, -1);

	private final Environment _env;
	private final Block _stoneBlock;
	private final Block _dirtBlock;
	private final Block _logBlock;
	private final Block _coalOreBlock;
	private final Block _ironOreBlock;
	private final Block _waterSourceBlock;
	private final EntityType _cow;

	private final boolean _shouldGenerateStructures;
	private final CommonStructures _structures;

	/**
	 * Creates the world generator, configured with options.
	 * 
	 * @param env The shared environment.
	 * @param shouldGenerateStructures True if generated structures should be added, false if not.
	 */
	public FlatWorldGenerator(Environment env, boolean shouldGenerateStructures)
	{
		_env = env;
		_stoneBlock = env.blocks.fromItem(env.items.getItemById("op.stone"));
		_dirtBlock = env.blocks.fromItem(env.items.getItemById("op.dirt"));
		_logBlock = env.blocks.fromItem(env.items.getItemById("op.log"));
		_coalOreBlock = env.blocks.fromItem(env.items.getItemById("op.coal_ore"));
		_ironOreBlock = env.blocks.fromItem(env.items.getItemById("op.iron_ore"));
		_waterSourceBlock = env.blocks.fromItem(env.items.getItemById("op.water_source"));
		_cow = env.creatures.getTypeById("op.cow");
		
		_shouldGenerateStructures = shouldGenerateStructures;
		_structures = new CommonStructures(env);
	}

	@Override
	public SuspendedCuboid<CuboidData> generateCuboid(CreatureIdAssigner creatureIdAssigner, CuboidAddress address)
	{
		// We will store the block types in the negative z blocks, but leave the non-negative blocks full or air.
		CuboidData data;
		if (address.z() < (short)0)
		{
			data = CuboidGenerator.createFilledCuboid(address, _stoneBlock);
			_fillPlane(data, (byte)31, _dirtBlock);
			_fillPlane(data, (byte)29, _logBlock);
			_fillPlane(data, (byte)27, _coalOreBlock);
			_fillPlane(data, (byte)25, _ironOreBlock);
			// We want to add a bit of water.
			data.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(6, 6, 31), _waterSourceBlock.item().number());
			data.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(7, 7, 31), _waterSourceBlock.item().number());
		}
		else
		{
			data = CuboidGenerator.createFilledCuboid(address, _env.special.AIR);
		}
		
		// See if this is a cuboid where we want to generate our structure (it is in the 8 cuboids around the origin).
		List<CreatureEntity> entities;
		List<ScheduledMutation> mutations = new ArrayList<>();
		Map<BlockAddress, Long> periodicMutationMillis = new HashMap<>();
		if (_shouldGenerateStructures
			&& _structures.nexusCastle.doesIntersectCuboid(address, BASE, OrientationAspect.Direction.NORTH)
		)
		{
			// Our structures don't have entities.
			entities = List.of();
			
			Structure.FollowUp followUp = _structures.nexusCastle.applyToCuboid(data, BASE, OrientationAspect.Direction.NORTH, Structure.REPLACE_ALL);
			mutations.addAll(followUp.overwriteMutations().stream()
					.map((IMutationBlock mutation) -> new ScheduledMutation(mutation, 0L))
					.toList()
			);
			periodicMutationMillis.putAll(followUp.periodicMutationMillis());
		}
		else
		{
			// Load in 2 cows near the base of this cuboid if it is at z=0.
			AbsoluteLocation baseOfCuboid = address.getBase();
			entities = (0 == address.z())
					? List.of(CreatureEntity.create(creatureIdAssigner.next()
							, _cow
							, baseOfCuboid.toEntityLocation()
							, (byte)100
						), CreatureEntity.create(creatureIdAssigner.next()
							, _cow
							, baseOfCuboid.getRelative(5, 5, 0).toEntityLocation()
							, (byte)100
					))
					: List.of()
			;
		}
		
		// See if there are any special structures to add.
		if (_shouldGenerateStructures && _structures.distanceTower.doesIntersectCuboid(address, PORTAL_NORTH, OrientationAspect.Direction.NORTH))
		{
			Structure.FollowUp followUp = _structures.distanceTower.applyToCuboid(data, PORTAL_NORTH, OrientationAspect.Direction.NORTH, Structure.REPLACE_ALL);
			mutations.addAll(followUp.overwriteMutations().stream()
				.map((IMutationBlock mutation) -> new ScheduledMutation(mutation, 0L))
				.toList()
			);
			periodicMutationMillis.putAll(followUp.periodicMutationMillis());
		}
		else if (_shouldGenerateStructures && _structures.distanceTower.doesIntersectCuboid(address, PORTAL_SOUTH, OrientationAspect.Direction.SOUTH))
		{
			Structure.FollowUp followUp = _structures.distanceTower.applyToCuboid(data, PORTAL_SOUTH, OrientationAspect.Direction.SOUTH, Structure.REPLACE_ALL);
			mutations.addAll(followUp.overwriteMutations().stream()
				.map((IMutationBlock mutation) -> new ScheduledMutation(mutation, 0L))
				.toList()
			);
			periodicMutationMillis.putAll(followUp.periodicMutationMillis());
		}
		else if (_shouldGenerateStructures && _structures.distanceTower.doesIntersectCuboid(address, PORTAL_EAST, OrientationAspect.Direction.EAST))
		{
			Structure.FollowUp followUp = _structures.distanceTower.applyToCuboid(data, PORTAL_EAST, OrientationAspect.Direction.EAST, Structure.REPLACE_ALL);
			mutations.addAll(followUp.overwriteMutations().stream()
				.map((IMutationBlock mutation) -> new ScheduledMutation(mutation, 0L))
				.toList()
			);
			periodicMutationMillis.putAll(followUp.periodicMutationMillis());
		}
		else if (_shouldGenerateStructures && _structures.distanceTower.doesIntersectCuboid(address, PORTAL_WEST, OrientationAspect.Direction.WEST))
		{
			Structure.FollowUp followUp = _structures.distanceTower.applyToCuboid(data, PORTAL_WEST, OrientationAspect.Direction.WEST, Structure.REPLACE_ALL);
			mutations.addAll(followUp.overwriteMutations().stream()
				.map((IMutationBlock mutation) -> new ScheduledMutation(mutation, 0L))
				.toList()
			);
			periodicMutationMillis.putAll(followUp.periodicMutationMillis());
		}
		
		// Create the height map.
		byte[][] rawHeight = HeightMapHelpers.createUniformHeightMap(CuboidHeightMap.UNKNOWN_HEIGHT);
		HeightMapHelpers.populateHeightMap(rawHeight, data);
		CuboidHeightMap heightMap = CuboidHeightMap.wrap(rawHeight);
		return new SuspendedCuboid<CuboidData>(data
				, heightMap
				, entities
				, mutations
				, periodicMutationMillis
		);
	}

	@Override
	public EntityLocation getDefaultSpawnLocation()
	{
		return new EntityLocation(0.0f, 0.0f, 0.0f);
	}


	private static void _fillPlane(CuboidData data, byte z, Block block)
	{
		short number = block.item().number();
		for (byte y = 0; y < 32; ++y)
		{
			for (byte x = 0; x < 32; ++x)
			{
				data.setData15(AspectRegistry.BLOCK, new BlockAddress(x, y, z), number);
			}
		}
	}
}
