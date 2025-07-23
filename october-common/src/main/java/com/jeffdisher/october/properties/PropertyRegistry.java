package com.jeffdisher.october.properties;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

import com.jeffdisher.october.data.IObjectCodec;
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
			public Integer loadData(ByteBuffer buffer) throws BufferUnderflowException
			{
				return buffer.getInt();
			}
			@Override
			public void storeData(ByteBuffer buffer, Integer object) throws BufferOverflowException
			{
				buffer.putInt(object);
			}
		}
	);


	private static int _nextIndex = 0;
	public static final PropertyType<?>[] ALL_PROPERTIES;
	static {
		// Just verify indices are assigned as expected.
		Assert.assertTrue(0 == DURABILITY.index());
		
		// Create the finished array, in-order.
		ALL_PROPERTIES = new PropertyType<?>[] {
			DURABILITY,
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
