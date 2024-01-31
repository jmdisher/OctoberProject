package com.jeffdisher.october.mutations;


public enum MutationEntityType
{
	ERROR,
	
	MOVE,
	JUMP,
	BLOCK_BREAK_END,
	BLOCK_PLACE,
	CRAFT,
	SELECT_ITEM,
	ITEMS_REQUEST_PUSH,
	ITEMS_REQUEST_PULL,
	ITEMS_STORE_TO_INVENTORY,
	
	END_OF_LIST,
}
