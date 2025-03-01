package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.DamageAspect;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Applies damage to the block.  If this results in the damage value exceeding the maximum for this block type, the
 * block will be replaced by air and dropped as an item in the inventory of the air block.
 */
public class MutationBlockIncrementalBreak implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.INCREMENTAL_BREAK_BLOCK;
	/**
	 * A constant we provide in case the block shouldn't be stored back into the inventory of a breking entity.
	 */
	public static final int NO_STORAGE_ENTITY = 0;

	public static MutationBlockIncrementalBreak deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		short damageToApply = buffer.getShort();
		int optionalEntityForStorage = buffer.getInt();
		return new MutationBlockIncrementalBreak(location, damageToApply, optionalEntityForStorage);
	}


	private final AbsoluteLocation _location;
	private final short _damageToApply;
	private final int _optionalEntityForStorage;

	public MutationBlockIncrementalBreak(AbsoluteLocation location, short damageToApply, int optionalEntityForStorage)
	{
		_location = location;
		_damageToApply = damageToApply;
		_optionalEntityForStorage = optionalEntityForStorage;
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
		
		// We want to see if this is a kind of block which can be broken.
		Block block = newBlock.getBlock();
		short toughness = env.damage.getToughness(block);
		if (DamageAspect.UNBREAKABLE != toughness)
		{
			// Apply the damage.
			short damage = (short)(newBlock.getDamage() + _damageToApply);
			
			// See if this is broken (note that damage could overflow).
			if ((damage >= toughness) || (damage < 0))
			{
				CommonBlockMutationHelpers.breakBlockAndHandleFollowUp(env, context, _location, newBlock, _optionalEntityForStorage);
				
				// This also triggers an event.
				context.eventSink.post(new EventRecord(EventRecord.Type.BLOCK_BROKEN
						, EventRecord.Cause.NONE
						, _location
						, 0
						, _optionalEntityForStorage
				));
			}
			else
			{
				// The block still exists so just update the damage.
				newBlock.setDamage(damage);
			}
			didApply = true;
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
		buffer.putShort(_damageToApply);
		buffer.putInt(_optionalEntityForStorage);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// There is an entity reference.
		return false;
	}
}
