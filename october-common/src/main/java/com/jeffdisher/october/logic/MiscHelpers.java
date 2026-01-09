package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.IOctree;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
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

	/**
	 * A utility method used to find the closest instance of a block of type to the given centre location by searching
	 * the given set of cuboids.
	 * 
	 * @param cuboids The cuboids to search.
	 * @param centre Find the closest block to this centre of the search area.
	 * @param type The block type.
	 * @return The location of the nearest block of the given type, or null if there isn't one.
	 */
	public static AbsoluteLocation findClosestBlock(Set<IReadOnlyCuboidData> cuboids, AbsoluteLocation centre, Block type)
	{
		AbsoluteLocation[] outParam = new AbsoluteLocation[1];
		AbsoluteLocation[] cuboidBase = new AbsoluteLocation[1];
		
		short blockToMatch = type.item().number();
		IOctree.IWalkerCallback<Short> walker = new IOctree.IWalkerCallback<>() {
			int squareDistance = Integer.MAX_VALUE;
			@Override
			public void visit(BlockAddress base, byte size, Short value)
			{
				if (blockToMatch == value)
				{
					AbsoluteLocation absoluteBase = cuboidBase[0].relativeForBlock(base);
					AbsoluteLocation inBase = _getClosest(centre, absoluteBase, size);
					int deltaX = centre.x() - inBase.x();
					int deltaY = centre.y() - inBase.y();
					int deltaZ = centre.z() - inBase.z();
					int thisDistance = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
					if (thisDistance < squareDistance)
					{
						squareDistance = thisDistance;
						outParam[0] = inBase;
					}
				}
			}
		};
		
		// We need to skip something so just make it the next value.
		short valueToSkip = (short)(blockToMatch + 1);
		for (IReadOnlyCuboidData cuboid : cuboids)
		{
			cuboidBase[0] = cuboid.getCuboidAddress().getBase();
			cuboid.walkData(AspectRegistry.BLOCK, walker, valueToSkip);
		}
		return outParam[0];
	}


	private static AbsoluteLocation _getClosest(AbsoluteLocation centre, AbsoluteLocation absoluteBase, byte size)
	{
		int lowX = absoluteBase.x();
		int highX = lowX + size  - 1;
		int lowY = absoluteBase.y();
		int highY = lowY + size  - 1;
		int lowZ = absoluteBase.z();
		int highZ = lowZ + size  - 1;
		
		int inX = _closestOneDimension(centre.x(), lowX, highX);
		int inY = _closestOneDimension(centre.y(), lowY, highY);
		int inZ = _closestOneDimension(centre.z(), lowZ, highZ);
		return new AbsoluteLocation(inX, inY, inZ);
	}

	public static int _closestOneDimension(int search, int low, int high)
	{
		int in;
		if (search < low)
		{
			in = low;
		}
		else if (search > high)
		{
			in = high;
		}
		else
		{
			in = search;
		}
		return in;
	}
}
