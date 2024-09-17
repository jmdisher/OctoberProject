package com.jeffdisher.october.types;


/**
 * A helper to fetch a byte value based on some lookup criteria.
 */
public interface IByteLookup<K>
{
	public static final byte NOT_FOUND = -1;
	/**
	 * Looks up the byte value corresponding to the given key, returning NOT_FOUND if it can't be found.
	 * 
	 * @param key The key to resolve.
	 * @return The byte value at that location or NOT_FOUND if not found.
	 */
	byte lookup(K key);
}
