package com.jeffdisher.october.properties;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.data.IObjectCodec;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.utils.Assert;


/**
 * Just a container of all the Property types defined in the base system.
 * This is similar to AspectRegistry except that a user of a Property doesn't need all of them and can have duplicates
 * so this is mostly just to group together the type bindings and identifiers used for serialization.
 */
public class PropertyRegistry
{
	/**
	 * The most obvious Property of a NonStackableItem is durability.
	 */
	public static final PropertyType<Integer> DURABILITY = registerProperty(Integer.class
		, new IObjectCodec<Integer>()
		{
			@Override
			public Integer loadData(DeserializationContext context) throws BufferUnderflowException
			{
				ByteBuffer buffer = context.buffer();
				return buffer.getInt();
			}
			@Override
			public void storeData(ByteBuffer buffer, Integer object) throws BufferOverflowException
			{
				buffer.putInt(object);
			}
		}
	);
	/**
	 * A custom name to attach to the NonStackableItem.
	 */
	public static final PropertyType<String> NAME = registerProperty(String.class
		, new IObjectCodec<String>()
		{
			@Override
			public String loadData(DeserializationContext context) throws BufferUnderflowException
			{
				ByteBuffer buffer = context.buffer();
				return CodecHelpers.readString(buffer);
			}
			@Override
			public void storeData(ByteBuffer buffer, String object) throws BufferOverflowException
			{
				CodecHelpers.writeString(buffer, object);
			}
		}
	);
	/**
	 * An enchantment to increase effective durability with a value being the "level" of enchantment.  The level is in
	 * the range of [1..127] and a random number is used to calculate durability loss according to this expression:
	 * if (0 == (random([0..255]) mod (1 + level))) -> decrement durability.
	 * This means that without this enchantment, the level is effectively "0", so the "mod 1" will always be zero,
	 * meaning always decrement.  At level 1, this means that damage is only applied 1/2 of the time.  At level 127,
	 * damage would be applied 1/128 of the time.
	 */
	public static final PropertyType<Byte> ENCHANT_DURABILITY = registerProperty(Byte.class
		, new IObjectCodec<Byte>()
		{
			@Override
			public Byte loadData(DeserializationContext context) throws BufferUnderflowException
			{
				ByteBuffer buffer = context.buffer();
				return buffer.get();
			}
			@Override
			public void storeData(ByteBuffer buffer, Byte object) throws BufferOverflowException
			{
				byte val = object.byteValue();
				Assert.assertTrue(val > 0);
				buffer.put(val);
			}
		}
	);
	/**
	 * An enchantment to increase effective damage of a melee weapon by the "level" of enchantment.  The level is in
	 * the range of [1..127] and this value is added to the weapon damage before accounting for armour.
	 */
	public static final PropertyType<Byte> ENCHANT_WEAPON_MELEE = registerProperty(Byte.class
		, new IObjectCodec<Byte>()
		{
			@Override
			public Byte loadData(DeserializationContext context) throws BufferUnderflowException
			{
				ByteBuffer buffer = context.buffer();
				return buffer.get();
			}
			@Override
			public void storeData(ByteBuffer buffer, Byte object) throws BufferOverflowException
			{
				byte val = object.byteValue();
				Assert.assertTrue(val > 0);
				buffer.put(val);
			}
		}
	);


	private static int _nextIndex = 0;
	public static final PropertyType<?>[] ALL_PROPERTIES;
	static {
		// Just verify indices are assigned as expected.
		Assert.assertTrue(0 == DURABILITY.index());
		Assert.assertTrue(1 == NAME.index());
		Assert.assertTrue(2 == ENCHANT_DURABILITY.index());
		Assert.assertTrue(3 == ENCHANT_WEAPON_MELEE.index());
		
		// Create the finished array, in-order.
		ALL_PROPERTIES = new PropertyType<?>[] {
			DURABILITY,
			NAME,
			ENCHANT_DURABILITY,
			ENCHANT_WEAPON_MELEE,
		};
	}


	public static <T> PropertyType<T> registerProperty(Class<T> type
			, IObjectCodec<T> codec
	)
	{
		int index = _nextIndex;
		_nextIndex += 1;
		return new PropertyType<>(index
				, type
				, codec
		);
	}


	private PropertyRegistry()
	{
		// No instantiation.
	}
}
