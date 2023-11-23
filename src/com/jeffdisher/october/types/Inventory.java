package com.jeffdisher.october.types;

import java.util.Collections;
import java.util.List;


/**
 * An immutable container where items are or could be.  This may be associated with a block, a player, or something
 * else.
 */
public class Inventory
{
	public final int maxEncumbrance;
	public final List<Items> items;
	public int currentEncumbrance;

	public Inventory(int maxEncumbrance, List<Items> items, int currentEncumbrance)
	{
		this.maxEncumbrance = maxEncumbrance;
		this.items = Collections.unmodifiableList(items);
		this.currentEncumbrance = currentEncumbrance;
	}
}
