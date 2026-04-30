package com.jeffdisher.october.logic;

import java.util.List;
import java.util.Map;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.SubBlock;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * A utility helper to resolve viscosity from a BlockProxy (since it requires a few checks we don't want to duplicate).
 * This is an instance type, instead of merely a static helper, so that it can be passed into utilities which need it.
 */
public class ViscosityReader
{
	private final Environment _env;
	private final TickProcessingContext.IBlockFetcher _blockLookup;

	public ViscosityReader(Environment env, TickProcessingContext.IBlockFetcher blockLookup)
	{
		_env = env;
		_blockLookup = blockLookup;
	}

	public float getMaxStillViscosityInVolume(EntityLocation base, EntityVolume volume)
	{
		List<AbsoluteLocation> locations = VolumeIterator.getAllInVolume(base, volume);
		Map<AbsoluteLocation, BlockProxy> map = _blockLookup.readBlockBatch(locations);
		
		float viscosity;
		if (map.size() < locations.size())
		{
			// We were missing something so just default to saying full viscosity.
			viscosity = 1.0f;
		}
		else
		{
			// In this case, we are always just considering the standing viscosity, not falling from above.
			boolean fromAbove = false;
			viscosity = _maxViscosityInVolume(map, base, volume, fromAbove);
		}
		return viscosity;
	}

	public boolean isSolidBlockInVolume(EntityLocation base, EntityVolume volume, boolean fromAbove)
	{
		List<AbsoluteLocation> locations = VolumeIterator.getAllInVolume(base, volume);
		Map<AbsoluteLocation, BlockProxy> map = _blockLookup.readBlockBatch(locations);
		
		boolean isSolid;
		if (map.size() < locations.size())
		{
			// We were missing something so just default to saying it is solid.
			isSolid = true;
		}
		else
		{
			float viscosity = _maxViscosityInVolume(map, base, volume, fromAbove);
			isSolid = (1.0f == viscosity);
		}
		return isSolid;
	}


	private float _maxViscosityInVolume(Map<AbsoluteLocation, BlockProxy> loadedProxies, EntityLocation base, EntityVolume volume, boolean fromAbove)
	{
		float viscosity = 0.0f;
		boolean isSolid = false;
		
		// We want to walk every sub-block within the volume.
		float edgeX = base.x() + volume.width();
		float edgeY = base.y() + volume.width();
		float edgeZ = base.z() + volume.height();
		for (Map.Entry<AbsoluteLocation, BlockProxy> elt : loadedProxies.entrySet())
		{
			BlockProxy proxy = elt.getValue();
			Block block = proxy.getBlock();
			boolean isActive = FlagsAspect.isSet(proxy.getFlags(), FlagsAspect.FLAG_ACTIVE);
			
			// Determine if we need to use sub-block collision.
			Long mask = _env.blocks.getSubBlocks(block, isActive);
			if (null != mask)
			{
				// We need to check the intersection of sub-blocks and look up their flags.
				AbsoluteLocation loc = elt.getKey();
				FacingDirection facing = proxy.getOrientation();
				EntityLocation thisBase = loc.toEntityLocation();
				EntityLocation thisBeyond = new EntityLocation(thisBase.x() + 0.99f, thisBase.y() + 0.99f, thisBase.z() + 0.99f);
				byte minX = SubBlock.oneAxis(Math.max(thisBase.x(), base.x()));
				byte minY = SubBlock.oneAxis(Math.max(thisBase.y(), base.y()));
				byte minZ = SubBlock.oneAxis(Math.max(thisBase.z(), base.z()));
				byte maxX = SubBlock.oneAxis(Math.min(thisBeyond.x(), edgeX));
				byte maxY = SubBlock.oneAxis(Math.min(thisBeyond.y(), edgeY));
				byte maxZ = SubBlock.oneAxis(Math.min(thisBeyond.z(), edgeZ));
				for (byte z = minZ; !isSolid && (z <= maxZ); ++z)
				{
					for (byte y = minY; !isSolid && (y <= maxY); ++y)
					{
						for (byte x = minX; !isSolid && (x <= maxX); ++x)
						{
							long bit = facing.inverseRotateInSubBlock(SubBlock.fromInt(x, y, z)).getMask();
							isSolid = (0L != (mask & bit));
						}
					}
				}
				if (isSolid)
				{
					break;
				}
			}
			else
			{
				// No sub-blocks so just use normal viscosity.
				float one = _env.blocks.getViscosityFraction(block, isActive, fromAbove);
				if (1.0f == one)
				{
					isSolid = true;
					break;
				}
				else
				{
					viscosity = Math.max(one, viscosity);
				}
			}
		}
		return isSolid
			? 1.0f
			: viscosity
		;
	}
}
