package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import com.jeffdisher.october.registries.Craft;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
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
		// This is an enum so just read a short as ordinal.
		short ordinal = buffer.getShort();
		return Craft.values()[ordinal];
	}

	public static void writeCraft(ByteBuffer buffer, Craft operation)
	{
		// This is an enum so just send ordinal as a short.
		short ordinal = (short)operation.ordinal();
		buffer.putShort(ordinal);
	}

	public static Entity readEntity(ByteBuffer buffer)
	{
		int id = buffer.getInt();
		EntityLocation location = _readEntityLocation(buffer);
		float zVelocityPerSecond = buffer.getFloat();
		EntityVolume volume = _readEntityVolume(buffer);
		float blocksPerTickSpeed = buffer.getFloat();
		Inventory inventory = _readInventory(buffer);
		Item selectedItem = _readItemNoAir(buffer);
		
		return new Entity(id
				, location
				, zVelocityPerSecond
				, volume
				, blocksPerTickSpeed
				, inventory
				, selectedItem
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
		Item selectedItem = entity.selectedItem();
		
		buffer.putInt(id);
		_writeEntityLocation(buffer, location);
		buffer.putFloat(zVelocityPerSecond);
		_writeEntityVolume(buffer, volume);
		buffer.putFloat(blocksPerTickSpeed);
		_writeInventory(buffer, inventory);
		_writeItemNoAir(buffer, selectedItem);
	}


	private static Item _readItemNoAir(ByteBuffer buffer)
	{
		// We look these up by number - we treat 0 "air" as null.
		short number = buffer.getShort();
		return (0 != number)
				? ItemRegistry.BLOCKS_BY_TYPE[number]
				: null
		;
	}

	private static void _writeItemNoAir(ByteBuffer buffer, Item item)
	{
		// We look these up by number so just send that - 0 is "null" since we can't send air as an item.
		Assert.assertTrue(ItemRegistry.AIR != item);
		short number = (null != item)
				? item.number()
				: 0
		;
		buffer.putShort(number);
	}

	private static Items _readItems(ByteBuffer buffer)
	{
		Item type = _readItemNoAir(buffer);
		int count = buffer.getInt();
		return new Items(type, count);
	}

	private static void _writeItems(ByteBuffer buffer, Items items)
	{
		_writeItemNoAir(buffer, items.type());
		buffer.putInt(items.count());
	}

	private static Inventory _readInventory(ByteBuffer buffer)
	{
		int maxEncumbrance = buffer.getInt();
		Inventory.Builder builder = Inventory.start(maxEncumbrance);
		int itemsToLoad = Byte.toUnsignedInt(buffer.get());
		for (int i = 0; i < itemsToLoad; ++i)
		{
			Items items = _readItems(buffer);
			builder.add(items.type(), items.count());
		}
		return builder.finish();
	}

	private static void _writeInventory(ByteBuffer buffer, Inventory inventory)
	{
		int maxEncumbrance = inventory.maxEncumbrance;
		// We don't currently limit how many items can be serialized in one Inventory since it should never fill a packet.
		int itemsToWrite = inventory.items.size();
		// We only store the size as a byte.
		Assert.assertTrue(itemsToWrite < 256);
		
		buffer.putInt(maxEncumbrance);
		buffer.put((byte) itemsToWrite);
		for (Items items : inventory.items.values())
		{
			_writeItems(buffer, items);
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
}