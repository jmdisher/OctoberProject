package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.OrientationAspect;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.Craft;
import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.FuelState;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.utils.Assert;


public class CodecHelpers
{
	/**
	 * Used to encode a null in some optional cases.
	 */
	public static final byte NULL_BYTE = 0;
	/**
	 * Used to encode that a non-null value follows in some optional cases.
	 */
	public static final byte NON_NULL_BYTE = 1;

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

	public static EntityLocation readNullableEntityLocation(ByteBuffer buffer)
	{
		byte nullBit = buffer.get();
		return (NULL_BYTE == nullBit)
				? null
				: _readEntityLocation(buffer)
		;
	}

	public static void writeEntityLocation(ByteBuffer buffer, EntityLocation location)
	{
		_writeEntityLocation(buffer, location);
	}

	public static void writeNullableEntityLocation(ByteBuffer buffer, EntityLocation location)
	{
		if (null != location)
		{
			buffer.put(NON_NULL_BYTE);
			_writeEntityLocation(buffer, location);
		}
		else
		{
			buffer.put(NULL_BYTE);
		}
	}

	public static AbsoluteLocation readAbsoluteLocation(ByteBuffer buffer)
	{
		return _readAbsoluteLocation(buffer);
	}

	public static void writeAbsoluteLocation(ByteBuffer buffer, AbsoluteLocation location)
	{
		_writeAbsoluteLocation(buffer, location);
	}

	public static AbsoluteLocation readNullableAbsoluteLocation(ByteBuffer buffer)
	{
		AbsoluteLocation location = null;
		boolean isNonNull = _readBoolean(buffer);
		if (isNonNull)
		{
			location = _readAbsoluteLocation(buffer);
		}
		return location;
	}

	public static void writeNullableAbsoluteLocation(ByteBuffer buffer, AbsoluteLocation location)
	{
		boolean isNonNull = (null != location);
		_writeBoolean(buffer, isNonNull);
		if (isNonNull)
		{
			_writeAbsoluteLocation(buffer, location);
		}
	}

	public static Item readItem(ByteBuffer buffer)
	{
		return _readItem(buffer);
	}

	public static void writeItem(ByteBuffer buffer, Item item)
	{
		_writeItem(buffer, item);
	}

	public static Block readBlock(ByteBuffer buffer)
	{
		Block block = null;
		Item item = _readItem(buffer);
		if (null != item)
		{
			block = Environment.getShared().blocks.fromItem(item);
		}
		return block;
	}

	public static void writeBlock(ByteBuffer buffer, Block block)
	{
		Item item = (null != block) ? block.item() : null;
		_writeItem(buffer, item);
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
		boolean isCreativeMode = _readBoolean(buffer);
		EntityLocation location = _readEntityLocation(buffer);
		EntityLocation velocity = _readEntityLocation(buffer);
		byte yaw = buffer.get();
		byte pitch = buffer.get();
		Inventory inventory = _readInventory(buffer);
		int[] hotbar = new int[Entity.HOTBAR_SIZE];
		for (int i = 0; i < hotbar.length; ++i)
		{
			hotbar[i] = buffer.getInt();
		}
		int hotbarIndex = buffer.getInt();
		NonStackableItem[] armour = new NonStackableItem[BodyPart.values().length];
		for (int i = 0; i < armour.length; ++i)
		{
			armour[i] = _readNonStackableItem(buffer);
		}
		CraftOperation localCraftOperation = _readCraftOperation(buffer);
		byte health = buffer.get();
		byte food = buffer.get();
		byte breath = buffer.get();
		int energyDeficit = buffer.getInt();
		EntityLocation spawn = _readEntityLocation(buffer);
		
		return new Entity(id
				, isCreativeMode
				, location
				, velocity
				, yaw
				, pitch
				, inventory
				, hotbar
				, hotbarIndex
				, armour
				, localCraftOperation
				, health
				, food
				, breath
				, energyDeficit
				, spawn
				, Entity.EMPTY_DATA
		);
	}

	public static void writeEntity(ByteBuffer buffer, Entity entity)
	{
		int id = entity.id();
		boolean isCreativeMode = entity.isCreativeMode();
		EntityLocation location = entity.location();
		EntityLocation velocity = entity.velocity();
		byte yaw = entity.yaw();
		byte pitch = entity.pitch();
		Inventory inventory = entity.inventory();
		int[] hotbar = entity.hotbarItems();
		int hotbarIndex = entity.hotbarIndex();
		NonStackableItem[] armour = entity.armourSlots();
		CraftOperation localCraftOperation = entity.localCraftOperation();
		
		buffer.putInt(id);
		_writeBoolean(buffer, isCreativeMode);
		_writeEntityLocation(buffer, location);
		_writeEntityLocation(buffer, velocity);
		buffer.put(yaw);
		buffer.put(pitch);
		_writeInventory(buffer, inventory);
		for (int key : hotbar)
		{
			buffer.putInt(key);
		}
		buffer.putInt(hotbarIndex);
		for (NonStackableItem piece : armour)
		{
			_writeNonStackableItem(buffer, piece);
		}
		_writeCraftOperation(buffer, localCraftOperation);
		buffer.put(entity.health());
		buffer.put(entity.food());
		buffer.put(entity.breath());
		buffer.putInt(entity.energyDeficit());
		_writeEntityLocation(buffer, entity.spawnLocation());
	}

	public static PartialEntity readPartialEntity(ByteBuffer buffer)
	{
		Environment env = Environment.getShared();
		int id = buffer.getInt();
		byte ordinal = buffer.get();
		EntityType type = env.creatures.ENTITY_BY_NUMBER[ordinal];
		EntityLocation location = _readEntityLocation(buffer);
		byte yaw = buffer.get();
		byte pitch = buffer.get();
		byte health = buffer.get();
		return new PartialEntity(id
				, type
				, location
				, yaw
				, pitch
				, health
		);
	}

	public static void writePartialEntity(ByteBuffer buffer, PartialEntity entity)
	{
		int id = entity.id();
		byte ordinal = entity.type().number();
		Assert.assertTrue((byte)0 != ordinal);
		EntityLocation location = entity.location();
		byte health = entity.health();
		byte yaw = entity.yaw();
		byte pitch = entity.pitch();
		
		buffer.putInt(id);
		buffer.put(ordinal);
		_writeEntityLocation(buffer, location);
		buffer.put(yaw);
		buffer.put(pitch);
		buffer.put(health);
	}

	public static CreatureEntity readCreatureEntity(int idToAssign, ByteBuffer buffer)
	{
		Environment env = Environment.getShared();
		// The IDs for creatures are assigned late.
		int id = idToAssign;
		byte ordinal = buffer.get();
		EntityType type = env.creatures.ENTITY_BY_NUMBER[ordinal];
		EntityLocation location = _readEntityLocation(buffer);
		EntityLocation velocity = _readEntityLocation(buffer);
		byte yaw = buffer.get();
		byte pitch = buffer.get();
		byte health = buffer.get();
		byte breath = buffer.get();
		
		return new CreatureEntity(id
				, type
				, location
				, velocity
				, yaw
				, pitch
				, health
				, breath
				
				, CreatureEntity.EMPTY_DATA
		);
	}

	public static void writeCreatureEntity(ByteBuffer buffer, CreatureEntity entity)
	{
		// Note that we don't serialize the IDs for creatures.
		byte ordinal = entity.type().number();
		Assert.assertTrue((byte)0 != ordinal);
		EntityLocation location = entity.location();
		EntityLocation velocity = entity.velocity();
		byte yaw = entity.yaw();
		byte pitch = entity.pitch();
		byte health = entity.health();
		byte breath = entity.breath();
		
		buffer.put(ordinal);
		_writeEntityLocation(buffer, location);
		_writeEntityLocation(buffer, velocity);
		buffer.put(yaw);
		buffer.put(pitch);
		buffer.put(health);
		buffer.put(breath);
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
		int millisFuelled = buffer.getInt();
		FuelState result;
		if (millisFuelled >= 0)
		{
			Item currentFuel = _readItem(buffer);
			Inventory fuelInventory = _readInventory(buffer);
			result = new FuelState(millisFuelled, currentFuel, fuelInventory);
		}
		else
		{
			Assert.assertTrue(-1 == millisFuelled);
			result = null;
		}
		return result;
	}

	public static void writeFuelState(ByteBuffer buffer, FuelState fuel)
	{
		if (null != fuel)
		{
			buffer.putInt(fuel.millisFuelled());
			_writeItem(buffer, fuel.currentFuel());
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

	public static BodyPart readBodyPart(ByteBuffer buffer)
	{
		// We will use -1 as the "null".
		int index = buffer.getInt();
		BodyPart result;
		if (index >= 0)
		{
			result = BodyPart.values()[index];
		}
		else
		{
			result = null;
		}
		return result;
	}

	public static void writeBodyPart(ByteBuffer buffer, BodyPart part)
	{
		if (null != part)
		{
			buffer.putInt(part.ordinal());
		}
		else
		{
			// Write the -1.
			buffer.putInt(-1);
		}
	}

	public static boolean readBoolean(ByteBuffer buffer)
	{
		return _readBoolean(buffer);
	}

	public static void writeBoolean(ByteBuffer buffer, boolean flag)
	{
		_writeBoolean(buffer, flag);
	}

	public static OrientationAspect.Direction readOrientation(ByteBuffer buffer)
	{
		byte val = buffer.get();
		return ((byte)0xFF == val)
				? null
				: OrientationAspect.byteToDirection(val)
		;
	}

	public static void writeOrientation(ByteBuffer buffer, OrientationAspect.Direction orientation)
	{
		byte val = (null == orientation)
				? (byte)0xFF
				: OrientationAspect.directionToByte(orientation)
		;
		buffer.put(val);
	}

	@SuppressWarnings("unchecked")
	public static <T extends IMutableMinimalEntity> IEntitySubAction<T> readNullableNestedChange(ByteBuffer buffer)
	{
		byte nullBit = buffer.get();
		return (NULL_BYTE == nullBit)
				? null
				: (IEntitySubAction<T>) EntitySubActionCodec.parseAndSeekFlippedBuffer(buffer)
		;
	}

	@SuppressWarnings("unchecked")
	public static <T extends IMutableMinimalEntity> void writeNullableNestedChange(ByteBuffer buffer, IEntitySubAction<T> nested)
	{
		if (null != nested)
		{
			buffer.put(NON_NULL_BYTE);
			EntitySubActionCodec.serializeToBuffer(buffer, (IEntitySubAction<IMutablePlayerEntity>) nested);
		}
		else
		{
			buffer.put(NULL_BYTE);
		}
	}


	private static Item _readItem(ByteBuffer buffer)
	{
		Environment env = Environment.getShared();
		// We look these up by number - we treat -1 as null.
		short number = buffer.getShort();
		return (number >= 0)
				? env.items.ITEMS_BY_TYPE[number]
				: null
		;
	}

	private static void _writeItem(ByteBuffer buffer, Item item)
	{
		// We use -1 as a null since all item numbers are >= 0.
		short number = (null != item)
				? item.number()
				: -1
		;
		buffer.putShort(number);
	}

	private static Items _readItems(ByteBuffer buffer)
	{
		Items result;
		Item type = _readItem(buffer);
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
			_writeItem(buffer, items.type());
			buffer.putInt(items.count());
		}
		else
		{
			_writeItem(buffer, null);
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
				// We will need to manually read this type in order to determine if this is stackable or not.
				Item type = _readItem(buffer);
				// NOTE:  We will inline the rest of the data since we are overlapping with types.
				if (env.durability.isStackable(type))
				{
					int count = buffer.getInt();
					Items items = new Items(type, count);
					stackableItems.put(keyValue, items);
					currentEncumbrance += env.encumbrance.getEncumbrance(items.type()) * items.count();
				}
				else
				{
					int durability = buffer.getInt();
					NonStackableItem item = new NonStackableItem(type, durability);
					nonStackableItems.put(keyValue, item);
					currentEncumbrance += env.encumbrance.getEncumbrance(item.type());
				}
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
				// See if this is a stackable or not.
				// NOTE:  We will inline the rest of the data since we are overlapping with types.
				Items stackable = inventory.getStackForKey(key);
				if (null != stackable)
				{
					_writeItem(buffer, stackable.type());
					buffer.putInt(stackable.count());
				}
				else
				{
					NonStackableItem nonStackable = inventory.getNonStackableForKey(key);
					_writeItem(buffer, nonStackable.type());
					buffer.putInt(nonStackable.durability());
				}
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
		Item item = _readItem(buffer);
		NonStackableItem nonStack;
		if (null != item)
		{
			int durability = buffer.getInt();
			nonStack = new NonStackableItem(item, durability);
		}
		else
		{
			nonStack = null;
		}
		return nonStack;
	}

	private static void _writeNonStackableItem(ByteBuffer buffer, NonStackableItem item)
	{
		Item underlying = (null != item)
				? item.type()
				: null
		;
		_writeItem(buffer, underlying);
		if (null != item)
		{
			buffer.putInt(item.durability());
		}
	}

	private static boolean _readBoolean(ByteBuffer buffer)
	{
		byte value = buffer.get();
		// This MUST be a 1 or 0.
		Assert.assertTrue((0 == value) || (1 == value));
		return (1 == value);
	}

	private static void _writeBoolean(ByteBuffer buffer, boolean flag)
	{
		byte value = (byte)(flag ? 1 : 0);
		buffer.put(value);
	}

	private static AbsoluteLocation _readAbsoluteLocation(ByteBuffer buffer)
	{
		int x = buffer.getInt();
		int y = buffer.getInt();
		int z = buffer.getInt();
		return new AbsoluteLocation(x, y, z);
	}

	private static void _writeAbsoluteLocation(ByteBuffer buffer, AbsoluteLocation location)
	{
		buffer.putInt(location.x());
		buffer.putInt(location.y());
		buffer.putInt(location.z());
	}
}
