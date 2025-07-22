package com.jeffdisher.october.subactions;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.logic.EntityMovementHelpers;
import com.jeffdisher.october.logic.ViscosityReader;
import com.jeffdisher.october.mutations.EntitySubActionType;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Ascends a ladder if any of the currently-occupied blocks are ladder blocks.
 */
public class EntitySubActionLadderAscend<T extends IMutableMinimalEntity> implements IEntitySubAction<T>
{
	/**
	 * The vertical distance we will ascend in a single tick.
	 * We currently use a tick speed of 20/second so 0.1 will mean 2 blocks per second.
	 */
	public static final float ASCEND_PER_TICK = 0.1f;
	public static final EntitySubActionType TYPE = EntitySubActionType.LADDER_ASCEND;

	public static boolean canAscend(Function<AbsoluteLocation, BlockProxy> previousBlockLookUp
			, EntityLocation location
			, EntityVolume volume
	)
	{
		return (null != _possibleAscendTarget(previousBlockLookUp
				, location
				, volume
		));
	}

	public static <T extends IMutableMinimalEntity> EntitySubActionLadderAscend<T> deserializeFromBuffer(ByteBuffer buffer)
	{
		return new EntitySubActionLadderAscend<>();
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutableMinimalEntity newEntity)
	{
		EntityLocation location = newEntity.getLocation();
		EntityVolume volume = newEntity.getType().volume();
		EntityLocation newLocation = _possibleAscendTarget(context.previousBlockLookUp, location, volume);
		
		boolean didApply = false;
		if (null != newLocation)
		{
			newEntity.setLocation(newLocation);
			newEntity.setVelocityVector(new EntityLocation (0.0f, 0.0f, 0.0f));
			didApply = true;
		}
		return didApply;
	}

	@Override
	public EntitySubActionType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		// There is nothing in this type.
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Common case.
		return true;
	}

	@Override
	public String toString()
	{
		return "Ascend";
	}


	private static EntityLocation _possibleAscendTarget(Function<AbsoluteLocation, BlockProxy> previousBlockLookUp
			, EntityLocation location
			, EntityVolume volume
	)
	{
		// We want to see if we are standing in a ladder and if we could move up.
		Environment env = Environment.getShared();
		boolean isLadder = EntityMovementHelpers.isInLadder(location, volume, (AbsoluteLocation loc) -> {
			BlockProxy proxy = previousBlockLookUp.apply(loc);
			boolean ladder = false;
			if (null != proxy)
			{
				ladder = env.blocks.isLadderType(proxy.getBlock());
			}
			return ladder;
		});
		
		_LadderHelper helper = new _LadderHelper(env, previousBlockLookUp);
		EntityMovementHelpers.interactiveEntityMove(location, volume, new EntityLocation(0.0f, 0.0f, ASCEND_PER_TICK), helper);
		return (isLadder && !location.equals(helper.outputLocation))
			? helper.outputLocation
			: null
		;
	}


	private static class _LadderHelper implements EntityMovementHelpers.InteractiveHelper
	{
		private final ViscosityReader _reader;
		public EntityLocation outputLocation;
		
		public _LadderHelper(Environment env, Function<AbsoluteLocation, BlockProxy> blockLookup)
		{
			_reader = new ViscosityReader(env, blockLookup);
		}
		@Override
		public float getViscosityForBlockAtLocation(AbsoluteLocation location, boolean fromAbove)
		{
			// This is only used for ladder movement so we override this not to be from above, ever.
			return _reader.getViscosityFraction(location, false);
		}
		@Override
		public void setLocationAndCancelVelocity(EntityLocation finalLocation, boolean cancelX, boolean cancelY, boolean cancelZ)
		{
			this.outputLocation = finalLocation;
		}
	}
}
