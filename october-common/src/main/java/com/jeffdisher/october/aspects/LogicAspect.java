package com.jeffdisher.october.aspects;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.function.Function;

import com.jeffdisher.october.config.IValueTransformer;
import com.jeffdisher.october.config.SimpleTabListCallbacks;
import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.config.TabListReader.TabListException;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;


/**
 * Represents the subset of Block objects which have some meaning in the logic layer.
 */
public class LogicAspect
{
	public static final byte MAX_LEVEL = 15;

	public static final String ROOT_SINK = "sink";
	public static final String ROOT_SOURCE = "source";
	public static final String FIELD_MANUAL = "manual";

	/**
	 * We handle logic wire as a special-case since it would have otherwise required specializing the entire parser
	 * just to mention it by ID (since none of the other parameters would apply).
	 */
	public static final String LOGIC_WIRE_ID = "op.logic_wire";
	/**
	 * Some logic block types have a special bit of starting logic.
	 */
	public static final String LOGIC_EMITTER_ID = "op.emitter";

	/**
	 * Loads the logic-sensitive blocks types from the tablist in the given stream, sourcing Blocks from the given item
	 * and block registries.
	 * 
	 * @param items The existing ItemRegistry.
	 * @param blocks The existing BlockAspect.
	 * @param stream The stream containing the tablist.
	 * @return The aspect (never null).
	 * @throws IOException There was a problem with a stream.
	 * @throws TabListReader.TabListException A tablist was malformed.
	 */
	public static LogicAspect load(ItemRegistry items
			, BlockAspect blocks
			, InputStream stream
	) throws IOException, TabListReader.TabListException
	{
		IValueTransformer<Block> blockTransformer = new IValueTransformer.BlockTransformer(items, blocks);
		IValueTransformer<_Role> roleTransformer = _Role.transformer;
		IValueTransformer<Boolean> booleanTransformer = new IValueTransformer.BooleanTransformer();
		
		SimpleTabListCallbacks<Block, _Role> callbacks = new SimpleTabListCallbacks<>(blockTransformer, roleTransformer);
		SimpleTabListCallbacks.SubRecordCapture<Block, Boolean> manual = callbacks.captureSubRecord(FIELD_MANUAL, booleanTransformer, true);
		
		TabListReader.readEntireFile(callbacks, stream);
		
		// Note that all of the logic-sensitive blocks (either sinks or sources) are required to have alternates.
		for (Block block : callbacks.topLevel.keySet())
		{
			if (!blocks.hasActiveVariant(block))
			{
				throw new TabListReader.TabListException("Block does not have an active variant: " + block);
			}
		}
		
		// We can just pass these in, directly.
		return new LogicAspect(callbacks.topLevel
				, manual.recordData
				, blocks.fromItem(items.getItemById(LOGIC_WIRE_ID))
				, blocks.fromItem(items.getItemById(LOGIC_EMITTER_ID))
		);
	}


	private final Map<Block, _Role> _roles;
	private final Map<Block, Boolean> _manual;
	private final Block _logicWireBlock;
	private final Block _specialEmitter;

	private LogicAspect(Map<Block, _Role> roles
			, Map<Block, Boolean> manual
			, Block logicWireBlock
			, Block specialEmitter
	)
	{
		_roles = roles;
		_manual = manual;
		_logicWireBlock = logicWireBlock;
		_specialEmitter = specialEmitter;
	}

	/**
	 * Used to check if this block could possibly be emitting a logic value into surrounding blocks.  Whether it is at
	 * the current moment depends on other information, not just the block type.
	 * 
	 * @param block The block type.
	 * @return True if this block can emit a logic signal.
	 */
	public boolean isSource(Block block)
	{
		_Role role = _roles.get(block);
		return (_Role.SOURCE == role);
	}

	/**
	 * Used to check if this block type can be changed to an active state by a logic signal from another block or
	 * conduit.
	 * 
	 * @param block The block type.
	 * @return True if this block type can have its state changed by logic.
	 */
	public boolean isSink(Block block)
	{
		_Role role = _roles.get(block);
		return (_Role.SINK == role);
	}

	/**
	 * Used to check if a block is involved in the logic layer in any way:  Conduit, sink, or source.
	 * 
	 * @param block The block type.
	 * @return True if this block may be involved in logic.
	 */
	public boolean isAware(Block block)
	{
		// A sink, source, or just a conduit are all considered aware.
		return (_logicWireBlock == block) || _roles.containsKey(block);
	}

	/**
	 * Used to check if a given block type can have its active state manually set, no matter whether it is a sink or a
	 * source.
	 * 
	 * @param block The block type.
	 * @return True if the block's active state can be manually set by an entity.
	 */
	public boolean isManual(Block block)
	{
		Boolean value = _manual.get(block);
		return (Boolean.TRUE == value);
	}

	/**
	 * Used to check if this block type can be a conduit for logic signals (that is, they can pass through it and
	 * degrade).
	 * 
	 * @param block The block type.
	 * @return True if this block is specifically a logic conduit.
	 */
	public boolean isConduit(Block block)
	{
		// This helper is a special-case for logic wire.
		return (_logicWireBlock == block);
	}

	public boolean shouldPlaceAsActive(Environment env
			, Function<AbsoluteLocation, BlockProxy> proxyLookup
			, AbsoluteLocation location
			, Block type
	)
	{
		boolean isActive = false;
		// NOTE:  This will likely be expanded later to handle other kinds of special logic blocks, since these can't
		// easily be made declarative.
		if (_specialEmitter == type)
		{
			isActive = true;
		}
		return isActive;
	}


	private static enum _Role
	{
		SINK,
		SOURCE,
		;
		
		public static IValueTransformer<_Role> transformer = new IValueTransformer<>() {
			@Override
			public _Role transform(String value) throws TabListException
			{
				_Role role = _Role.valueOf(value.toUpperCase());
				if (null == role)
				{
					throw new TabListReader.TabListException("Invalid role: \"" + value + "\"");
				}
				return role;
			}
		};
	}
}
