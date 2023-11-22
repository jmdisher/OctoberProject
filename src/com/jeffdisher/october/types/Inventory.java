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
}
