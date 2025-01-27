package com.jeffdisher.october.creatures;

import java.util.function.Consumer;

import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.mutations.EntityChangeImpregnateCreature;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * The extended state data in CreatureEntity is immutable but the interactions with it form a pretty straight-forward
 * state machine so this object exists as a mutable projection of that state with a high-level interface.
 * It will re-serialize itself to the original instance if unchanged.
 * Ideally, we will eventually find a way to encode this in some declarative data structure, but that is a ways off, if
 * even possible (may not be sufficiently expressive).
 */
public class CowStateMachine implements ICreatureStateMachine
{
	private final float _viewDistance;
	private final float _matingDistance;
	private final Item _breedingItem;

	/**
	 * Creates a mutable state machine for a cow.
	 * 
	 * @param type The type being acted upon.
	 */
	public CowStateMachine(EntityType type)
	{
		_viewDistance = type.viewDistance();
		_matingDistance = type.actionDistance();
		_breedingItem = type.breedingItem();
	}

	@Override
	public boolean applyItem(Item itemType, EntityLocation offspringLocation)
	{
		boolean didApply = false;
		if (_breedingItem == itemType)
		{
			// We can't enter love mode if already pregnant (although that would only remain the case for a single tick).
			if (null == offspringLocation)
			{
				didApply = true;
			}
		}
		return didApply;
	}

	@Override
	public ICreatureStateMachine.TargetEntity selectTarget(TickProcessingContext context, EntityCollection entityCollection, EntityLocation creatureLocation, EntityType thisType, int thisCreatureId, boolean isInLoveMode)
	{
		ICreatureStateMachine.TargetEntity target;
		if (isInLoveMode)
		{
			// Find another cow in breeding mode.
			target = _findBreedableCow(entityCollection, creatureLocation, thisType, thisCreatureId);
		}
		else
		{
			// We will keep this simple:  Find the closest player holding wheat, up to our limit.
			target = _findWheatTarget(entityCollection, creatureLocation);
		}
		return target;
	}

	@Override
	public boolean setPregnant(EntityLocation offspringLocation, boolean isInLoveMode)
	{
		boolean didBecomePregnant = false;
		if (isInLoveMode)
		{
			didBecomePregnant = true;
		}
		return didBecomePregnant;
	}

	@Override
	public boolean doneSpecialActions(TickProcessingContext context, Consumer<CreatureEntity> creatureSpawner, EntityLocation creatureLocation, EntityType thisType, int thisCreatureId, int targetEntityId, long lastAttackTick, boolean isInLoveMode, EntityLocation offspringLocation)
	{
		// See if we are pregnant or searching for our mate.
		boolean didTakeAction = false;
		if (null != offspringLocation)
		{
			// We need to spawn an entity here (we use a placeholder since ID is re-assigned in the consumer).
			creatureSpawner.accept(CreatureEntity.create(context.idAssigner.next(), thisType, offspringLocation, thisType.maxHealth()));
			didTakeAction = true;
		}
		else if (CreatureEntity.NO_TARGET_ENTITY_ID != targetEntityId)
		{
			// We have a target so see if we are in love mode and if they are in range to breed.
			MinimalEntity targetEntity = context.previousEntityLookUp.apply(targetEntityId);
			// If we got here, they must not have unloaded (we would have observed that in didUpdateTargetLocation.
			Assert.assertTrue(null != targetEntity);
			
			// See if they are within mating distance and we are the father.
			EntityLocation targetLocation = targetEntity.location();
			float distance = SpatialHelpers.distanceBetween(creatureLocation, targetLocation);
			if (isInLoveMode && (distance <= _matingDistance) && (targetEntity.id() < thisCreatureId))
			{
				// Send the message to impregnate them.
				EntityChangeImpregnateCreature sperm = new EntityChangeImpregnateCreature(creatureLocation);
				context.newChangeSink.creature(targetEntityId, sperm);
				didTakeAction = true;
			}
		}
		return didTakeAction;
	}


	private ICreatureStateMachine.TargetEntity _findBreedableCow(EntityCollection entityCollection, EntityLocation creatureLocation, EntityType thisType, int thisCreatureId)
	{
		ICreatureStateMachine.TargetEntity[] target = new ICreatureStateMachine.TargetEntity[1];
		float[] distanceToTarget = new float[] { Float.MAX_VALUE };
		entityCollection.walkCreaturesInRange(creatureLocation, _viewDistance, (CreatureEntity check) -> {
			// Ignore ourselves and make sure that they are the same type.
			if ((thisCreatureId != check.id()) && (thisType == check.type()))
			{
				// See if they are also in love mode.
				if (check.inLoveMode())
				{
					EntityLocation end = check.location();
					float distance = SpatialHelpers.distanceBetween(creatureLocation, end);
					if (distance < distanceToTarget[0])
					{
						target[0] = new ICreatureStateMachine.TargetEntity(check.id(), end);
						distanceToTarget[0] = distance;
					}
				}
			}
		});
		return target[0];
	}

	private ICreatureStateMachine.TargetEntity _findWheatTarget(EntityCollection entityCollection, EntityLocation creatureLocation)
	{
		ICreatureStateMachine.TargetEntity[] target = new ICreatureStateMachine.TargetEntity[1];
		float[] distanceToTarget = new float[] { Float.MAX_VALUE };
		entityCollection.walkPlayersInRange(creatureLocation, _viewDistance, (Entity player) -> {
			// See if this player has wheat in their hand.
			int itemKey = player.hotbarItems()[player.hotbarIndex()];
			Items itemsInHand = player.inventory().getStackForKey(itemKey);
			if ((null != itemsInHand) && (_breedingItem == itemsInHand.type()))
			{
				EntityLocation end = player.location();
				float distance = SpatialHelpers.distanceBetween(creatureLocation, end);
				if (distance < distanceToTarget[0])
				{
					target[0] = new ICreatureStateMachine.TargetEntity(player.id(), end);
					distanceToTarget[0] = distance;
				}
			}
		});
		return target[0];
	}
}
