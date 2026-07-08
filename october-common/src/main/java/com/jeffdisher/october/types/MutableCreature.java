package com.jeffdisher.october.types;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.logic.MiscHelpers;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.utils.Assert;


/**
 * A short-lived mutable version of an creature to allow for parallel tick processing.
 */
public class MutableCreature implements IMutableMinimalEntity
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
	public EntityType newType;
	public EntityLocation newLocation;
	public EntityLocation newVelocity;
	public byte newYaw;
	public byte newPitch;
	public byte newHealth;
	public byte newBreath;
	public Object newExtendedData;

	// Data related to the Ephemeral sub-structure.
	public CreatureEntity.MovementPlan movementPlan;
	public long nextMovementPlanMillis;
	public long despawnMillis;
	public long nextActionMillis;
	public long nextTakeDamageMillis;

	// This data is only kept within this instance and discarded when it is (only useful within this one tick).
	public boolean shouldTakeActionInTick;

	private MutableCreature(CreatureEntity creature)
	{
		_creature = creature;
		this.newType = creature.type();
		this.newLocation = creature.location();
		this.newVelocity = creature.velocity();
		this.newYaw = creature.yaw();
		this.newPitch = creature.pitch();
		this.newHealth = creature.health();
		this.newBreath = creature.breath();
		this.newExtendedData = creature.extendedData();
		
		this.movementPlan = creature.ephemeral().movementPlan();
		this.nextMovementPlanMillis = creature.ephemeral().nextMovementPlanMillis();
		this.despawnMillis = creature.ephemeral().despawnMillis();
		this.nextActionMillis = creature.ephemeral().nextActionMillis();
		this.nextTakeDamageMillis = creature.ephemeral().nextTakeDamageMillis();
	}

	@Override
	public int getId()
	{
		return _creature.id();
	}

	@Override
	public EntityType getType()
	{
		return this.newType;
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
		Environment env = Environment.getShared();
		DropChance[] chances = this.newType.drops();
		ItemSlot[] dropped = MiscHelpers.convertToDrops(env, context.randomInt.applyAsInt(MiscHelpers.RANDOM_DROP_LIMIT), chances);
		for (ItemSlot toDrop : dropped)
		{
			// Drop the drops as passives.
			context.passiveSpawner.spawnPassive(PassiveType.ITEM_SLOT, entityCentre, new EntityLocation(0.0f, 0.0f, 0.0f), toDrop);
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
		this.movementPlan = null;
		this.shouldTakeActionInTick = true;
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
		boolean canUpdate = (this.nextTakeDamageMillis <= currentTickMillis);
		if (canUpdate)
		{
			this.nextTakeDamageMillis = currentTickMillis + MiscConstants.DAMAGE_TAKEN_TIMEOUT_MILLIS;
		}
		return canUpdate;
	}

	/**
	 * Changes the receiver's type, resetting its health and extended data to defaults for this type.  This is typically
	 * used for cases such as livestock growing from a baby to adult but there is no internal check on usage.
	 * 
	 * @param newType The new type to assign to the receiver.
	 * @param gameTimeMillis The most recent game time, in case the instance needs to track relative timeouts, etc.
	 */
	public void changeEntityType(EntityType newType, long gameTimeMillis)
	{
		// We set the type but also set the health and extended data to the defaults for this type.
		this.newType = newType;
		this.newHealth = newType.maxHealth();
		this.newExtendedData = newType.extension().buildDefaultExtendedData(gameTimeMillis);
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
			CreatureEntity.Ephemeral ephemeral = new CreatureEntity.Ephemeral(
				this.movementPlan
				, this.nextMovementPlanMillis
				, this.despawnMillis
				, this.nextActionMillis
				, this.nextTakeDamageMillis
			);
			CreatureEntity immutable = new CreatureEntity(_creature.id()
					, this.newType
					, this.newLocation
					, this.newVelocity
					, this.newYaw
					, this.newPitch
					, this.newHealth
					, this.newBreath
					, ((null == this.newExtendedData) || !this.newExtendedData.equals(_creature.extendedData()))
						? this.newExtendedData
						: _creature.extendedData()
					
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
