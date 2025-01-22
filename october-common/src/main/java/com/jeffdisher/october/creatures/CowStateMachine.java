package com.jeffdisher.october.creatures;

import java.util.function.Consumer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.mutations.EntityChangeImpregnateCreature;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
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
	public static final float COW_VIEW_DISTANCE = 7.0f;
	public static final float COW_MATING_DISTANCE = 1.0f;
	// Use 2x the view distance to account for obstacles.
	public static final int COW_PATH_DISTANCE = 2 * (int) COW_VIEW_DISTANCE;

	/**
	 * TESTING ONLY!
	 * Packages the given testing data into the extended data object for a cow.
	 * 
	 * @param testing The testing data.
	 * @return The packaged extended object.
	 */
	public static Object encodeExtendedData(Test_ExtendedData testing)
	{
		return (null != testing)
				? new _ExtendedData(testing.inLoveMode
						, testing.offspringLocation
				)
				: null
		;
	}

	/**
	 * TESTING ONLY!
	 * Unpackages the testing data from the extended data object for a cow.
	 * 
	 * @param creature The creature to read.
	 * @return The testing data, potentially null.
	 */
	public static Test_ExtendedData decodeExtendedData(Object data)
	{
		_ExtendedData extended = (_ExtendedData) data;
		return (null != extended)
				? new Test_ExtendedData(extended.inLoveMode
						, extended.offspringLocation
				)
				: null
		;
	}


	private final Item _breedingItem;
	private final _ExtendedData _originalData;
	private boolean _inLoveMode;
	private EntityLocation _offspringLocation;

	/**
	 * Creates a mutable state machine for a cow based on the given extendedData opaque type (could be null).
	 * 
	 * @param extendedData The cow's extended data (previously created by this class).
	 */
	public CowStateMachine(Item breedingItem, Object extendedData)
	{
		_ExtendedData data = (_ExtendedData) extendedData;
		_breedingItem = breedingItem;
		_originalData = data;
		if (null != data)
		{
			_inLoveMode = data.inLoveMode;
			_offspringLocation = data.offspringLocation;
		}
		else
		{
			_inLoveMode = false;
			_offspringLocation = null;
		}
	}

	@Override
	public boolean applyItem(Item itemType)
	{
		boolean didApply = false;
		if (_breedingItem == itemType)
		{
			// We can't enter love mode if already pregnant (although that would only remain the case for a single tick).
			if (null == _offspringLocation)
			{
				// If this is already true, the item is lost as this is a saturating operation.
				_inLoveMode = true;
				didApply = true;
			}
		}
		return didApply;
	}

	@Override
	public ICreatureStateMachine.TargetEntity selectTarget(TickProcessingContext context, EntityCollection entityCollection, EntityLocation creatureLocation, int creatureId)
	{
		ICreatureStateMachine.TargetEntity target;
		if (_inLoveMode)
		{
			// Find another cow in breeding mode.
			target = _findBreedableCow(entityCollection, creatureLocation, creatureId);
		}
		else
		{
			// We will keep this simple:  Find the closest player holding wheat, up to our limit.
			target = _findWheatTarget(entityCollection, creatureLocation);
		}
		return target;
	}

	@Override
	public boolean setPregnant(EntityLocation offspringLocation)
	{
		boolean didBecomePregnant = false;
		if (_inLoveMode)
		{
			// Must not already be pregnant if in love mode.
			Assert.assertTrue(null == _offspringLocation);
			
			_inLoveMode = false;
			_offspringLocation = offspringLocation;
			didBecomePregnant = true;
		}
		return didBecomePregnant;
	}

	@Override
	public boolean doneSpecialActions(TickProcessingContext context, Consumer<CreatureEntity> creatureSpawner, Runnable requestDespawnWithoutDrops, EntityLocation creatureLocation, int creatureId, int targetEntityId)
	{
		// See if we are pregnant or searching for our mate.
		boolean didTakeAction = false;
		if (null != _offspringLocation)
		{
			Environment env = Environment.getShared();
			// We need to spawn an entity here (we use a placeholder since ID is re-assigned in the consumer).
			creatureSpawner.accept(CreatureEntity.create(context.idAssigner.next(), env.creatures.COW, _offspringLocation, env.creatures.COW.maxHealth()));
			_offspringLocation = null;
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
			if (_inLoveMode && (distance <= COW_MATING_DISTANCE) && (targetEntity.id() < creatureId))
			{
				// Send the message to impregnate them.
				EntityChangeImpregnateCreature sperm = new EntityChangeImpregnateCreature(creatureLocation);
				context.newChangeSink.creature(targetEntityId, sperm);
				// We can now exit love mode.
				_inLoveMode = false;
				didTakeAction = true;
			}
		}
		return didTakeAction;
	}

	@Override
	public int getPathDistance()
	{
		return COW_PATH_DISTANCE;
	}

	@Override
	public Object freezeToData()
	{
		_ExtendedData newData = (_inLoveMode || (null != _offspringLocation))
				? new _ExtendedData(_inLoveMode, _offspringLocation)
				: null
		;
		_ExtendedData matchingData = (null != _originalData)
				? (_originalData.equals(newData) ? _originalData : newData)
				: newData
		;
		return matchingData;
	}


	private ICreatureStateMachine.TargetEntity _findBreedableCow(EntityCollection entityCollection, EntityLocation creatureLocation, int thisCreatureId)
	{
		Environment env = Environment.getShared();
		ICreatureStateMachine.TargetEntity[] target = new ICreatureStateMachine.TargetEntity[1];
		float[] distanceToTarget = new float[] { Float.MAX_VALUE };
		entityCollection.walkCreaturesInRange(creatureLocation, COW_VIEW_DISTANCE, (CreatureEntity check) -> {
			// Ignore ourselves and make sure that they are the right type.
			if ((thisCreatureId != check.id()) && (env.creatures.COW == check.type()))
			{
				// See if they are also in love mode.
				_ExtendedData other = (_ExtendedData) check.extendedData();
				if ((null != other) && other.inLoveMode)
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
		entityCollection.walkPlayersInRange(creatureLocation, COW_VIEW_DISTANCE, (Entity player) -> {
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


	/**
	 * This is a testing variant of _ExtendedData which only exists to make unit tests simpler.
	 */
	public static record Test_ExtendedData(boolean inLoveMode
			, EntityLocation offspringLocation
	)
	{}

	private static record _ExtendedData(boolean inLoveMode
			, EntityLocation offspringLocation
	)
	{}
}
