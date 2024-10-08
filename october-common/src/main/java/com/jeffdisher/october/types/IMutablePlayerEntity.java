package com.jeffdisher.october.types;


/**
 * The mutable interface for a an entity representing a player.
 * Note that this is currently designed to be very much an abstraction over a mostly pure-data object with actual
 * functionality built on top of it.
 */
public interface IMutablePlayerEntity extends IMutableMinimalEntity
{
	IMutableInventory accessMutableInventory();

	CraftOperation getCurrentCraftingOperation();

	void setCurrentCraftingOperation(CraftOperation operation);

	int[] copyHotbar();

	/**
	 * @return The key in the currently selected hotbar slot.
	 */
	int getSelectedKey();

	void setSelectedKey(int key);

	void clearHotBarWithKey(int key);

	boolean changeHotbarIndex(int index);

	byte getFood();

	void setFood(byte food);

	int getEnergyDeficit();

	void setEnergyDeficit(int deficit);

	boolean isCreativeMode();

	void setCreativeMode(boolean enableCreative);

	long getLastSpecialActionMillis();

	void setLastSpecialActionMillis(long millis);

	void setSpawnLocation(EntityLocation spawnLocation);
}
