package com.jeffdisher.october.creatures;

import java.util.function.Consumer;

import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * The common interface of the state machines for creatures which are stored in their extended data.
 */
public interface ICreatureStateMachine
{
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
	 * Called before doneSpecialActions() to allow the receiver to update its tracking on a target entity, in case it
	 * has moved, returning the new location if it did.
	 * If a non-null value is returned, it will be used to rebuild the entity's movement plan.
	 * 
	 * @param context The context of the current tick.
	 * @param creatureLocation The creature's location.
	 * @return The updated location of the target entity, null if there isn't one or it didn't move meaningfully.
	 */
	EntityLocation didUpdateTargetLocation(TickProcessingContext context, EntityLocation creatureLocation);

	/**
	 * Allows an opportunity for the creature to take a special action in this tick.  This includes things like sending
	 * actions to other entities or requesting a creature to be spawned.
	 * If it returns true, the system will assume that this is all the creature wanted to do in this tick.
	 * 
	 * @param context The context of the current tick.
	 * @param creatureSpawner A consumer for any new entities spawned.
	 * @param requestDespawnWithoutDrops Called to request that this creature be despawned without dropping anything.
	 * @param creatureLocation The creature's location.
	 * @param creatureId The creature's ID.
	 * @return True if this creature wants to skip any other actions for this tick.
	 */
	boolean doneSpecialActions(TickProcessingContext context, Consumer<CreatureEntity> creatureSpawner, Runnable requestDespawnWithoutDrops, EntityLocation creatureLocation, int creatureId);

	/**
	 * @return The maximum pathing distance this creature should use when planning the path to a target.
	 */
	int getPathDistance();

	/**
	 * @return True if the current plan has deliberate intent (false if no plan or just idle wandering).
	 */
	boolean isPlanDeliberate();

	/**
	 * Freezes the current state of the creature's extended data into an opaque read-only instance.  May return null or
	 * the original instance.
	 * NOTE:  The receiver should be considered invalid after this call.
	 * 
	 * @return An opaque extended data object (could be null).
	 */
	Object freezeToData();
}
