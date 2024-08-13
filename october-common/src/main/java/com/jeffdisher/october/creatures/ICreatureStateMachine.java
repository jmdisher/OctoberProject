package com.jeffdisher.october.creatures;

import java.util.List;
import java.util.function.Consumer;

import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * The common interface of the state machines for creatures which are stored in their extended data.
 */
public interface ICreatureStateMachine
{
	/**
	 * An accessor for the read-only movement plan in the instance.
	 * 
	 * @return A read-only view of the current movement plan (could be null).
	 */
	List<AbsoluteLocation> getMovementPlan();

	/**
	 * Updates the movement plan to a copy of the one given.
	 * 
	 * @param movementPlan The movement plan (could be null).
	 */
	void setMovementPlan(List<AbsoluteLocation> mutablePlan);

	/**
	 * Asks the creature to pick a new target entity location based on its currently location and the other players or
	 * creatures in the loaded world.
	 * 
	 * @param context The context of the current tick.
	 * @param entityCollection The collection of entities in the world.
	 * @param creatureLocation The creature's location.
	 * @param creatureId The creature's ID.
	 * @return The location of the target entity or null if there is no target.
	 */
	EntityLocation selectDeliberateTarget(TickProcessingContext context, EntityCollection entityCollection, EntityLocation creatureLocation, int creatureId);

	/**
	 * Allows an opportunity for the creature to take a special action in this tick.  This includes things like sending
	 * actions to other entities or requesting a creature to be spawned.
	 * If it returns true, the system will assume that this is all the creature wanted to do in this tick.
	 * 
	 * @param context The context of the current tick.
	 * @param creatureSpawner A consumer for any new entities spawned.
	 * @param creatureLocation The creature's location.
	 * @param creatureId The creature's ID.
	 * @return True if this creature wants to skip any other actions for this tick.
	 */
	boolean doneSpecialActions(TickProcessingContext context, Consumer<CreatureEntity> creatureSpawner, EntityLocation creatureLocation, int creatureId);

	/**
	 * @return The maximum pathing distance this creature should use when planning the path to a target.
	 */
	int getPathDistance();

	/**
	 * @return True if the current plan has deliberate intent (false if no plan or just idle wandering).
	 */
	boolean isPlanDeliberate();

	/**
	 * Called if selectDeliberateTarget() returns null to ask if the creature should make an idle movement (since that
	 * is currently generalized across all creature types).
	 * Returns true if a random plan should be attempted or false to do nothing.
	 * NOTE:  The implementation may change state in response to this call!
	 * 
	 * @param context The context of the current tick.
	 * @return True if a random plan should be made in this tick.
	 */
	boolean canMakeIdleMovement(TickProcessingContext context);

	/**
	 * Freezes the current state of the creature's extended data into an opaque read-only instance.  May return null or
	 * the original instance.
	 * NOTE:  The receiver should be considered invalid after this call.
	 * 
	 * @return An opaque extended data object (could be null).
	 */
	Object freezeToData();
}
