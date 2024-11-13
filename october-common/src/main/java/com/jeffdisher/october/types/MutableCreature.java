package com.jeffdisher.october.types;

import com.jeffdisher.october.aspects.Environment;
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
		// TODO:  Define this drop loot table in data once we have a better sense of what mob information should be represented declaratively.
		Environment env = Environment.getShared();
		EntityLocation entityCentre = SpatialHelpers.getCentreFeetLocation(this);
		Items toDrop;
		switch (_creature.type())
		{
		case COW:
			// We will try to drop 5 beef, although they may not all fit if the storage overflows.
			toDrop = new Items(env.items.getItemById("op.beef"), 5);
			break;
		case ORC:
			// We will drop iron dust from the orc, creating an incentive to attack them (although this might be over-powered).
			toDrop = new Items(env.items.getItemById("op.iron_dust"), 1);
			break;
		case ERROR:
		case PLAYER:
		default:
			throw Assert.unreachable();
		}
		context.mutationSink.next(new MutationBlockStoreItems(entityCentre.getBlockLocation(), toDrop, null, Inventory.INVENTORY_ASPECT_INVENTORY));
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
		this.newExtendedData = null;
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
			CreatureEntity immutable = new CreatureEntity(_creature.id()
					, _creature.type()
					, this.newLocation
					, this.newVelocity
					, this.newYaw
					, this.newPitch
					, this.newHealth
					, this.newBreath
					, this.newExtendedData
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
