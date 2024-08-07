package com.jeffdisher.october.mutations;


public enum MutationEntityType
{
	ERROR,
	
	MOVE,
	JUMP,
	SWIM,
	BLOCK_PLACE,
	CRAFT,
	SELECT_ITEM,
	ITEMS_REQUEST_PUSH,
	ITEMS_REQUEST_PULL,
	ITEMS_STORE_TO_INVENTORY,
	INCREMENTAL_BREAK_BLOCK,
	CRAFT_IN_BLOCK,
	ATTACK_ENTITY,
	TAKE_DAMAGE,
	PERIODIC,
	USE_SELECTED_ITEM_ON_SELF,
	USE_SELECTED_ITEM_ON_BLOCK,
	USE_SELECTED_ITEM_ON_ENTITY,
	CHANGE_HOTBAR_SLOT,
	SWAP_ARMOUR,
	SET_BLOCK_LOGIC_STATE,
	
	END_OF_LIST,
}
