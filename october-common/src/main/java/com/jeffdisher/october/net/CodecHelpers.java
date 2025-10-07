package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.OrientationAspect;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.properties.PropertyRegistry;
import com.jeffdisher.october.properties.PropertyType;
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
import com.jeffdisher.october.types.ItemSlot;
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

	public static Inventory readInventory(DeserializationContext context)
	{
		return _readInventory(context);
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

	public static Entity readEntity(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		int id = buffer.getInt();
		boolean isCreativeMode = _readBoolean(buffer);
		EntityLocation location = _readEntityLocation(buffer);
		EntityLocation velocity = _readEntityLocation(buffer);
		byte yaw = buffer.get();
		byte pitch = buffer.get();
		Inventory inventory = _readInventory(context);
		int[] hotbar = new int[Entity.HOTBAR_SIZE];
		for (int i = 0; i < hotbar.length; ++i)
		{
			hotbar[i] = buffer.getInt();
		}
		int hotbarIndex = buffer.getInt();
		NonStackableItem[] armour = new NonStackableItem[BodyPart.values().length];
		for (int i = 0; i < armour.length; ++i)
		{
			armour[i] = _readNonStackableItem(context);
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
		Object extendedData = type.extendedCodec().read(buffer);
		return new PartialEntity(id
				, type
				, location
				, yaw
				, pitch
				, health
				, extendedData
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
		Object extendedData = entity.extendedData();
		
		buffer.putInt(id);
		buffer.put(ordinal);
		_writeEntityLocation(buffer, location);
		buffer.put(yaw);
		buffer.put(pitch);
		buffer.put(health);
		entity.type().extendedCodec().write(buffer, extendedData);
	}

	public static CreatureEntity readCreatureEntity(int idToAssign, ByteBuffer buffer, long gameTimeMillis)
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
		Object extendedData = type.extendedCodec().read(buffer);
		
		return new CreatureEntity(id
				, type
				, location
				, velocity
				, yaw
				, pitch
				, health
				, breath
				, extendedData
				
				, CreatureEntity.createEmptyEphemeral(gameTimeMillis)
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
		Object extendedData = entity.extendedData();
		
		buffer.put(ordinal);
		_writeEntityLocation(buffer, location);
		_writeEntityLocation(buffer, velocity);
		buffer.put(yaw);
		buffer.put(pitch);
		buffer.put(health);
		buffer.put(breath);
		entity.type().extendedCodec().write(buffer, extendedData);
	}

	public static CraftOperation readCraftOperation(ByteBuffer buffer)
	{
		return _readCraftOperation(buffer);
	}

	public static void writeCraftOperation(ByteBuffer buffer, CraftOperation operation)
	{
		_writeCraftOperation(buffer, operation);
	}

	public static FuelState readFuelState(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		
		// We will use -1 as the "null".
		int millisFuelled = buffer.getInt();
		FuelState result;
		if (millisFuelled >= 0)
		{
			Item currentFuel = _readItem(buffer);
			Inventory fuelInventory = _readInventory(context);
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

	public static NonStackableItem readNonStackableItem(DeserializationContext context)
	{
		return _readNonStackableItem(context);
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

	public static ItemSlot readSlot(DeserializationContext context)
	{
		return _readSlot(context);
	}

	public static void writeSlot(ByteBuffer buffer, ItemSlot slot)
	{
		_writeSlot(buffer, slot);
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

	private static Inventory _readInventory(DeserializationContext context)
	{
		Environment env = Environment.getShared();
		ByteBuffer buffer = context.buffer();
		Inventory parsed;
		int maxEncumbrance = buffer.getInt();
		if (maxEncumbrance > 0)
		{
			Map<Integer, ItemSlot> slots = new HashMap<>();
			int currentEncumbrance = 0;
			
			int itemsToLoad = Byte.toUnsignedInt(buffer.get());
			for (int i = 0; i < itemsToLoad; ++i)
			{
				int keyValue = buffer.getInt();
				Assert.assertTrue(keyValue > 0);
				
				ItemSlot slot = _readSlot(context);
				slots.put(keyValue, slot);
				currentEncumbrance += env.encumbrance.getEncumbrance(slot.getType()) * slot.getCount();
			}
			parsed = Inventory.build(maxEncumbrance, slots, currentEncumbrance);
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
				ItemSlot slot = inventory.getSlotForKey(key);
				_writeSlot(buffer, slot);
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

	private static NonStackableItem _readNonStackableItem(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		Item item = _readItem(buffer);
		NonStackableItem nonStack;
		if (null != item)
		{
			nonStack = _readNonStackableRemainder(item, context);
		}
		else
		{
			nonStack = null;
		}
		return nonStack;
	}

	private static NonStackableItem _readNonStackableRemainder(Item item, DeserializationContext context)
	{
		// This is split out since some NonStackableItem instances are read after interpreting the item type.
		ByteBuffer buffer = context.buffer();
		NonStackableItem nonStack;
		if (context.usePreV8NonStackableDecoding())
		{
			// This version was pre-Properties so we just had a durability and that is all.
			int durability = buffer.getInt();
			nonStack = new NonStackableItem(item, Map.of(PropertyRegistry.DURABILITY, durability));
		}
		else
		{
			// This uses the new property encoding so read those.
			int propertyCount = Byte.toUnsignedInt(buffer.get());
			Map<PropertyType<?>, Object> props = new HashMap<>();
			for (int i = 0; i < propertyCount; ++i)
			{
				// Read the type of the first.
				PropertyType<?> type = PropertyRegistry.ALL_PROPERTIES[Byte.toUnsignedInt(buffer.get())];
				Object value = type.codec().loadData(context);
				props.put(type, value);
			}
			nonStack = new NonStackableItem(item, Collections.unmodifiableMap(props));
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
			// Write the number of properties.
			int propertyCount = item.properties().size();
			buffer.put((byte)propertyCount);
			for (Map.Entry<PropertyType<?>, Object> elt : item.properties().entrySet())
			{
				PropertyType<?> type = elt.getKey();
				int index = type.index();
				buffer.put((byte)index);
				_writePropertyValue(buffer, type, elt.getValue());
			}
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

	private static <T> void _writePropertyValue(ByteBuffer buffer, PropertyType<T> type, Object value)
	{
		type.codec().storeData(buffer, type.type().cast(value));
	}

	private static ItemSlot _readSlot(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		
		// We will need to manually read this type in order to determine if this is stackable or not.
		Item type = _readItem(buffer);
		// NOTE:  We will inline the rest of the data since we are overlapping with types.
		ItemSlot slot;
		if (null == type)
		{
			slot = null;
		}
		else if (context.env().durability.isStackable(type))
		{
			int count = buffer.getInt();
			Items items = new Items(type, count);
			slot = ItemSlot.fromStack(items);
		}
		else
		{
			// Use the standard helper for the non-stackable remainder.
			NonStackableItem item = _readNonStackableRemainder(type, context);
			slot = ItemSlot.fromNonStack(item);
		}
		return slot;
	}

	private static void _writeSlot(ByteBuffer buffer, ItemSlot slot)
	{
		if (null != slot)
		{
			// See if this is a stackable or not.
			// NOTE:  We will inline the rest of the data since we are overlapping with types.
			Items stackable = slot.stack;
			if (null != stackable)
			{
				_writeItem(buffer, stackable.type());
				buffer.putInt(stackable.count());
			}
			else
			{
				_writeNonStackableItem(buffer, slot.nonStackable);
			}
		}
		else
		{
			// We just write a null item type.
			_writeItem(buffer, null);
		}
	}
}
