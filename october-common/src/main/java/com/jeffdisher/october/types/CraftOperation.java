package com.jeffdisher.october.types;


/**
 * Represents an in-progress crafting operation.  It contains the selected Craft action as well as the total number of
 * milliseconds completed, so far.
 * It is read-only and passive, purely operated on by mutation objects.  This also means that crafting speed is not
 * fixed as the milliseconds completed could be incremented at any speed.
 */
public record CraftOperation(Craft selectedCraft
		, long completedMillis
)
{
	public boolean isCompleted()
	{
		return this.completedMillis >= this.selectedCraft.millisPerCraft;
	}
}
