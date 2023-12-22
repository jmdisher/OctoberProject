package com.jeffdisher.october.types;


/**
 * A short-lived mutable version of an entity to allow for parallel tick processing.
 */
public class MutableEntity
{
	// Some data elements are actually immutable (id, for example) so they are just left in the original, along with the original data.
	public final Entity original;
	public final MutableInventory newInventory;

	// The location is immutable but can be directly replaced.
	public EntityLocation newLocation;

	public MutableEntity(Entity original)
	{
		this.original = original;
		this.newInventory = new MutableInventory(original.inventory());
		this.newLocation = original.location();
	}

	/**
	 * Creates an immutable snapshot of the receiver.
	 * 
	 * @return A read-only copy of the current state of the mutable entity.
	 */
	public Entity freeze()
	{
		return new Entity(this.original.id()
				, this.newLocation
				, this.original.volume()
				, this.original.blocksPerTickSpeed()
				, this.newInventory.freeze()
		);
	}
}
