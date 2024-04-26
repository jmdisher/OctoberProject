package com.jeffdisher.october.aspects;

import java.io.IOException;
import java.io.InputStream;
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
	public static final String FIELD_GROW_AS_TREE = "grow_as_tree";
	public static final String FIELD_NEXT_PHASE = "next_phase";

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
		IValueTransformer<Block> nextPhaseTransformer = new IValueTransformer.BlockTransformer(items, blocks);
		
		SimpleTabListCallbacks<Block, Void> callbacks = new SimpleTabListCallbacks<>(keyTransformer, null);
		SimpleTabListCallbacks.SubRecordCapture<Block, Integer> growthDivisors = callbacks.captureSubRecord(FIELD_GROWTH_DIVISOR, divisorTransformer, true);
		SimpleTabListCallbacks.SubRecordCapture<Block, Block> treeSet = callbacks.captureSubRecord(FIELD_GROW_AS_TREE, null, false);
		SimpleTabListCallbacks.SubRecordCapture<Block, Block> nextPhaseMap = callbacks.captureSubRecord(FIELD_NEXT_PHASE, nextPhaseTransformer, false);
		
		TabListReader.readEntireFile(callbacks, stream);
		
		// We can just pass these in, directly.
		return new PlantRegistry(growthDivisors.recordData, treeSet.recordData.keySet(), nextPhaseMap.recordData);
	}
	private final Map<Block, Integer> _growthDivisors;
	private final Set<Block> _treeSet;
	private final Map<Block, Block> _nextPhaseMap;

	private PlantRegistry(Map<Block, Integer> growthDivisors
			, Set<Block> treeSet
			, Map<Block, Block> nextPhaseMap
	)
	{
		_growthDivisors = growthDivisors;
		_treeSet = treeSet;
		_nextPhaseMap = nextPhaseMap;
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
	 * Checks the next block type in the growth sequence for the given block.
	 * 
	 * @param block The block.
	 * @return The next block type or null, if this block type doesn't grow or doesn't grow in this way.
	 */
	public Block nextPhaseForPlant(Block block)
	{
		return _nextPhaseMap.get(block);
	}
}
