package com.jeffdisher.october.aspects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.jeffdisher.october.logic.PropertyHelpers;
import com.jeffdisher.october.properties.PropertyRegistry;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Enchantment;
import com.jeffdisher.october.types.Infusion;
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
 * Serialization note:  The "number" field of Enchantment and Infusion objects is considered stable and can be used for
 * serializing references to these (be aware that they are in their own namespaces and 0 is considered "null").
 */
public class EnchantmentRegistry
{
	public static final String ID_ENCHANTING_TABLE = "op.enchanting_table";
	public static final String ID_IRON_PICKAXE = "op.iron_pickaxe";
	public static final String ID_STONE = "op.stone";
	public static final String ID_STONE_BRICK = "op.stone_brick";
	public static final String ID_IRON_INGOT = "op.iron_ingot";
	public static final String ID_PORTAL_STONE = "op.void_stone";
	public static final String ID_COPPER_INGOT = "op.copper_ingot";
	public static final String ID_PORTAL_ORB = "op.portal_orb";
	public static final String ID_DIAMOND = "op.diamond";

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
		Item copperIngot = items.getItemById(ID_COPPER_INGOT);
		Assert.assertTrue(null != copperIngot);
		Item portalOrb = items.getItemById(ID_PORTAL_ORB);
		Assert.assertTrue(null != portalOrb);
		Item diamond = items.getItemById(ID_DIAMOND);
		Assert.assertTrue(null != diamond);
		
		Enchantment enchantDurability = new Enchantment(1
			, enchantingTable
			, 10_000L
			, ironPick
			, _sortedItemList(List.of(stone, stone, ironIngot, ironIngot))
			, PropertyRegistry.ENCHANT_DURABILITY
		);
		Infusion infusePortalStone = new Infusion(1
			, enchantingTable
			, 2000L
			, stoneBrick
			, _sortedItemList(List.of(stone, stone, ironIngot, ironIngot))
			, portalStone
		);
		Infusion infusePortalOrb = new Infusion(2
			, enchantingTable
			, 20_000L
			, diamond
			, _sortedItemList(List.of(copperIngot, copperIngot, ironIngot, ironIngot))
			, portalOrb
		);
		return new EnchantmentRegistry(List.of(enchantDurability)
			, List.of(infusePortalStone, infusePortalOrb)
		);
	}

	/**
	 * Checks if the given enchantment can successfully be applied to the given target.
	 * 
	 * @param target The item to enchant.
	 * @param enchantment The enchantment to apply.
	 * @return True if the enchantment can be applied to the target.
	 */
	public static boolean canApplyToTarget(NonStackableItem target, Enchantment enchantment)
	{
		return _canApplyToTarget(target, enchantment);
	}

	/**
	 * Since we want to assume that lists of item requirements can be easily compared with the List .equals(), this
	 * helper will sort a list of Item types into the canonical order for that comparison.
	 * 
	 * @param toConsume The list to sort.
	 * @return The canonically sorted list.
	 */
	public static List<Item> getCanonicallySortedList(List<Item> toConsume)
	{
		return _sortedItemList(toConsume);
	}


	private final Enchantment[] _enchantments;
	private final Infusion[] _infusions;
	private final Map<Block, List<Enchantment>> _enchantmentsByBlock;
	private final Map<Block, List<Infusion>> _infusionsByBlock;

	private EnchantmentRegistry(List<Enchantment> enchantments, List<Infusion> infusions)
	{
		// We need to leave the 0 index empty since we reserve that value as "null".
		_enchantments = new Enchantment[enchantments.size() + 1];
		for (int i = 0; i < enchantments.size(); ++i)
		{
			_enchantments[i + 1] = enchantments.get(i);
		}
		_infusions = new Infusion[infusions.size() + 1];
		for (int i = 0; i < infusions.size(); ++i)
		{
			_infusions[i + 1] = infusions.get(i);
		}
		
		_enchantmentsByBlock = _packMap(enchantments, (Enchantment input) -> input.table());
		_infusionsByBlock = _packMap(infusions, (Infusion input) -> input.table());
	}

	public List<Enchantment> allEnchantments(Block table)
	{
		return _enchantmentsByBlock.get(table);
	}

	public List<Infusion> allInfusions(Block table)
	{
		return _infusionsByBlock.get(table);
	}

	public Enchantment enchantmentForNumber(int number)
	{
		return _enchantments[number];
	}

	public Infusion infusionForNumber(int number)
	{
		return _infusions[number];
	}

	public Enchantment getEnchantment(Block table, NonStackableItem target, List<Item> toConsume)
	{
		Enchantment match = null;
		List<Enchantment> possible = _enchantmentsByBlock.get(table);
		if (null != possible)
		{
			Item targetType = target.type();
			// Filter these by anything which can apply to these arguments.
			List<Item> sorted = _sortedItemList(toConsume);
			List<Enchantment> matched = possible.stream().filter((Enchantment e) -> {
				return (targetType == e.targetItem()) && sorted.equals(e.consumedItems());
			}).toList();
			
			if (!matched.isEmpty())
			{
				// There should be only one (or there are duplicates in the enchantment config.
				Assert.assertTrue(1 == matched.size());
				Enchantment candidate = matched.get(0);
				
				if (_canApplyToTarget(target, candidate))
				{
					match = candidate;
				}
			}
		}
		return match;
	}

	public Infusion getInfusion(Block table, Item centralItem, List<Item> toConsume)
	{
		Infusion match = null;
		List<Infusion> possible = _infusionsByBlock.get(table);
		if (null != possible)
		{
			// Filter these by anything which can apply to these arguments.
			List<Item> sorted = _sortedItemList(toConsume);
			List<Infusion> matched = possible.stream().filter((Infusion e) -> {
				return (centralItem == e.centralItem()) && sorted.equals(e.consumedItems());
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

	public boolean canEnchant(Block block)
	{
		return _enchantmentsByBlock.containsKey(block) || _infusionsByBlock.containsKey(block);
	}


	private static <T> Map<Block, List<T>> _packMap(List<T> input
		, Function<T, Block> keyMap
	)
	{
		return input.stream()
			.collect(Collectors.toMap(keyMap
			, (T elt) -> {
				Block k = keyMap.apply(elt);
				return input.stream().filter((T inner) -> (k == keyMap.apply(inner))).toList();
			}
			, (List<T> one, List<T> two) -> {
				// Each sublist will be the same, we just see them for each key instance.
				Assert.assertTrue(one.equals(two));
				return one;
			}))
		;
	}

	private static List<Item> _sortedItemList(List<Item> toConsume)
	{
		List<Item> sorted = new ArrayList<>(toConsume);
		Collections.sort(sorted, (Item one, Item two) -> one.number() - two.number());
		return Collections.unmodifiableList(sorted);
	}

	private static boolean _canApplyToTarget(NonStackableItem target, Enchantment enchantment)
	{
		// Make sure that we aren't at the limit of this enchantment.
		// For now, this is just max byte but we will probably constrain this in the future.
		byte value = PropertyHelpers.getBytePropertyValue(target.properties(), enchantment.enchantmentToApply());
		return (value < Byte.MAX_VALUE);
	}
}
