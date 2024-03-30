package com.jeffdisher.october.aspects;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import com.jeffdisher.october.config.FlatTabListCallbacks;
import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.types.Block;


/**
 * Contains constants and helpers associated with the light aspect.
 * Note that all light is in-world, so this aspect is based directly on BlockAspect.
 */
public class LightAspect
{
	/**
	 * Loads the block opacity from the tablist in the given stream, sourcing Items from the given items registry.
	 * 
	 * @param items The existing ItemRegistry.
	 * @param blocks The existing BlockAspect.
	 * @param stream The stream containing the tablist describing block opacity.
	 * @return The aspect (never null).
	 * @throws IOException There was a problem with a stream.
	 * @throws TabListReader.TabListException A tablist was malformed.
	 */
	public static LightAspect load(ItemRegistry items, BlockAspect blocks
			, InputStream stream
	) throws IOException, TabListReader.TabListException
	{
		FlatTabListCallbacks<Block, Integer> callbacks = new FlatTabListCallbacks<>(new FlatTabListCallbacks.BlockTransformer(items, blocks), new FlatTabListCallbacks.IntegerTransformer("opacity"));
		TabListReader.readEntireFile(callbacks, stream);
		
		byte[] opacityByBlockType = new byte[blocks.BLOCKS_BY_TYPE.length];
		for (int i = 0; i < opacityByBlockType.length; ++i)
		{
			opacityByBlockType[i] = OPAQUE;
		}
		
		// Now, over-write with the values from the file.
		for (Map.Entry<Block, Integer> elt : callbacks.data.entrySet())
		{
			int value = elt.getValue();
			if ((value < 1) || (value > MAX_LIGHT))
			{
				throw new TabListReader.TabListException("Opacity values must be in the range [1..15]");
			}
			opacityByBlockType[elt.getKey().item().number()] = (byte)value;
		}
		return new LightAspect(blocks, opacityByBlockType);
	}

	public static final byte MAX_LIGHT = 15;
	public static final byte OPAQUE = MAX_LIGHT;

	private final BlockAspect _blocks;
	private final byte[] _opacityByBlockType;

	private LightAspect(BlockAspect blocks, byte[] opacityByBlockType)
	{
		_blocks = blocks;
		_opacityByBlockType = opacityByBlockType;
	}

	/**
	 * Used to check the opacity of a block since light may only partially pass through it.  Note that all blocks have
	 * an opacity >= 1.
	 * 
	 * @param block The block type.
	 * @return The opacity value ([1..15]).
	 */
	public byte getOpacity(Block block)
	{
		return _opacityByBlockType[block.item().number()];
	}

	/**
	 * Returns the light level emitted by this item type.
	 * 
	 * @param block The block type.
	 * @return The light level from this block ([0..15]).
	 */
	public byte getLightEmission(Block block)
	{
		// Only the lantern currently emits light.
		return (_blocks.LANTERN == block)
				? MAX_LIGHT
				: 0
		;
	}
}
