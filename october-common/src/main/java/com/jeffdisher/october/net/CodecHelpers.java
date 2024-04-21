package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Craft;
import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.FuelState;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.utils.Assert;


public class CodecHelpers
{
	public static String readString(ByteBuffer buffer)
	{
		int length = Short.toUnsignedInt(buffer.getShort());
		byte[] data = new byte[length];
		buffer.get(data);
		return new String(data, StandardCharsets.UTF_8);
	}

	public static void writeString(ByteBuffer buffer, String value)
	{
		byte[] data = value.getBytes(StandardCharsets.UTF_8);
		int length = data.length;
		Assert.assertTrue(length <= Short.MAX_VALUE);
		buffer.putShort((short)length);
		buffer.put(data);
	}

	public static CuboidAddress readCuboidAddress(ByteBuffer buffer)
	{
		short x = buffer.getShort();
		short y = buffer.getShort();
		short z = buffer.getShort();
		return new CuboidAddress(x, y, z);
	}

	public static void writeCuboidAddress(ByteBuffer buffer, CuboidAddress value)
	{
		buffer.putShort(value.x());
		buffer.putShort(value.y());
		buffer.putShort(value.z());
	}

	public static Inventory readInventory(ByteBuffer buffer)
	{
		return _readInventory(buffer);
	}

	public static void writeInventory(ByteBuffer buffer, Inventory inventory)
	{
		_writeInventory(buffer, inventory);
	}

	public static EntityLocation readEntityLocation(ByteBuffer buffer)
	{
		return _readEntityLocation(buffer);
	}

	public static void writeEntityLocation(ByteBuffer buffer, EntityLocation location)
	{
		_writeEntityLocation(buffer, location);
	}

	public static AbsoluteLocation readAbsoluteLocation(ByteBuffer buffer)
	{
		int x = buffer.getInt();
		int y = buffer.getInt();
		int z = buffer.getInt();
		return new AbsoluteLocation(x, y, z);
	}

	public static void writeAbsoluteLocation(ByteBuffer buffer, AbsoluteLocation location)
	{
		buffer.putInt(location.x());
		buffer.putInt(location.y());
		buffer.putInt(location.z());
	}

	public static Item readItem(ByteBuffer buffer)
	{
		return _readItemNoAir(buffer);
	}

	public static void writeItem(ByteBuffer buffer, Item item)
	{
		_writeItemNoAir(buffer, item);
	}

	public static Items readItems(ByteBuffer buffer)
	{
		return _readItems(buffer);
	}

	public static void writeItems(ByteBuffer buffer, Items items)
	{
		_writeItems(buffer, items);
	}

	public static Craft readCraft(ByteBuffer buffer)
	{
		return _readCraft(buffer);
	}

	public static void writeCraft(ByteBuffer buffer, Craft operation)
	{
		_writeCraft(buffer, operation);
	}

	public static Entity readEntity(ByteBuffer buffer)
	{
		int id = buffer.getInt();
		EntityLocation location = _readEntityLocation(buffer);
		float zVelocityPerSecond = buffer.getFloat();
		EntityVolume volume = _readEntityVolume(buffer);
		float blocksPerTickSpeed = buffer.getFloat();
		Inventory inventory = _readInventory(buffer);
		int selectedItemKey = buffer.getInt();
		CraftOperation localCraftOperation = _readCraftOperation(buffer);
		byte health = buffer.get();
		byte food = buffer.get();
		
		return new Entity(id
				, location
				, zVelocityPerSecond
				, volume
				, blocksPerTickSpeed
				, inventory
				, selectedItemKey
				, localCraftOperation
				, health
				, food
		);
	}

	public static void writeEntity(ByteBuffer buffer, Entity entity)
	{
		int id = entity.id();
		EntityLocation location = entity.location();
		float zVelocityPerSecond = entity.zVelocityPerSecond();
		EntityVolume volume = entity.volume();
		float blocksPerTickSpeed = entity.blocksPerTickSpeed();
		Inventory inventory = entity.inventory();
		int selectedItemKey = entity.selectedItemKey();
		CraftOperation localCraftOperation = entity.localCraftOperation();
		
		buffer.putInt(id);
		_writeEntityLocation(buffer, location);
		buffer.putFloat(zVelocityPerSecond);
		_writeEntityVolume(buffer, volume);
		buffer.putFloat(blocksPerTickSpeed);
		_writeInventory(buffer, inventory);
		buffer.putInt(selectedItemKey);
		_writeCraftOperation(buffer, localCraftOperation);
		buffer.put(entity.health());
		buffer.put(entity.food());
	}

	public static PartialEntity readPartialEntity(ByteBuffer buffer)
	{
		int id = buffer.getInt();
		EntityLocation location = _readEntityLocation(buffer);
		float zVelocityPerSecond = buffer.getFloat();
		EntityVolume volume = _readEntityVolume(buffer);
		return new PartialEntity(id
				, location
				, zVelocityPerSecond
				, volume
		);
	}

	public static void writePartialEntity(ByteBuffer buffer, PartialEntity entity)
	{
		int id = entity.id();
		EntityLocation location = entity.location();
		float zVelocityPerSecond = entity.zVelocityPerSecond();
		EntityVolume volume = entity.volume();
		
		buffer.putInt(id);
		_writeEntityLocation(buffer, location);
		buffer.putFloat(zVelocityPerSecond);
		_writeEntityVolume(buffer, volume);
	}

	public static CraftOperation readCraftOperation(ByteBuffer buffer)
	{
		return _readCraftOperation(buffer);
	}

	public static void writeCraftOperation(ByteBuffer buffer, CraftOperation operation)
	{
		_writeCraftOperation(buffer, operation);
	}

	public static FuelState readFuelState(ByteBuffer buffer)
	{
		// We will use -1 as the "null".
		int millisFueled = buffer.getInt();
		FuelState result;
		if (millisFueled >= 0)
		{
			Item currentFuel = _readItemNoAir(buffer);
			Inventory fuelInventory = _readInventory(buffer);
			result = new FuelState(millisFueled, currentFuel, fuelInventory);
		}
		else
		{
			Assert.assertTrue(-1 == millisFueled);
			result = null;
		}
		return result;
	}

	public static void writeFuelState(ByteBuffer buffer, FuelState fuel)
	{
		if (null != fuel)
		{
			buffer.putInt(fuel.millisFueled());
			_writeItemNoAir(buffer, fuel.currentFuel());
			_writeInventory(buffer, fuel.fuelInventory());
		}
		else
		{
			// Write the -1.
			buffer.putInt(-1);
		}
	}

	public static NonStackableItem readNonStackableItem(ByteBuffer buffer)
	{
		return _readNonStackableItem(buffer);
	}

	public static void writeNonStackableItem(ByteBuffer buffer, NonStackableItem item)
	{
		_writeNonStackableItem(buffer, item);
	}


	private static Item _readItemNoAir(ByteBuffer buffer)
	{
		Environment env = Environment.getShared();
		// We look these up by number - we treat 0 "air" as null.
		short number = buffer.getShort();
		return (0 != number)
				? env.items.ITEMS_BY_TYPE[number]
				: null
		;
	}

	private static void _writeItemNoAir(ByteBuffer buffer, Item item)
	{
		Environment env = Environment.getShared();
		// We look these up by number so just send that - 0 is "null" since we can't send air as an item.
		Assert.assertTrue(env.items.AIR != item);
		short number = (null != item)
				? item.number()
				: 0
		;
		buffer.putShort(number);
	}

	private static Items _readItems(ByteBuffer buffer)
	{
		Items result;
		Item type = _readItemNoAir(buffer);
		if (null != type)
		{
			int count = buffer.getInt();
			result = new Items(type, count);
		}
		else
		{
			result = null;
		}
		return result;
	}

	private static void _writeItems(ByteBuffer buffer, Items items)
	{
		if (null != items)
		{
			_writeItemNoAir(buffer, items.type());
			buffer.putInt(items.count());
		}
		else
		{
			_writeItemNoAir(buffer, null);
		}
	}

	private static Inventory _readInventory(ByteBuffer buffer)
	{
		Environment env = Environment.getShared();
		Inventory parsed;
		int maxEncumbrance = buffer.getInt();
		if (maxEncumbrance > 0)
		{
			Map<Integer, Items> stackableItems = new HashMap<>();
			Map<Integer, NonStackableItem> nonStackableItems = new HashMap<>();
			int currentEncumbrance = 0;
			
			int itemsToLoad = Byte.toUnsignedInt(buffer.get());
			for (int i = 0; i < itemsToLoad; ++i)
			{
				int keyValue = buffer.getInt();
				Assert.assertTrue(keyValue > 0);
				// TODO:  Add non-stackable support, once we have the full support for that implemented.
				Items items = _readItems(buffer);
				stackableItems.put(keyValue, items);
				currentEncumbrance += env.inventory.getEncumbrance(items.type()) * items.count();
			}
			parsed = Inventory.build(maxEncumbrance, stackableItems, nonStackableItems, currentEncumbrance);
		}
		else
		{
			// The 0 value is used to describe a null.
			Assert.assertTrue(0 == maxEncumbrance);
			parsed = null;
		}
		return parsed;
	}

	private static void _writeInventory(ByteBuffer buffer, Inventory inventory)
	{
		if (null != inventory)
		{
			int maxEncumbrance = inventory.maxEncumbrance;
			// We don't currently limit how many items can be serialized in one Inventory since it should never fill a packet.
			List<Integer> keys = inventory.sortedKeys();
			int itemsToWrite = keys.size();
			// We only store the size as a byte.
			Assert.assertTrue(itemsToWrite < 256);
			
			buffer.putInt(maxEncumbrance);
			buffer.put((byte) itemsToWrite);
			for (Integer key : keys)
			{
				buffer.putInt(key.intValue());
				Items stackable = inventory.getStackForKey(key);
				// TODO:  Add non-stackable support, once we have the full support for that implemented.
				Assert.assertTrue(null != stackable);
				_writeItems(buffer, stackable);
			}
		}
		else
		{
			// We overload the maxEncumbrance as 0 to describe a null.
			buffer.putInt(0);
		}
	}

	private static EntityLocation _readEntityLocation(ByteBuffer buffer)
	{
		float x = buffer.getFloat();
		float y = buffer.getFloat();
		float z = buffer.getFloat();
		return new EntityLocation(x, y, z);
	}

	private static void _writeEntityLocation(ByteBuffer buffer, EntityLocation location)
	{
		buffer.putFloat(location.x());
		buffer.putFloat(location.y());
		buffer.putFloat(location.z());
	}

	private static EntityVolume _readEntityVolume(ByteBuffer buffer)
	{
		float height = buffer.getFloat();
		float width = buffer.getFloat();
		return new EntityVolume(height, width);
	}

	private static void _writeEntityVolume(ByteBuffer buffer, EntityVolume volume)
	{
		float height = volume.height();
		float width = volume.width();
		buffer.putFloat(height);
		buffer.putFloat(width);
	}

	private static CraftOperation _readCraftOperation(ByteBuffer buffer)
	{
		CraftOperation parsed;
		// We encode the millis completed first so that 0 can mean null.
		long completedMillis = buffer.getLong();
		if (completedMillis > 0)
		{
			Craft selectedCraft = _readCraft(buffer);
			parsed = new CraftOperation(selectedCraft, completedMillis);
		}
		else
		{
			Assert.assertTrue(0 == completedMillis);
			parsed = null;
		}
		return parsed;
	}

	private static void _writeCraftOperation(ByteBuffer buffer, CraftOperation operation)
	{
		if (null != operation)
		{
			long completedMillis = operation.completedMillis();
			Assert.assertTrue(completedMillis > 0);
			buffer.putLong(completedMillis);
			_writeCraft(buffer, operation.selectedCraft());
		}
		else
		{
			// We overload the completedMillis as 0 to describe a null.
			buffer.putLong(0L);
		}
	}

	private static Craft _readCraft(ByteBuffer buffer)
	{
		Environment env = Environment.getShared();
		// Each craft has a numbered index.
		short ordinal = buffer.getShort();
		Craft craft;
		if (-1 == ordinal)
		{
			// -1 is our "null"
			craft = null;
		}
		else
		{
			craft = env.crafting.CRAFTING_OPERATIONS[ordinal];
		}
		return craft;
	}

	private static void _writeCraft(ByteBuffer buffer, Craft operation)
	{
		// We will use -1 as a "null".
		if (null == operation)
		{
			buffer.putShort((short) -1);
		}
		else
		{
			// Each craft has a numbered index.
			short ordinal = operation.number;
			buffer.putShort(ordinal);
		}
	}

	private static NonStackableItem _readNonStackableItem(ByteBuffer buffer)
	{
		Item item = _readItemNoAir(buffer);
		return (null != item)
				? new NonStackableItem(item)
				: null
		;
	}

	private static void _writeNonStackableItem(ByteBuffer buffer, NonStackableItem item)
	{
		Item underlying = (null != item)
				? item.type()
				: null
		;
		_writeItemNoAir(buffer, underlying);
	}
}
