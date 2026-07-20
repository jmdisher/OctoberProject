package com.jeffdisher.october.types;

import com.jeffdisher.october.utils.Assert;


/**
 * This is an axis-aligned, fixed-location volume in 3D floating point space.  In most cases, these are created from an
 * EntityLocation as the base and an EntityVolume to describe the edge.
 * The "low edges" (or base) are west-south-down while the "high edges" are east-north-up.
 */
public final class FixedRegion
{
	/**
	 * Creates an instance from a base and high edge.
	 * NOTE:  The edge must NOT be West, South, or Down from base.
	 * 
	 * @param base The west-south-down corner of the region.
	 * @param edge The east-north-up corner of the region.
	 * @return The new instance.
	 */
	public static FixedRegion fromBaseAndEdge(EntityLocation base, EntityLocation edge)
	{
		Assert.assertTrue(base.x() <= edge.x());
		Assert.assertTrue(base.y() <= edge.y());
		Assert.assertTrue(base.z() <= edge.z());
		
		return new FixedRegion(base.x()
			, base.y()
			, base.z()
			, edge.x()
			, edge.y()
			, edge.z()
		);
	}

	/**
	 * Creates an instance for a given base location and volume extending east-north-up from there.
	 * 
	 * @param base The west-south-down corner of the region.
	 * @param volume The volume describing the size of the region.
	 * @return A new instance.
	 */
	public static FixedRegion fromBaseAndVolume(EntityLocation base, EntityVolume volume)
	{
		return new FixedRegion(base.x()
			, base.y()
			, base.z()
			, base.x() + volume.width()
			, base.y() + volume.width()
			, base.z() + volume.height()
		);
	}


	public final float westX;
	public final float southY;
	public final float downZ;
	public final float eastX;
	public final float northY;
	public final float upZ;

	private FixedRegion(float westX
		, float southY
		, float downZ
		, float eastX
		, float northY
		, float upZ
	)
	{
		this.westX = westX;
		this.southY = southY;
		this.downZ = downZ;
		this.eastX = eastX;
		this.northY = northY;
		this.upZ = upZ;
	}

	/**
	 * Gets the centre-point of the region in all 3 dimensions.
	 * 
	 * @return The centre of the region.
	 */
	public EntityLocation getCentre()
	{
		float x = (this.eastX + this.westX) / 2.0f;
		float y = (this.northY + this.southY) / 2.0f;
		float z = (this.upZ + this.downZ) / 2.0f;
		return new EntityLocation(x, y, z);
	}
}
