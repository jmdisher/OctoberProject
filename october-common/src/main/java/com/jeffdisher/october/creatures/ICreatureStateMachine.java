package com.jeffdisher.october.creatures;

import java.util.function.Consumer;

import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * The common interface of the state machines for creatures which are stored in their extended data.
 */
public interface ICreatureStateMachine
{
	/**
	 * Applies the given item to the cow.  Note that this may do nothing if the item can't be applied to this creature
	 * or the creature isn't in a state where it will have any effect.
	 * 
	 * @param itemType The type of item to apply.
	 * @param offspringLocation The location where offspring should spawn, if non-null.
	 * @return True if the item did something.
	 */
	boolean applyItem(Item itemType, EntityLocation offspringLocation);

	/**
	 * Asks the creature to pick a new target entity location based on its currently location and the other players or
	 * creatures in the loaded world.
	 * 
	 * @param context The context of the current tick.
	 * @param entityCollection The collection of entities in the world.
	 * @param creatureLocation The creature's location.
	 * @param thisType This creature's type.
	 * @param thisCreatureId This creature's ID.
	 * @param isInLoveMode True if this creature should look for a breeding partner.
	 * @return A description of the target entity or null if there is no target.
	 */
	TargetEntity selectTarget(TickProcessingContext context, EntityCollection entityCollection, EntityLocation creatureLocation, EntityType thisType, int thisCreatureId, boolean isInLoveMode);

	/**
	 * Sets the state of the cow to be ready to produce offspring at a specific location if it is in love mode but not
	 * already pregnant.
	 * 
	 * @param offspringLocation The location where the offspring should be created.
	 * @param isInLoveMode True if this creature is in love mode.
	 * @return True if the cow became pregnant.
	 */
	boolean setPregnant(EntityLocation offspringLocation, boolean isInLoveMode);

	/**
	 * Allows an opportunity for the creature to take a special action in this tick.  This includes things like sending
	 * actions to other entities or requesting a creature to be spawned.
	 * If it returns true, the system will assume that this is all the creature wanted to do in this tick.
	 * 
	 * @param context The context of the current tick.
	 * @param creatureSpawner A consumer for any new entities spawned.
	 * @param creatureLocation The creature's location.
	 * @param thisType This creature's type.
	 * @param thisCreatureId This creature's ID.
	 * @param targetEntityId The ID of the currently-selected target (could be 0).
	 * @param lastAttackTick The tick number of the last time this creature attacked (could be 0).
	 * @param isInLoveMode True if this creature is in love mode.
	 * @param offspringLocation The location where offspring should spawn, if non-null.
	 * @return True if this creature wants to skip any other actions for this tick.
	 */
	boolean doneSpecialActions(TickProcessingContext context, Consumer<CreatureEntity> creatureSpawner, EntityLocation creatureLocation, EntityType thisType, int thisCreatureId, int targetEntityId, long lastAttackTick, boolean isInLoveMode, EntityLocation offspringLocation);


	/**
	 * A record which contains the information about an entity selected as a target.
	 */
	public static record TargetEntity(int id, EntityLocation location) {}
}
