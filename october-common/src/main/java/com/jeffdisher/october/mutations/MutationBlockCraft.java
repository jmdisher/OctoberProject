package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.logic.CraftingBlockSupport;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Craft;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Starts or continues a crafting operation within the given block.
 */
public class MutationBlockCraft implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.CRAFT_IN_BLOCK;

	public static MutationBlockCraft deserialize(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		Craft craft = CodecHelpers.readCraft(buffer);
		long millisToApply = buffer.getLong();
		return new MutationBlockCraft(location, craft, millisToApply);
	}


	private final AbsoluteLocation _location;
	private final Craft _craft;
	private final long _millisToApply;

	public MutationBlockCraft(AbsoluteLocation location, Craft craft, long millisToApply)
	{
		Assert.assertTrue(null != location);
		// Note that the crafting operation can be null to "continue what is already happening".
		Assert.assertTrue(millisToApply > 0L);
		
		_location = location;
		_craft = craft;
		_millisToApply = millisToApply;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _location;
	}

	@Override
	public boolean applyMutation(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		Environment env = Environment.getShared();
		boolean didApply = false;
		
		// Make sure that we are a crafting table.
		if (env.stations.getManualMultiplier(newBlock.getBlock()) > 0)
		{
			didApply = CraftingBlockSupport.runManual(env, newBlock, _craft, _millisToApply);
		}
		return didApply;
	}

	@Override
	public MutationBlockType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeAbsoluteLocation(buffer, _location);
		CodecHelpers.writeCraft(buffer, _craft);
		buffer.putLong(_millisToApply);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Common case.
		return true;
	}
}
