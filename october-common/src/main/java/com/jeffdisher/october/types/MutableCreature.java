package com.jeffdisher.october.types;

import java.util.List;
import java.util.function.Consumer;

import com.jeffdisher.october.aspects.Environment;
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
	public EntityLocation newVelocity;
	public byte newHealth;
	public int newBreath;
	public long newLastActionGameTick;
	public List<IMutationEntity<IMutableCreatureEntity>> newStepsToNextMove;
	public Object newExtendedData;

	private MutableCreature(CreatureEntity creature)
	{
		this.creature = creature;
		this.newLocation = creature.location();
		this.newVelocity = creature.velocity();
		this.newHealth = creature.health();
		this.newBreath = creature.breath();
		this.newLastActionGameTick = creature.lastActionGameTick();
		this.newStepsToNextMove = creature.stepsToNextMove();
		this.newExtendedData = creature.extendedData();
	}

	@Override
	public int getId()
	{
		return this.creature.id();
	}

	@Override
	public EntityType getType()
	{
		return this.creature.type();
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
	public void handleEntityDeath(Consumer<IMutationBlock> mutationConsumer)
	{
		// For now, just drop wheat item for all creatures.
		// TODO:  Define this drop loot table in data.
		Environment env = Environment.getShared();
		EntityLocation entityCentre = SpatialHelpers.getEntityCentre(this.newLocation, EntityConstants.getVolume(this.creature.type()));
		Items toDrop;
		switch (this.creature.type())
		{
		case COW:
			toDrop = new Items(env.items.getItemById("op.beef"), 1);
			break;
		case ORC:
			toDrop = new Items(env.items.getItemById("op.iron_ingot"), 1);
			break;
		case ERROR:
		case PLAYER:
		default:
			throw Assert.unreachable();
		}
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
		// TODO:  In the future, we should make this about taking damage from a specific source.
		this.newStepsToNextMove = null;
		this.newExtendedData = null;
	}

	@Override
	public int getBreath()
	{
		return this.newBreath;
	}

	@Override
	public void setBreath(int breath)
	{
		this.newBreath = breath;
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
			Assert.assertTrue(0.0f == this.newVelocity.x());
			CreatureEntity immutable = new CreatureEntity(this.creature.id()
					, this.creature.type()
					, this.newLocation
					, this.newVelocity
					, this.newHealth
					, this.newBreath
					, this.newLastActionGameTick
					, this.newStepsToNextMove
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
