package com.jeffdisher.october.types;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.registries.ItemRegistry;


public class TestMutableEntity
{
	@Test
	public void noChange() throws Throwable
	{
		Entity input = _buildTestEntity();
		MutableEntity mutable = new MutableEntity(input);
		Entity output = mutable.freeze();
		
		Assert.assertTrue(input == output);
	}

	@Test
	public void simpleChange()
	{
		Entity input = _buildTestEntity();
		MutableEntity mutable = new MutableEntity(input);
		mutable.newLocation = new EntityLocation(1.0f, 0.0f, 0.0f);
		Entity output = mutable.freeze();
		
		Assert.assertTrue(input != output);
		Assert.assertEquals(1.0f, output.location().x(), 0.01f);
	}

	@Test
	public void revertedChange()
	{
		Entity input = _buildTestEntity();
		MutableEntity mutable = new MutableEntity(input);
		mutable.newLocation = new EntityLocation(1.0f, 0.0f, 0.0f);
		mutable.newLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		Entity output = mutable.freeze();
		
		Assert.assertTrue(input == output);
	}

	@Test
	public void revertedInventoryChange()
	{
		Entity input = _buildTestEntity();
		MutableEntity mutable = new MutableEntity(input);
		mutable.newInventory.addAllItems(ItemRegistry.STONE, 1);
		mutable.newInventory.removeItems(ItemRegistry.STONE, 1);
		Entity output = mutable.freeze();
		
		Assert.assertTrue(input == output);
	}


	private static Entity _buildTestEntity()
	{
		return new Entity(1, new EntityLocation(0.0f, 0.0f, 0.0f), 0.0f, new EntityVolume(1.8f, 0.5f), 0.1f, Inventory.start(10).finish(), null, null);
	}
}
