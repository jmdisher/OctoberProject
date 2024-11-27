package com.jeffdisher.october.data;

import java.util.HashSet;
import java.util.Set;

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
import com.jeffdisher.october.utils.Assert;


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

	/**
	 * Checks if the receiver and the given other are backed by the same aspect values.  Note that this depends on the
	 * underlying aspect value type implementing .equals(), which most don't, so changes to this type instance will
	 * cause this to return false.  This shouldn't generally be an issue if they originated from the same base data due
	 * to the copy-on-write design.
	 * 
	 * @param other The other proxy.
	 * @return True if all the aspects match.
	 */
	public boolean doAspectsMatch(BlockProxy other)
	{
		boolean doesMatch = (_address.equals(other._address) && (_cachedBlock == other._cachedBlock));
		for (Aspect<?, ?> aspect : AspectRegistry.ALL_ASPECTS)
		{
			if (!doesMatch)
			{
				break;
			}
			if (aspect != AspectRegistry.BLOCK)
			{
				Object one = _data.getDataSpecial(aspect, _address);
				Object two = other._data.getDataSpecial(aspect, other._address);
				// NOTE:  Most actual object types don't implement this .equals() check (Inventory, for example).
				doesMatch = (one == two) || ((null != one) && (null != two) && one.equals(two));
			}
		}
		return doesMatch;
	}

	/**
	 * Checks if the receiver and the given other are backed by the same aspect values, returning the set which differs.
	 * Note that this depends on the underlying aspect value type implementing .equals(), which most don't, so changes
	 * to this type instance will typically end up in the set, no matter what.  This shouldn't generally be an issue if
	 * they originated from the same base data due to the copy-on-write design.
	 * NOTE:  The receiver and other MUST have the same block address.
	 * 
	 * @param other The other proxy.
	 * @return The set of mismatched aspects.
	 */
	public Set<Aspect<?, ?>> checkMismatchedAspects(BlockProxy other)
	{
		// It doesn't make sense to ask for this check if the blocks are in different locations.
		Assert.assertTrue(_address.equals(other._address));
		Set<Aspect<?, ?>> set = new HashSet<>();
		
		// We check the cached block instead of block aspect for performance reasons.
		if (_cachedBlock != other._cachedBlock)
		{
			set.add(AspectRegistry.BLOCK);
		}
		for (Aspect<?, ?> aspect : AspectRegistry.ALL_ASPECTS)
		{
			if (aspect != AspectRegistry.BLOCK)
			{
				Object one = _data.getDataSpecial(aspect, _address);
				Object two = other._data.getDataSpecial(aspect, other._address);
				// NOTE:  Most actual object types don't implement this .equals() check (Inventory, for example).
				boolean doesMatch = (one == two) || ((null != one) && (null != two) && one.equals(two));
				if (!doesMatch)
				{
					set.add(aspect);
				}
			}
		}
		return set;
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
