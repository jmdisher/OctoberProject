package com.jeffdisher.october.client;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IOctree;
import com.jeffdisher.october.data.IReadOnlyCuboidData;


/**
 * Helpers used to expose and modify internal state of a CuboidData object.  Since these are normally intended to be
 * read-only, these helpers are explicitly unsafe and can only be reasonably used on the client to support optimizations
 * in the SpeculativeProjection.
 */
public class CuboidUnsafe
{
	public static IReadOnlyCuboidData cloneWithReplacement(IReadOnlyCuboidData raw, Aspect<?, ?> type, IOctree<?> octree)
	{
		CuboidData original = (CuboidData)raw;
		IOctree<?>[] copy = original.unsafeDataAccess().clone();
		copy[type.index()] = octree;
		return CuboidData.createNew(raw.getCuboidAddress(), copy);
	}

	public static <O extends IOctree<?>> O getAspectUnsafe(IReadOnlyCuboidData raw, Aspect<?, O> type)
	{
		CuboidData original = (CuboidData)raw;
		return type.octreeType().cast(original.unsafeDataAccess()[type.index()]);
	}
}
