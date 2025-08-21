package com.jeffdisher.october.subactions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.logic.PropertyHelpers;
import com.jeffdisher.october.mutations.EntitySubActionType;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * This sub-action will move the entity to the destination of the given portal surface block, assuming it is
 * intersecting with the entity and the entity isn't on a special action cooldown.
 */
public class EntitySubActionTravelViaBlock implements IEntitySubAction<IMutablePlayerEntity>
{
	public static final EntitySubActionType TYPE = EntitySubActionType.TRAVEL_VIA_BLOCK;
	public static final long TRAVEL_COOLDOWN_MILLIS = 10_000L;
	public static final String PORTAL_SURFACE_ID = "op.portal_surface";
	public static final String PORTAL_KEYSTONE_ID = "op.portal_keystone";

	public static EntitySubActionTravelViaBlock deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation portalSurfaceLocation = CodecHelpers.readAbsoluteLocation(buffer);
		return new EntitySubActionTravelViaBlock(portalSurfaceLocation);
	}


	private final AbsoluteLocation _portalSurfaceLocation;

	public EntitySubActionTravelViaBlock(AbsoluteLocation portalSurfaceLocation)
	{
		Assert.assertTrue(null != portalSurfaceLocation);
		
		_portalSurfaceLocation = portalSurfaceLocation;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		// We want to make sure that we are not still busy doing something else.
		boolean isReady = ((newEntity.getLastSpecialActionMillis() + TRAVEL_COOLDOWN_MILLIS) <= context.currentTickTimeMillis);
		
		// Verify that this entity is intersecting with this surface block location.
		boolean isIntersecting = isReady && _checkBlockIntersecting(newEntity.getLocation(), newEntity.getType().volume(), _portalSurfaceLocation);
		
		// Get the target location.
		EntityLocation newLocation = isIntersecting
			? _getTargetLocation(context, _portalSurfaceLocation)
			: null
		;
		
		boolean didApply = false;
		if (null != newLocation)
		{
			newEntity.setLocation(newLocation);
			newEntity.setVelocityVector(new EntityLocation(0.0f, 0.0f, 0.0f));
			newEntity.setLastSpecialActionMillis(context.currentTickTimeMillis);
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
		CodecHelpers.writeAbsoluteLocation(buffer, _portalSurfaceLocation);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Default case.
		return true;
	}

	@Override
	public String toString()
	{
		return "Travel via " + _portalSurfaceLocation;
	}


	private static boolean _checkBlockIntersecting(EntityLocation location, EntityVolume volume, AbsoluteLocation portalSurfaceLocation)
	{
		AbsoluteLocation base = location.getBlockLocation();
		AbsoluteLocation edge = new EntityLocation(location.x() + volume.width(), location.y() + volume.width(), location.z() + volume.height()).getBlockLocation();
		
		int minX = base.x();
		int minY = base.y();
		int minZ = base.z();
		int maxX = edge.x();
		int maxY = edge.y();
		int maxZ = edge.z();
		return ((minX <= portalSurfaceLocation.x()) && (maxX >= portalSurfaceLocation.x())
			&& (minY <= portalSurfaceLocation.y()) && (maxY >= portalSurfaceLocation.y())
			&& (minZ <= portalSurfaceLocation.z()) && (maxZ >= portalSurfaceLocation.z())
		);
	}

	private static EntityLocation _getTargetLocation(TickProcessingContext context, AbsoluteLocation portalSurfaceLocation)
	{
		EntityLocation newLocation = null;
		BlockProxy surfaceProxy = context.previousBlockLookUp.apply(portalSurfaceLocation);
		if ((null != surfaceProxy) && PORTAL_SURFACE_ID.equals(surfaceProxy.getBlock().item().id()))
		{
			AbsoluteLocation rootLocation = surfaceProxy.getMultiBlockRoot();
			if (null == rootLocation)
			{
				rootLocation = portalSurfaceLocation;
			}
			AbsoluteLocation keystoneLocation = rootLocation.getRelative(0, 0, -1);
			BlockProxy target = context.previousBlockLookUp.apply(keystoneLocation);
			if ((null != target) && PORTAL_KEYSTONE_ID.equals(target.getBlock().item().id()))
			{
				ItemSlot slot = target.getSpecialSlot();
				NonStackableItem nonStack = (null != slot) ? slot.nonStackable : null;
				AbsoluteLocation targetLocation = (null != nonStack) ? PropertyHelpers.getLocation(nonStack) : null;
				if (null != targetLocation)
				{
					// We assume that the target is a solid block (probably a keystone) so teleport right above it.
					newLocation = targetLocation.getRelative(0, 0, 1).toEntityLocation();
				}
			}
		}
		return newLocation;
	}
}
