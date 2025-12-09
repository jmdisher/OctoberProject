package com.jeffdisher.october.aspects;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jeffdisher.october.logic.PropertyHelpers;
import com.jeffdisher.october.properties.PropertyRegistry;
import com.jeffdisher.october.properties.PropertyType;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.utils.Assert;


/**
 * Describes the enchantment registered in the system:  What block is required to apply the enchantment, the target
 * item, the consumed items, how long it takes, and the enchantment to apply to the target.
 * Note that there are 2 variants of this case:
 * 1) A NonStackableItem to which an enchantment property is applied (called "enchantment")
 * 2) An item (stackable or not) which is deleted and replaced with another (called "infusion")
 * Note that how the enchanting block interprets this data is up to it.
 */
public class EnchantmentRegistry
{
	public static final String ID_ENCHANTING_TABLE = "op.enchanting_table";
	public static final String ID_IRON_PICKAXE = "op.iron_pickaxe";
	public static final String ID_STONE = "op.stone";
	public static final String ID_STONE_BRICK = "op.stone_brick";
	public static final String ID_IRON_INGOT = "op.iron_ingot";
	public static final String ID_PORTAL_STONE = "op.void_stone";

	public static EnchantmentRegistry load(ItemRegistry items, BlockAspect blocks)
	{
		// TODO:  Convert this logic and constant into some declarative data file.
		Block enchantingTable = blocks.fromItem(items.getItemById(ID_ENCHANTING_TABLE));
		Assert.assertTrue(null != enchantingTable);
		Item ironPick = items.getItemById(ID_IRON_PICKAXE);
		Assert.assertTrue(null != ironPick);
		Item stone = items.getItemById(ID_STONE);
		Assert.assertTrue(null != stone);
		Item stoneBrick = items.getItemById(ID_STONE_BRICK);
		Assert.assertTrue(null != stoneBrick);
		Item ironIngot = items.getItemById(ID_IRON_INGOT);
		Assert.assertTrue(null != ironIngot);
		Item portalStone = items.getItemById(ID_PORTAL_STONE);
		Assert.assertTrue(null != portalStone);
		
		Enchantment enchantDurability = new Enchantment(enchantingTable
			, 10_000L
			, ironPick
			, Map.of(stone, 2
				, ironIngot, 2
			)
			, PropertyRegistry.ENCHANT_DURABILITY
		);
		Infusion infusePortalStone = new Infusion(enchantingTable
			, 2000L
			, Map.of(stoneBrick, 1
				, stone, 2
				, ironIngot, 2
			)
			, portalStone
		);
		return new EnchantmentRegistry(Map.of(enchantingTable, List.of(enchantDurability))
			, Map.of(enchantingTable, List.of(infusePortalStone))
		);
	}


	private final Map<Block, List<Enchantment>> _enchantments;
	private final Map<Block, List<Infusion>> _infusions;

	private EnchantmentRegistry(Map<Block, List<Enchantment>> enchantments, Map<Block, List<Infusion>> infusions)
	{
		_enchantments = Collections.unmodifiableMap(enchantments);
		_infusions = Collections.unmodifiableMap(infusions);
	}

	public List<Enchantment> allEnchantments(Block table)
	{
		return _enchantments.get(table);
	}

	public List<Infusion> allInfusions(Block table)
	{
		return _infusions.get(table);
	}

	public Enchantment getEnchantment(Block table, NonStackableItem target, List<Item> toConsume)
	{
		Enchantment match = null;
		List<Enchantment> possible = _enchantments.get(table);
		if (null != possible)
		{
			Item targetType = target.type();
			Map<Item, Integer> counts = _mutableCountMap(toConsume);
			// Filter these by anything which can apply to these arguments.
			List<Enchantment> matched = possible.stream().filter((Enchantment e) -> {
				return (targetType == e.targetItem) && _mapsMatch(e.consumedItems, counts);
			}).toList();
			
			if (!matched.isEmpty())
			{
				// There should be only one (or there are duplicates in the enchantment config.
				Assert.assertTrue(1 == matched.size());
				Enchantment candidate = matched.get(0);
				
				// Make sure that we aren't at the limit of this enchantment.
				// For now, this is just max byte but we will probably constrain this in the future.
				byte value = PropertyHelpers.getBytePropertyValue(target.properties(), candidate.enchantmentToApply);
				if (value < Byte.MAX_VALUE)
				{
					// We are allowed to apply this.
					match = candidate;
				}
			}
		}
		return match;
	}

	public Infusion getInfusion(Block table, List<Item> toConsume)
	{
		Infusion match = null;
		List<Infusion> possible = _infusions.get(table);
		if (null != possible)
		{
			Map<Item, Integer> counts = _mutableCountMap(toConsume);
			// Filter these by anything which can apply to these arguments.
			List<Infusion> matched = possible.stream().filter((Infusion e) -> {
				return _mapsMatch(e.consumedItems, counts);
			}).toList();
			
			if (!matched.isEmpty())
			{
				// There should be only one (or there are duplicates in the infusion config.
				Assert.assertTrue(1 == matched.size());
				match = matched.get(0);
			}
		}
		return match;
	}


	private static Map<Item, Integer> _mutableCountMap(List<Item> raw)
	{
		Map<Item, Integer> map = new HashMap<>();
		for (Item i : raw)
		{
			int count = map.getOrDefault(i, 0);
			count += 1;
			map.put(i, count);
		}
		return map;
	}

	private static boolean _mapsMatch(Map<Item, Integer> one, Map<Item, Integer> two)
	{
		int count = one.size();
		if (count == two.size())
		{
			for (Map.Entry<Item, Integer> elt : one.entrySet())
			{
				Integer other = two.get(elt.getKey());
				if (null != other)
				{
					if (elt.getValue().intValue() == other.intValue())
					{
						count -= 1;
					}
					else
					{
						count = -1;
						break;
					}
				}
				else
				{
					count = -1;
					break;
				}
			}
		}
		else
		{
			count = -1;
		}
		return (0 == count);
	}


	public static record Enchantment(Block table
		, long millisToApply
		, Item targetItem
		, Map<Item, Integer> consumedItems
		, PropertyType<Byte> enchantmentToApply
	) {}

	public static record Infusion(Block table
		, long millisToApply
		, Map<Item, Integer> consumedItems
		, Item outputItem
	) {}
}
