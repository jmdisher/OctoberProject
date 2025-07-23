package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.logic.FireHelpers;
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

	public static MutationBlockIncrementalRepair deserialize(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
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
		boolean didApply = false;
		
		// Check that this block is damaged.
		short damage = newBlock.getDamage();
		boolean hasPositiveDamage = (damage > 0);
		
		if (hasPositiveDamage)
		{
			// Repair up to the maximum damage.
			short repair = (short)Math.min(_damageToRepair, damage);
			short updatedDamage = (short)(damage - repair);
			newBlock.setDamage(updatedDamage);
			didApply = true;
		}
		
		// We also use this path to extinguish a fire in the block.
		byte flags = newBlock.getFlags();
		if (FlagsAspect.isSet(flags, FlagsAspect.FLAG_BURNING))
		{
			flags = FlagsAspect.clear(flags, FlagsAspect.FLAG_BURNING);
			newBlock.setFlags(flags);
			didApply = true;
			
			// See if this is still something which can be re-ignited.
			Environment env = Environment.getShared();
			if (FireHelpers.canIgnite(env, context, _location, newBlock))
			{
				MutationBlockStartFire startFire = new MutationBlockStartFire(_location);
				context.mutationSink.future(startFire, MutationBlockStartFire.IGNITION_DELAY_MILLIS);
			}
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
