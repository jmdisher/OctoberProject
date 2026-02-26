package com.jeffdisher.october.subactions;

import java.nio.ByteBuffer;
import java.util.function.Predicate;

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
 * Descends a ladder if any of the currently-occupied blocks are ladder blocks.
 */
public class EntitySubActionLadderDescend<T extends IMutableMinimalEntity> implements IEntitySubAction<T>
{
	/**
	 * The vertical distance we will descend in a single tick.
	 * We currently use a tick speed of 20/second so 0.1 will mean 2 blocks per second.
	 */
	public static final float DESCEND_PER_TICK = -0.1f;
	public static final EntitySubActionType TYPE = EntitySubActionType.LADDER_DESCEND;

	public static boolean canDescend(TickProcessingContext.IBlockFetcher previousBlockLookUp
			, EntityLocation location
			, EntityVolume volume
	)
	{
		return (null != _possibleDescendTarget(previousBlockLookUp
				, location
				, volume
		));
	}

	public static <T extends IMutableMinimalEntity> EntitySubActionLadderDescend<T> deserializeFromBuffer(ByteBuffer buffer)
	{
		return new EntitySubActionLadderDescend<>();
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutableMinimalEntity newEntity)
	{
		EntityLocation location = newEntity.getLocation();
		EntityVolume volume = newEntity.getType().volume();
		EntityLocation newLocation = _possibleDescendTarget(context.previousBlockLookUp, location, volume);
		
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
		return "Descend";
	}


	private static EntityLocation _possibleDescendTarget(TickProcessingContext.IBlockFetcher previousBlockLookUp
			, EntityLocation location
			, EntityVolume volume
	)
	{
		// We want to see if we are standing in or on a ladder and if we could move down.
		Environment env = Environment.getShared();
		Predicate<AbsoluteLocation> ladderSupplier = (AbsoluteLocation loc) -> {
			BlockProxy proxy = previousBlockLookUp.readBlock(loc);
			boolean ladder = false;
			if (null != proxy)
			{
				ladder = env.blocks.isLadderType(proxy.getBlock());
			}
			return ladder;
		};
		boolean isLadder = (null != EntityMovementHelpers.checkTypeIntersection(location, volume, ladderSupplier));
		if (!isLadder)
		{
			// Check the area just below us (where we will be going).
			isLadder = (null != EntityMovementHelpers.checkTypeIntersection(new EntityLocation(location.x(), location.y(), location.z() + DESCEND_PER_TICK), volume, ladderSupplier));
		}
		
		ViscosityReader reader = new ViscosityReader(env, previousBlockLookUp);
		_LadderHelper helper = new _LadderHelper(reader);
		EntityMovementHelpers.interactiveEntityMove(location, volume, new EntityLocation(0.0f, 0.0f, DESCEND_PER_TICK), helper);
		return (isLadder && !location.equals(helper.outputLocation))
			? helper.outputLocation
			: null
		;
	}


	private static class _LadderHelper implements EntityMovementHelpers.IInteractiveHelper
	{
		private final ViscosityReader _reader;
		public EntityLocation outputLocation;
		
		public _LadderHelper(ViscosityReader reader)
		{
			_reader = reader;
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
