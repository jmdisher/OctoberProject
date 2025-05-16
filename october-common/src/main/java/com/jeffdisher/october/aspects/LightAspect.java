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
	 * @param activeSourceStream The stream containing the "active" variant tablist of sourceStream.
	 * @return The aspect (never null).
	 * @throws IOException There was a problem with a stream.
	 * @throws TabListReader.TabListException A tablist was malformed.
	 */
	public static LightAspect load(ItemRegistry items, BlockAspect blocks
			, InputStream opacityStream
			, InputStream sourceStream
			, InputStream activeSourceStream
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
		
		// ... and the active sources.
		FlatTabListCallbacks<Block, Byte> activeSourceCallbacks = new FlatTabListCallbacks<>(new IValueTransformer.BlockTransformer(items, blocks), new IValueTransformer.PositiveByteTransformer("source", MAX_LIGHT));
		TabListReader.readEntireFile(activeSourceCallbacks, activeSourceStream);
		
		return new LightAspect(opacityByBlockType, sourceCallbacks.data, activeSourceCallbacks.data);
	}

	public static final byte MAX_LIGHT = 15;
	public static final byte OPAQUE = MAX_LIGHT;

	private final byte[] _opacityByBlockType;
	private final Map<Block, Byte> _sources;
	private final Map<Block, Byte> _activeSources;

	private LightAspect(byte[] opacityByBlockType, Map<Block, Byte> sources, Map<Block, Byte> activeSources)
	{
		_opacityByBlockType = opacityByBlockType;
		_sources = sources;
		_activeSources = activeSources;
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
	 * @param isActive True if the block is in the active state.
	 * @return The light level from this block ([0..15]).
	 */
	public byte getLightEmission(Block block, boolean isActive)
	{
		Byte known = isActive
				? _activeSources.get(block)
				: _sources.get(block)
		;
		return (null != known)
				? known.byteValue()
				: 0
		;
	}
}
