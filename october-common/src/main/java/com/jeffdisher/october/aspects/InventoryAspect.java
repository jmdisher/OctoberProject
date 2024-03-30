package com.jeffdisher.october.aspects;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Item;


/**
 * Contains constants and helpers associated with the inventory aspect.
 */
public class InventoryAspect
{
	/**
	 * Loads the block aspect from the tablist in the given stream, sourcing Items from the given items registry.
	 * 
	 * @param items The existing ItemRegistry.
	 * @param blocks The existing BlockAspect.
	 * @param encumbranceStream The stream containing the tablist describing per-item encumbrance.
	 * @param capacityStream The stream containing the tablist describing block inventory capacities.
	 * @return The aspect (never null).
	 * @throws IOException There was a problem with a stream.
	 * @throws TabListReader.TabListException A tablist was malformed.
	 */
	public static InventoryAspect load(ItemRegistry items, BlockAspect blocks
			, InputStream encumbranceStream
			, InputStream capacityStream
	) throws IOException, TabListReader.TabListException
	{
		int[] encumbranceByItemType = new int[items.ITEMS_BY_TYPE.length];
		for (int i = 0; i < encumbranceByItemType.length; ++i)
		{
			encumbranceByItemType[i] = -1;
		}
		TabListReader.readEntireFile(new TabListReader.IParseCallbacks() {
			@Override
			public void startNewRecord(String name, String[] parameters) throws TabListReader.TabListException
			{
				Item item = items.getItemById(name);
				if (null == item)
				{
					throw new TabListReader.TabListException("Uknown item: \"" + name + "\"");
				}
				if (1 != parameters.length)
				{
					throw new TabListReader.TabListException("A single encumbrance must be provided");
				}
				int encumbrance;
				try
				{
					encumbrance = Integer.parseInt(parameters[0]);
				}
				catch (NumberFormatException e)
				{
					throw new TabListReader.TabListException("Not a valid encumbrance: \"" + parameters[0] + "\"");
				}
				if (-1 != encumbranceByItemType[item.number()])
				{
					throw new TabListReader.TabListException("Duplicate encumbrance: \"" + name + "\"");
				}
				encumbranceByItemType[item.number()] = encumbrance;
			}
			@Override
			public void endRecord() throws TabListReader.TabListException
			{
				// Do nothing.
			}
			@Override
			public void processSubRecord(String name, String[] parameters) throws TabListReader.TabListException
			{
				// Not expected in this list type.
				throw new TabListReader.TabListException("Inventory encumbrance tablist has no sub-records");
			}
		}, encumbranceStream);
		
		// We expect an entry for every item.
		for (int i = 0; i < encumbranceByItemType.length; ++i)
		{
			if (-1 == encumbranceByItemType[i])
			{
				throw new TabListReader.TabListException("Missing encumbrance: \"" + items.ITEMS_BY_TYPE[i].name() + "\"");
			}
		}
		
		// We will pre-populate our default value for blocks the entity can enter (this is special logic - we don't want it in the data file).
		Map<Block, Integer> blockCapacities = new HashMap<>();
		for (Block block : blocks.BLOCKS_BY_TYPE)
		{
			if ((null != block) && blocks.permitsEntityMovement(block))
			{
				blockCapacities.put(block, CAPACITY_AIR);
			}
		}
		TabListReader.readEntireFile(new TabListReader.IParseCallbacks() {
			@Override
			public void startNewRecord(String name, String[] parameters) throws TabListReader.TabListException
			{
				Item item = items.getItemById(name);
				Block block = (null != item) ? blocks.BLOCKS_BY_TYPE[item.number()] : null;
				if (null == block)
				{
					throw new TabListReader.TabListException("Not a block: \"" + name + "\"");
				}
				if (1 != parameters.length)
				{
					throw new TabListReader.TabListException("A single capacity must be provided");
				}
				int capacity;
				try
				{
					capacity = Integer.parseInt(parameters[0]);
				}
				catch (NumberFormatException e)
				{
					throw new TabListReader.TabListException("Not a valid capacity: \"" + parameters[0] + "\"");
				}
				blockCapacities.put(block, capacity);
			}
			@Override
			public void endRecord() throws TabListReader.TabListException
			{
				// Do nothing.
			}
			@Override
			public void processSubRecord(String name, String[] parameters) throws TabListReader.TabListException
			{
				// Not expected in this list type.
				throw new TabListReader.TabListException("Inventory encumbrance tablist has no sub-records");
			}
		}, capacityStream);
		
		return new InventoryAspect(encumbranceByItemType, blockCapacities);
	}

	/**
	 * The capacity of an air block is small since it is just "on the ground" and we want containers to be used.
	 */
	public static final int CAPACITY_AIR = 10;
	public static final int CAPACITY_PLAYER = 20;

	private final int[] _encumbranceByItemType;
	private final Map<Block, Integer> _blockCapacities;

	private InventoryAspect(int[] encumbranceByItemType, Map<Block, Integer> blockCapacities)
	{
		_encumbranceByItemType = encumbranceByItemType;
		_blockCapacities = blockCapacities;
	}

	public int getInventoryCapacity(Block block)
	{
		Integer capacity = _blockCapacities.get(block);
		// We default to 0 if not specified and not using a special over-ride.
		return (null != capacity) ? capacity : 0;
	}

	public int getEncumbrance(Item item)
	{
		return _encumbranceByItemType[item.number()];
	}
}
