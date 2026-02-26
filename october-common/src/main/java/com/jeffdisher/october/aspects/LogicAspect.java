package com.jeffdisher.october.aspects;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.october.config.IValueTransformer;
import com.jeffdisher.october.config.SimpleTabListCallbacks;
import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.config.TabListReader.TabListException;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Represents the subset of Block objects which have some meaning in the logic layer.
 */
public class LogicAspect
{
	public static final byte MAX_LEVEL = 15;

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
		
		SimpleTabListCallbacks<Block, _Role> callbacks = new SimpleTabListCallbacks<>(blockTransformer, roleTransformer);
		
		TabListReader.readEntireFile(callbacks, stream);
		
		// We want to find the "logic wire" and then strip it out of the parser map.
		// (for now, we expect precisely one of these wire types).
		Map<Block, _Role> roles = new HashMap<>();
		Block logicWire = null;
		for (Map.Entry<Block, _Role> elt : callbacks.topLevel.entrySet())
		{
			Block block = elt.getKey();
			_Role role = elt.getValue();
			if (_Role.CONDUIT == role)
			{
				if (null != logicWire)
				{
					throw new TabListReader.TabListException("Only one CONDUIT type supported: Both " + block + " and " + logicWire + " specified");
				}
				logicWire = block;
			}
			else if (!blocks.hasActiveVariant(block))
			{
				throw new TabListReader.TabListException("Block does not have an active variant: " + block);
			}
			else
			{
				roles.put(block, role);
			}
		}
		
		// We can just pass these in, directly.
		return new LogicAspect(roles
			, logicWire
		);
	}


	private final Map<Block, _Role> _roles;
	private final Block _logicWireBlock;

	private LogicAspect(Map<Block, _Role> roles
			, Block logicWireBlock
	)
	{
		_roles = roles;
		_logicWireBlock = logicWireBlock;
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
		return (null != role)
				? role.isSource
				: false
		;
	}

	/**
	 * Used to determine the initial value of the active flag for this block when it is first placed.
	 * 
	 * @param block The block type.
	 * @return The helper to calculate the initial value or null if it always defaults to inactive.
	 */
	public ISignalChangeCallback initialPlacementHandler(Block block)
	{
		_Role role = _roles.get(block);
		return (null != role)
				? role.placement
				: null
		;
	}

	/**
	 * Used to request the helper to update this block's active state in response to a block update event.  This
	 * essentially means that the block is a sink which can change its state based on block state update events.
	 * 
	 * @param block The block type.
	 * @return The helper to calculate the block's new active state in response to a block update event or null if it
	 * ignores block update events.
	 */
	public ISignalChangeCallback blockUpdateHandler(Block block)
	{
		_Role role = _roles.get(block);
		return (null != role)
				? role.blockUpdate
				: null
		;
	}

	/**
	 * Used to request the helper to update this block's active state in response to adjacent block logic changes.  This
	 * essentially means that the block is a sink which can change its state based on logic update events.
	 * 
	 * @param block The block type.
	 * @return The helper to calculate the block's new active state in response to an update event or null if it ignores
	 * logic update events.
	 */
	public ISignalChangeCallback logicUpdateHandler(Block block)
	{
		_Role role = _roles.get(block);
		return (null != role)
				? role.logicUpdate
				: null
		;
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
		_Role role = _roles.get(block);
		return (null != role)
				? role.canManuallyChange
				: false
		;
	}

	/**
	 * Used to check if this block requires some special logic for state change which can't be generically captured.
	 * 
	 * @param block The block type.
	 * @return True if a change in logic state needs to be specially handled.
	 */
	public boolean hasSpecialChangeLogic(Block block)
	{
		_Role role = _roles.get(block);
		return (_Role.OFFLINE_MANUAL == role);
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


	private static enum _Role
	{
		DOOR(false, LogicSpecialRegistry.GENERIC_SINK, null, LogicSpecialRegistry.GENERIC_SINK, true),
		LAMP(false, LogicSpecialRegistry.GENERIC_SINK, null, LogicSpecialRegistry.GENERIC_SINK, false),
		SWITCH(true, null, null, null, true),
		EMITTER(true, LogicSpecialRegistry.EMITTER, null, null, false),
		DIODE(true, LogicSpecialRegistry.DIODE_SINK, null, LogicSpecialRegistry.DIODE_SINK, false),
		AND_GATE(true, LogicSpecialRegistry.AND_SINK, null, LogicSpecialRegistry.AND_SINK, false),
		OR_GATE(true, LogicSpecialRegistry.OR_SINK, null, LogicSpecialRegistry.OR_SINK, false),
		NOT_GATE(true, LogicSpecialRegistry.NOT_SINK, null, LogicSpecialRegistry.NOT_SINK, false),
		SENSOR_INVENTORY(true, LogicSpecialRegistry.SENSOR_INVENTORY, LogicSpecialRegistry.SENSOR_INVENTORY, null, false),
		OFFLINE_MANUAL(false, null, null, null, true),
		// NOTE:  CONDUIT is a degenerate type, only used for parsing (these are never seen after bootstrap).
		CONDUIT(false, null, null, null, false),
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
		
		public final boolean isSource;
		public final ISignalChangeCallback placement;
		public final ISignalChangeCallback blockUpdate;
		public final ISignalChangeCallback logicUpdate;
		public final boolean canManuallyChange;
		
		private _Role(boolean isSource, ISignalChangeCallback placement, ISignalChangeCallback blockUpdate, ISignalChangeCallback logicUpdate, boolean canManuallyChange)
		{
			this.isSource = isSource;
			this.placement = placement;
			this.blockUpdate = blockUpdate;
			this.logicUpdate = logicUpdate;
			this.canManuallyChange = canManuallyChange;
		}
	}


	/**
	 * The interface implemented by special logic sinks to determine if they are receiving a signal from their
	 * surroundings.
	 * The reason this exists is that different logic blocks have different rules around which blocks can push logic
	 * into them and how they apply those states.
	 */
	public static interface ISignalChangeCallback
	{
		/**
		 * Determines if the block would receive a high signal if placed in location, facing outputDirection.
		 * 
		 * @param env The environment.
		 * @param proxyLookup A look-up for that last tick's output proxies.
		 * @param location The location where the block is being placed.
		 * @param outputDirection The output direction of the block.
		 * @return True if it would receive a high signal here.
		 */
		public boolean shouldStoreHighSignal(Environment env, TickProcessingContext.IBlockFetcher proxyLookup, AbsoluteLocation location, FacingDirection outputDirection);
	}
}
