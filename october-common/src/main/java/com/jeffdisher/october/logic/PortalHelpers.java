package com.jeffdisher.october.logic;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.mutations.MultiBlockUtils;
import com.jeffdisher.october.properties.PropertyRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Helpers related to the periodic updates for the portal keystone.
 * Note that much of this will need to be hardened once users can create their own portals.  For now, this just manages
 * the creation/destruction of the portal surface when the portal composite structure changes.
 */
public class PortalHelpers
{
	/**
	 * Checks that the given proxy is currently a portal keystone.
	 * 
	 * @param proxy The proxy for the block to check.
	 * @return True if this is a portal keystone.
	 */
	public static boolean isKeystone(IMutableBlockProxy proxy)
	{
		Environment env = Environment.getShared();
		return (env.special.blockPortalKeystone == proxy.getBlock());
	}

	/**
	 * Called to handle the updates to the portal surface (creating or destroying it) when the keystone receives a
	 * periodic event (so, this is currently a polling solution).
	 * NOTE:  MUST be called on a portal keystone.
	 * 
	 * @param env The environment.
	 * @param context The current tick context.
	 * @param keystoneLocation The location of the keystone.
	 * @param keystoneProxy The proxy for the keystone.
	 */
	public static void handlePortalSurface(Environment env, TickProcessingContext context, AbsoluteLocation keystoneLocation, IMutableBlockProxy keystoneProxy)
	{
		AbsoluteLocation surfaceRootLocation = keystoneLocation.getRelative(0, 0, 1);
		BlockProxy rootProxy = context.previousBlockLookUp.apply(surfaceRootLocation);
		
		if (null != rootProxy)
		{
			boolean isPortalSurfaceActive = (env.special.blockPortalSurface == rootProxy.getBlock());
			AbsoluteLocation activeTarget = _checkActiveTarget(keystoneProxy);
			
			if (null != activeTarget)
			{
				// Note that we currently don't check this output location but we assume it is a remote keystone (may
				// need to check in the future - could be solid blocks which isn't ideal).
				// We need to create, or maintain, the portal surface.
				if (!isPortalSurfaceActive)
				{
					FacingDirection orientation = keystoneProxy.getOrientation();
					int entityId = 0;
					MultiBlockUtils.send2PhaseMultiBlock(env, context, env.special.blockPortalSurface, surfaceRootLocation, orientation, entityId);
				}
			}
			else
			{
				// We need to break the portal surface, if it is here.
				if (isPortalSurfaceActive)
				{
					// This is always replacing with air
					MultiBlockUtils.replaceMultiBlock(env, context, surfaceRootLocation, env.special.blockPortalSurface, env.special.AIR);
				}
			}
		}
		else
		{
			// NOTE:  For now, we ignore this when not loaded.
		}
	}


	private static AbsoluteLocation _checkActiveTarget(IMutableBlockProxy keystoneProxy)
	{
		byte flags = keystoneProxy.getFlags();
		AbsoluteLocation target = null;
		if (FlagsAspect.isSet(flags, FlagsAspect.FLAG_ACTIVE))
		{
			ItemSlot slot = keystoneProxy.getSpecialSlot();
			NonStackableItem nonStack = (null != slot) ? slot.nonStackable : null;
			target = (null != nonStack) ? (AbsoluteLocation) nonStack.properties().get(PropertyRegistry.LOCATION) : null;
		}
		return target;
	}
}
