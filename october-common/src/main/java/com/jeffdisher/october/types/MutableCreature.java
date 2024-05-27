package com.jeffdisher.october.types;

import java.util.List;
import java.util.function.Consumer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.creatures.CreatureVolumes;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.IMutationEntity;
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
	public final CreatureEntity creature;

	// The location is immutable but can be directly replaced.
	public EntityLocation newLocation;
	public float newZVelocityPerSecond;
	public byte newHealth;
	public long newLastActionGameTick;
	public List<IMutationEntity<IMutableCreatureEntity>> newStepsToNextMove;
	public List<AbsoluteLocation> newMovementPlan;
	public Object newExtendedData;

	private MutableCreature(CreatureEntity creature)
	{
		this.creature = creature;
		this.newLocation = creature.location();
		this.newZVelocityPerSecond = creature.zVelocityPerSecond();
		this.newHealth = creature.health();
		this.newLastActionGameTick = creature.lastActionGameTick();
		this.newStepsToNextMove = creature.stepsToNextMove();
		this.newMovementPlan = creature.movementPlan();
		this.newExtendedData = creature.extendedData();
	}

	@Override
	public int getId()
	{
		return this.creature.id();
	}

	@Override
	public EntityLocation getLocation()
	{
		return this.newLocation;
	}

	@Override
	public EntityVolume getVolume()
	{
		// TODO:  Change how we get this volume so we keep the package layering logical.
		return CreatureVolumes.getVolume(this.creature);
	}

	@Override
	public float getZVelocityPerSecond()
	{
		return this.newZVelocityPerSecond;
	}

	@Override
	public void setLocationAndVelocity(EntityLocation location, float zVelocityPerSecond)
	{
		this.newLocation = location;
		this.newZVelocityPerSecond = zVelocityPerSecond;
	}

	@Override
	public void resetLongRunningOperations()
	{
		// Nothing for creatures.
	}

	@Override
	public void handleEntityDeath(Consumer<IMutationBlock> mutationConsumer)
	{
		// For now, just drop wheat item for all creatures.
		// TODO:  Define this drop loot table in data.
		Environment env = Environment.getShared();
		// TODO:  Change how we get this volume so we keep the package layering logical.
		EntityLocation entityCentre = SpatialHelpers.getEntityCentre(this.newLocation, CreatureVolumes.getVolume(this.creature));
		Items toDrop = new Items(env.items.getItemById("op.wheat_item"), 1);
		mutationConsumer.accept(new MutationBlockStoreItems(entityCentre.getBlockLocation(), toDrop, null, Inventory.INVENTORY_ASPECT_INVENTORY));
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
		this.newStepsToNextMove = null;
		this.newMovementPlan = null;
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
	public void applyEnergyCost(TickProcessingContext context, int cost)
	{
		// Creatures don't currently have energy so do nothing.
	}

	@Override
	public EntityType getType()
	{
		return this.creature.type();
	}

	@Override
	public Object getExtendedData()
	{
		return this.newExtendedData;
	}

	@Override
	public void setExtendedData(Object data)
	{
		this.newExtendedData = data;
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
			CreatureEntity immutable = new CreatureEntity(this.creature.id()
					, this.creature.type()
					, this.newLocation
					, this.newZVelocityPerSecond
					, this.newHealth
					, this.newLastActionGameTick
					, this.newStepsToNextMove
					, this.newMovementPlan
					, this.newExtendedData
			);
			// See if these are identical.
			newInstance = this.creature.equals(immutable)
					? this.creature
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
