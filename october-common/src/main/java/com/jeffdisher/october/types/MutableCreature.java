package com.jeffdisher.october.types;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.mutations.MutationBlockStoreItems;
import com.jeffdisher.october.utils.Assert;


/**
 * A short-lived mutable version of an creature to allow for parallel tick processing.
 */
public class MutableCreature implements IMutableCreatureEntity
{
	/**
	 * Create a mutable entity from the elements of an existing creature.
	 * 
	 * @param creature An existing creature.
	 * @return A mutable creature.
	 */
	public static MutableCreature existing(CreatureEntity creature)
	{
		return new MutableCreature(creature);
	}


	// Some data elements are actually immutable (id, for example) so they are just left in the original, along with the original data.
	private final CreatureEntity _creature;

	// The location is immutable but can be directly replaced.
	public EntityLocation newLocation;
	public EntityLocation newVelocity;
	public byte newYaw;
	public byte newPitch;
	public byte newHealth;
	public byte newBreath;
	public Object newExtendedData;

	public List<AbsoluteLocation> newMovementPlan;
	public long newLastActionTick;
	public boolean newShouldTakeAction;
	public long newDespawnKeepAliveTick;
	public int newTargetEntityId;
	public AbsoluteLocation newTargetPreviousLocation;
	public long newLastAttackTick;
	public boolean newInLoveMode;
	public EntityLocation newOffspringLocation;
	public long newLastDamageTakenMillis;

	private MutableCreature(CreatureEntity creature)
	{
		_creature = creature;
		this.newLocation = creature.location();
		this.newVelocity = creature.velocity();
		this.newYaw = creature.yaw();
		this.newPitch = creature.pitch();
		this.newHealth = creature.health();
		this.newBreath = creature.breath();
		this.newExtendedData = creature.extendedData();
		
		this.newMovementPlan = creature.ephemeral().movementPlan();
		this.newLastActionTick = creature.ephemeral().lastActionTick();
		this.newShouldTakeAction = creature.ephemeral().shouldTakeImmediateAction();
		this.newDespawnKeepAliveTick = creature.ephemeral().despawnKeepAliveTick();
		this.newTargetEntityId = creature.ephemeral().targetEntityId();
		this.newTargetPreviousLocation = creature.ephemeral().targetPreviousLocation();
		this.newLastAttackTick = creature.ephemeral().lastAttackTick();
		this.newInLoveMode = creature.ephemeral().inLoveMode();
		this.newOffspringLocation = creature.ephemeral().offspringLocation();
		this.newLastDamageTakenMillis = creature.ephemeral().lastDamageTakenMillis();
	}

	@Override
	public int getId()
	{
		return _creature.id();
	}

	@Override
	public EntityType getType()
	{
		return _creature.type();
	}

	@Override
	public EntityLocation getLocation()
	{
		return this.newLocation;
	}

	@Override
	public void setLocation(EntityLocation location)
	{
		this.newLocation = location;
	}

	@Override
	public EntityLocation getVelocityVector()
	{
		return this.newVelocity;
	}

	@Override
	public void setVelocityVector(EntityLocation vector)
	{
		this.newVelocity = vector;
	}

	@Override
	public void resetLongRunningOperations()
	{
		// Nothing for creatures.
	}

	@Override
	public void handleEntityDeath(TickProcessingContext context)
	{
		EntityLocation entityCentre = SpatialHelpers.getCentreFeetLocation(this);
		for (Items toDrop : _creature.type().drops())
		{
			context.mutationSink.next(new MutationBlockStoreItems(entityCentre.getBlockLocation(), toDrop, null, Inventory.INVENTORY_ASPECT_INVENTORY));
		}
		this.newHealth = 0;
	}

	@Override
	public byte getHealth()
	{
		return this.newHealth;
	}

	@Override
	public void setHealth(byte health)
	{
		this.newHealth = health;
		
		// Whenever a creature's health changes, we will wipe its AI state.
		// TODO:  In the future, we should make this about taking damage from a specific source.
		this.newMovementPlan = null;
		this.newShouldTakeAction = true;
	}

	@Override
	public byte getBreath()
	{
		return this.newBreath;
	}

	@Override
	public void setBreath(byte breath)
	{
		this.newBreath = breath;
	}

	@Override
	public void setOrientation(byte yaw, byte pitch)
	{
		this.newYaw = yaw;
		this.newPitch = pitch;
	}

	@Override
	public byte getYaw()
	{
		return this.newYaw;
	}

	@Override
	public byte getPitch()
	{
		return this.newPitch;
	}

	@Override
	public NonStackableItem getArmour(BodyPart part)
	{
		// Currently, no armour for creatures.
		return null;
	}

	@Override
	public void setArmour(BodyPart part, NonStackableItem item)
	{
		// Currently, no armour for creatures.
		throw Assert.unreachable();
	}

	@Override
	public void applyEnergyCost(int cost)
	{
		// Creatures don't currently have energy so do nothing.
	}

	@Override
	public boolean updateDamageTimeoutIfValid(long currentTickMillis)
	{
		long nextValidTime = this.newLastDamageTakenMillis + MiscConstants.DAMAGE_TAKEN_TIMEOUT_MILLIS;
		boolean canUpdate = (currentTickMillis >= nextValidTime);
		if (canUpdate)
		{
			this.newLastDamageTakenMillis = currentTickMillis;
		}
		return canUpdate;
	}

	@Override
	public List<AbsoluteLocation> getMovementPlan()
	{
		// The caller shouldn't change this.
		return (null != this.newMovementPlan)
				? Collections.unmodifiableList(this.newMovementPlan)
				: null
		;
	}

	@Override
	public void setMovementPlan(List<AbsoluteLocation> movementPlan)
	{
		// This can be null but never empty.
		Assert.assertTrue((null == movementPlan) || !movementPlan.isEmpty());
		this.newMovementPlan = (null != movementPlan)
				? new ArrayList<>(movementPlan)
				: null
		;
		if (null == movementPlan)
		{
			// If we are clearing the plan, clear the target.
			this.newTargetEntityId = CreatureEntity.NO_TARGET_ENTITY_ID;
			this.newTargetPreviousLocation = null;
		}
	}

	@Override
	public void setReadyForAction()
	{
		this.newShouldTakeAction = true;
	}

	@Override
	public void setOffspringLocation(EntityLocation spawnLocation)
	{
		this.newOffspringLocation = spawnLocation;
	}

	@Override
	public EntityLocation getOffspringLocation()
	{
		return this.newOffspringLocation;
	}

	@Override
	public void setLoveMode(boolean isInLoveMode)
	{
		this.newInLoveMode = isInLoveMode;
	}

	@Override
	public boolean isInLoveMode()
	{
		return this.newInLoveMode;
	}

	/**
	 * Creates an immutable snapshot of the receiver.
	 * Note that this will return the original instance if a new instance would have been identical.
	 * 
	 * @return A read-only copy of the current state of the mutable creature.
	 */
	public CreatureEntity freeze()
	{
		// We will return null if the creature died.
		CreatureEntity newInstance;
		if (this.newHealth > 0)
		{
			Assert.assertTrue((null == this.newMovementPlan) || !this.newMovementPlan.isEmpty());
			CreatureEntity.Ephemeral ephemeral = new CreatureEntity.Ephemeral(
					(null != this.newMovementPlan) ? Collections.unmodifiableList(this.newMovementPlan) : null
							, this.newLastActionTick
							, this.newShouldTakeAction
							, this.newDespawnKeepAliveTick
							, this.newTargetEntityId
							, this.newTargetPreviousLocation
							, this.newLastAttackTick
							, this.newInLoveMode
							, this.newOffspringLocation
							, this.newLastDamageTakenMillis
			);
			CreatureEntity immutable = new CreatureEntity(_creature.id()
					, _creature.type()
					, this.newLocation
					, this.newVelocity
					, this.newYaw
					, this.newPitch
					, this.newHealth
					, this.newBreath
					, this.newExtendedData
					
					, ephemeral.equals(_creature.ephemeral()) ? _creature.ephemeral() : ephemeral
			);
			// See if these are identical.
			newInstance = _creature.equals(immutable)
					? _creature
					: immutable
			;
		}
		else
		{
			newInstance = null;
		}
		return newInstance;
	}
}
