package com.jeffdisher.october.types;


/**
 * Describes what type an entity is.  This is used to determine what kind of AI should be applied by the server, if any,
 * and whether or not there is a common volume and drop for a creature type versus a player type.
 */
public enum EntityType
{
	ERROR,

	PLAYER,
	COW,
	ORC,

	;
}
