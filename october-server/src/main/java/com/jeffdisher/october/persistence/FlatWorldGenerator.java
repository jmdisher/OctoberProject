package com.jeffdisher.october.persistence;

import java.util.List;
import java.util.function.BiFunction;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.logic.ScheduledMutation;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.worldgen.CuboidGenerator;
import com.jeffdisher.october.worldgen.Structure;
import com.jeffdisher.october.worldgen.StructureLoader;


/**
 * A relatively simple world generator, designed to include the basic block types supported.
 * We also drop other miscellaneous items to make testing easier in the 0,0,0 cuboid.
 */
public class FlatWorldGenerator implements BiFunction<CreatureIdAssigner, CuboidAddress, SuspendedCuboid<CuboidData>> 
{
	// We want to generate a basic starting structure around the world centre.  For now, we just build that from a static string.
	public static final String[] STRUCTURE = new String[] {""
			+ "DDDDDDDD\n"
			+ "DBBBBBBD\n"
			+ "DBWBBWBD\n"
			+ "DBBBBBBD\n"
			+ "DBBBBBBD\n"
			+ "DBWBBWBD\n"
			+ "DBBBBBBD\n"
			+ "DDDDDDDD\n"
			, ""
			+ "S PPPP S\n"
			+ " L    L \n"
			+ "P      P\n"
			+ "P      P\n"
			+ "P      P\n"
			+ "P      P\n"
			+ " L    L \n"
			+ "S PPPP S\n"
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
	public SuspendedCuboid<CuboidData> apply(CreatureIdAssigner creatureIdAssigner, CuboidAddress address)
	{
		Environment env = Environment.getShared();
		// We will store the block types in the negative z blocks, but leave the non-negative blocks full or air.
		CuboidData data;
		if (address.z() < (short)0)
		{
			data = CuboidGenerator.createFilledCuboid(address, env.blocks.fromItem(env.items.getItemById("op.stone")));
			_fillPlane(data, (byte)31, env.blocks.fromItem(env.items.getItemById("op.dirt")));
			_fillPlane(data, (byte)29, env.blocks.fromItem(env.items.getItemById("op.log")));
			_fillPlane(data, (byte)27, env.blocks.fromItem(env.items.getItemById("op.coal_ore")));
			_fillPlane(data, (byte)25, env.blocks.fromItem(env.items.getItemById("op.iron_ore")));
			// We want to add a bit of water.
			Block waterSource = env.blocks.fromItem(env.items.getItemById("op.water_source"));
			data.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)6, (byte)6, (byte)31), waterSource.item().number());
			data.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)7, (byte)7, (byte)31), waterSource.item().number());
		}
		else
		{
			data = CuboidGenerator.createFilledCuboid(address, env.blocks.fromItem(env.items.getItemById("op.air")));
		}
		// See if this is a cuboid where we want to generate our structure (it is in the 8 cuboids around the origin).
		List<CreatureEntity> entities;
		List<ScheduledMutation> mutations;
		if (_shouldGenerateStructures
				&& ((-1 == address.x()) || (0 == address.x()))
				&& ((-1 == address.y()) || (0 == address.y()))
				&& ((-1 == address.z()) || (0 == address.z()))
		)
		{
			// Our structures don't have entities.
			entities = List.of();
			
			StructureLoader loader = new StructureLoader(env.items, env.blocks);
			Structure structure = loader.loadFromStrings(STRUCTURE);
			AbsoluteLocation baseOffset = new AbsoluteLocation(
					(0 == address.x()) ? BASE.x() : (32 + BASE.x()),
					(0 == address.y()) ? BASE.y() : (32 + BASE.y()),
					(0 == address.z()) ? BASE.z() : (32 + BASE.z())
			);
			List<IMutationBlock> immediateMutations = structure.applyToCuboid(data, baseOffset);
			mutations = immediateMutations.stream()
					.map((IMutationBlock mutation) -> new ScheduledMutation(mutation, 0L))
					.toList()
			;
		}
		else
		{
			// We will load a single entity in the base of this cuboid if it is z=0.
			AbsoluteLocation baseOfCuboid = address.getBase();
			entities = (0 == address.z())
					? List.of(CreatureEntity.create(creatureIdAssigner.next()
							, EntityType.COW
							, new EntityLocation(baseOfCuboid.x(), baseOfCuboid.y(), baseOfCuboid.z())
							, (byte)100
					))
					: List.of()
			;
			mutations = List.of();
		}
		return new SuspendedCuboid<CuboidData>(data
				, entities
				, mutations
		);
	}

	private static void _fillPlane(CuboidData data, byte z, Block block)
	{
		short number = block.item().number();
		for (int y = 0; y < 32; ++y)
		{
			for (int x = 0; x < 32; ++x)
			{
				data.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)x, (byte)y, z), number);
			}
		}
	}
}
