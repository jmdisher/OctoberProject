package com.jeffdisher.october.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.aspects.ItemRegistry;
import com.jeffdisher.october.config.TabListReader.TabListException;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Item;


/**
 * An implementation of the callbacks interface to handle the common case of a flat key-value list.
 * 
 * @param <K> The key type.
 * @param <V> The value type.
 */
public class FlatTabListCallbacks<K, V> implements TabListReader.IParseCallbacks
{
	private final IValueTransformer<K> _keyTransformer;
	private final IValueTransformer<V> _valueTransformer;
	public final List<K> keyOrder;
	public final Map<K, V> data;

	public FlatTabListCallbacks(IValueTransformer<K> keyTransformer, IValueTransformer<V> valueTransformer)
	{
		_keyTransformer = keyTransformer;
		_valueTransformer = valueTransformer;
		this.keyOrder = new ArrayList<>();
		this.data = new HashMap<>();
	}

	@Override
	public void startNewRecord(String name, String[] parameters) throws TabListReader.TabListException
	{
		K key = _keyTransformer.transform(name);
		if (1 != parameters.length)
		{
			throw new TabListReader.TabListException("Exactly 1 parameter expected");
		}
		V value = _valueTransformer.transform(parameters[0]);
		if (data.containsKey(key))
		{
			throw new TabListReader.TabListException("Duplicate data element: \"" + name + "\"");
		}
		this.keyOrder.add(key);
		this.data.put(key, value);
	}

	@Override
	public void endRecord() throws TabListReader.TabListException
	{
	}

	@Override
	public void processSubRecord(String name, String[] parameters) throws TabListReader.TabListException
	{
		throw new TabListReader.TabListException("Sub-records are not expected");
	}


	/**
	 * Used to transform a string value into a specific type.
	 * 
	 * @param <T> The output type.
	 */
	public static interface IValueTransformer<T>
	{
		T transform(String value) throws TabListReader.TabListException;
	}


	public static class IntegerTransformer implements IValueTransformer<Integer>
	{
		private final String _name;
		public IntegerTransformer(String numberName)
		{
			_name = numberName;
		}
		@Override
		public Integer transform(String value) throws TabListException
		{
			try
			{
				return Integer.parseInt(value);
			}
			catch (NumberFormatException e)
			{
				throw new TabListReader.TabListException("Not a valid " + _name + ": \"" + value + "\"");
			}
		}
	}

	public static class ItemTransformer implements IValueTransformer<Item>
	{
		private final ItemRegistry _items;
		public ItemTransformer(ItemRegistry items)
		{
			_items = items;
		}
		@Override
		public Item transform(String value) throws TabListException
		{
			Item item = _items.getItemById(value);
			if (null == item)
			{
				throw new TabListReader.TabListException("Unknown item: \"" + value + "\"");
			}
			return item;
		}
	}

	public static class BlockTransformer implements IValueTransformer<Block>
	{
		private final ItemRegistry _items;
		private final BlockAspect _blocks;
		public BlockTransformer(ItemRegistry items, BlockAspect blocks)
		{
			_items = items;
			_blocks = blocks;
		}
		@Override
		public Block transform(String value) throws TabListException
		{
			Item item = _items.getItemById(value);
			Block block = (null != item) ? _blocks.BLOCKS_BY_TYPE[item.number()] : null;
			if (null == block)
			{
				throw new TabListReader.TabListException("Not a block: \"" + value + "\"");
			}
			return block;
		}
	}
}
