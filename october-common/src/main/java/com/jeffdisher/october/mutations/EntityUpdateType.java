package com.jeffdisher.october.mutations;


/**
 * Type constants used for serializing and deserializing IEntityUpdate instances.
 */
public enum EntityUpdateType
{
	ERROR,
	
	WHOLE_ENTITY,
	PARTIAL_ENTITY,
	
	END_OF_LIST,
}
