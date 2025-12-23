package com.jeffdisher.october.aspects;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.config.TabListReader.TabListException;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Item;


/**
 * Helpers for interacting with the SPECIAL_ITEM_SLOT aspect.
 */
public class SpecialSlotAspect
{
	public static SpecialSlotAspect load(ItemRegistry items
		, BlockAspect blocks
		, InputStream specialSlotStream
	) throws IOException, TabListException
	{
		Set<Block> hasSpecial = new HashSet<>();
		TabListReader.IParseCallbacks callbacks = new TabListReader.IParseCallbacks() {
			@Override
			public void startNewRecord(String name, String[] parameters) throws TabListException
			{
				if (0 != parameters.length)
				{
					throw new TabListReader.TabListException("No parameters expected for: \"" + name + "\"");
				}
				
				Item item = items.getItemById(name);
				if (null == item)
				{
					throw new TabListReader.TabListException("Not a valid item: \"" + name + "\"");
				}
				Block block = blocks.fromItem(item);
				if (null == block)
				{
					throw new TabListReader.TabListException("Not a block: \"" + name + "\"");
				}
				boolean didAdd = hasSpecial.add(block);
				if (!didAdd)
				{
					throw new TabListReader.TabListException("Duplicate block: \"" + name + "\"");
				}
			}
			@Override
			public void endRecord() throws TabListException
			{
			}
			@Override
			public void processSubRecord(String name, String[] parameters) throws TabListException
			{
				throw new TabListReader.TabListException("No sub-records expected");
			}
		};
		TabListReader.readEntireFile(callbacks, specialSlotStream);
		
		return new SpecialSlotAspect(hasSpecial);
	}


	private final Set<Block> _hasSpecial;

	private SpecialSlotAspect(Set<Block> hasSpecial)
	{
		_hasSpecial = Collections.unmodifiableSet(hasSpecial);
	}

	/**
	 * Checks if this block type can have a special slot.
	 * 
	 * @param block The block type.
	 * @return True if this block type can have a special slot, false if it should always be left null.
	 */
	public boolean hasSpecialSlot(Block block)
	{
		return _hasSpecial.contains(block);
	}
}
