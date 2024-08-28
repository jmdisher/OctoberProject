package com.jeffdisher.october.persistence;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.BiFunction;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.logic.CreatureIdAssigner;
import com.jeffdisher.october.logic.ScheduledMutation;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.utils.Assert;
import com.jeffdisher.october.worldgen.CuboidGenerator;
import com.jeffdisher.october.worldgen.Structure;
import com.jeffdisher.october.worldgen.StructureLoader;


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
public class BasicWorldGenerator implements BiFunction<CreatureIdAssigner, CuboidAddress, SuspendedCuboid<CuboidData>> 
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

	public static final int COAL_NODES_PER_CUBOID_COLUMN = 4;
	public static final int COAL_MIN_Z = -50;
	public static final int COAL_MAX_Z = 20;
	public static final String[] COAL_NODE = new String[] {""
			+ "AA\n"
			+ "AA\n"
			, ""
			+ "AA\n"
			+ "AA\n"
	};
	public static final int IRON_NODES_PER_CUBOID_COLUMN = 2;
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
	public static final int FOREST_TREE_COUNT = 6;
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

	private final Environment _env;
	private final int _seed;
	private final Block _blockStone;
	private final Block _blockDirt;
	private final Block _blockWheatMature;
	private final Block _blockCarrotMature;
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
		_blockDirt = env.blocks.fromItem(env.items.getItemById("op.dirt"));
		_blockWheatMature = env.blocks.fromItem(env.items.getItemById("op.wheat_mature"));
		_blockCarrotMature = env.blocks.fromItem(env.items.getItemById("op.carrot_mature"));
		
		StructureLoader loader = new StructureLoader(env.items, env.blocks);
		_coalNode = loader.loadFromStrings(COAL_NODE);
		_ironNode = loader.loadFromStrings(IRON_NODE);
		_basicTree = loader.loadFromStrings(BASIC_TREE);
	}

	@Override
	public SuspendedCuboid<CuboidData> apply(CreatureIdAssigner creatureIdAssigner, CuboidAddress address)
	{
		// For now, we will just place dirt at the peak block in each column, stone below that, and either air or water sources above.
		_SeedField seeds = _SeedField.buildSeedField5x5(_seed, address.x(), address.y());
		_SubField subField = new _SubField(seeds, 0, 0);
		ColumnHeightMap heightMap = _generateHeightMapForCuboidColumn(subField);
		int minHeight = 0;
		int maxHeight = Integer.MAX_VALUE;
		int totalHeight = 0;
		for (int y = 0; y < Structure.CUBOID_EDGE_SIZE; ++y)
		{
			for (int x = 0; x < Structure.CUBOID_EDGE_SIZE; ++x)
			{
				int height = heightMap.getHeight(x, y);
				minHeight = Math.min(minHeight, height);
				maxHeight = Math.max(maxHeight, height);
				totalHeight += height;
			}
		}
		int count = Structure.CUBOID_EDGE_SIZE * Structure.CUBOID_EDGE_SIZE;
		int averageHeight = totalHeight / count;
		AbsoluteLocation cuboidBase = address.getBase();
		int cuboidZ = cuboidBase.z();
		
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
			defaultEmptyBlock = _env.special.WATER_SOURCE;
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
		
		// Now, walk the height map and make any required adjustments.
		for (int y = 0; y < Structure.CUBOID_EDGE_SIZE; ++y)
		{
			for (int x = 0; x < Structure.CUBOID_EDGE_SIZE; ++x)
			{
				int height = heightMap.getHeight(x, y);
				for (int z = 0; z < Structure.CUBOID_EDGE_SIZE; ++z)
				{
					int thisZ = cuboidZ + z;
					Block blockToWrite;
					if (thisZ > height)
					{
						// This is whatever the air block is.
						blockToWrite = defaultEmptyBlock;
					}
					else if (thisZ < height)
					{
						// This is stone.
						blockToWrite = _blockStone;
					}
					else
					{
						// This is dirt since it IS the top.
						blockToWrite = _blockDirt;
					}
					if (blockToWrite != defaultBlock)
					{
						data.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)x, (byte)y, (byte)z), blockToWrite.item().number());
					}
				}
			}
		}
		
		// Generate the ore nodes and other structures (including trees).
		CuboidHeightMap cuboidLocalMap = _generateOreNodesAndStructures(subField, address, data);
		if (null == cuboidLocalMap)
		{
			// Generate the default map from the column.
			cuboidLocalMap = _extractFromColumn(heightMap, cuboidZ);
		}
		
		// We want to spawn the flora.  This is only ever done within a single cuboid column if it is the appropriate biome type and contains a "gully".
		int cuboidSeed = subField.get(0, 0);
		_Biome biome = BIOMES[_buildBiomeFromSeeds5x5(subField)];
		EntityType herdTypeToSpawn = _generateFlora(data, cuboidBase, cuboidSeed, heightMap, biome);
		
		// Spawn any creatures associated with this cuboid.
		List<CreatureEntity> entities = new ArrayList<>();
		if (null != herdTypeToSpawn)
		{
			// We don't often do herd spawning so we will try 5 times in random locations on the surface.
			Random random = new Random(cuboidSeed);
			for (int i = 0; i < HERD_SIZE; ++i)
			{
				int relativeX = random.nextInt(Structure.CUBOID_EDGE_SIZE);
				int relativeY = random.nextInt(Structure.CUBOID_EDGE_SIZE);
				// Choose the block above the dirt.
				int relativeZ = heightMap.getHeight(relativeX, relativeY) - cuboidBase.z() + 1;
				entities.add(CreatureEntity.create(creatureIdAssigner.next()
						, EntityType.COW
						, cuboidBase.getRelative(relativeX, relativeY, relativeZ).toEntityLocation()
						, (byte)100
				));
			}
		}
		
		// We don't currently require any mutations for anything we spawned.
		List<ScheduledMutation> mutations = List.of();
		
		return new SuspendedCuboid<CuboidData>(data
				, cuboidLocalMap
				, entities
				, mutations
		);
	}

	/**
	 * This just returns a "reasonable" spawn location in the world but where the target starting location is is handled
	 * purely internally.
	 * Currently, this just returns a location in the 0,0 column which is standing on the ground where the world
	 * would be generated (since it cannot account for changes since the world was generated).
	 * 
	 * @return The location where new entities can be reasonably spawned.
	 */
	public EntityLocation getDefaultSpawnLocation()
	{
		_SeedField seeds = _SeedField.buildSeedField5x5(_seed, (short)0, (short)0);
		ColumnHeightMap heightMap = _generateHeightMapForCuboidColumn(new _SubField(seeds, 0, 0));
		// Find the largest value here and spawn there (note that this may not be in the zero-z cuboid).
		int maxZ = Integer.MIN_VALUE;
		int targetX = -1;
		int targetY = -1;
		for (int y = 0; y < Structure.CUBOID_EDGE_SIZE; ++y)
		{
			for (int x = 0; x < Structure.CUBOID_EDGE_SIZE; ++x)
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
		return _deterministicRandom(_seed, cuboidX, cuboidY);
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
		_SeedField seeds = _SeedField.buildSeedField5x5(_seed, cuboidX, cuboidY);
		_SubField subField = new _SubField(seeds, 0, 0);
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
		_SeedField seeds = _SeedField.buildSeedField5x5(_seed, cuboidX, cuboidY);
		_SubField subField = new _SubField(seeds, 0, 0);
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
		_SeedField seeds = _SeedField.buildSeedField5x5(_seed, cuboidX, cuboidY);
		int[][] yCentres = new int[3][3];
		int[][] xCentres = new int[3][3];
		_buildCentreField3x3(new _SubField(seeds, 0, 0), yCentres, xCentres);
		
		return new BlockAddress((byte)xCentres[1][1], (byte)yCentres[1][1], (byte)0);
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
		_SeedField seeds = _SeedField.buildSeedField5x5(_seed, cuboidX, cuboidY);
		return _buildHeightTotal(new _SubField(seeds, 0, 0));
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
		_SeedField seeds = _SeedField.buildSeedField5x5(_seed, cuboidX, cuboidY);
		_SubField subField = new _SubField(seeds, 0, 0);
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
		_SeedField seeds = _SeedField.buildSeedField5x5(_seed, cuboidX, cuboidY);
		return _generateHeightMapForCuboidColumn(new _SubField(seeds, 0, 0));
	}

	/**
	 * Used by tests:  Populates the given data, at address, with expected  ore nodes.
	 * 
	 * @param address The cuboid address.
	 * @param data The cuboid data.
	 */
	public void test_generateOreNodes(CuboidAddress address, CuboidData data)
	{
		_SeedField seeds = _SeedField.buildSeedField5x5(_seed, address.x(), address.y());
		// (we ignore the updated height map)
		_generateOreNodesAndStructures(new _SubField(seeds, 0, 0), address, data);
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
		_SeedField seeds = _SeedField.buildSeedField5x5(_seed, cuboidX, cuboidY);
		ColumnHeightMap heightMap = _generateHeightMapForCuboidColumn(new _SubField(seeds, 0, 0));
		return _findGully(heightMap);
	}


	private ColumnHeightMap _generateHeightMapForCuboidColumn(_SubField subField)
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
		int[][] heightMapForCuboidColumn = new int[Structure.CUBOID_EDGE_SIZE][Structure.CUBOID_EDGE_SIZE];
		for (int y = 0; y < Structure.CUBOID_EDGE_SIZE; ++y)
		{
			for (int x = 0; x < Structure.CUBOID_EDGE_SIZE; ++x)
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

	private static int _deterministicRandom(int seed, int x, int y)
	{
		// This "deterministic random" is random since it is based on the given random seed but "deterministic" in that
		// it will be the same for a given seed-y-x triplet.
		// We write these into the buffer in various combinations just because it seems to give the hash better distribution.
		ByteBuffer buffer = ByteBuffer.allocate(4 * Integer.BYTES);
		buffer.putInt(y);
		buffer.putInt(x);
		buffer.putInt(x ^ y);
		buffer.putInt(x + y);
		int hash = Arrays.hashCode(buffer.array());
		return seed ^ hash;
	}

	private static int _biomeVote(int i)
	{
		// We need to pick a value in [0..15]:
		return (MASK_BIOME & i) >> SHIFT_BIOME;
	}

	private int _buildHeightTotal(_SubField field)
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

	private int _findHeight(_SubField subField, int[][] yCentres, int[][] xCentres, int thisY, int thisX)
	{
		// We only want to average the heights of the 3 nearest peaks (if we average all 9, we get subtle breaks along cuboid boundaries which will look bad).
		double[] closestDistances = new double[] { 100.0, 100.0, 100.0 };
		int[] closestPeaks = new int[3];
		
		for (int y = -1; y <= 1; ++y)
		{
			for (int x = -1; x <= 1; ++x)
			{
				int peak = _peakWithinBiome(subField.relativeField(x, y));
				int yC = yCentres[1 + y][1 + x] + (Structure.CUBOID_EDGE_SIZE * y);
				int xC = xCentres[1 + y][1 + x] + (Structure.CUBOID_EDGE_SIZE * x);
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

	private int _buildBiomeFromSeeds5x5(_SubField subField)
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

	private void _buildCentreField3x3(_SubField subField, int[][] yCentres, int[][] xCentres)
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

	private int _peakWithinBiome(_SubField subField)
	{
		int rawHeight = _buildHeightTotal(subField);
		int biome = _buildBiomeFromSeeds5x5(subField);
		int offset = BIOMES[biome].heightOffset;
		return rawHeight + offset;
	}

	// Returns non-null if this generation changed the height map for this cuboid (returns the new map).
	private CuboidHeightMap _generateOreNodesAndStructures(_SubField subField, CuboidAddress address, CuboidData data)
	{
		// Since the nodes can cross cuboid boundaries, we will consider the 9 chunk columns around this one and apply all generated nodes to this cuboid.
		// (in the future, we might short-circuit this to avoid cases where the generation isn't possibly here - for now, we always do it to test the code path)
		AbsoluteLocation base = address.getBase();
		short airNumber = _env.special.AIR.item().number();
		CuboidHeightMap newMap = null;
		for (int y = -1; y <= 1; ++y)
		{
			for (int x = -1; x <= 1; ++x)
			{
				CuboidAddress sideAddress = address.getRelative(x, y, 0);
				_SubField relField = subField.relativeField(x, y);
				int cuboidSeed = relField.get(0, 0);
				AbsoluteLocation sideBase = sideAddress.getBase();
				int relativeBaseX = sideBase.x() - base.x();
				int relativeBaseY = sideBase.y() - base.y();
				int targetCuboidBaseZ = base.z();
				_applyOreNodes(data, cuboidSeed, relativeBaseX, relativeBaseY, targetCuboidBaseZ, COAL_NODES_PER_CUBOID_COLUMN, COAL_MIN_Z, COAL_MAX_Z, _coalNode);
				_applyOreNodes(data, cuboidSeed, relativeBaseX, relativeBaseY, targetCuboidBaseZ, IRON_NODES_PER_CUBOID_COLUMN, IRON_MIN_Z, IRON_MAX_Z, _ironNode);
				
				// If this is a forest, also generate random trees.
				int biome = _buildBiomeFromSeeds5x5(relField);
				if (FOREST_CODE == BIOMES[biome].code)
				{
					ColumnHeightMap heightMap = _generateHeightMapForCuboidColumn(relField);
					Random random = new Random(cuboidSeed);
					for (int i = 0; i < FOREST_TREE_COUNT; ++i)
					{
						int relativeX = random.nextInt(Structure.CUBOID_EDGE_SIZE);
						int relativeY = random.nextInt(Structure.CUBOID_EDGE_SIZE);
						// Choose the block above the dirt.
						int absoluteZ = heightMap.getHeight(relativeX, relativeY) + 1;
						// The tree is a 3x3 structure with the tree in the middle so step back by one.
						// NOTE:  This relativeBase is NOT an absolute location but is relative to the cuboid base.
						AbsoluteLocation relativeBase = new AbsoluteLocation(relativeBaseX + relativeX - 1, relativeBaseY + relativeY - 1, absoluteZ - targetCuboidBaseZ);
						// Make sure that these are over dirt.
						AbsoluteLocation dirtLocation = base.getRelative(relativeBase.x() + 1, relativeBase.y() + 1,relativeBase.z() - 1);
						if (dirtLocation.getCuboidAddress().equals(address))
						{
							Assert.assertTrue(_blockDirt.item().number() == data.getData15(AspectRegistry.BLOCK, dirtLocation.getBlockAddress()));
						}
						_basicTree.applyToCuboid(data, relativeBase, airNumber);
					}
					// We generated trees into this so update the cuboid height map.
					if ((0 == x) && (0 == y))
					{
						newMap = HeightMapHelpers.buildHeightMap(data);
					}
				}
			}
		}
		return newMap;
	}

	private void _applyOreNodes(CuboidData data, int cuboidSeed, int relativeBaseX, int relativeBaseY, int targetCuboidBaseZ, int tries, int minZ, int maxZ, Structure node)
	{
		short stoneNumber = _blockStone.item().number();
		int range = maxZ - minZ;
		Random random = new Random(cuboidSeed);
		for (int i = 0; i < tries; ++i)
		{
			int relativeX = random.nextInt(Structure.CUBOID_EDGE_SIZE);
			int relativeY = random.nextInt(Structure.CUBOID_EDGE_SIZE);
			int absoluteZ = random.nextInt(range) + minZ;
			// NOTE:  This relativeBase is NOT an absolute location but is relative to the cuboid base.
			AbsoluteLocation relativeBase = new AbsoluteLocation(relativeBaseX + relativeX, relativeBaseY + relativeY, absoluteZ - targetCuboidBaseZ);
			node.applyToCuboid(data, relativeBase, stoneNumber);
		}
	}

	private EntityType _generateFlora(CuboidData data, AbsoluteLocation cuboidBase, int cuboidSeed, ColumnHeightMap heightMap, _Biome biome)
	{
		EntityType typeToSpawn = null;
		if ((FIELD_CODE == biome.code) || (MEADOW_CODE == biome.code))
		{
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
				int cuboidTopExclusive = cuboidBottomZ + Structure.CUBOID_EDGE_SIZE;
				
				for (int z = bottomLayerToChange; z <= topLayerToChange; ++z)
				{
					if ((z >= cuboidBottomZ) && (z < cuboidTopExclusive))
					{
						_replaceLayer(data, (byte)(z - cuboidBottomZ), blockToReplace, blockToAdd);
						// We were able to generate something here.
						didFillGully = true;
					}
				}
			}
			
			// Now, inject a few random ones.
			Random random = new Random(cuboidSeed);
			int count = (FIELD_CODE == biome.code)
					? FIELD_WHEAT_COUNT
					: FIELD_CARROT_COUNT
			;
			for (int i = 0; i < count; ++i)
			{
				int relativeX = random.nextInt(Structure.CUBOID_EDGE_SIZE);
				int relativeY = random.nextInt(Structure.CUBOID_EDGE_SIZE);
				// Choose the block above the dirt.
				int relativeZ = heightMap.getHeight(relativeX, relativeY) - cuboidBottomZ + 1;
				if ((relativeZ >= 0) && (relativeZ < Structure.CUBOID_EDGE_SIZE))
				{
					BlockAddress address = new BlockAddress((byte)relativeX, (byte)relativeY, (byte)relativeZ);
					short original = data.getData15(AspectRegistry.BLOCK, address);
					if (blockToReplace == original)
					{
						// Make sure that these are over dirt.
						if (address.z() > 0)
						{
							Assert.assertTrue(_blockDirt.item().number() == data.getData15(AspectRegistry.BLOCK, new BlockAddress(address.x(), address.y(), (byte)(address.z() - 1))));
						}
						data.setData15(AspectRegistry.BLOCK, address, blockToAdd);
					}
				}
			}
			
			// If this is a field, and we could fill a gully, spawn a small herd of cows in the centre of the cuboid.
			if ((FIELD_CODE == biome.code) && didFillGully)
			{
				typeToSpawn = EntityType.COW;
			}
		}
		return typeToSpawn;
	}

	private int _findGully(ColumnHeightMap heightMap)
	{
		// A gully is a point in the cuboid lower than the perimeter of the cuboid.
		int minGully = Integer.MAX_VALUE;
		int minPerimeter = Integer.MAX_VALUE;
		int edge = Structure.CUBOID_EDGE_SIZE - 1;
		for (int y = 0; y < Structure.CUBOID_EDGE_SIZE; ++y)
		{
			for (int x = 0; x < Structure.CUBOID_EDGE_SIZE; ++x)
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
		for (int y = 0; y < Structure.CUBOID_EDGE_SIZE; ++y)
		{
			for (int x = 0; x < Structure.CUBOID_EDGE_SIZE; ++x)
			{
				int height = heightMap.getHeight(x, y);
				min = Math.min(min, height);
			}
		}
		return min;
	}

	private void _replaceLayer(CuboidData data, byte z, short blockToReplace, short blockToAdd)
	{
		for (byte y = 0; y < Structure.CUBOID_EDGE_SIZE; ++y)
		{
			for (byte x = 0; x < Structure.CUBOID_EDGE_SIZE; ++x)
			{
				BlockAddress address = new BlockAddress(x, y, z);
				short original = data.getData15(AspectRegistry.BLOCK, address);
				if (blockToReplace == original)
				{
					data.setData15(AspectRegistry.BLOCK, address, blockToAdd);
				}
			}
		}
	}

	private static CuboidHeightMap _extractFromColumn(ColumnHeightMap column, int baseZ)
	{
		byte[][] localMap = new byte[Structure.CUBOID_EDGE_SIZE][Structure.CUBOID_EDGE_SIZE];
		for (int y = 0; y < Structure.CUBOID_EDGE_SIZE; ++y)
		{
			for (int x = 0; x < Structure.CUBOID_EDGE_SIZE; ++x)
			{
				int absoluteHeight = column.getHeight(x, y);
				int local = absoluteHeight - baseZ;
				byte localHeight;
				if (local > Structure.CUBOID_EDGE_SIZE)
				{
					// NOTE:  Here we assume that the world is solid below the column height map (only true for the current state of this world generator).
					localHeight = Structure.CUBOID_EDGE_SIZE - 1;
				}
				else if (local >= 0)
				{
					// This is within our cuboid so we know the height.
					localHeight = (byte)local;
				}
				else
				{
					// If the actual height is below this cuboid, we are clearly empty.
					localHeight = CuboidHeightMap.UNKNOWN_HEIGHT;
				}
				localMap[y][x] = localHeight;
			}
		}
		return CuboidHeightMap.wrap(localMap);
	}


	private static class _SeedField
	{
		public static _SeedField buildSeedField5x5(int seed, short cuboidX, short cuboidY)
		{
			int[][] seeds = new int[9][9];
			for (int y = -4; y <= 4; ++y)
			{
				for (int x = -4; x <= 4; ++x)
				{
					seeds[4 + y][4 + x] = _deterministicRandom(seed, cuboidX + x, cuboidY + y);
				}
			}
			return new _SeedField(seeds);
		}
		
		private final int[][] _seeds;
		
		private _SeedField(int[][] seeds)
		{
			Assert.assertTrue(9 == seeds.length);
			_seeds = seeds;
		}
		public int get(int relX, int relY)
		{
			return _seeds[4 + relY][4 + relX];
		}
	}

	private static class _SubField
	{
		private final _SeedField _field;
		private final int _relX;
		private final int _relY;
		
		private _SubField(_SeedField field, int relX, int relY)
		{
			_field = field;
			_relX = relX;
			_relY = relY;
		}
		public _SubField relativeField(int relX, int relY)
		{
			return new _SubField(_field, _relX + relX, _relY + relY);
		}
		public int get(int relX, int relY)
		{
			return _field.get(_relX + relX, _relY + relY);
		}
	}

	private static record _Biome(String name
			, char code
			, int heightOffset
	)
	{}
}
