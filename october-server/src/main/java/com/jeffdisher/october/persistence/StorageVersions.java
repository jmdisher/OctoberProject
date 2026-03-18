package com.jeffdisher.october.persistence;


/**
 * This class just contains the definitions and documentation around the different storage versions for on-disk files.
 * Note that we currently use the same version number for entities and cuboids, as this keeps things simpler (it just
 * means that there are some redundant version changes, which are trivial to handle in code and also helps to document
 * what the versions mean).
 */
public class StorageVersions
{
	/**
	 * Version 0 was used in pre-releases and is no longer supported (pre-releases have no migration support).
	 */

	/**
	 * Version 1 was used in v1.0.1 and earlier, and is supported.
	 */
	public static final int V1 = 1;

	/**
	 * Version 2 was used in v1.1 and earlier, and is supported.
	 */
	public static final int V2 = 2;

	/**
	 * Version 3 was used in v1.2.1 and earlier, and is supported.
	 */
	public static final int V3 = 3;

	/**
	 * Version 4 was used in v1.3 and earlier, and is supported.
	 */
	public static final int V4 = 4;

	/**
	 * Version 5 was used in v1.5 and earlier, and is supported.
	 */
	public static final int V5 = 5;

	/**
	 * Version 6 was used in v1.6 and earlier, and is supported.
	 */
	public static final int V6 = 6;

	/**
	 * Version 7 was used in v1.7 and earlier, and is supported.
	 */
	public static final int V7 = 7;

	/**
	 * Version 8 was used in v1.8 and earlier, and is supported.
	 */
	public static final int V8 = 8;

	/**
	 * Version 9 was used in v1.9 and earlier, and is supported.
	 */
	public static final int V9 = 9;

	/**
	 * Version 10 was used in v1.10 and earlier, and is supported.
	 */
	public static final int V10 = 10;

	/**
	 * The storage version used in the current development version (and usually the most recent release).
	 */
	public static final int CURRENT = 11;

}
