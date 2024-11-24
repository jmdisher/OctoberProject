package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Repairs a block.  The block can only be repaired to 0 damage and this will fail if the block can't accept damage or
 * doesn't have any.
 */
public class MutationBlockIncrementalRepair implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.INCREMENTAL_REPAIR_BLOCK;

	public static MutationBlockIncrementalRepair deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		short damageToRepair = buffer.getShort();
		return new MutationBlockIncrementalRepair(location, damageToRepair);
	}


	private final AbsoluteLocation _location;
	private final short _damageToRepair;

	public MutationBlockIncrementalRepair(AbsoluteLocation location, short damageToRepair)
	{
		_location = location;
		_damageToRepair = damageToRepair;
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
		
		// This block could have changed since the mutation was created so check that this is not replaceable and has positive damage.
		boolean isReplaceable = env.blocks.canBeReplaced(newBlock.getBlock());
		
		short damage = newBlock.getDamage();
		boolean hasPositiveDamage = (damage > 0);
		
		if (!isReplaceable && hasPositiveDamage)
		{
			// Repair up to the maximum damage.
			short repair = (short)Math.min(_damageToRepair, damage);
			short updatedDamage = (short)(damage - repair);
			newBlock.setDamage(updatedDamage);
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
		buffer.putShort(_damageToRepair);
	}

	@Override
	public boolean canSaveToDisk()
	{
		return true;
	}
}
