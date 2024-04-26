package com.jeffdisher.october.aspects;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import com.jeffdisher.october.config.IValueTransformer;
import com.jeffdisher.october.config.SimpleTabListCallbacks;
import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.config.TabListReader.TabListException;
import com.jeffdisher.october.types.Item;


/**
 * Represents the subset of Item objects which are non-stackable, have durability, and have special uses.
 */
public class ToolRegistry
{
	public static final String FIELD_BLOCK_MATERIAL = "block_material";

	/**
	 * Loads the tool registry from the tablist in the given stream, sourcing Items from the given items registry.
	 * 
	 * @param items The existing ItemRegistry.
	 * @param stream The stream containing the tablist describing tool durabilities.
	 * @return The registry (never null).
	 * @throws IOException There was a problem with a stream.
	 * @throws TabListReader.TabListException A tablist was malformed.
	 */
	public static ToolRegistry load(ItemRegistry items
			, InputStream stream
	) throws IOException, TabListReader.TabListException
	{
		IValueTransformer<Item> keyTransformer = new IValueTransformer.ItemTransformer(items);
		IValueTransformer<Integer> valueTransformer = new IValueTransformer.IntegerTransformer("speed");
		IValueTransformer<BlockMaterial> materialTransformer = new IValueTransformer<>() {
			@Override
			public BlockMaterial transform(String value) throws TabListException
			{
				BlockMaterial material = BlockMaterial.valueOf(value);
				if (null == material)
				{
					throw new TabListReader.TabListException("Unknown constant for block_material: \"" + value + "\"");
				}
				return material;
			}
		};
		
		SimpleTabListCallbacks<Item, Integer> callbacks = new SimpleTabListCallbacks<>(keyTransformer, valueTransformer);
		SimpleTabListCallbacks.SubRecordCapture<Item, BlockMaterial> blockMaterials = callbacks.captureSubRecord(FIELD_BLOCK_MATERIAL, materialTransformer, true);
		
		TabListReader.readEntireFile(callbacks, stream);
		
		// We can just pass these in, directly.
		return new ToolRegistry(callbacks.topLevel, blockMaterials.recordData);
	}


	private final Map<Item, Integer> _speedValues;
	private final Map<Item, BlockMaterial> _blockMaterials;

	private ToolRegistry(Map<Item, Integer> speedValues
			, Map<Item, BlockMaterial> blockMaterials
	)
	{
		_speedValues = speedValues;
		_blockMaterials = blockMaterials;
	}

	/**
	 * Checks to see if the given item is a tool, returning the speed modifier if it is, or 1 if it isn't.
	 * 
	 * @param item The item to check.
	 * @return The speed modifier or 1 if this is not a tool.
	 */
	public int toolSpeedModifier(Item item)
	{
		Integer toolValue = _speedValues.get(item);
		return (null != toolValue)
				? toolValue.intValue()
				: 1
		;
	}

	/**
	 * Checks the type of blocks which receive a speed multiplier when using the given item as a tool.  NO_MATERIAL is
	 * returned if this is no tool.
	 * 
	 * @param item The item which may be a tool.
	 * @return The material type this tool can break.
	 */
	public BlockMaterial toolTargetMaterial(Item item)
	{
		BlockMaterial value = _blockMaterials.get(item);
		return (null != value)
				? value
				: BlockMaterial.NO_MATERIAL
		;
	}
}
