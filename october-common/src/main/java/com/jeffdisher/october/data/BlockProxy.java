package com.jeffdisher.october.data;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.aspects.FuelAspect;
import com.jeffdisher.october.aspects.InventoryAspect;
import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.FuelState;
import com.jeffdisher.october.types.Inventory;


/**
 * A proxy to access immutable block data within a specific cuboid snapshot.
 */
public class BlockProxy implements IBlockProxy
{
	private final BlockAddress _address;
	private final IReadOnlyCuboidData _data;
	private final Block _cachedBlock;

	public BlockProxy(BlockAddress address, IReadOnlyCuboidData data)
	{
		_address = address;
		_data = data;
		
		// We cache the item since we use it to make some other internal decisions.
		_cachedBlock = BlockAspect.BLOCKS_BY_TYPE[_getData15(AspectRegistry.BLOCK)];
	}

	@Override
	public Block getBlock()
	{
		return _cachedBlock;
	}

	@Override
	public Inventory getInventory()
	{
		Inventory inv = _getDataSpecial(AspectRegistry.INVENTORY);
		// We can't return null if this block can support one.
		if (null == inv)
		{
			int size = InventoryAspect.getInventoryCapacity(_cachedBlock);
			if (size > 0)
			{
				inv = Inventory.start(size).finish();
			}
		}
		return inv;
	}

	@Override
	public short getDamage()
	{
		return _getData15(AspectRegistry.DAMAGE);
	}

	@Override
	public CraftOperation getCrafting()
	{
		return _getDataSpecial(AspectRegistry.CRAFTING);
	}

	@Override
	public FuelState getFuel()
	{
		FuelState fuel = _getDataSpecial(AspectRegistry.FUELED);
		// We can't return null if this block can support fuel.
		if ((null == fuel) && FuelAspect.doesHaveFuelInventory(_cachedBlock.asItem()))
		{
			fuel = new FuelState(0, null, Inventory.start(FuelAspect.CAPACITY).finish());
		}
		return fuel;
	}

	@Override
	public byte getLight()
	{
		return _getData7(AspectRegistry.LIGHT);
	}


	private short _getData15(Aspect<Short, ?> type)
	{
		return _data.getData15(type, _address);
	}

	private byte _getData7(Aspect<Byte, ?> type)
	{
		return _data.getData7(type, _address);
	}

	private <T> T _getDataSpecial(Aspect<T, ?> type)
	{
		return _data.getDataSpecial(type, _address);
	}
}
