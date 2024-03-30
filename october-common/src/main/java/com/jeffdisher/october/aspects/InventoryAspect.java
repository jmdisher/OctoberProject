package com.jeffdisher.october.aspects;

import java.io.IOException;
import java.io.InputStream;

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
	 * @return The aspect (never null).
	 * @throws IOException There was a problem with a stream.
	 * @throws TabListReader.TabListException A tablist was malformed.
	 */
	public static InventoryAspect load(ItemRegistry items, BlockAspect blocks, InputStream encumbranceStream) throws IOException, TabListReader.TabListException
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
		return new InventoryAspect(blocks, encumbranceByItemType);
	}

	/**
	 * The capacity of an air block is small since it is just "on the ground" and we want containers to be used.
	 */
	public static final int CAPACITY_AIR = 10;
	public static final int CAPACITY_PLAYER = 20;
	public static final int CAPACITY_CRAFTING_TABLE = CAPACITY_AIR;

	private final BlockAspect _blocks;
	private final int[] _encumbranceByItemType;

	private InventoryAspect(BlockAspect blocks, int[] encumbranceByItemType)
	{
		_blocks = blocks;
		_encumbranceByItemType = encumbranceByItemType;
	}

	public int getInventoryCapacity(Block block)
	{
		// Here, we will opt-in to specific item types, only returning 0 if the block type has no inventory.
		int size;
		// We will treat any block where the entity can walk as an "air inventory".
		if (_blocks.permitsEntityMovement(block))
		{
			size = CAPACITY_AIR;
		}
		else if ((_blocks.CRAFTING_TABLE == block)
				|| (_blocks.FURNACE == block)
		)
		{
			size = CAPACITY_CRAFTING_TABLE;
		}
		else
		{
			// We default to 0.
			size = 0;
		}
		return size;
	}

	public int getEncumbrance(Item item)
	{
		return _encumbranceByItemType[item.number()];
	}
}
