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
import com.jeffdisher.october.persistence.SuspendedCuboid;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
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
	public static final int MASK_HEIGHT  = 0x00000F00;
	public static final int SHIFT_HEIGHT  = 8;
	public static final int MASK_BIOME   = 0x0000F000;
	public static final int SHIFT_BIOME   = 12;
	public static final int MASK_YCENTRE = 0x001F0000;
	public static final int SHIFT_YCENTRE = 16;
	public static final int MASK_XCENTRE = 0x03E00000;
	public static final int SHIFT_XCENTRE = 21;
	public static final int WATER_Z_LEVEL = 0;
	public static final int STONE_PEAK_Z_LEVEL = 16;
	public static final int LAVA_Z_DEPTH = -100;

	public static final char FOREST_CODE = 'R';
	public static final char FIELD_CODE = 'F';
	public static final char MEADOW_CODE = 'E';
	public static final _Biome[] BIOMES = {
			new _Biome("Deep Ocean 2"
					, 'D'
					, -200
			),
			new _Biome("Deep Ocean"
					, 'D'
					, -100
			),
			new _Biome("Ocean 2"
					, 'O'
					, -50
			),
			new _Biome("Ocean"
					, 'O'
					, -20
			),
			new _Biome("Coast 2"
					, 'C'
					, -10
			),
			new _Biome("Coast"
					, 'C'
					, -10
			),
			new _Biome("Field"
					, FIELD_CODE
					, 0
			),
			new _Biome("Meadow"
					, MEADOW_CODE
					, 0
			),
			new _Biome("Forest"
					, FOREST_CODE
					, 0
			),
			new _Biome("Swamp"
					, 'S'
					, 0
			),
			new _Biome("Foothills"
					, 'h'
					, 10
			),
			new _Biome("Foothills 2"
					, 'h'
					, 10
			),
			new _Biome("Hills"
					, 'H'
					, 20
			),
			new _Biome("Hills 2"
					, 'H'
					, 50
			),
			new _Biome("Mountain"
					, 'M'
					, 100
			),
			new _Biome("Mountain 2"
					, 'M'
					, 200
			),
	};

	public static final int COAL_NODES_PER_CUBOID_COLUMN = 10;
	public static final int COAL_MIN_Z = -50;
	public static final int COAL_MAX_Z = 20;
	public static final String[] COAL_NODE = new String[] {""
			+ "AA\n"
			+ "AA\n"
			, ""
			+ "AA\n"
			+ "AA\n"
	};
	public static final int IRON_NODES_PER_CUBOID_COLUMN = 10;
	public static final int IRON_MIN_Z = -100;
	public static final int IRON_MAX_Z = -10;
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
	public static final int IRON_EXTRA_MIN = 1;
	public static final int IRON_EXTRA_MAX = 100;
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
	private final Block _blockWheatMature;
	private final Block _blockCarrotMature;
	private final Block _blockIronOre;
	private final Block _blockWaterSource;
	private final Block _blockBasalt;
	private final Block _blockLavaSource;
	private final EntityType _cow;
	private final Structure _coalNode;
	private final Structure _ironNode;
	private final Structure _basicTree;

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
		_blockWheatMature = env.blocks.fromItem(env.items.getItemById("op.wheat_mature"));
		_blockCarrotMature = env.blocks.fromItem(env.items.getItemById("op.carrot_mature"));
		_blockIronOre = env.blocks.fromItem(env.items.getItemById("op.iron_ore"));
		_blockWaterSource = env.blocks.fromItem(env.items.getItemById("op.water_source"));
		_blockBasalt = env.blocks.fromItem(env.items.getItemById("op.basalt"));
		_blockLavaSource = env.blocks.fromItem(env.items.getItemById("op.lava_source"));
		
		_cow = env.creatures.getTypeById("op.cow");
		
		StructureLoader loader = new StructureLoader(env.items, env.blocks);
		_coalNode = loader.loadFromStrings(COAL_NODE);
		_ironNode = loader.loadFromStrings(IRON_NODE);
		_basicTree = loader.loadFromStrings(BASIC_TREE);
	}

	@Override
	public SuspendedCuboid<CuboidData> generateCuboid(CreatureIdAssigner creatureIdAssigner, CuboidAddress address)
	{
		// For now, we will just place dirt at the peak block in each column, stone below that, and either air or water sources above.
		PerColumnRandomSeedField seeds = PerColumnRandomSeedField.buildSeedField9x9(_seed, address.x(), address.y());
		PerColumnRandomSeedField.View subField = seeds.view();
		ColumnHeightMap heightMap = _generateHeightMapForCuboidColumn(subField);
		
		// Generate the starting-point of the cuboid, containing only stone and empty (air/water/lava) blocks.
		CuboidData data = _generateStoneCrustCuboid(address, heightMap);
		
		// Create caves.
		AbsoluteLocation cuboidBase = address.getBase();
		_carveOutCaves(data, cuboidBase);
		
		// Replace the top and bottom of the crust with the appropriate transition blocks.
		int cuboidZ = cuboidBase.z();
		_replaceCrustTopAndBottom(heightMap, data, cuboidZ);
		
		// Generate the ore nodes and other structures (including trees).
		_generateOreNodesAndStructures(subField, address, data);
		
		// We have finished populating the cuboid so we can generate the cuboid-local height map.
		CuboidHeightMap cuboidLocalMap = HeightMapHelpers.buildHeightMap(data);
		
		// Spawn the creatures within the cuboid.
		List<CreatureEntity> entities = _spawnCreatures(creatureIdAssigner, subField, heightMap, data, cuboidBase);
		
		// We don't currently require any mutations for anything we spawned.
		List<ScheduledMutation> mutations = List.of();
		
		return new SuspendedCuboid<CuboidData>(data
				, cuboidLocalMap
				, entities
				, mutations
				, Map.of()
		);
	}

	@Override
	public EntityLocation getDefaultSpawnLocation()
	{
		PerColumnRandomSeedField seeds = PerColumnRandomSeedField.buildSeedField9x9(_seed, (short)0, (short)0);
		ColumnHeightMap heightMap = _generateHeightMapForCuboidColumn(seeds.view());
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
	public int test_getBiome(short cuboidX, short cuboidY)
	{
		PerColumnRandomSeedField seeds = PerColumnRandomSeedField.buildSeedField9x9(_seed, cuboidX, cuboidY);
		PerColumnRandomSeedField.View subField = seeds.view();
		return _buildBiomeFromSeeds5x5(subField);
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
		int biome = _buildBiomeFromSeeds5x5(subField);
		return BIOMES[biome].code;
	}

	/**
	 * Used by tests:  Returns the "centre" of the cuboid for the given cuboid X/Y address.
	 * 
	 * @param cuboidX The cuboid X address.
	 * @param cuboidY The cuboid Y address.
	 * @return The "centre" of this cuboid (z coordinate should be ignored).
	 */
	public BlockAddress test_getCentre(short cuboidX, short cuboidY)
	{
		PerColumnRandomSeedField seeds = PerColumnRandomSeedField.buildSeedField9x9(_seed, cuboidX, cuboidY);
		int[][] yCentres = new int[3][3];
		int[][] xCentres = new int[3][3];
		_buildCentreField3x3(seeds.view(), yCentres, xCentres);
		
		return BlockAddress.fromInt(xCentres[1][1], yCentres[1][1], 0);
	}

	/**
	 * Used by tests:  Returns the peak value of the "centre" of the cuboid for the given cuboid X/Y address (not
	 * adjusting for biome).
	 * 
	 * @param cuboidX The cuboid X address.
	 * @param cuboidY The cuboid Y address.
	 * @return The raw peak height for "centre" of this cuboid.
	 */
	public int test_getRawPeak(short cuboidX, short cuboidY)
	{
		PerColumnRandomSeedField seeds = PerColumnRandomSeedField.buildSeedField9x9(_seed, cuboidX, cuboidY);
		return _buildHeightTotal(seeds.view());
	}

	/**
	 * Used by tests:  Returns the peak value of the "centre" of the cuboid for the given cuboid X/Y address (after
	 * adjusting for biome).
	 * 
	 * @param cuboidX The cuboid X address.
	 * @param cuboidY The cuboid Y address.
	 * @return The biome-adjusted peak height for "centre" of this cuboid.
	 */
	public int test_getAdjustedPeak(short cuboidX, short cuboidY)
	{
		PerColumnRandomSeedField seeds = PerColumnRandomSeedField.buildSeedField9x9(_seed, cuboidX, cuboidY);
		PerColumnRandomSeedField.View subField = seeds.view();
		return _peakWithinBiome(subField);
	}

	/**
	 * Used by tests:  Returns the height-map of the cuboid for the given cuboid X/Y address.
	 * 
	 * @param cuboidX The cuboid X address.
	 * @param cuboidY The cuboid Y address.
	 * @return The height map of this cuboid.
	 */
	public ColumnHeightMap test_getHeightMap(short cuboidX, short cuboidY)
	{
		PerColumnRandomSeedField seeds = PerColumnRandomSeedField.buildSeedField9x9(_seed, cuboidX, cuboidY);
		return _generateHeightMapForCuboidColumn(seeds.view());
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
		// (we ignore the updated height map)
		_generateOreNodesAndStructures(seeds.view(), address, data);
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
		ColumnHeightMap heightMap = _generateHeightMapForCuboidColumn(seeds.view());
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


	private ColumnHeightMap _generateHeightMapForCuboidColumn(PerColumnRandomSeedField.View subField)
	{
		// Note that we need to consider "biome" and "cuboid centre height" which requires that we generate the seed values for 5x5 cuboids around this one.
		// centres
		int[][] yCentres = new int[3][3];
		int[][] xCentres = new int[3][3];
		_buildCentreField3x3(subField, yCentres, xCentres);
		
		// Note that the peak height is a combination of the 3x3 average peak height and the 5x5 average biome value.
		int thisPeak = _peakWithinBiome(subField);
		int thisPeakY = yCentres[1][1];
		int thisPeakX = xCentres[1][1];
		int[][] heightMapForCuboidColumn = new int[Encoding.CUBOID_EDGE_SIZE][Encoding.CUBOID_EDGE_SIZE];
		for (int y = 0; y < Encoding.CUBOID_EDGE_SIZE; ++y)
		{
			for (int x = 0; x < Encoding.CUBOID_EDGE_SIZE; ++x)
			{
				int height;
				if ((thisPeakY == y) && (thisPeakX == x))
				{
					height = thisPeak;
				}
				else
				{
					height = _findHeight(subField, yCentres, xCentres, y, x);
				}
				heightMapForCuboidColumn[y][x] = height;
			}
		}
		return ColumnHeightMap.wrap(heightMapForCuboidColumn);
	}

	private static int _biomeVote(int i)
	{
		// We need to pick a value in [0..15]:
		return (MASK_BIOME & i) >> SHIFT_BIOME;
	}

	private int _buildHeightTotal(PerColumnRandomSeedField.View field)
	{
		int total = 0;
		for (int y = -1; y <= 1; ++y)
		{
			for (int x = -1; x <= 1; ++x)
			{
				total += _heightVote(field.get(x, y));
			}
		}
		return total / 9;
	}

	private static int _heightVote(int i)
	{
		// We need to pick a value in [0..15]:
		return (MASK_HEIGHT & i) >> SHIFT_HEIGHT;
	}

	private static int _yCentre(int i)
	{
		// We need to pick a value in [0..31]:
		return (MASK_YCENTRE & i) >> SHIFT_YCENTRE;
	}

	private static int _xCentre(int i)
	{
		// We need to pick a value in [0..31]:
		return (MASK_XCENTRE & i) >> SHIFT_XCENTRE;
	}

	private int _findHeight(PerColumnRandomSeedField.View subField, int[][] yCentres, int[][] xCentres, int thisY, int thisX)
	{
		// We only want to average the heights of the 3 nearest peaks (if we average all 9, we get subtle breaks along cuboid boundaries which will look bad).
		double[] closestDistances = new double[] { 100.0, 100.0, 100.0 };
		int[] closestPeaks = new int[3];
		
		for (int y = -1; y <= 1; ++y)
		{
			for (int x = -1; x <= 1; ++x)
			{
				int peak = _peakWithinBiome(subField.relativeView(x, y));
				int yC = yCentres[1 + y][1 + x] + (Encoding.CUBOID_EDGE_SIZE * y);
				int xC = xCentres[1 + y][1 + x] + (Encoding.CUBOID_EDGE_SIZE * x);
				int dY = thisY - yC;
				int dX = thisX - xC;
				int distanceSquare = (dY * dY) + (dX * dX);
				double distance = Math.sqrt((double)distanceSquare);
				
				// The array is 3 elements so just bubble this in.
				int i = 0;
				while ((i < 3) && (distance < closestDistances[2]))
				{
					double swapDistance = closestDistances[i];
					if (distance < swapDistance)
					{
						int swapPeak = closestPeaks[i];
						closestPeaks[i] = peak;
						closestDistances[i] = distance;
						peak = swapPeak;
						distance = swapDistance;
					}
					i += 1;
				}
			}
		}
		double totalDistance = closestDistances[0] + closestDistances[1] + closestDistances[2];
		double total = 0.0;
		double totalWeight = 0.0f;
		for (int j = 0; j < 3; ++j)
		{
			double weight = totalDistance / closestDistances[j];//1.0 - (distances[j] / totalDistance);
			total += (double)closestPeaks[j] * weight;
			totalWeight += weight;
		}
		return Math.round((float)(total / totalWeight));//Math.round((float)Math.sqrt(total) / 9.0f);
	}

	private int _buildBiomeFromSeeds5x5(PerColumnRandomSeedField.View subField)
	{
		int biomeTotal = 0;
		for (int y = -2; y <= 2; ++y)
		{
			for (int x = -2; x <= 2; ++x)
			{
				biomeTotal += _biomeVote(subField.get(x, y));
			}
		}
		// We want to spread the biomes more aggressively since this averaging will push them too close together.
		biomeTotal = (biomeTotal * 2) + 1;
		// We also want to avoid division truncation making the numbers smaller.
		int biome = biomeTotal / 25;
		if ((biomeTotal % 25) > 12)
		{
			biome += 1;
		}
		// We can now strip off the edges and collapse this back into [0..15].
		biome -= 8;
		if (biome < 0)
		{
			biome = 0;
		}
		else if (biome > 15)
		{
			biome = 15;
		}
		return biome;
	}

	private void _buildCentreField3x3(PerColumnRandomSeedField.View subField, int[][] yCentres, int[][] xCentres)
	{
		for (int y = -1; y <= 1; ++y)
		{
			for (int x = -1; x <= 1; ++x)
			{
				int seed = subField.get(x, y);
				int cY = _yCentre(seed);
				yCentres[1 + y][1 + x] = cY;
				int cX = _xCentre(seed);
				xCentres[1 + y][1 + x] = cX;
			}
		}
	}

	private int _peakWithinBiome(PerColumnRandomSeedField.View subField)
	{
		int rawHeight = _buildHeightTotal(subField);
		int biome = _buildBiomeFromSeeds5x5(subField);
		int offset = BIOMES[biome].heightOffset;
		return rawHeight + offset;
	}

	private void _generateOreNodesAndStructures(PerColumnRandomSeedField.View subField, CuboidAddress address, CuboidData data)
	{
		// Since the nodes can cross cuboid boundaries, we will consider the 9 chunk columns around this one and apply all generated nodes to this cuboid.
		// (in the future, we might short-circuit this to avoid cases where the generation isn't possibly here - for now, we always do it to test the code path)
		AbsoluteLocation base = address.getBase();
		short airNumber = _env.special.AIR.item().number();
		for (int y = -1; y <= 1; ++y)
		{
			for (int x = -1; x <= 1; ++x)
			{
				CuboidAddress sideAddress = address.getRelative(x, y, 0);
				PerColumnRandomSeedField.View relField = subField.relativeView(x, y);
				int columnSeed = relField.get(0, 0);
				AbsoluteLocation sideBase = sideAddress.getBase();
				int relativeBaseX = sideBase.x() - base.x();
				int relativeBaseY = sideBase.y() - base.y();
				int targetCuboidBaseZ = base.z();
				_applyOreNodes(data, columnSeed, relativeBaseX, relativeBaseY, targetCuboidBaseZ, COAL_NODES_PER_CUBOID_COLUMN, COAL_MIN_Z, COAL_MAX_Z, _coalNode);
				_applyOreNodes(data, columnSeed, relativeBaseX, relativeBaseY, targetCuboidBaseZ, IRON_NODES_PER_CUBOID_COLUMN, IRON_MIN_Z, IRON_MAX_Z, _ironNode);
				
				// If this is a forest, also generate random trees.
				int biome = _buildBiomeFromSeeds5x5(relField);
				if (FOREST_CODE == BIOMES[biome].code)
				{
					ColumnHeightMap heightMap = _generateHeightMapForCuboidColumn(relField);
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
							// NOTE:  This relativeBase is NOT an absolute location but is relative to the cuboid base.
							AbsoluteLocation relativeBase = new AbsoluteLocation(relativeBaseX + relativeX - 1, relativeBaseY + relativeY - 1, absoluteZ - targetCuboidBaseZ);
							// Make sure that these are over dirt.
							AbsoluteLocation dirtLocation = base.getRelative(relativeBase.x() + 1, relativeBase.y() + 1,relativeBase.z() - 1);
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
							_basicTree.applyToCuboid(data, relativeBase, airNumber);
						}
					}
				}
			}
		}
		
		// At least temporarily (until we add something like cave generation), we will add in more random bits of iron ore in the stone (based on depth).
		int extraIronAttemptCount = Math.max(IRON_EXTRA_MIN, Math.min(IRON_EXTRA_MAX, -address.z()));
		short stoneNumber = _blockStone.item().number();
		short ironNumber = _blockIronOre.item().number();
		int columnSeed = subField.get(0, 0);
		Random random = new Random(columnSeed);
		for (int i = 0; i < extraIronAttemptCount; ++i)
		{
			int relativeX = random.nextInt(Encoding.CUBOID_EDGE_SIZE);
			int relativeY = random.nextInt(Encoding.CUBOID_EDGE_SIZE);
			int relativeZ = random.nextInt(Encoding.CUBOID_EDGE_SIZE);
			AbsoluteLocation location = base.getRelative(relativeX, relativeY, relativeZ);
			BlockAddress blockAddress = location.getBlockAddress();
			
			// Overwrite this with iron if it is just stone.
			if (stoneNumber == data.getData15(AspectRegistry.BLOCK, blockAddress))
			{
				data.setData15(AspectRegistry.BLOCK, blockAddress, ironNumber);
			}
		}
	}

	private void _applyOreNodes(CuboidData data, int columnSeed, int relativeBaseX, int relativeBaseY, int targetCuboidBaseZ, int tries, int minZ, int maxZ, Structure node)
	{
		short stoneNumber = _blockStone.item().number();
		int range = maxZ - minZ;
		Random random = new Random(columnSeed);
		for (int i = 0; i < tries; ++i)
		{
			int relativeX = random.nextInt(Encoding.CUBOID_EDGE_SIZE);
			int relativeY = random.nextInt(Encoding.CUBOID_EDGE_SIZE);
			int absoluteZ = random.nextInt(range) + minZ;
			// NOTE:  This relativeBase is NOT an absolute location but is relative to the cuboid base.
			AbsoluteLocation relativeBase = new AbsoluteLocation(relativeBaseX + relativeX, relativeBaseY + relativeY, absoluteZ - targetCuboidBaseZ);
			node.applyToCuboid(data, relativeBase, stoneNumber);
		}
	}

	private int _generateFlora(CuboidData data, AbsoluteLocation cuboidBase, int columnSeed, ColumnHeightMap heightMap, _Biome biome)
	{
		int herdSize = 0;
		if ((FIELD_CODE == biome.code) || (MEADOW_CODE == biome.code))
		{
			// We always plant these on dirt but need to replace any grass.
			short supportBlockToReplace = _blockGrass.item().number();
			short supportBlockToAdd = _blockDirt.item().number();
			// We only want to replace air (since this could be under water).
			short blockToReplace = _env.special.AIR.item().number();
			short blockToAdd = (FIELD_CODE == biome.code)
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
			int count = (FIELD_CODE == biome.code)
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
						if (_blockGrass.item().number() == data.getData15(AspectRegistry.BLOCK, underBlock))
						{
							data.setData15(AspectRegistry.BLOCK, underBlock, _blockDirt.item().number());
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

	private CuboidData _generateStoneCrustCuboid(CuboidAddress address, ColumnHeightMap heightMap)
	{
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

	private void _replaceCrustTopAndBottom(ColumnHeightMap heightMap, CuboidData data, int cuboidZ)
	{
		// This function replaces the top block and bottom block of the crust with the appropriate block.
		// It assumes that the crust is completely made of stone and will only replace stone (meaning gaps or special blocks will NOT be replaced).
		short stoneValue = _blockStone.item().number();
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
							// Dirt is under water.
							blockToWrite = _blockDirt;
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

	private List<CreatureEntity> _spawnCreatures(CreatureIdAssigner creatureIdAssigner, PerColumnRandomSeedField.View subField, ColumnHeightMap heightMap, CuboidData data, AbsoluteLocation cuboidBase)
	{
		// We want to spawn the flora.  This is only ever done within a single cuboid column if it is the appropriate biome type and contains a "gully".
		int columnSeed = subField.get(0, 0);
		_Biome biome = BIOMES[_buildBiomeFromSeeds5x5(subField)];
		int herdSizeToSpawn = _generateFlora(data, cuboidBase, columnSeed, heightMap, biome);
		EntityType faunaType = (FIELD_CODE == biome.code)
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
						, faunaType.maxHealth()
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


	private static record _Biome(String name
			, char code
			, int heightOffset
	)
	{}

	private static record _Cavern(byte x
			, byte y
			, byte z
			, byte radius
	)
	{}
}
