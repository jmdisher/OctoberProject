package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.types.DropChance;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.utils.Assert;


/**
 * A collection of one-off helpers which don't belong anywhere in particular, just to avoid duplication.
 */
public class MiscHelpers
{
	/**
	 * The limit for random drop chance calculations.
	 */
	public static final int RANDOM_DROP_LIMIT = 100;

	/**
	 * Returns the array of items which should be dropped when random0to99 is applied to the given array of chances.
	 * 
	 * @param block The block to break.
	 * @param random0to99 A random value between [0..99] (pass 99 to only handle 100% cases).
	 * @param chances The list of possible drops.
	 * @return The array of items (never null).
	 */
	public static ItemSlot[] convertToDrops(Environment env, int random0to99, DropChance[] chances)
	{
		// First, apply the random chance to see each instance we will concretely drop.
		Assert.assertTrue(random0to99 < RANDOM_DROP_LIMIT);
		Item[] dropped = Arrays.stream(chances)
			.filter((DropChance one) -> (random0to99 < one.chance1to100()))
			.map((DropChance one) -> one.item())
			.toArray((int size) -> new Item[size])
		;
		
		// Coalesce these into the right types.
		List<NonStackableItem> nonStacking = new ArrayList<>();
		Map<Item, Integer> accumulation = new HashMap<>();
		for (Item item : dropped)
		{
			if (env.durability.isStackable(item))
			{
				Integer prev = accumulation.get(item);
				int count = (null != prev)
					? (prev.intValue() + 1)
					: 1
				;
				accumulation.put(item, count);
			}
			else
			{
				// We will just use default durability, for now.
				NonStackableItem nonStack = PropertyHelpers.newItemWithDefaults(env, item);
				nonStacking.add(nonStack);
			}
		}
		
		ItemSlot[] slots = new ItemSlot[nonStacking.size() + accumulation.size()];
		int index = 0;
		for (NonStackableItem in : nonStacking)
		{
			slots[index] = ItemSlot.fromNonStack(in);
			index += 1;
		}
		for (Map.Entry<Item, Integer> elt : accumulation.entrySet())
		{
			Items stack = new Items(elt.getKey(), elt.getValue());
			slots[index] = ItemSlot.fromStack(stack);
			index += 1;
		}
		return slots;
	}

}
