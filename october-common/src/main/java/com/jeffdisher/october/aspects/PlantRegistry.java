package com.jeffdisher.october.aspects;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.october.config.IValueTransformer;
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
		
		Map<Block, Integer> growthDivisors = new HashMap<>();
		Set<Block> treeSet = new HashSet<>();
		Map<Block, Block> nextPhaseMap = new HashMap<>();
		
		TabListReader.IParseCallbacks callbacks = new TabListReader.IParseCallbacks() {
			private Block _currentKey;
			@Override
			public void startNewRecord(String name, String[] parameters) throws TabListReader.TabListException
			{
				if (0 != parameters.length)
				{
					throw new TabListReader.TabListException("Plant header expected to have no parameters: \"" + name + "\"");
				}
				_currentKey = keyTransformer.transform(name);
			}
			@Override
			public void endRecord() throws TabListReader.TabListException
			{
				// Make sure that the arguments are consistent.
				if (!growthDivisors.containsKey(_currentKey))
				{
					throw new TabListReader.TabListException("Plant requires growth_divisor: \"" + _currentKey.item().id() + "\"");
				}
				if (treeSet.contains(_currentKey) == nextPhaseMap.containsKey(_currentKey))
				{
					throw new TabListReader.TabListException("Plant must have one of grow_as_tree or next_phase: \"" + _currentKey.item().id() + "\"");
				}
				_currentKey = null;
			}
			@Override
			public void processSubRecord(String name, String[] parameters) throws TabListReader.TabListException
			{
				if (name.equals(FIELD_GROWTH_DIVISOR))
				{
					String first = _requireOneParamter(name, parameters);
					int divisor = divisorTransformer.transform(first);
					if (divisor <= 0)
					{
						throw new TabListReader.TabListException("growth_divisor must be positive \"" + _currentKey.item().id() + "\"");
					}
					growthDivisors.put(_currentKey, divisor);
				}
				else if (name.equals(FIELD_GROW_AS_TREE))
				{
					if (0 != parameters.length)
					{
						throw new TabListReader.TabListException("grow_as_tree takes no parameters \"" + _currentKey.item().id() + "\"");
					}
					treeSet.add(_currentKey);
				}
				else if (name.equals(FIELD_NEXT_PHASE))
				{
					String first = _requireOneParamter(name, parameters);
					Block block = nextPhaseTransformer.transform(first);
					nextPhaseMap.put(_currentKey, block);
				}
				else
				{
					throw new TabListReader.TabListException("Unexpected field in \"" + _currentKey.item().id() + "\": \"" + name + "\"");
				}
			}
			private String _requireOneParamter(String name, String[] parameters) throws TabListReader.TabListException
			{
				if (1 != parameters.length)
				{
					throw new TabListReader.TabListException("Field in \"" + _currentKey.item().id() + "\" expects a single parameter: \"" + name + "\"");
				}
				return parameters[0];
			}
		};
		
		TabListReader.readEntireFile(callbacks, stream);
		
		// We can just pass these in, directly.
		return new PlantRegistry(growthDivisors, treeSet, nextPhaseMap);
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
