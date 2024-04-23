package com.jeffdisher.october.aspects;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import com.jeffdisher.october.config.FlatTabListCallbacks;
import com.jeffdisher.october.config.IValueTransformer;
import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.utils.Assert;


/**
 * Contains constants and helpers associated with the damage aspect.
 * Since damage is only defined on blocks placed in the world, this aspect directly depends BlockAspect.
 */
public class DamageAspect
{
	/**
	 * Loads the block aspect from the tablist in the given stream, sourcing Items from the given items registry.
	 * 
	 * @param items The existing ItemRegistry.
	 * @param blocks The existing BlockAspect.
	 * @param stream The stream containing the tablist describing block toughness.
	 * @param capacityStream The stream containing the tablist describing block inventory capacities.
	 * @return The aspect (never null).
	 * @throws IOException There was a problem with a stream.
	 * @throws TabListReader.TabListException A tablist was malformed.
	 */
	public static DamageAspect load(ItemRegistry items, BlockAspect blocks
			, InputStream stream
	) throws IOException, TabListReader.TabListException
	{
		FlatTabListCallbacks<Block, Integer> callbacks = new FlatTabListCallbacks<>(new IValueTransformer.BlockTransformer(items, blocks), new IValueTransformer.IntegerTransformer("toughness"));
		TabListReader.readEntireFile(callbacks, stream);
		
		short[] toughnessByBlockType = new short[items.ITEMS_BY_TYPE.length];
		// Set defaults:  Blocks get MEDIUM, non-blocks get NOT_BLOCK.
		for (int i = 0; i < toughnessByBlockType.length; ++i)
		{
			Item item = items.ITEMS_BY_TYPE[i];
			boolean isBlock = (null != blocks.fromItem(item));
			toughnessByBlockType[i] = isBlock ? MEDIUM : NOT_BLOCK;
		}
		
		// Now, over-write with the values from the file.
		for (Map.Entry<Block, Integer> elt : callbacks.data.entrySet())
		{
			int value = elt.getValue();
			Assert.assertTrue(value <= MAX_DAMAGE);
			toughnessByBlockType[elt.getKey().item().number()] = (short)value;
		}
		return new DamageAspect(toughnessByBlockType);
	}

	/**
	 * We are limited to 15 bits to store the damage so we just fix the maximum at a round 32000.
	 */
	public static final short MAX_DAMAGE = 32000;

	/**
	 * The durability of items which CANNOT exist as blocks in the world.
	 */
	public static final short NOT_BLOCK = -1;

	/**
	 * Blocks which either can't be broken or don't make sense to break.
	 */
	public static final short UNBREAKABLE = 0;

	/**
	 * Very weak blocks which are trivial to break.
	 */
	public static final short TRIVIAL = 20;

	/**
	 * Common weak blocks.
	 */
	public static final short WEAK = 200;

	/**
	 * Common medium toughness blocks.
	 */
	public static final short MEDIUM = 2000;

	/**
	 * Common hard toughness blocks.
	 */
	public static final short HARD = 8000;

	/**
	 * Exceptionally strong blocks.
	 */
	public static final short STRONG = 20000;

	private final short[] _toughnessByBlockType;

	private DamageAspect(short[] toughnessByBlockType)
	{
		_toughnessByBlockType = toughnessByBlockType;
	}

	public short getToughness(Block block)
	{
		return _toughnessByBlockType[block.item().number()];
	}
}
