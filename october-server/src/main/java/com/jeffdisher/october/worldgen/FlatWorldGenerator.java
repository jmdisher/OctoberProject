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
	public static final AbsoluteLocation BASE = new AbsoluteLocation(CommonStructures.CASTLE_X, CommonStructures.CASTLE_Y, CommonStructures.CASTLE_Z);
	public static final AbsoluteLocation PORTAL_NORTH = new AbsoluteLocation(CommonStructures.TOWER_NORTH_X, CommonStructures.TOWER_NORTH_Y, CommonStructures.TOWER_Z);
	public static final AbsoluteLocation PORTAL_SOUTH = new AbsoluteLocation(CommonStructures.TOWER_SOUTH_X, CommonStructures.TOWER_SOUTH_Y, CommonStructures.TOWER_Z);
	public static final AbsoluteLocation PORTAL_EAST = new AbsoluteLocation(CommonStructures.TOWER_EAST_X, CommonStructures.TOWER_EAST_Y, CommonStructures.TOWER_Z);
	public static final AbsoluteLocation PORTAL_WEST = new AbsoluteLocation(CommonStructures.TOWER_WEST_X, CommonStructures.TOWER_WEST_Y, CommonStructures.TOWER_Z);

	private final Environment _env;
	private final Block _stoneBlock;
	private final Block _dirtBlock;
	private final Block _logBlock;
	private final Block _coalOreBlock;
	private final Block _ironOreBlock;
	private final Block _waterSourceBlock;
	private final EntityType _cow;

	private final boolean _shouldGenerateStructures;
	private final StructureRegistry _structures;

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
		CommonStructures structures = new CommonStructures(env);
		_structures = new StructureRegistry();
		_structures.register(structures.nexusCastle, BASE, OrientationAspect.Direction.NORTH);
		_structures.register(structures.distanceTower, PORTAL_NORTH, OrientationAspect.Direction.NORTH);
		_structures.register(structures.distanceTower, PORTAL_SOUTH, OrientationAspect.Direction.SOUTH);
		_structures.register(structures.distanceTower, PORTAL_EAST, OrientationAspect.Direction.EAST);
		_structures.register(structures.distanceTower, PORTAL_WEST, OrientationAspect.Direction.WEST);
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
		if (_shouldGenerateStructures
			&& ((-1 == address.x()) || (0 == address.x()))
			&& ((-1 == address.y()) || (0 == address.y()))
			&& ((-1 == address.z()) || (0 == address.z()))
		)
		{
			// Our structures don't have entities.
			entities = List.of();
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
		List<ScheduledMutation> mutations = new ArrayList<>();
		Map<BlockAddress, Long> periodicMutationMillis = new HashMap<>();
		if (_shouldGenerateStructures)
		{
			Structure.FollowUp followUp = _structures.generateAllInCuboid(data);
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
