package com.jeffdisher.october.config;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.aspects.ItemRegistry;
import com.jeffdisher.october.config.TabListReader.TabListException;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Item;


/**
 * Used to transform a string value into a specific type.
 * 
 * @param <T> The output type.
 */
public interface IValueTransformer<T>
{
	T transform(String value) throws TabListReader.TabListException;

	/**
	 * Decodes the given data as an Integer.
	 */
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

	/**
	 * Decodes the given data as a positive Byte with a specific limit.
	 */
	public static class PositiveByteTransformer implements IValueTransformer<Byte>
	{
		private final String _name;
		private final byte _limit;
		public PositiveByteTransformer(String numberName, byte limit)
		{
			_name = numberName;
			_limit = limit;
		}
		@Override
		public Byte transform(String value) throws TabListException
		{
			try
			{
				byte parsed = Byte.parseByte(value);
				if ((parsed <= 0) || (parsed > _limit))
				{
					throw new TabListReader.TabListException("Values for " + _name + " must be positive and not greater than " + _limit);
				}
				return parsed;
			}
			catch (NumberFormatException e)
			{
				throw new TabListReader.TabListException("Not a valid " + _name + ": \"" + value + "\"");
			}
		}
	}

	/**
	 * Decodes the given data as an Item.
	 */
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

	/**
	 * Decodes the given data as a Block.
	 */
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
