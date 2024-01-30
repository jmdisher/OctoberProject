package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.CuboidAddress;
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

	public static byte[] readBytes(ByteBuffer buffer)
	{
		// Get the size.
		int size = Short.toUnsignedInt(buffer.getShort());
		byte[] data = new byte[size];
		// Read the buffer.
		buffer.get(data);
		return data;
	}

	public static void writeBytes(ByteBuffer buffer, byte[] data)
	{
		// Write the size.
		buffer.putShort((short)data.length);
		// Write the buffer.
		buffer.put(data);
	}

	public static Inventory readInventory(ByteBuffer buffer)
	{
		return _readInventory(buffer);
	}

	public static void writeInventory(ByteBuffer buffer, Inventory inventory)
	{
		_writeInventory(buffer, inventory);
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
}
