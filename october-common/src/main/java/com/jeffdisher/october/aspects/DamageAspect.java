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
		
		int[] toughnessByBlockType = new int[items.ITEMS_BY_TYPE.length];
		// Set defaults:  Blocks get MEDIUM, non-blocks get NOT_BLOCK.
		for (int i = 0; i < toughnessByBlockType.length; ++i)
		{
			Item item = items.ITEMS_BY_TYPE[i];
			boolean isBlock = (null != blocks.fromItem(item));
			toughnessByBlockType[i] = isBlock ? DEFAULT_TOUGHNESS : NOT_BLOCK;
		}
		
		// Now, over-write with the values from the file.
		for (Map.Entry<Block, Integer> elt : callbacks.data.entrySet())
		{
			int value = elt.getValue();
			Assert.assertTrue(value >= 0);
			toughnessByBlockType[elt.getKey().item().number()] = value;
		}
		return new DamageAspect(toughnessByBlockType);
	}

	/**
	 * The durability of items which CANNOT exist as blocks in the world.
	 */
	public static final int NOT_BLOCK = -1;

	/**
	 * Blocks which either can't be broken or don't make sense to break.
	 */
	public static final int UNBREAKABLE = 0;

	/**
	 * Unless otherwise specified, we will assume blocks take 30 seconds to break with a bare hand.
	 */
	public static final int DEFAULT_TOUGHNESS = 30000;

	private final int[] _toughnessByBlockType;

	private DamageAspect(int[] toughnessByBlockType)
	{
		_toughnessByBlockType = toughnessByBlockType;
	}

	public int getToughness(Block block)
	{
		return _toughnessByBlockType[block.item().number()];
	}
}
