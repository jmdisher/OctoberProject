package com.jeffdisher.october.types;


/**
 * A short-lived mutable version of an entity to allow for parallel tick processing.
 */
public class MutableEntity
{
	// Some data elements are actually immutable (id, for example) so they are just left in the original, along with the original data.
	public final Entity original;

	// These are the realistically mutable elements.  Since these types are immutable, they can only be replaced (these may be exploded if there are often changes here).
	public EntityLocation newLocation;
	public Inventory newInventory;

	public MutableEntity(Entity original)
	{
		this.original = original;
		this.newLocation = original.location();
		this.newInventory = original.inventory();
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
				, this.newInventory
		);
	}
}
