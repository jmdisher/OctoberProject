package com.jeffdisher.october.actions.passive;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.logic.EntityMovementHelpers;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.logic.ViscosityReader;
import com.jeffdisher.october.mutations.MutationBlockReplaceDropExisting;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.PassiveEntity;
import com.jeffdisher.october.types.PassiveType;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;
import com.jeffdisher.october.utils.CuboidGenerator;


/**
 * These are synthesized by the system for every tick and applied to every loaded FALLING_BLOCK passive entity.
 * This means that it is responsible for normal movement (which is just passively applying existing velocity and
 * gravity) but also when to change back into a solid block and whether to break blocks when doing so.
 */
public class PassiveSynth_FallingBlock
{
	private PassiveSynth_FallingBlock()
	{
		// No point in instantiating these.
	}

	public static PassiveEntity applyChange(TickProcessingContext context, PassiveEntity entity)
	{
		Environment env = Environment.getShared();
		PassiveType type = entity.type();
		Assert.assertTrue(PassiveType.FALLING_BLOCK == type);
		
		// Try to move and, if we collide with any surface, despawn and turn into a block in the centre of our current location.
		EntityLocation startLocation = entity.location();
		EntityLocation startVelocity = entity.velocity();
		EntityVolume volume = type.volume();
		
		// Now apply normal movement.
		// NOTE:  We don't want to collide with anything "above" the block's current location (once it fell through a block, we will assume that it has left that block).
		int startZ = startLocation.getBlockLocation().z();
		TickProcessingContext.IBlockFetcher filterLookup = new TickProcessingContext.IBlockFetcher() {
			@Override
			public BlockProxy readBlock(AbsoluteLocation location)
			{
				// NOTE:  We may want to change this lookup to return an interface or something more restrictive, in the
				// future, as creating this empty cuboid is not cheap (not too expensive, though).
				return (location.z() > startZ)
					? BlockProxy.load(location.getBlockAddress(), CuboidGenerator.createFilledCuboid(location.getCuboidAddress(), env.special.AIR))
					: context.previousBlockLookUp.readBlock(location)
				;
			}
			@Override
			public Map<AbsoluteLocation, BlockProxy> readBlockBatch(Collection<AbsoluteLocation> locations)
			{
				// NOTE:  We may want to change this lookup to return an interface or something more restrictive, in the
				// future, as creating this empty cuboid is not cheap (not too expensive, though).
				// We just use a fake address since it doesn't actually matter.
				CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(-1, -1, -1), env.special.AIR);
				
				Map<AbsoluteLocation, BlockProxy> completed = new HashMap<>();
				for (AbsoluteLocation location : locations)
				{
					BlockProxy proxy = (location.z() > startZ)
						? BlockProxy.load(location.getBlockAddress(), cuboid)
						: context.previousBlockLookUp.readBlock(location)
					;
					if (null != proxy)
					{
						completed.put(location, proxy);
					}
				}
				return completed;
			}
		};
		float seconds = (float)context.millisPerTick / EntityMovementHelpers.FLOAT_MILLIS_PER_SECOND;
		ViscosityReader reader = new ViscosityReader(env, filterLookup);
		EntityMovementHelpers.HighLevelMovementResult movement = EntityMovementHelpers.commonMovementIdiom(reader
			, startLocation
			, startVelocity
			, volume
			, 0.0f
			, 0.0f
			, 0.0f
			, seconds
		);
		EntityLocation finalLocation = movement.location();
		
		// If we are on the ground, despawn and form the block.
		PassiveEntity result;
		if (movement.isOnGround())
		{
			// Send the mutation to the block to break anything there as an item and set the block.
			AbsoluteLocation target = SpatialHelpers.getCentreOfRegion(finalLocation, volume).getBlockLocation();
			Block blockType = (Block)entity.extendedData();
			MutationBlockReplaceDropExisting blockMutation = new MutationBlockReplaceDropExisting(target, blockType);
			context.mutationSink.next(blockMutation);
			
			// Despawn the falling block.
			result = null;
		}
		else
		{
			EntityLocation finalVelocity = movement.velocity();
			
			// We should never be able to stand still without falling onto the ground and despawning.
			Assert.assertTrue(!finalLocation.equals(entity.location()) || !finalVelocity.equals(entity.velocity()));
			result = new PassiveEntity(entity.id()
				, type
				, finalLocation
				, finalVelocity
				, entity.extendedData()
				, entity.lastAliveMillis()
			);
		}
		return result;
	}
}
