package com.jeffdisher.october.data;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.EnchantingOperation;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.FuelState;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.ItemSlot;


/**
 * A proxy to access immutable block data within a specific cuboid snapshot.
 */
public class BlockProxy implements IBlockProxy
{
	public static Inventory getDefaultNormalInventory(Environment env, Block block)
	{
		int size = env.stations.getNormalInventorySize(block);
		return (size > 0)
				? Inventory.start(size).finish()
				: null
		;
	}

	/**
	 * Checks if the given address in cuboids one and two have the same aspect values.  Note that this depends on the
	 * underlying aspect value type implementing .equals(), which most don't, so changes to this type instance will
	 * cause this to return false.  This shouldn't generally be an issue if they originated from the same base data due
	 * to the copy-on-write design.
	 * 
	 * @param address The address to compare in each cuboid.
	 * @param one One cuboid.
	 * @param two The other cuboid.
	 * @return True if all the aspects match.
	 */
	public static boolean doAspectsMatch(BlockAddress address, IReadOnlyCuboidData one, IReadOnlyCuboidData two)
	{
		boolean doesMatch = true;
		if (one != two)
		{
			for (Aspect<?, ?> aspect : AspectRegistry.ALL_ASPECTS)
			{
				Object d1 = one.getDataSpecial(aspect, address);
				Object d2 = two.getDataSpecial(aspect, address);
				// NOTE:  Most actual object types don't implement this .equals() check (Inventory, for example).
				doesMatch = (d1 == d2) || ((null != d1) && (null != d2) && d1.equals(d2));
				
				if (!doesMatch)
				{
					break;
				}
			}
		}
		return doesMatch;
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
			inv = BlockProxy.getDefaultNormalInventory(_env, _cachedBlock);
		}
		return inv;
	}

	@Override
	public int getDamage()
	{
		Integer object = _getDataSpecial(AspectRegistry.DAMAGE);
		return (null != object)
			? object.intValue()
			: 0
		;
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

	@Override
	public byte getFlags()
	{
		return _getData7(AspectRegistry.FLAGS);
	}

	@Override
	public FacingDirection getOrientation()
	{
		byte ordinal = _getData7(AspectRegistry.ORIENTATION);
		return FacingDirection.byteToDirection(ordinal);
	}

	@Override
	public AbsoluteLocation getMultiBlockRoot()
	{
		return _getDataSpecial(AspectRegistry.MULTI_BLOCK_ROOT);
	}

	@Override
	public ItemSlot getSpecialSlot()
	{
		return _getDataSpecial(AspectRegistry.SPECIAL_ITEM_SLOT);
	}

	@Override
	public EnchantingOperation getEnchantingOperation()
	{
		return _getDataSpecial(AspectRegistry.ENCHANTING);
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
