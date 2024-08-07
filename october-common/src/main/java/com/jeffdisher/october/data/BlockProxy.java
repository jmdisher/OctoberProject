package com.jeffdisher.october.data;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.StationRegistry;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.FuelState;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;


/**
 * A proxy to access immutable block data within a specific cuboid snapshot.
 */
public class BlockProxy implements IBlockProxy
{
	public static Inventory getDefaultNormalOrEmptyBlockInventory(Environment env, Block block)
	{
		int size = env.stations.getNormalInventorySize(block);
		if (0 == size)
		{
			// If this is 0, it means that it isn't a station so we will also check if this is a block which is "empty".
			if (!env.blocks.isSolid(block))
			{
				size = StationRegistry.CAPACITY_BLOCK_EMPTY;
			}
		}
		return (size > 0)
				? Inventory.start(size).finish()
				: null
		;
	}


	private final Environment _env;
	private final BlockAddress _address;
	private final IReadOnlyCuboidData _data;
	private final Block _cachedBlock;

	public BlockProxy(BlockAddress address, IReadOnlyCuboidData data)
	{
		_env = Environment.getShared();
		_address = address;
		_data = data;
		
		// We cache the item since we use it to make some other internal decisions.
		Item rawItem = _env.items.ITEMS_BY_TYPE[_getData15(AspectRegistry.BLOCK)];
		_cachedBlock = _env.blocks.fromItem(rawItem);
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
			inv = BlockProxy.getDefaultNormalOrEmptyBlockInventory(_env, _cachedBlock);
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
		FuelState fuel = _getDataSpecial(AspectRegistry.FUELLED);
		// We can't return null if this block can support fuel.
		if (null == fuel)
		{
			int fuelInventorySize = _env.stations.getFuelInventorySize(_cachedBlock);
			if (fuelInventorySize > 0)
			{
				fuel = new FuelState(0, null, Inventory.start(fuelInventorySize).finish());
			}
		}
		return fuel;
	}

	@Override
	public byte getLight()
	{
		return _getData7(AspectRegistry.LIGHT);
	}

	@Override
	public byte getLogic()
	{
		return _getData7(AspectRegistry.LOGIC);
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
