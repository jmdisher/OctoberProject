package com.jeffdisher.october.worldgen;

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
	// We want to generate a basic starting structure around the world centre.  For now, we just build that from a static string.
	public static final String[] STRUCTURE = new String[] {""
			+ "DDOOOODD\n"
			+ "DBBBBBBD\n"
			+ "OBWBBWBO\n"
			+ "OBBBBBBO\n"
			+ "OBBBBBBO\n"
			+ "OBWBBWBO\n"
			+ "DBBBBBBD\n"
			+ "DDOOOODD\n"
			, ""
			+ "S CCCC S\n"
			+ " L    L \n"
			+ "P      P\n"
			+ "P      P\n"
			+ "P      P\n"
			+ "P      P\n"
			+ " L    L \n"
			+ "S CCCC S\n"
	};
	public static final AbsoluteLocation BASE = new AbsoluteLocation(-4, -4, -1);

	private final boolean _shouldGenerateStructures;

	/**
	 * Creates the world generator, configured with options.
	 * 
	 * @param shouldGenerateStructures True if generated structures should be added, false if not.
	 */
	public FlatWorldGenerator(boolean shouldGenerateStructures)
	{
		_shouldGenerateStructures = shouldGenerateStructures;
	}

	@Override
	public SuspendedCuboid<CuboidData> generateCuboid(CreatureIdAssigner creatureIdAssigner, CuboidAddress address)
	{
		Environment env = Environment.getShared();
		// We will store the block types in the negative z blocks, but leave the non-negative blocks full or air.
		CuboidData data;
		// The height map will have a single value until we consider adding structures.
		byte heightMapValue;
		if (address.z() < (short)0)
		{
			data = CuboidGenerator.createFilledCuboid(address, env.blocks.fromItem(env.items.getItemById("op.stone")));
			_fillPlane(data, (byte)31, env.blocks.fromItem(env.items.getItemById("op.dirt")));
			_fillPlane(data, (byte)29, env.blocks.fromItem(env.items.getItemById("op.log")));
			_fillPlane(data, (byte)27, env.blocks.fromItem(env.items.getItemById("op.coal_ore")));
			_fillPlane(data, (byte)25, env.blocks.fromItem(env.items.getItemById("op.iron_ore")));
			// We want to add a bit of water.
			Block waterSource = env.blocks.fromItem(env.items.getItemById("op.water_source"));
			data.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(6, 6, 31), waterSource.item().number());
			data.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(7, 7, 31), waterSource.item().number());
			heightMapValue = 31;
		}
		else
		{
			data = CuboidGenerator.createFilledCuboid(address, env.blocks.fromItem(env.items.getItemById("op.air")));
			heightMapValue = CuboidHeightMap.UNKNOWN_HEIGHT;
		}
		
		// Fill the height map based on this initial value (we might modify it with structure generation later).
		byte[][] rawHeight = HeightMapHelpers.createUniformHeightMap(heightMapValue);
		
		// See if this is a cuboid where we want to generate our structure (it is in the 8 cuboids around the origin).
		List<CreatureEntity> entities;
		List<ScheduledMutation> mutations;
		Map<BlockAddress, Long> periodicMutationMillis;
		if (_shouldGenerateStructures
				&& ((-1 == address.x()) || (0 == address.x()))
				&& ((-1 == address.y()) || (0 == address.y()))
				&& ((-1 == address.z()) || (0 == address.z()))
		)
		{
			// Our structures don't have entities.
			entities = List.of();
			
			StructureLoader loader = new StructureLoader(StructureLoader.getBasicMapping(env.items, env.blocks));
			Structure structure = loader.loadFromStrings(STRUCTURE);
			int baseX = (0 == address.x()) ? BASE.x() : (32 + BASE.x());
			int baseY = (0 == address.y()) ? BASE.y() : (32 + BASE.y());
			int baseZ = (0 == address.z()) ? BASE.z() : (32 + BASE.z());
			AbsoluteLocation rootLocation = address.getBase().getRelative(baseX, baseY, baseZ);
			Structure.FollowUp followUp = structure.applyToCuboid(data, rootLocation, OrientationAspect.Direction.NORTH, Structure.REPLACE_ALL);
			mutations = followUp.overwriteMutations().stream()
					.map((IMutationBlock mutation) -> new ScheduledMutation(mutation, 0L))
					.toList()
			;
			periodicMutationMillis = followUp.periodicMutationMillis();
			
			// Walk the octree structure to update the height map.
			HeightMapHelpers.populateHeightMap(rawHeight, data);
		}
		else
		{
			// Load in 2 cows near the base of this cuboid if it is at z=0.
			EntityType cow = env.creatures.getTypeById("op.cow");
			AbsoluteLocation baseOfCuboid = address.getBase();
			entities = (0 == address.z())
					? List.of(CreatureEntity.create(creatureIdAssigner.next()
							, cow
							, baseOfCuboid.toEntityLocation()
							, (byte)100
						), CreatureEntity.create(creatureIdAssigner.next()
							, cow
							, baseOfCuboid.getRelative(5, 5, 0).toEntityLocation()
							, (byte)100
					))
					: List.of()
			;
			mutations = List.of();
			periodicMutationMillis = Map.of();
		}
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
