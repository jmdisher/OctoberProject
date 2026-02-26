package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;
import java.util.List;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * This mutation is sent to every location in a multi-block in the tick following placing the multi-block to verify that
 * they were all able to place.  If any of them are missing, this block will be reverted.
 * Note that this results in an n^2 verification since each block checks each block, which may cause scaling problems if
 * these blocks get too big (a more efficient algorithm would require more coordination across more ticks, though).
 * In reality, this is probably not going to be used for anything all that large (~4 blocks), so this should be ok.
 */
public class MutationBlockPhase2Multi implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.MULTI_PHASE2;

	public static MutationBlockPhase2Multi deserialize(DeserializationContext context)
	{
		Environment env = context.env();
		ByteBuffer buffer = context.buffer();
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		AbsoluteLocation rootLocation = CodecHelpers.readAbsoluteLocation(buffer);
		FacingDirection orientation = CodecHelpers.readOrientation(buffer);
		Block rootType = env.blocks.getAsPlaceableBlock(CodecHelpers.readItem(buffer));
		Block revertType = env.blocks.getAsPlaceableBlock(CodecHelpers.readItem(buffer));
		return new MutationBlockPhase2Multi(location, rootLocation, orientation, rootType, revertType);
	}


	private final AbsoluteLocation _location;
	private final AbsoluteLocation _rootLocation;
	private final FacingDirection _orientation;
	private final Block _rootType;
	private final Block _revertType;

	public MutationBlockPhase2Multi(AbsoluteLocation location, AbsoluteLocation rootLocation, FacingDirection orientation, Block rootType, Block revertType)
	{
		Environment env = Environment.getShared();
		// This can only be called if the root is a multi-block.
		Assert.assertTrue(env.blocks.isMultiBlock(rootType));
		
		_location = location;
		_rootLocation = rootLocation;
		_orientation = orientation;
		_rootType = rootType;
		_revertType = revertType;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _location;
	}

	@Override
	public boolean applyMutation(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		// Check all the blocks.
		Environment env = Environment.getShared();
		List<AbsoluteLocation> extensions = env.multiBlocks.getExtensions(_rootType, _rootLocation, _orientation);
		boolean doesMatch = _doesMatch(context, _rootLocation, _rootType);
		for (AbsoluteLocation location : extensions)
		{
			doesMatch &= _doesMatch(context, location, _rootType);
		}
		
		if (!doesMatch)
		{
			Block oldType = newBlock.getBlock();
			
			CommonBlockMutationHelpers.setBlockCheckingFire(env, context, _location, newBlock, _revertType);
			
			// See if we might need to reflow water (if this multi-block failed to be placed in water).
			CommonBlockMutationHelpers.scheduleLiquidFlowIfRequired(env, context, _location, oldType, _revertType);
		}
		
		// Whatever happened, we always say that this applied.
		return true;
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
		CodecHelpers.writeAbsoluteLocation(buffer, _rootLocation);
		CodecHelpers.writeOrientation(buffer, _orientation);
		CodecHelpers.writeItem(buffer, _rootType.item());
		CodecHelpers.writeItem(buffer, _revertType.item());
	}

	@Override
	public boolean canSaveToDisk()
	{
		// This references an entity so we can't save it.
		return false;
	}


	private static boolean _doesMatch(TickProcessingContext context, AbsoluteLocation location, Block block)
	{
		BlockProxy proxy = context.previousBlockLookUp.readBlock(location);
		return (null != proxy)
				? proxy.getBlock().equals(block)
				: false
		;
	}
}
