package com.jeffdisher.october.worldgen;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.ColumnHeightMap;
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
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.PassiveEntity;
import com.jeffdisher.october.utils.Assert;
import com.jeffdisher.october.utils.CuboidGenerator;
import com.jeffdisher.october.utils.Encoding;


/**
 * A basic world generator which tries to build a random world just by averaging out random numbers selected based on
 * cuboid x/y addresses and a starting seed.
 * The general design includes the following key points:
 * -the world has a seed and cuboid coordinates are used to generate the "local" seed for each cuboid
 * -the local cuboid is used to determine a "biome vote", cuboid "centre", and "height vote" for each cuboid
 * -the biome votes of the 5x5 cuboid columns around the target are averaged to determine its biome (smoothes
 *  distribution).
 * -the height votes of the 3x3 cuboid columns around the target are averaged to determine its "centre height" (allows
 *  for variation without abrupt changes).
 * -the height value assigned for each block column is determined by averaging the 3 closest "centre heights" in the 3x3
 *  cuboid columns around it, weighted by how close they are to the block.
 * 
 * The general idea is that all the interest is in generating a reasonable height map for each block column as this
 * generator doesn't do anything too special with how it fills in the space underneath these or what it populates above
 * them.
 * 
 * In the future (once this can be play-tested with a more immersive interface), this will likely be replaced with a
 * more traditional noise-based generator.  For now, it allows for a smooth terrain which is at least not repetitive or
 * manually defined.
 */
public class BasicWorldGenerator implements IWorldGenerator
{
	public static final int WATER_Z_LEVEL = 0;
	public static final int STONE_PEAK_Z_LEVEL = 16;
	public static final int LAVA_Z_DEPTH = -200;

	public static final String[] COAL_NODE = new String[] {""
			+ "AA\n"
			+ "AA\n"
			, ""
			+ "AA\n"
			+ "AA\n"
	};
	public static final String[] COPPER_NODE = new String[] {""
			+ " R \n"
			+ "RRR\n"
			+ " R \n"
			, ""
			+ "RRR\n"
			+ "RRR\n"
			+ "RRR\n"
			, ""
			+ " R \n"
			+ "RRR\n"
			+ " R \n"
	};
	public static final String[] IRON_NODE = new String[] {""
			+ "III\n"
			+ "III\n"
			+ "III\n"
			, ""
			+ "III\n"
			+ "III\n"
			+ "III\n"
			, ""
			+ "III\n"
			+ "III\n"
			+ "III\n"
	};
	public static final String[] DIAMOND_NODE = new String[] {""
			+ "M \n"
			+ " M\n"
			, ""
			+ " M\n"
			+ "M \n"
	};

	// We generate ores in column segments, generating a certain number of ore nodes in the given segment.
	public static final int COAL1_NODES = 40;
	public static final int COAL1_MIN_Z = -30;
	public static final int COAL1_MAX_Z = 20;
	public static final int COAL2_NODES = 20;
	public static final int COAL2_MIN_Z = -80;
	public static final int COAL2_MAX_Z = -20;
	public static final int COPPER_NODES = 30;
	public static final int COPPER_MIN_Z = -30;
	public static final int COPPER_MAX_Z = 30;
	public static final int IRON1_NODES = 30;
	public static final int IRON1_MIN_Z = -100;
	public static final int IRON1_MAX_Z = -10;
	public static final int IRON2_NODES = 50;
	public static final int IRON2_MIN_Z = -200;
	public static final int IRON2_MAX_Z = -80;
	public static final int DIAMOND_NODES = 10;
	public static final int DIAMOND_MIN_Z = -200;
	public static final int DIAMOND_MAX_Z = -150;

	public static final int FOREST_TREE_COUNT = 18;
	public static final String[] BASIC_TREE = new String[] {""
			+ "   \n"
			+ " T \n"
			+ "   \n"
			, ""
			+ " E \n"
			+ "ETE\n"
			+ " E \n"
	};
	public static final int FIELD_WHEAT_COUNT = 4;
	public static final int FIELD_CARROT_COUNT = 3;
	public static final int HERD_SIZE = 5;
	public static final int RANDOM_FAUNA_DENOMINATOR = 20;
	public static final int CAVERN_LIMIT_RADIUS = 7;
	public static final int RANDOM_CAVERN_DENOMINATOR = 4;

	private final Environment _env;
	private final int _seed;
	private final Block _blockStone;
	private final Block _blockGrass;
	private final Block _blockDirt;
	private final Block _blockSoil;
	private final Block _blockSand;
	private final Block _blockWheatMature;
	private final Block _blockCarrotMature;
	private final Block _blockWaterSource;
	private final Block _blockBasalt;
	private final Block _blockLavaSource;
	private final EntityType _cow;
	private final Structure _coalNode;
	private final Structure _copperNode;
	private final Structure _ironNode;
	private final Structure _diamondNode;
	private final Structure _basicTree;
	private final StructureRegistry _structures;

	/**
	 * Creates the world generator.
	 * 
	 * @param env The environment.
	 * @param seed A base seed for the world generator.
	 */
	public BasicWorldGenerator(Environment env, int seed)
	{
		_env = env;
		_seed = seed;
		
		_blockStone = env.blocks.fromItem(env.items.getItemById("op.stone"));
		_blockGrass = env.blocks.fromItem(env.items.getItemById("op.grass"));
		_blockDirt = env.blocks.fromItem(env.items.getItemById("op.dirt"));
		_blockSoil = env.blocks.fromItem(env.items.getItemById("op.tilled_soil"));
		_blockSand = env.blocks.fromItem(env.items.getItemById("op.sand"));
		_blockWheatMature = env.blocks.fromItem(env.items.getItemById("op.wheat_mature"));
		_blockCarrotMature = env.blocks.fromItem(env.items.getItemById("op.carrot_mature"));
		_blockWaterSource = env.blocks.fromItem(env.items.getItemById("op.water_source"));
		_blockBasalt = env.blocks.fromItem(env.items.getItemById("op.basalt"));
		_blockLavaSource = env.blocks.fromItem(env.items.getItemById("op.lava_source"));
		
		_cow = env.creatures.getTypeById("op.cow");
		
		StructureLoader loader = new StructureLoader(StructureLoader.getBasicMapping(env.items, env.blocks));
		_coalNode = loader.loadFromStrings(COAL_NODE);
		_copperNode = loader.loadFromStrings(COPPER_NODE);
		_ironNode = loader.loadFromStrings(IRON_NODE);
		_diamondNode = loader.loadFromStrings(DIAMOND_NODE);
		_basicTree = loader.loadFromStrings(BASIC_TREE);
		
		// We will place the base of the nexus castle at a random location (based directly on seed), 500 blocks from the
		// world origin.
		CommonStructures structures = new CommonStructures(env);
		_structures = new StructureRegistry();
		Random random = new Random(_seed);
		float angle = 2.0f * (float)Math.PI * random.nextFloat();
		float distance = 500.0f;
		int x = (int)(distance * Math.cos(angle));
		int y = (int)(distance * Math.sin(angle));
		int height = 100;
		_structures.register(structures.nexusCastle, new AbsoluteLocation(x + CommonStructures.CASTLE_X, y + CommonStructures.CASTLE_Y, height + CommonStructures.CASTLE_Z), FacingDirection.NORTH);
		_structures.register(structures.distanceTower, new AbsoluteLocation(x + CommonStructures.TOWER_NORTH_X, y + CommonStructures.TOWER_NORTH_Y, height + CommonStructures.TOWER_Z), FacingDirection.NORTH);
		_structures.register(structures.distanceTower, new AbsoluteLocation(x + CommonStructures.TOWER_SOUTH_X, y + CommonStructures.TOWER_SOUTH_Y, height + CommonStructures.TOWER_Z), FacingDirection.SOUTH);
		_structures.register(structures.distanceTower, new AbsoluteLocation(x + CommonStructures.TOWER_EAST_X, y + CommonStructures.TOWER_EAST_Y, height + CommonStructures.TOWER_Z), FacingDirection.EAST);
		_structures.register(structures.distanceTower, new AbsoluteLocation(x + CommonStructures.TOWER_WEST_X, y + CommonStructures.TOWER_WEST_Y, height + CommonStructures.TOWER_Z), FacingDirection.WEST);
	}

	@Override
	public SuspendedCuboid<CuboidData> generateCuboid(CreatureIdAssigner creatureIdAssigner, CuboidAddress address, long gameTimeMillis)
	{
		// For now, we will just place dirt at the peak block in each column, stone below that, and either air or water sources above.
		PerColumnRandomSeedField seeds = PerColumnRandomSeedField.buildSeedField9x9(_seed, address.x(), address.y());
		PerColumnRandomSeedField.View subField = seeds.view();
		LazyColumnHeightMapGrid heightMaps = new LazyColumnHeightMapGrid(subField);
		
		// Generate the starting-point of the cuboid, containing only stone and empty (air/water/lava) blocks.
		CuboidData data = _generateStoneCrustCuboid(address, heightMaps);
		
		// Create caves.
		AbsoluteLocation cuboidBase = address.getBase();
		_carveOutCaves(data, cuboidBase);
		
		// Replace the top and bottom of the crust with the appropriate transition blocks.
		int cuboidZ = cuboidBase.z();
		_replaceCrustTopAndBottom(heightMaps, data, cuboidZ);
		
		// Generate the ore nodes and other structures (including trees).
		_generateOreNodesAndStructures(subField, heightMaps, address, data);
		
		// Generate any fixed structures.
		Structure.FollowUp followUp = _structures.generateAllInCuboid(data);
		List<ScheduledMutation> mutations = followUp.overwriteMutations().stream()
			.map((IMutationBlock mutation) -> new ScheduledMutation(mutation, 0L))
			.toList()
		;
		
		Map<BlockAddress, Long> periodicMutationMillis = followUp.periodicMutationMillis();
		
		// We have finished populating the cuboid so we can generate the cuboid-local height map.
		CuboidHeightMap cuboidLocalMap = HeightMapHelpers.buildHeightMap(data);
		
		// Spawn the creatures within the cuboid.
		List<CreatureEntity> entities = _spawnCreatures(creatureIdAssigner, subField, heightMaps.fetchHeightMapForCuboidColumn(0, 0), data, cuboidBase, gameTimeMillis);
		
		// No passives.
		List<PassiveEntity> passives = List.of();
		
		return new SuspendedCuboid<CuboidData>(data
				, cuboidLocalMap
				, entities
				, mutations
				, periodicMutationMillis
				, passives
		);
	}

	@Override
	public EntityLocation getDefaultSpawnLocation()
	{
		PerColumnRandomSeedField seeds = PerColumnRandomSeedField.buildSeedField9x9(_seed, (short)0, (short)0);
		LazyColumnHeightMapGrid heightMaps = new LazyColumnHeightMapGrid(seeds.view());
		ColumnHeightMap heightMap = heightMaps.fetchHeightMapForCuboidColumn(0, 0);
		// Find the largest value here and spawn there (note that this may not be in the zero-z cuboid).
		int maxZ = Integer.MIN_VALUE;
		int targetX = -1;
		int targetY = -1;
		for (int y = 0; y < Encoding.CUBOID_EDGE_SIZE; ++y)
		{
			for (int x = 0; x < Encoding.CUBOID_EDGE_SIZE; ++x)
			{
				int height = heightMap.getHeight(x, y);
				if (height > maxZ)
				{
					maxZ = height;
					targetX = x;
					targetY = y;
				}
			}
		}
		return new EntityLocation(targetX, targetY, (maxZ + 1));
	}

	/**
	 * Used by tests:  Returns the cuboid-local seed for the given cuboid X/Y address.
	 * 
	 * @param cuboidX The cuboid X address.
	 * @param cuboidY The cuboid Y address.
	 * @return The cuboid-local seed.
	 */
	public int test_getCuboidSeed(short cuboidX, short cuboidY)
	{
		PerColumnRandomSeedField seeds = PerColumnRandomSeedField.buildSeedField9x9(_seed, cuboidX, cuboidY);
		return seeds.get(0, 0);
	}

	/**
	 * Used by tests:  Returns the biome for the given cuboid X/Y address.
	 * 
	 * @param cuboidX The cuboid X address.
	 * @param cuboidY The cuboid Y address.
	 * @return The biome of this cuboid.
	 */
	public Biomes.Biome test_getBiome(short cuboidX, short cuboidY)
	{
		PerColumnRandomSeedField seeds = PerColumnRandomSeedField.buildSeedField9x9(_seed, cuboidX, cuboidY);
		PerColumnRandomSeedField.View subField = seeds.view();
		return Biomes.chooseBiomeFromSeeds5x5(subField);
	}

	/**
	 * Used by tests:  Returns the biome character code for the given cuboid X/Y address.  This is only useful for
	 * creating visual depictions of biome distribution.
	 * 
	 * @param cuboidX The cuboid X address.
	 * @param cuboidY The cuboid Y address.
	 * @return The biome code of this cuboid.
	 */
	public char test_getBiomeCode(short cuboidX, short cuboidY)
	{
		PerColumnRandomSeedField seeds = PerColumnRandomSeedField.buildSeedField9x9(_seed, cuboidX, cuboidY);
		PerColumnRandomSeedField.View subField = seeds.view();
		Biomes.Biome biome = Biomes.chooseBiomeFromSeeds5x5(subField);
		return biome.code();
	}

	/**
	 * Used by tests:  Populates the given data, at address, with expected  ore nodes.
	 * 
	 * @param address The cuboid address.
	 * @param data The cuboid data.
	 */
	public void test_generateOreNodes(CuboidAddress address, CuboidData data)
	{
		PerColumnRandomSeedField seeds = PerColumnRandomSeedField.buildSeedField9x9(_seed, address.x(), address.y());
		PerColumnRandomSeedField.View subField = seeds.view();
		LazyColumnHeightMapGrid heightMaps = new LazyColumnHeightMapGrid(subField);
		// (we ignore the updated height map)
		_generateOreNodesAndStructures(subField, heightMaps, address, data);
	}

	/**
	 * Used by tests:  Finds the depth of the gully in this cuboid (0 if there isn't one).
	 * 
	 * @param cuboidX The cuboid X address.
	 * @param cuboidY The cuboid Y address.
	 * @return The depth of the gully in this cuboid.
	 */
	public int test_getGullyDepth(short cuboidX, short cuboidY)
	{
		PerColumnRandomSeedField seeds = PerColumnRandomSeedField.buildSeedField9x9(_seed, cuboidX, cuboidY);
		LazyColumnHeightMapGrid heightMaps = new LazyColumnHeightMapGrid(seeds.view());
		ColumnHeightMap heightMap = heightMaps.fetchHeightMapForCuboidColumn(0, 0);
		return _findGully(heightMap);
	}

	/**
	 * Used by tests:  Modifies the given data by carving out caves which intersect it.
	 * 
	 * @param data The cuboid to modify.
	 */
	public void test_carveOutCaves(CuboidData data)
	{
		AbsoluteLocation cuboidBase = data.getCuboidAddress().getBase();
		_carveOutCaves(data, cuboidBase);
	}


	private void _generateOreNodesAndStructures(PerColumnRandomSeedField.View subField, LazyColumnHeightMapGrid heightMaps, CuboidAddress address, CuboidData data)
	{
		// Since the nodes can cross cuboid boundaries, we will consider the 9 chunk columns around this one and apply all generated nodes to this cuboid.
		// (in the future, we might short-circuit this to avoid cases where the generation isn't possibly here - for now, we always do it to test the code path)
		short airNumber = _env.special.AIR.item().number();
		for (int y = -1; y <= 1; ++y)
		{
			for (int x = -1; x <= 1; ++x)
			{
				CuboidAddress sideAddress = address.getRelative(x, y, 0);
				PerColumnRandomSeedField.View relField = subField.relativeView(x, y);
				int columnSeed = relField.get(0, 0);
				AbsoluteLocation sideBase = sideAddress.getBase();
				_applyOreNodes(data, columnSeed, sideBase.x(), sideBase.y(), COAL1_NODES, COAL1_MIN_Z, COAL1_MAX_Z, _coalNode);
				_applyOreNodes(data, columnSeed, sideBase.x(), sideBase.y(), COAL2_NODES, COAL2_MIN_Z, COAL2_MAX_Z, _coalNode);
				_applyOreNodes(data, columnSeed, sideBase.x(), sideBase.y(), COPPER_NODES, COPPER_MIN_Z, COPPER_MAX_Z, _copperNode);
				_applyOreNodes(data, columnSeed, sideBase.x(), sideBase.y(), IRON1_NODES, IRON1_MIN_Z, IRON1_MAX_Z, _ironNode);
				_applyOreNodes(data, columnSeed, sideBase.x(), sideBase.y(), IRON2_NODES, IRON2_MIN_Z, IRON2_MAX_Z, _ironNode);
				_applyOreNodes(data, columnSeed, sideBase.x(), sideBase.y(), DIAMOND_NODES, DIAMOND_MIN_Z, DIAMOND_MAX_Z, _diamondNode);
				
				// If this is a forest, also generate random trees.
				Biomes.Biome biome = Biomes.chooseBiomeFromSeeds5x5(relField);
				if (Biomes.FOREST_CODE == biome.code())
				{
					ColumnHeightMap heightMap = heightMaps.fetchHeightMapForCuboidColumn(x, y);
					Random random = new Random(columnSeed);
					for (int i = 0; i < FOREST_TREE_COUNT; ++i)
					{
						int relativeX = random.nextInt(Encoding.CUBOID_EDGE_SIZE);
						int relativeY = random.nextInt(Encoding.CUBOID_EDGE_SIZE);
						// We only generate here if the target block is at an elevation where dirt spawns.
						int dirtBlockZ = heightMap.getHeight(relativeX, relativeY);
						if (dirtBlockZ < STONE_PEAK_Z_LEVEL)
						{
							// Choose the block above the dirt.
							int absoluteZ = dirtBlockZ + 1;
							// The tree is a 3x3 structure with the tree in the middle so step back by one.
							AbsoluteLocation rootLocation = new AbsoluteLocation(sideBase.x() + relativeX - 1, sideBase.y() + relativeY - 1, absoluteZ);
							// Make sure that these are over dirt.
							// (re-add the +1 to reach the trunk)
							AbsoluteLocation dirtLocation = new AbsoluteLocation(rootLocation.x() + 1, rootLocation.y() + 1, rootLocation.z() - 1);
							// TODO:  To determine if this dirtLocation is _actually_ dirt, we would need to do a more
							// complete generation of these other cuboids.  As it stands, this could generate trees
							// floating over caves.
							if (dirtLocation.getCuboidAddress().equals(address))
							{
								// We want to convert this to dirt, if it is over grass.
								BlockAddress dirtBlock = dirtLocation.getBlockAddress();
								if (_blockGrass.item().number() == data.getData15(AspectRegistry.BLOCK, dirtBlock))
								{
									data.setData15(AspectRegistry.BLOCK, dirtBlock, _blockDirt.item().number());
								}
							}
							Structure.FollowUp followUp = _basicTree.applyToCuboid(data, rootLocation, FacingDirection.NORTH, airNumber);
							Assert.assertTrue(followUp.isEmpty());
						}
					}
				}
			}
		}
	}

	private void _applyOreNodes(CuboidData data, int columnSeed, int relativeBaseX, int relativeBaseY, int tries, int minZ, int maxZ, Structure node)
	{
		short stoneNumber = _blockStone.item().number();
		int range = maxZ - minZ;
		Random random = new Random(columnSeed);
		for (int i = 0; i < tries; ++i)
		{
			int relativeX = random.nextInt(Encoding.CUBOID_EDGE_SIZE);
			int relativeY = random.nextInt(Encoding.CUBOID_EDGE_SIZE);
			int absoluteZ = random.nextInt(range) + minZ;
			AbsoluteLocation rootLocation = new AbsoluteLocation(relativeBaseX + relativeX, relativeBaseY + relativeY, absoluteZ);
			Structure.FollowUp followUp = node.applyToCuboid(data, rootLocation, FacingDirection.NORTH, stoneNumber);
			Assert.assertTrue(followUp.isEmpty());
		}
	}

	private int _generateFlora(CuboidData data, AbsoluteLocation cuboidBase, int columnSeed, ColumnHeightMap heightMap, Biomes.Biome biome)
	{
		int herdSize = 0;
		if ((Biomes.FIELD_CODE == biome.code()) || (Biomes.MEADOW_CODE == biome.code()))
		{
			// We always plant these on dirt but need to replace any grass.
			short supportBlockToReplace = _blockGrass.item().number();
			short supportBlockToAdd = _blockSoil.item().number();
			// We only want to replace air (since this could be under water).
			short blockToReplace = _env.special.AIR.item().number();
			short blockToAdd = (Biomes.FIELD_CODE == biome.code())
					? _blockWheatMature.item().number()
					: _blockCarrotMature.item().number()
			;
			int cuboidBottomZ = cuboidBase.z();
			
			// We will generate wheat in a field biome in 2 ways:  A few random placements and a fully-saturated gully.
			// First, check the gully.
			boolean didFillGully = false;
			int gullyDepth = _findGully(heightMap);
			if (gullyDepth > 0)
			{
				// See if this applies to this cuboid.
				int gullyBase = _getLowestHeight(heightMap);
				// The "gullyBase" is the dirt, but we want to replace the block above that, filling in gullyDepth layers, so see if those are in range.
				int bottomLayerToChange = gullyBase + 1;
				int topLayerToChange = gullyBase + gullyDepth;
				int cuboidTopExclusive = cuboidBottomZ + Encoding.CUBOID_EDGE_SIZE;
				
				for (int z = bottomLayerToChange; z <= topLayerToChange; ++z)
				{
					if ((z >= cuboidBottomZ) && (z < cuboidTopExclusive))
					{
						_replaceLayer(data, (byte)(z - cuboidBottomZ), supportBlockToReplace, supportBlockToAdd, blockToReplace, blockToAdd);
						// We were able to generate something here.
						didFillGully = true;
					}
				}
			}
			
			// Now, inject a few random ones.
			Random random = new Random(columnSeed);
			int count = (Biomes.FIELD_CODE == biome.code())
					? FIELD_WHEAT_COUNT
					: FIELD_CARROT_COUNT
			;
			int randomPlantCount = 0;
			for (int i = 0; i < count; ++i)
			{
				int relativeX = random.nextInt(Encoding.CUBOID_EDGE_SIZE);
				int relativeY = random.nextInt(Encoding.CUBOID_EDGE_SIZE);
				// Choose the block above the grass (making the grass dirt).
				int relativeZ = heightMap.getHeight(relativeX, relativeY) - cuboidBottomZ + 1;
				if ((relativeZ >= 0) && (relativeZ < Encoding.CUBOID_EDGE_SIZE))
				{
					BlockAddress address = BlockAddress.fromInt(relativeX, relativeY, relativeZ);
					short original = data.getData15(AspectRegistry.BLOCK, address);
					if (blockToReplace == original)
					{
						// Make sure that these are over grass.
						BlockAddress underBlock = address.getRelativeInt(0, 0, -1);
						if (supportBlockToReplace == data.getData15(AspectRegistry.BLOCK, underBlock))
						{
							data.setData15(AspectRegistry.BLOCK, underBlock, supportBlockToAdd);
							data.setData15(AspectRegistry.BLOCK, address, blockToAdd);
							randomPlantCount += 1;
						}
					}
				}
			}
			
			// If this is a gully, we want to spawn a herd of the native fauna.
			if (didFillGully)
			{
				herdSize = HERD_SIZE;
			}
			else
			{
				// We want to take a random spawn chance for each plant.
				for (int i = 0; i < randomPlantCount; ++i)
				{
					if (0 == random.nextInt(RANDOM_FAUNA_DENOMINATOR))
					{
						herdSize += 1;
					}
				}
			}
		}
		return herdSize;
	}

	private int _findGully(ColumnHeightMap heightMap)
	{
		// A gully is a point in the cuboid lower than the perimeter of the cuboid.
		int minGully = Integer.MAX_VALUE;
		int minPerimeter = Integer.MAX_VALUE;
		int edge = Encoding.CUBOID_EDGE_SIZE - 1;
		for (int y = 0; y < Encoding.CUBOID_EDGE_SIZE; ++y)
		{
			for (int x = 0; x < Encoding.CUBOID_EDGE_SIZE; ++x)
			{
				int height = heightMap.getHeight(x, y);
				minGully = Math.min(minGully, height);
				if ((0 == y) || (edge == y) || (0 == x) || (edge == x))
				{
					// This is the perimeter.
					minPerimeter = Math.min(minPerimeter, height);
				}
			}
		}
		return minPerimeter - minGully;
	}

	private int _getLowestHeight(ColumnHeightMap heightMap)
	{
		int min = Integer.MAX_VALUE;
		for (int y = 0; y < Encoding.CUBOID_EDGE_SIZE; ++y)
		{
			for (int x = 0; x < Encoding.CUBOID_EDGE_SIZE; ++x)
			{
				int height = heightMap.getHeight(x, y);
				min = Math.min(min, height);
			}
		}
		return min;
	}

	private void _replaceLayer(CuboidData data, byte z, short supportBlockToReplace, short supportBlockToAdd, short blockToReplace, short blockToAdd)
	{
		for (byte y = 0; y < Encoding.CUBOID_EDGE_SIZE; ++y)
		{
			for (byte x = 0; x < Encoding.CUBOID_EDGE_SIZE; ++x)
			{
				BlockAddress address = new BlockAddress(x, y, z);
				short original = data.getData15(AspectRegistry.BLOCK, address);
				short underBlock = 0;
				if (z > 0)
				{
					// TODO:  We currently can't check the block below the bottom of the cuboid so we will default to not placing there.
					BlockAddress supportAddress = address.getRelative((byte)0, (byte)0, (byte)-1);
					underBlock = data.getData15(AspectRegistry.BLOCK, supportAddress);
					if (supportBlockToReplace == underBlock)
					{
						data.setData15(AspectRegistry.BLOCK, supportAddress, supportBlockToAdd);
						underBlock = supportBlockToAdd;
					}
				}
				if ((blockToReplace == original) && (supportBlockToAdd == underBlock))
				{
					data.setData15(AspectRegistry.BLOCK, address, blockToAdd);
				}
			}
		}
	}

	private int _getAverageHeightInColumn(ColumnHeightMap heightMap)
	{
		int minHeight = 0;
		int maxHeight = Integer.MAX_VALUE;
		int totalHeight = 0;
		for (int y = 0; y < Encoding.CUBOID_EDGE_SIZE; ++y)
		{
			for (int x = 0; x < Encoding.CUBOID_EDGE_SIZE; ++x)
			{
				int height = heightMap.getHeight(x, y);
				minHeight = Math.min(minHeight, height);
				maxHeight = Math.max(maxHeight, height);
				totalHeight += height;
			}
		}
		int count = Encoding.CUBOID_EDGE_SIZE * Encoding.CUBOID_EDGE_SIZE;
		int averageHeight = totalHeight / count;
		return averageHeight;
	}

	private CuboidData _generateStoneCrustCuboid(CuboidAddress address, LazyColumnHeightMapGrid heightMaps)
	{
		ColumnHeightMap heightMap = heightMaps.fetchHeightMapForCuboidColumn(0, 0);
		int averageHeight = _getAverageHeightInColumn(heightMap);
		int cuboidZ = address.getBase().z();
		
		// Determine how to start this based on the height compared to this z.
		Block defaultEmptyBlock;
		if (cuboidZ >= WATER_Z_LEVEL)
		{
			// Air.
			defaultEmptyBlock = _env.special.AIR;
		}
		else
		{
			// Water.
			defaultEmptyBlock = _blockWaterSource;
		}
		
		Block defaultBlock;
		if (averageHeight >= cuboidZ)
		{
			// This cuboid is either underground or intersecting with the surface so start with stone.
			defaultBlock = _blockStone;
		}
		else
		{
			// This cuboid is at least mostly above the surface so fill with either air or water.
			defaultBlock = defaultEmptyBlock;
		}
		CuboidData data = CuboidGenerator.createFilledCuboid(address, defaultBlock);
		
		// Now, walk the height map and make any required adjustments so that we end up with only empty (air/water) blocks and stone crust.
		_finishShapingStoneCrust(heightMap, data, cuboidZ, defaultEmptyBlock, defaultBlock);
		return data;
	}

	private void _finishShapingStoneCrust(ColumnHeightMap heightMap, CuboidData data, int cuboidZ, Block defaultEmptyBlock, Block defaultBlock)
	{
		// This function carves the block from a uniform default into a formed crust of only stone.
		// Verify that the default blocks are the cases we are assuming.
		Assert.assertTrue((_blockWaterSource == defaultEmptyBlock) || (_env.special.AIR == defaultEmptyBlock));
		Assert.assertTrue((_blockStone == defaultBlock) || (_blockWaterSource == defaultBlock) || (_env.special.AIR == defaultBlock));
		
		for (int y = 0; y < Encoding.CUBOID_EDGE_SIZE; ++y)
		{
			for (int x = 0; x < Encoding.CUBOID_EDGE_SIZE; ++x)
			{
				int height = heightMap.getHeight(x, y);
				// Note that the mantle height will be basalt and everything below will be lava.
				int mantleHeight = height + LAVA_Z_DEPTH;
				for (int z = 0; z < Encoding.CUBOID_EDGE_SIZE; ++z)
				{
					int thisZ = cuboidZ + z;
					Block blockToWrite;
					if (thisZ > height)
					{
						// This is above the crust so use whatever the empty block should be.
						blockToWrite = defaultEmptyBlock;
					}
					else if (thisZ < mantleHeight)
					{
						// The mantle is never the default block, and is the same everywhere, so we just apply lava.
						blockToWrite = _blockLavaSource;
					}
					else
					{
						// This is the crust layer, so replace this with stone.
						blockToWrite = _blockStone;
					}
					if (blockToWrite != defaultBlock)
					{
						data.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(x, y, z), blockToWrite.item().number());
					}
				}
			}
		}
	}

	private void _replaceCrustTopAndBottom(LazyColumnHeightMapGrid heightMaps, CuboidData data, int cuboidZ)
	{
		// This function replaces the top block and bottom block of the crust with the appropriate block.
		// It assumes that the crust is completely made of stone and will only replace stone (meaning gaps or special blocks will NOT be replaced).
		short stoneValue = _blockStone.item().number();
		ColumnHeightMap heightMap = heightMaps.fetchHeightMapForCuboidColumn(0, 0);
		for (int y = 0; y < Encoding.CUBOID_EDGE_SIZE; ++y)
		{
			for (int x = 0; x < Encoding.CUBOID_EDGE_SIZE; ++x)
			{
				int height = heightMap.getHeight(x, y);
				// Note that the mantle height will be basalt and everything below will be lava.
				int mantleHeight = height + LAVA_Z_DEPTH;
				for (int z = 0; z < Encoding.CUBOID_EDGE_SIZE; ++z)
				{
					int thisZ = cuboidZ + z;
					Block blockToWrite;
					
					if (thisZ == height)
					{
						// This is the top of the crust so see what kind of regolith to use.
						// We want to make sure that peaks generate without dirt on them so we apply this extra check.
						// We put dirt under water but grass blocks on the rest of the surface.
						if (thisZ >= STONE_PEAK_Z_LEVEL)
						{
							// Stone peak.
							blockToWrite = _blockStone;
						}
						else if (thisZ < (WATER_Z_LEVEL - 1))
						{
							// Sand is under water.
							blockToWrite = _blockSand;
						}
						else if (thisZ == (WATER_Z_LEVEL - 1))
						{
							// This _is_ the level where the water surface blocks generate so make it sand if there is water nearby.
							if (_isAdjacentHeightLess(thisZ, heightMaps, x, y))
							{
								// An adjacent block is below thisZ so we assume it is water, meaning this should be sand.
								blockToWrite = _blockSand;
							}
							else
							{
								// No nearby water so assume that this is grass.
								blockToWrite = _blockGrass;
							}
						}
						else
						{
							// Grass surface.
							blockToWrite = _blockGrass;
						}
					}
					else if (thisZ == mantleHeight)
					{
						// The mantle barrier, so this is basalt.
						blockToWrite = _blockBasalt;
					}
					else
					{
						// We don't care about the other cases.
						blockToWrite = null;
					}
					
					// Make sure that we have something to write, is not stone, and it is actually changing the value.
					if ((null != blockToWrite) && (_blockStone != blockToWrite))
					{
						BlockAddress blockAddress = BlockAddress.fromInt(x, y, z);
						short blockValue = blockToWrite.item().number();
						short existingValue = data.getData15(AspectRegistry.BLOCK, blockAddress);
						// We only want to write this if it changes something and there is stone to overwrite, already (otherwise, it is probably a cave).
						if ((blockValue != existingValue) && (stoneValue == existingValue))
						{
							data.setData15(AspectRegistry.BLOCK, blockAddress, blockValue);
						}
					}
				}
			}
		}
	}

	private List<CreatureEntity> _spawnCreatures(CreatureIdAssigner creatureIdAssigner, PerColumnRandomSeedField.View subField, ColumnHeightMap heightMap, CuboidData data, AbsoluteLocation cuboidBase, long gameTimeMillis)
	{
		// We want to spawn the flora.  This is only ever done within a single cuboid column if it is the appropriate biome type and contains a "gully".
		int columnSeed = subField.get(0, 0);
		Biomes.Biome biome = Biomes.chooseBiomeFromSeeds5x5(subField);
		int herdSizeToSpawn = _generateFlora(data, cuboidBase, columnSeed, heightMap, biome);
		EntityType faunaType = (Biomes.FIELD_CODE == biome.code())
				? _cow
				: null
		;
		
		// Spawn any creatures associated with this cuboid.
		List<CreatureEntity> entities = new ArrayList<>();
		if ((null != faunaType) && (herdSizeToSpawn > 0))
		{
			// We don't often do herd spawning so we will try 5 times in random locations on the surface.
			Random random = new Random(columnSeed);
			for (int i = 0; i < herdSizeToSpawn; ++i)
			{
				int relativeX = random.nextInt(Encoding.CUBOID_EDGE_SIZE);
				int relativeY = random.nextInt(Encoding.CUBOID_EDGE_SIZE);
				// Choose the block above the dirt.
				int relativeZ = heightMap.getHeight(relativeX, relativeY) - cuboidBase.z() + 1;
				entities.add(CreatureEntity.create(creatureIdAssigner.next()
						, faunaType
						, cuboidBase.getRelative(relativeX, relativeY, relativeZ).toEntityLocation()
						, gameTimeMillis
				));
			}
		}
		return entities;
	}

	private void _carveOutCaves(CuboidData data, AbsoluteLocation cuboidBase)
	{
		// We generate the cave system by checking whether or not a cavern should be generated in this cuboid and each of the 26 around it.
		_Cavern[][][] caverns = new _Cavern[5][5][5];
		CuboidAddress address = data.getCuboidAddress();
		for (int z = -1; z <= 1; ++z)
		{
			for (int y = -1; y <= 1; ++y)
			{
				for (int x = -1; x <= 1; ++x)
				{
					long seed = _getSeedForCuboid(address.getRelative(x, y, z));
					Random random = new Random(seed);
					if (0 == random.nextInt(RANDOM_CAVERN_DENOMINATOR))
					{
						byte cavernX = (byte) random.nextInt(Encoding.CUBOID_EDGE_SIZE);
						byte cavernY = (byte) random.nextInt(Encoding.CUBOID_EDGE_SIZE);
						byte cavernZ = (byte) random.nextInt(Encoding.CUBOID_EDGE_SIZE);
						byte cavernRadius = (byte) random.nextInt(CAVERN_LIMIT_RADIUS);
						caverns[2 + z][2 + y][2 + x] = new _Cavern(cavernX, cavernY, cavernZ, cavernRadius);
					}
				}
			}
		}
		
		// We then connect those caverns to adjacent caverns in the 6 adjacent cuboids, if they exist.
		short stoneNumber = _blockStone.item().number();
		short airNumber = _env.special.AIR.item().number();
		for (int z = -1; z <= 1; ++z)
		{
			for (int y = -1; y <= 1; ++y)
			{
				for (int x = -1; x <= 1; ++x)
				{
					_Cavern cavern = caverns[2 + z][2 + y][2 + x];
					if (null != cavern)
					{
						CuboidAddress thisAddress = address.getRelative(x, y, z);
						AbsoluteLocation centre = thisAddress.getBase().getRelative(cavern.x, cavern.y, cavern.z);
						int radius = cavern.radius;
						Assert.assertTrue(centre.getCuboidAddress().equals(thisAddress));
						PathDigger.hollowOutSphere(data, centre, radius, stoneNumber, airNumber);
						_hollowPath(data, caverns, address, stoneNumber, airNumber, z + 1, y, x, centre, radius);
						_hollowPath(data, caverns, address, stoneNumber, airNumber, z - 1, y, x, centre, radius);
						_hollowPath(data, caverns, address, stoneNumber, airNumber, z, y + 1, x, centre, radius);
						_hollowPath(data, caverns, address, stoneNumber, airNumber, z, y - 1, x, centre, radius);
						_hollowPath(data, caverns, address, stoneNumber, airNumber, z, y, x + 1, centre, radius);
						_hollowPath(data, caverns, address, stoneNumber, airNumber, z, y, x - 1, centre, radius);
					}
				}
			}
		}
	}

	private static void _hollowPath(CuboidData data
			, _Cavern[][][] caverns
			, CuboidAddress address
			, short stoneNumber
			, short airNumber
			, int z
			, int y
			, int x
			, AbsoluteLocation centre
			, int radius
	)
	{
		_Cavern one = caverns[2 + z][2 + y][2 + x];
		if (null != one)
		{
			CuboidAddress oneAddress = address.getRelative(x, y, z);
			AbsoluteLocation oneCentre = oneAddress.getBase().getRelative(one.x, one.y, one.z);
			int oneRadius = one.radius;
			PathDigger.hollowOutPath(data, centre, radius, oneCentre, oneRadius, stoneNumber, airNumber);
		}
	}

	private static long _getSeedForCuboid(CuboidAddress address)
	{
		// This logic is loosely derived from PerColumnRandomSeedField._deterministicRandom.
		ByteBuffer buffer = ByteBuffer.allocate(3 * Short.BYTES + 2 * Integer.BYTES);
		short x = address.x();
		short y = address.y();
		short z = address.z();
		buffer.putShort(z);
		buffer.putShort(y);
		buffer.putShort(x);
		buffer.putInt(x ^ y ^ z);
		buffer.putInt(x + y + z);
		return Arrays.hashCode(buffer.array());
	}

	private static boolean _isAdjacentHeightLess(int compareZ, LazyColumnHeightMapGrid heightMaps, int localX, int localY)
	{
		// We want to check all 8 blocks around this and see if they are less than compareZ.
		boolean isLess = false;
		for (int y = -1; !isLess && (y <= 1); ++y)
		{
			for (int x = -1; !isLess && (x <= 1); ++x)
			{
				if ((0 != y) || (0 != x))
				{
					int oneX = localX + x;
					int oneY = localY + y;
					int cuboidX = 0;
					int cuboidY = 0;
					if (oneX >= Encoding.CUBOID_EDGE_SIZE)
					{
						cuboidX = 1;
						oneX -= Encoding.CUBOID_EDGE_SIZE;
					}
					else if (oneX < 0)
					{
						cuboidX = -1;
						oneX += Encoding.CUBOID_EDGE_SIZE;
					}
					if (oneY >= Encoding.CUBOID_EDGE_SIZE)
					{
						cuboidY = 1;
						oneY -= Encoding.CUBOID_EDGE_SIZE;
					}
					else if (oneY < 0)
					{
						cuboidY = -1;
						oneY += Encoding.CUBOID_EDGE_SIZE;
					}
					ColumnHeightMap map = heightMaps.fetchHeightMapForCuboidColumn(cuboidX, cuboidY);
					int height = map.getHeight(oneX, oneY);
					isLess = (height < compareZ);
				}
			}
		}
		return isLess;
	}


	private static record _Cavern(byte x
			, byte y
			, byte z
			, byte radius
	)
	{}
}
