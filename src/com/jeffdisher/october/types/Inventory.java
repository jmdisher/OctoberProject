package com.jeffdisher.october.types;

import java.util.ArrayList;
import java.util.List;


/**
 * Mutable - external callers can add/remove with the items list but must update currentEncumbrance.
 */
public class Inventory
{
	public final int maxEncumbrance;
	public final List<Items> items;
	public int currentEncumbrance;

	public Inventory(int maxEncumbrance)
	{
		this.maxEncumbrance = maxEncumbrance;
		this.items = new ArrayList<>();
		this.currentEncumbrance = 0;
	}

	public Inventory copy()
	{
		Inventory copy = new Inventory(this.maxEncumbrance);
		// The "Items" elements are immutable records so we can reference them.
		copy.items.addAll(this.items);
		copy.currentEncumbrance = this.currentEncumbrance;
		return copy;
	}
}
