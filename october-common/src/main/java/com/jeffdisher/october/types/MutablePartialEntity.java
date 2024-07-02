package com.jeffdisher.october.types;


/**
 * A short-lived mutable version of a partial entity for client-side update applications.
 */
public class MutablePartialEntity
{
	/**
	 * Create a mutable partial entity from the elements of an existing partial entity.
	 * 
	 * @param entity An existing entity.
	 * @return A mutable entity.
	 */
	public static MutablePartialEntity existing(PartialEntity entity)
	{
		return new MutablePartialEntity(entity);
	}


	// Some data elements are actually immutable (id, for example) so they are just left in the original, along with the original data.
	public final PartialEntity original;

	// The location is immutable but can be directly replaced.
	public EntityLocation newLocation;

	private MutablePartialEntity(PartialEntity original)
	{
		this.original = original;
		this.newLocation = original.location();
	}

	/**
	 * Creates an immutable snapshot of the receiver.
	 * Note that this will return the original instance if a new instance would have been identical.
	 * 
	 * @return A read-only copy of the current state of the mutable partial entity, potentially the original instance.
	 */
	public PartialEntity freeze()
	{
		PartialEntity newCopy = new PartialEntity(this.original.id()
				, this.original.type()
				, this.newLocation
		);
		return this.original.equals(newCopy)
				? this.original
				: newCopy
		;
	}
}
