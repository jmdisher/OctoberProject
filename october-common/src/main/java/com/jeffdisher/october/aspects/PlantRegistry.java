package com.jeffdisher.october.aspects;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.october.config.IValueTransformer;
import com.jeffdisher.october.config.SimpleTabListCallbacks;
import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.types.Block;


/**
 * Represents the subset of Item objects related to plant life.  Specifically, it contains the logic for what blocks
 * need to grow, how often, and what should be done when they do grow.
 */
public class PlantRegistry
{
	public static final String FIELD_GROWTH_DIVISOR = "growth_divisor";
	public static final String FIELD_REQUIRES_LIGHT = "requires_light";
	public static final String FIELD_GROW_AS_TREE = "grow_as_tree";
	public static final String FIELD_GROW_AS_BRANCH = "grow_as_branch";
	public static final String FIELD_GROWTH_STAGES = "growth_stages";
	public static final String FIELD_MATURE_BLOCK = "mature_block";

	/**
	 * Loads the plant growth config from the tablist in the given stream, sourcing Items from the given items registry.
	 * 
	 * @param items The existing ItemRegistry.
	 * @param blocks The existing BlockAspect.
	 * @param stream The stream containing the tablist describing plants.
	 * @return The aspect (never null).
	 * @throws IOException There was a problem with a stream.
	 * @throws TabListReader.TabListException A tablist was malformed.
	 */
	public static PlantRegistry load(ItemRegistry items, BlockAspect blocks
			, InputStream stream
	) throws IOException, TabListReader.TabListException
	{
		IValueTransformer<Block> keyTransformer = new IValueTransformer.BlockTransformer(items, blocks);
		IValueTransformer<Integer> divisorTransformer = new IValueTransformer.IntegerTransformer(FIELD_GROWTH_DIVISOR);
		IValueTransformer<Byte> stagesTransformer = new IValueTransformer.PositiveByteTransformer(FIELD_GROWTH_STAGES, Byte.MAX_VALUE);
		IValueTransformer<Block> matureBlocksTransformer = new IValueTransformer.BlockTransformer(items, blocks);
		
		SimpleTabListCallbacks<Block, Void> callbacks = new SimpleTabListCallbacks<>(keyTransformer, null);
		SimpleTabListCallbacks.SubRecordCapture<Block, Integer> growthDivisors = callbacks.captureSubRecord(FIELD_GROWTH_DIVISOR, divisorTransformer, true);
		SimpleTabListCallbacks.SubRecordCapture<Block, Block> requiresLightSet = callbacks.captureSubRecord(FIELD_REQUIRES_LIGHT, null, false);
		SimpleTabListCallbacks.SubRecordCapture<Block, Block> treeSet = callbacks.captureSubRecord(FIELD_GROW_AS_TREE, null, false);
		SimpleTabListCallbacks.SubRecordCapture<Block, Block> branchSet = callbacks.captureSubRecord(FIELD_GROW_AS_BRANCH, null, false);
		SimpleTabListCallbacks.SubRecordCapture<Block, Byte> growthStages = callbacks.captureSubRecord(FIELD_GROWTH_STAGES, stagesTransformer, false);
		SimpleTabListCallbacks.SubRecordCapture<Block, Block> matureBlocks= callbacks.captureSubRecord(FIELD_MATURE_BLOCK, matureBlocksTransformer, false);
		
		TabListReader.readEntireFile(callbacks, stream);
		
		// Verify that the rules around the optional fields are honoured.
		Set<Block> stagedSet = new HashSet<>();
		stagedSet.addAll(growthStages.recordData.keySet());
		stagedSet.addAll(matureBlocks.recordData.keySet());
		if ((stagedSet.size() != growthStages.recordData.size()) || (stagedSet.size() != matureBlocks.recordData.size()))
		{
			throw new TabListReader.TabListException("growth_stages and mature_block must always be present together");
		}
		
		Set<Block> exclusiveSet = new HashSet<>();
		exclusiveSet.addAll(treeSet.recordData.keySet());
		exclusiveSet.addAll(branchSet.recordData.keySet());
		exclusiveSet.addAll(stagedSet);
		if (exclusiveSet.size() != (treeSet.recordData.size() + branchSet.recordData.size() + stagedSet.size()))
		{
			throw new TabListReader.TabListException("There can be no overlap between trees, branches, and staged growth");
		}
		
		if (exclusiveSet.size() != growthDivisors.recordData.size())
		{
			throw new TabListReader.TabListException("Each plant must be either a tree, a branch, or staged");
		}
		
		// We can just pass these in, directly.
		return new PlantRegistry(growthDivisors.recordData
			, requiresLightSet.recordData.keySet()
			, treeSet.recordData.keySet()
			, branchSet.recordData.keySet()
			, growthStages.recordData
			, matureBlocks.recordData
		);
	}


	private final Map<Block, Integer> _growthDivisors;
	private final Set<Block> _requiresLightSet;
	private final Set<Block> _treeSet;
	private final Set<Block> _branchSet;
	private final Map<Block, Byte> _stagesCount;
	private final Map<Block, Block> _stagesMaturity;

	private PlantRegistry(Map<Block, Integer> growthDivisors
		, Set<Block> requiresLightSet
		, Set<Block> treeSet
		, Set<Block> branchSet
		, Map<Block, Byte> stagesCount
		, Map<Block, Block> stagesMaturity
	)
	{
		_growthDivisors = growthDivisors;
		_requiresLightSet = requiresLightSet;
		_treeSet = treeSet;
		_branchSet = branchSet;
		_stagesCount = stagesCount;
		_stagesMaturity = stagesMaturity;
	}

	/**
	 * Returns the growth divisor to use when checking if this growth should happen.  Growth rate can be viewed as 1/x
	 * where x is the number returned from this function.  Returns 0 if this item doesn't have a concept of growth.
	 * 
	 * @param block The block to check.
	 * @return The divisor (0 if not growable).
	 */
	public int growthDivisor(Block block)
	{
		return _growthDivisors.containsKey(block)
				? _growthDivisors.get(block)
				: 0
		;
	}

	/**
	 * Checks if the given block requires light in order to grow.
	 * 
	 * @param block The block.
	 * @return True if this block requires light to grow.
	 */
	public boolean requiresLight(Block block)
	{
		return _requiresLightSet.contains(block);
	}

	/**
	 * Checks if the given block uses the special tree growth mechanic.
	 * 
	 * @param block The block.
	 * @return True if this block grows using the special tree growth mechanic.
	 */
	public boolean isTree(Block block)
	{
		return _treeSet.contains(block);
	}

	/**
	 * Checks if the given block uses the special branch growth mechanic.
	 * 
	 * @param block The block.
	 * @return True if this block grows using the special branch growth mechanic.
	 */
	public boolean isBranch(Block block)
	{
		return _branchSet.contains(block);
	}

	/**
	 * The number of growth stages a young plant goes through before being replaced by its mature variant.  This number
	 * is positive for every young plant type but 0 if not something which uses growth stages.
	 * Note that the current number of completed growth stages for a plant is stored in the BLOCK_DEFINED_BYTE aspect
	 * and is always less than this returned number (as the point at which this many stages are completed, it matures).
	 * 
	 * @param plant The plant block.
	 * @return The number of growth stages before this plant matures (always > 0) unless the block doesn't grow this way
	 * (in which case 0 is returned).
	 */
	public byte growthStagesForPlant(Block plant)
	{
		return _stagesCount.getOrDefault(plant, (byte)0);
	}

	/**
	 * Checks the block type the given block should be replaced by once it has completed its growth stages.
	 * 
	 * @param block The block.
	 * @return The mature block type or null, if this block type isn't a plant or doesn't grow in this way.
	 */
	public Block matureBlockForPlant(Block block)
	{
		return _stagesMaturity.get(block);
	}
}
