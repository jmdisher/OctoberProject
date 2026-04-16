package com.jeffdisher.october.block_movement;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.FuelState;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.utils.Assert;


/**
 * Captures the state of block data from a BlockProxy which can be moved (since not all aspects made sense as moved) and
 * can then apply that block data to a MutableBlockProxy.
 * Note that this data is NOT serializable and assumes that the instances under BlockProxy are immutable (which is
 * true).
 */
public class MovableBlockData
{
	public static MovableBlockData fromProxy(BlockProxy proxy)
	{
		// We need to extract the value of all aspects which make sense as "movable":
		// -BLOCK
		// -INVENTORY
		// -DAMAGE
		// -CRAFTING (note that some of these will prevent the transaction, since they auto-update)
		// -FUELLED
		// -**NOT LIGHT (this is updated using a different mechanism)
		// -**NOT LOGIC (this works like light)
		// -FLAGS
		// -ORIENTATION
		// -**NOT MULTI_BLOCK_ROOT (multi-blocks can't be moved)
		// -SPECIAL_ITEM_SLOT
		// -**NOT ENCHANTING (like CRAFTING, but these always block movement)
		Assert.assertTrue(_canBeMoved(proxy));
		
		Block block = proxy.getBlock();
		Inventory inventory = proxy.getInventory();
		int damage = proxy.getDamage();
		CraftOperation crafting = proxy.getCrafting();
		FuelState fuel = proxy.getFuel();
		byte flags = proxy.getFlags();
		FacingDirection orientation = proxy.getOrientation();
		ItemSlot specialItemSlot = proxy.getSpecialSlot();
		
		return new MovableBlockData(block
			, inventory
			, damage
			, crafting
			, fuel
			, flags
			, orientation
			, specialItemSlot
		);
	}

	public static boolean canBeMoved(BlockProxy proxy)
	{
		return _canBeMoved(proxy);
	}


	private static boolean _canBeMoved(BlockProxy proxy)
	{
		// We CANNOT move multi-blocks or anything with an enchanting aspect.
		// We also won't move anything which is sensitive to logic (that might be possible but requires further analysis).
		Environment env = Environment.getShared();
		Block block = proxy.getBlock();
		boolean isMultiBlock = env.blocks.isMultiBlock(block);
		return !isMultiBlock
			&& (null == proxy.getEnchantingOperation())
			&& !env.logic.isAware(block)
		;
	}


	private final Block _block;
	private final Inventory _inventory;
	private final int _damage;
	private final CraftOperation _crafting;
	private final FuelState _fuel;
	private final byte _flags;
	private final FacingDirection _orientation;
	private final ItemSlot _specialItemSlot;

	private MovableBlockData(Block block
		, Inventory inventory
		, int damage
		, CraftOperation crafting
		, FuelState fuel
		, byte flags
		, FacingDirection orientation
		, ItemSlot specialItemSlot
	)
	{
		_block = block;
		_inventory = inventory;
		_damage = damage;
		_crafting = crafting;
		_fuel = fuel;
		_flags = flags;
		_orientation = orientation;
		_specialItemSlot = specialItemSlot;
	}

	public void clearProxyAndApply(MutableBlockProxy proxy)
	{
		// We write the block type first since that will clear other relevant data.
		proxy.setBlockAndClear(_block);
		if (null != _inventory)
		{
			proxy.setInventory(_inventory);
		}
		proxy.setDamage(_damage);
		if (null != _crafting)
		{
			proxy.setCrafting(_crafting);
		}
		if (null != _fuel)
		{
			proxy.setFuel(_fuel);
		}
		proxy.setFlags(_flags);
		proxy.setOrientation(_orientation);
		proxy.setSpecialSlot(_specialItemSlot);
	}
}
