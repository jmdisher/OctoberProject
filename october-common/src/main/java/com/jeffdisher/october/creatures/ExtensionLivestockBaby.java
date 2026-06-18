package com.jeffdisher.october.creatures;

import java.nio.ByteBuffer;

import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


public class ExtensionLivestockBaby implements EntityType.IExtension
{
	/**
	 * The time it takes for a newly spawned baby animal to grow to an adult.
	 */
	public static final long MILLIS_TO_MATURITY = 20L * 60L * 1000L;

	@Override
	public Object buildDefaultExtendedData(long gameTimeMillis)
	{
		long maturityMillis = gameTimeMillis + MILLIS_TO_MATURITY;
		return new BabyData(maturityMillis);
	}

	@Override
	public Object readExtendedData(ByteBuffer buffer, long gameTimeMillis)
	{
		// The baby codec was added in storage version 10 so there is no special-handling for old versions and this is never null.
		int millisRemaining = buffer.getInt();
		long maturityMillis = gameTimeMillis + (long)millisRemaining;
		return new BabyData(maturityMillis);
	}

	@Override
	public void writeExtendedData(ByteBuffer buffer, Object extendedData, long gameTimeMillis)
	{
		// The baby codec was added in storage version 10 so there is no special-handling for old versions and this is never null.
		BabyData safe = (BabyData) extendedData;
		long spill = safe.maturityMillis - gameTimeMillis;
		int millisRemaining = (spill > 0L)
			? (int)spill
			: 0
		;
		buffer.putInt(millisRemaining);
	}

	@Override
	public EntityType.TargetEntity findDeliberateTarget(MutableCreature creature, EntityCollection entityCollection)
	{
		// These never have deliberate paths.
		return null;
	}

	@Override
	public boolean isTargetValid(MutableCreature creature, EntityCollection entityCollection)
	{
		throw Assert.unreachable();
	}

	@Override
	public boolean didTakeSpecialAction(MutableCreature creature, TickProcessingContext context)
	{
		boolean isDone = false;
		
		// The only special action a baby can take is growing up, so see if that is ready.
		BabyData extendedData = (BabyData)creature.newExtendedData;
		if (context.currentTickTimeMillis >= extendedData.maturityMillis())
		{
			// We will change the type to the corresponding adult.
			EntityType currentType = creature.getType();
			EntityType adultType = currentType.adultType();
			Assert.assertTrue(null != adultType);
			creature.changeEntityType(adultType, context.currentTickTimeMillis);
			isDone = true;
		}
		return isDone;
	}

	@Override
	public boolean setCreaturePregnant(MutableCreature creature, EntityLocation sireLocation, long gameTimeMillis)
	{
		throw Assert.unreachable();
	}

	@Override
	public boolean shouldDespawn(MutableCreature creature, TickProcessingContext context)
	{
		// Livestock babies never automatically despawn.
		return false;
	}

	@Override
	public boolean canApplyItemToCreature(PartialEntity creature, Item itemType, long gameTimeMillis)
	{
		// We don't do direct item application to babies.
		return false;
	}

	@Override
	public boolean applyItemToCreature(MutableCreature creature, Item itemType, long gameTimeMillis)
	{
		// We don't do direct item application to babies.
		return false;
	}


	public static record BabyData(
		// The only data we currently track is when to grow up.
		long maturityMillis
	) {}
}
