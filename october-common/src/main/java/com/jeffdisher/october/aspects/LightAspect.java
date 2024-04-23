package com.jeffdisher.october.aspects;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import com.jeffdisher.october.config.FlatTabListCallbacks;
import com.jeffdisher.october.config.IValueTransformer;
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
	 * @param opacityStream The stream containing the tablist describing block opacity.
	 * @param sourceStream The stream containing the tablist describing block light sources.
	 * @return The aspect (never null).
	 * @throws IOException There was a problem with a stream.
	 * @throws TabListReader.TabListException A tablist was malformed.
	 */
	public static LightAspect load(ItemRegistry items, BlockAspect blocks
			, InputStream opacityStream
			, InputStream sourceStream
	) throws IOException, TabListReader.TabListException
	{
		// Process opacity.
		FlatTabListCallbacks<Block, Integer> opacityCallbacks = new FlatTabListCallbacks<>(new IValueTransformer.BlockTransformer(items, blocks), new IValueTransformer.IntegerTransformer("opacity"));
		TabListReader.readEntireFile(opacityCallbacks, opacityStream);
		
		// Set all items to be opaque if placed as blocks (not all can).
		byte[] opacityByBlockType = new byte[items.ITEMS_BY_TYPE.length];
		for (int i = 0; i < opacityByBlockType.length; ++i)
		{
			opacityByBlockType[i] = OPAQUE;
		}
		
		// Now, over-write with the values from the file.
		for (Map.Entry<Block, Integer> elt : opacityCallbacks.data.entrySet())
		{
			int value = elt.getValue();
			if ((value < 1) || (value > MAX_LIGHT))
			{
				throw new TabListReader.TabListException("Opacity values must be in the range [1..15]");
			}
			opacityByBlockType[elt.getKey().item().number()] = (byte)value;
		}
		
		// Now, process the sources.
		FlatTabListCallbacks<Block, Byte> sourceCallbacks = new FlatTabListCallbacks<>(new IValueTransformer.BlockTransformer(items, blocks), new IValueTransformer.PositiveByteTransformer("source", MAX_LIGHT));
		TabListReader.readEntireFile(sourceCallbacks, sourceStream);
		
		return new LightAspect(opacityByBlockType, sourceCallbacks.data);
	}

	public static final byte MAX_LIGHT = 15;
	public static final byte OPAQUE = MAX_LIGHT;

	private final byte[] _opacityByBlockType;
	private final Map<Block, Byte> _sources;

	private LightAspect(byte[] opacityByBlockType, Map<Block, Byte> sources)
	{
		_opacityByBlockType = opacityByBlockType;
		_sources = sources;
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
		Byte known = _sources.get(block);
		return (null != known)
				? known.byteValue()
				: 0
		;
	}
}
