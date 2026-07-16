package com.jeffdisher.october.creatures;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.actions.EntityActionImpregnateCreature;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * While breeding is normally just a livestock mechanic, it can technically be used by things like villagers, just
 * triggered a different way.
 * This helper class allows this logic to be used in more like the "1-level delegation" design being considered before
 * the IExtension interface was created by embedding an instance of this inside a IExtension instance.
 */
public class CommonBreedingLogic
{
	/**
	 * The timeout from exiting love mode until it can be entered again.
	 */
	public static final long MILLIS_BREEDING_COOLDOWN = 5L * 60L * 1000L;

	private final Function<Object, Data> _embeddedReader;

	/**
	 * Creates the embedded breeding logic instance.
	 * 
	 * @param embeddedReader A function to convert "extended data" into the internal data object type.
	 */
	public CommonBreedingLogic(Function<Object, Data> embeddedReader)
	{
		_embeddedReader = embeddedReader;
	}

	/**
	 * @return A default/empty Data object.
	 */
	public Data buildDefault()
	{
		return new Data(false
			, null
			, 0L
		);
	}

	/**
	 * Reads a Data object.
	 * 
	 * @param buffer The buffer to read.
	 * @param gameTimeMillis The current game time.
	 * @return The new Data object (never null).
	 */
	public Data readData(ByteBuffer buffer, long gameTimeMillis)
	{
		boolean inLoveMode = CodecHelpers.readBoolean(buffer);
		EntityLocation offspringLocation = CodecHelpers.readNullableEntityLocation(buffer);
		int millisRemaining = buffer.getInt();
		long breedingReadyMillis = gameTimeMillis + (long)millisRemaining;
		return new Data(inLoveMode
			, offspringLocation
			, breedingReadyMillis
		);
	}

	/**
	 * Writes a Data object.
	 * 
	 * @param buffer The buffer to write.
	 * @param data The object to store.
	 * @param gameTimeMillis The current game time.
	 */
	public void writeData(ByteBuffer buffer, Data data, long gameTimeMillis)
	{
		CodecHelpers.writeBoolean(buffer, data.inLoveMode);
		CodecHelpers.writeNullableEntityLocation(buffer, data.offspringLocation);
		long spill = data.breedingReadyMillis - gameTimeMillis;
		int millisRemaining = (spill > 0L)
			? (int)spill
			: 0
		;
		buffer.putInt(millisRemaining);
	}

	/**
	 * Reads the internal data to determine if this creature is in love mode.
	 * 
	 * @param creature The creature to read.
	 * @return True if the creature is in love mode.
	 */
	public boolean isCreatureInLoveMode(CreatureEntity creature)
	{
		Data data = _embeddedReader.apply(creature.extendedData());
		return data.inLoveMode;
	}

	/**
	 * Reads the internal data to determine if this creature is in love mode.
	 * 
	 * @param creature The creature to read.
	 * @return True if the creature is in love mode.
	 */
	public boolean isMutableInLoveMode(MutableCreature creature)
	{
		Data data = _embeddedReader.apply(creature.newExtendedData);
		return data.inLoveMode;
	}

	/**
	 * Finds and returns a breeding partner for the given creature (both MUST be in love mode).
	 * 
	 * @param creature The creature searching (MUST be in love mode).
	 * @param entityCollection The collection to use to find the partner.
	 * @return The target creature or null if the caller wasn't in love mode or couldn't find a partner.
	 */
	public EntityType.TargetEntity findBreedingPartner(MutableCreature creature
		, EntityCollection entityCollection
	)
	{
		Data data = _embeddedReader.apply(creature.newExtendedData);
		
		// The caller needs to check if we are in love mode before calling or they will look for the wrong kind of target.
		Assert.assertTrue(data.inLoveMode);
		
		EntityType.TargetEntity[] target = new EntityType.TargetEntity[1];
		float[] distanceToTarget = new float[] { Float.MAX_VALUE };
		EntityType thisType = creature.getType();
		EntityVolume thisVolume = thisType.volume();
		int thisCreatureId = creature.getId();
		EntityLocation sourceEyeLocation = SpatialHelpers.getEyeLocation(creature.getLocation(), thisVolume);
		entityCollection.walkCreaturesInViewDistance(creature, (CreatureEntity check) -> {
			// Ignore ourselves and make sure that they are the same type and in love mode.
			if ((thisCreatureId != check.id())
				&& (thisType == check.type())
				&& _embeddedReader.apply(check.extendedData()).inLoveMode
			)
			{
				// See how far away they are so we choose the closest.
				EntityLocation end = check.location();
				float distance = SpatialHelpers.distanceFromLocationToVolume(sourceEyeLocation, end, thisVolume);
				if (distance < distanceToTarget[0])
				{
					target[0] = new EntityType.TargetEntity(check.id(), end);
					distanceToTarget[0] = distance;
				}
			}
		});
		EntityType.TargetEntity ifChanged = target[0];
		return ifChanged;
	}

	/**
	 * A helper to check if a creature could possibly enter love mode.
	 * 
	 * @param creature A read-only minimal representation of the creature.
	 * @param gameTimeMillis The current game time.
	 * @return True if this creature could enter love mode.
	 */
	public boolean canEnterLoveMode(MinimalEntity creature, long gameTimeMillis)
	{
		// Check that we are not already in love mode and that our breeding timeout is expired.
		Data data = _embeddedReader.apply(creature.extendedData());
		return !data.inLoveMode() && (gameTimeMillis >= data.breedingReadyMillis());
	}

	/**
	 * Puts the creature into love mode, returning a new Data object if successful.
	 * Internally, this will also prepare them to create a new movement plan if changed.
	 * 
	 * @param creature The creature to update.
	 * @param gameTimeMillis The current game time.
	 * @return The new data object if they were changed into love mode.
	 */
	public Data enterLoveMode(MutableCreature creature, long gameTimeMillis)
	{
		// We can enter love mode if not already in love mode, not already pregnant, and not still on breeding cooldown.
		Data ifChanged = null;
		Data data = _embeddedReader.apply(creature.newExtendedData);
		if (!data.inLoveMode()
			&& (null == data.offspringLocation())
			&& (gameTimeMillis >= data.breedingReadyMillis())
		)
		{
			// If we applied this, put us into love mode and clear other plans.
			ifChanged = new Data(
				true
				, null
				, 0L
			);
			creature.movementPlan = null;
			creature.shouldTakeActionInTick = true;
		}
		return ifChanged;
	}

	/**
	 * Puts the creature into a pregnant state such that they will spawn offspring when given the chance.
	 * Internally, this will also prepare them to create a new movement plan if changed.
	 * 
	 * @param creature The creature to update.
	 * @param sireLocation The base location of the father.
	 * @param gameTimeMillis The current game time.
	 * @return The new data object if they were changed into pregnant mode.
	 */
	public Data setCreaturePregnant(MutableCreature creature
		, EntityLocation sireLocation
		, long gameTimeMillis
	)
	{
		Data newAfterSetPregnant = null;
		
		// We can only do this if already in love mode.
		if (_embeddedReader.apply(creature.newExtendedData).inLoveMode)
		{
			// Average the locations.
			EntityLocation parentLocation = creature.getLocation();
			EntityLocation spawnLocation = new EntityLocation((sireLocation.x() + parentLocation.x()) / 2.0f
					, (sireLocation.y() + parentLocation.y()) / 2.0f
					, (sireLocation.z() + parentLocation.z()) / 2.0f
			);
			// Clear the love mode, set the spawn location, and clear existing plans.
			long breedingReadyMillis = gameTimeMillis + MILLIS_BREEDING_COOLDOWN;
			newAfterSetPregnant = new Data(
				false
				, spawnLocation
				, breedingReadyMillis
			);
			creature.movementPlan = null;
			creature.shouldTakeActionInTick = true;
		}
		return newAfterSetPregnant;
	}

	/**
	 * Spawns offspring if the creature was pregnant, clearing their pregnant state if so.
	 * 
	 * @param context The current tick's context.
	 * @param creature The creature to update.
	 * @return The new data object if they were changed out of pregnant mode.
	 */
	public Data spawnOffspring(TickProcessingContext context
		, MutableCreature creature
	)
	{
		Data ifChanged = null;
		Data data = _embeddedReader.apply(creature.newExtendedData);
		if (null != data.offspringLocation)
		{
			// Spawn the creature and clear our offspring location.
			Environment env = Environment.getShared();
			EntityType offspringType = env.creatures.getOffspringType(creature.newType);
			context.creatureSpawner.spawnCreature(offspringType, data.offspringLocation);
			ifChanged = new Data(
				false
				, null
				, data.breedingReadyMillis()
			);
		}
		return ifChanged;
	}

	/**
	 * Called to allow the given creature to sire offspring, if they are in the right mode and have the right ID.
	 * Note that they can only impregnate their target if they are in love mode, have a target, and the target is within
	 * action distance.
	 * 
	 * @param context The current tick's context.
	 * @param creature The creature to update.
	 * @return The new data object if they cleared their love state and sent an action to impregnate their target.
	 */
	public Data impregnateTarget(TickProcessingContext context
		, MutableCreature creature
	)
	{
		Data ifChanged = null;
		
		Data data = _embeddedReader.apply(creature.newExtendedData);
		int targetEntityId = (null != creature.movementPlan) ? creature.movementPlan.targetEntityId() : CreatureEntity.NO_TARGET_ENTITY_ID;
		if (data.inLoveMode()
			&& (CreatureEntity.NO_TARGET_ENTITY_ID != targetEntityId)
		)
		{
			// We are in love mode, and have found a target, so see if we are close enough to impregnate our target.
			// We have a target so see if we are in love mode and if they are in range to breed.
			MinimalEntity targetEntity = context.previousEntityLookUp.getById(targetEntityId);
			// If we got here, they must not have unloaded (we would have observed that in didUpdateTargetLocation.
			Assert.assertTrue(null != targetEntity);
			
			// See if they are within mating distance and we are the father.
			EntityLocation location = creature.newLocation;
			EntityType creatureType = creature.newType;
			EntityLocation sourceEyeLocation = SpatialHelpers.getEyeLocation(location, creatureType.volume());
			
			EntityLocation targetBase = targetEntity.location();
			EntityVolume targetVolume = targetEntity.type().volume();
			float distance = SpatialHelpers.distanceFromLocationToVolume(sourceEyeLocation, targetBase, targetVolume);
			
			float matingDistance = creatureType.actionDistance();
			if ((distance <= matingDistance) && (targetEntity.id() < creature.getId()))
			{
				// Send the message to impregnate them.
				EntityActionImpregnateCreature sperm = new EntityActionImpregnateCreature(location);
				context.newChangeSink.creature(targetEntityId, sperm);
				// We can also now clear our plans since we are done with them.
				// However, we exited love mode so record when we should re-enter it.
				long breedingReadyMillis = context.currentTickTimeMillis + MILLIS_BREEDING_COOLDOWN;
				ifChanged = new Data(
					false
					, null
					, breedingReadyMillis
				);
			}
		}
		return ifChanged;
	}


	public static record Data(
		// True if this is a breedable creature which should now search for a partner.
		boolean inLoveMode
		// Non-null if this is a breedable creature who is ready to spawn offspring.
		, EntityLocation offspringLocation
		// The gameTimeMillis when breeding becomes available again (cooldown).
		, long breedingReadyMillis
	) {}
}
