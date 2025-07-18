package com.jeffdisher.october.aspects;


/**
 * This is a list of material types for purposes of determining what tools can apply their speed bonuses.  As such, the
 * material types are named after their associated tool types.
 */
public enum BlockMaterial
{
	/**
	 * A material for which there is no special tool.  This is used for blocks which have no special tools.
	 */
	NO_TOOL,
	/**
	 * A material which does not exist.  This is used for tools which should never apply their bonus.
	 */
	NO_MATERIAL,
	/**
	 * Things like dirt which must be shovelled.
	 */
	SHOVEL,
	/**
	 * Things like stone which must be broken.
	 */
	PICKAXE,
	/**
	 * Things like wood which must be split.
	 */
	AXE,
}
