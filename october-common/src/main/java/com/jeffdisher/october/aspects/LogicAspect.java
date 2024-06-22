package com.jeffdisher.october.aspects;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import com.jeffdisher.october.config.IValueTransformer;
import com.jeffdisher.october.config.SimpleTabListCallbacks;
import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.config.TabListReader.TabListException;
import com.jeffdisher.october.types.Block;


/**
 * Represents the subset of Block objects which have some meaning in the logic layer.
 */
public class LogicAspect
{
	public static final byte MAX_LEVEL = 15;

	public static final String ROOT_SINK = "sink";
	public static final String ROOT_SOURCE = "source";
	public static final String FIELD_VALUE = "value";
	public static final String VALUE_HIGH = "high";
	public static final String VALUE_LOW = "low";
	public static final String FIELD_ALTERNATE = "alternate";
	public static final String FIELD_MANUAL = "manual";

	/**
	 * We handle logic wire as a special-case since it would have otherwise required specializing the entire parser
	 * just to mention it by ID (since none of the other parameters would apply).
	 */
	public static final String LOGIC_WIRE_ID = "op.logic_wire";

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
		IValueTransformer<Role> roleTransformer = Role.transformer;
		IValueTransformer<Value> valueTransformer = Value.transformer;
		IValueTransformer<Boolean> booleanTransformer = new IValueTransformer.BooleanTransformer();
		
		SimpleTabListCallbacks<Block, Role> callbacks = new SimpleTabListCallbacks<>(blockTransformer, roleTransformer);
		SimpleTabListCallbacks.SubRecordCapture<Block, Value> value = callbacks.captureSubRecord(FIELD_VALUE, valueTransformer, true);
		SimpleTabListCallbacks.SubRecordCapture<Block, Block> alternate = callbacks.captureSubRecord(FIELD_ALTERNATE, blockTransformer, true);
		SimpleTabListCallbacks.SubRecordCapture<Block, Boolean> manual = callbacks.captureSubRecord(FIELD_MANUAL, booleanTransformer, true);
		
		TabListReader.readEntireFile(callbacks, stream);
		
		// We can just pass these in, directly.
		return new LogicAspect(callbacks.topLevel
				, value.recordData
				, alternate.recordData
				, manual.recordData
				, blocks.fromItem(items.getItemById(LOGIC_WIRE_ID))
		);
	}


	private final Map<Block, Role> _roles;
	private final Map<Block, Value> _values;
	private final Map<Block, Block> _alternates;
	private final Map<Block, Boolean> _manual;
	private final Block _logicWireBlock;

	private LogicAspect(Map<Block, Role> roles
			, Map<Block, Value> values
			, Map<Block, Block> alternates
			, Map<Block, Boolean> manual
			, Block logicWireBlock
	)
	{
		_roles = roles;
		_values = values;
		_alternates = alternates;
		_manual = manual;
		_logicWireBlock = logicWireBlock;
	}

	public boolean isSource(Block block)
	{
		Role role = _roles.get(block);
		return (Role.SOURCE == role);
	}

	public boolean isSink(Block block)
	{
		Role role = _roles.get(block);
		return (Role.SINK == role);
	}

	public boolean isAware(Block block)
	{
		// A sink, source, or just a conduit are all considered aware.
		return (_logicWireBlock == block) || _roles.containsKey(block);
	}

	public boolean isHigh(Block block)
	{
		Value value = _values.get(block);
		return (Value.HIGH == value);
	}

	public Block getAlternate(Block block)
	{
		return _alternates.get(block);
	}

	public boolean isManual(Block block)
	{
		Boolean value = _manual.get(block);
		return (Boolean.TRUE == value);
	}

	public boolean isConduit(Block block)
	{
		// This helper is a special-case for logic wire.
		return (_logicWireBlock == block);
	}


	public static enum Role
	{
		SINK,
		SOURCE,
		;
		
		public static IValueTransformer<Role> transformer = new IValueTransformer<>() {
			@Override
			public Role transform(String value) throws TabListException
			{
				Role role = Role.valueOf(value.toUpperCase());
				if (null == role)
				{
					throw new TabListReader.TabListException("Invalid role: \"" + value + "\"");
				}
				return role;
			}
		};
	}

	public static enum Value
	{
		HIGH,
		LOW,
		;
		
		public static IValueTransformer<Value> transformer = new IValueTransformer<>() {
			@Override
			public Value transform(String value) throws TabListException
			{
				Value result = Value.valueOf(value.toUpperCase());
				if (null == result)
				{
					throw new TabListReader.TabListException("Invalid value: \"" + value + "\"");
				}
				return result;
			}
		};
	}
}
