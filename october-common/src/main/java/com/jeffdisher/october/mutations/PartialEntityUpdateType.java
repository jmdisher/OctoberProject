package com.jeffdisher.october.mutations;


/**
 * Type constants used for serializing and deserializing IPartialEntityUpdate instances.
 */
public enum PartialEntityUpdateType
{
	ERROR,
	
	WHOLE_PARTIAL_ENTITY,
	
	END_OF_LIST,
}
