package com.jeffdisher.october.types;


/**
 * The mutable interface for a an entity representing a player.
 * Note that this is currently designed to be very much an abstraction over a mostly pure-data object with actual
 * functionality built on top of it.
 */
public interface IMutablePlayerEntity
{
	int getId();

	MutableInventory accessMutableInventory();

	CraftOperation getCurrentCraftingOperation();

	void setCurrentCraftingOperation(CraftOperation operation);

	/**
	 * @return The key in the currently selected hotbar slot.
	 */
	int getSelectedKey();

	void setSelectedKey(int key);

	EntityLocation getLocation();

	EntityVolume getVolume();

	float getZVelocityPerSecond();

	void setLocationAndVelocity(EntityLocation location, float zVelocityPerSecond);

	void clearHotBarWithKey(int key);

	void clearInventoryAndRespawn();

	boolean changeHotbarIndex(int index);

	byte getHealth();

	void setHealth(byte health);

	byte getFood();

	void setFood(byte food);

	NonStackableItem getArmour(BodyPart part);

	void setArmour(BodyPart part, NonStackableItem item);
}
