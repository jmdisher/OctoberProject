package com.jeffdisher.october.types;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;


public class TestMutableEntity
{
	private static Environment ENV;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void noChange() throws Throwable
	{
		Entity input = _buildTestEntity();
		MutableEntity mutable = MutableEntity.existing(input);
		Entity output = mutable.freeze();
		
		Assert.assertTrue(input == output);
	}

	@Test
	public void simpleChange()
	{
		Entity input = _buildTestEntity();
		MutableEntity mutable = MutableEntity.existing(input);
		mutable.newLocation = new EntityLocation(1.0f, 0.0f, 0.0f);
		Entity output = mutable.freeze();
		
		Assert.assertTrue(input != output);
		Assert.assertEquals(1.0f, output.location().x(), 0.01f);
	}

	@Test
	public void revertedChange()
	{
		Entity input = _buildTestEntity();
		MutableEntity mutable = MutableEntity.existing(input);
		mutable.newLocation = new EntityLocation(1.0f, 0.0f, 0.0f);
		mutable.newLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		Entity output = mutable.freeze();
		
		Assert.assertTrue(input == output);
	}

	@Test
	public void revertedInventoryChange()
	{
		Entity input = _buildTestEntity();
		MutableEntity mutable = MutableEntity.existing(input);
		mutable.newInventory.addAllItems(ENV.items.STONE, 1);
		mutable.newInventory.removeStackableItems(ENV.items.STONE, 1);
		Entity output = mutable.freeze();
		
		Assert.assertTrue(input == output);
	}

	// Technically, this is a MutablePartialEntity test, but it is the only one worth having so we leave it here with related tests.
	@Test
	public void partialEntity_revertedChange()
	{
		EntityLocation location0 = new EntityLocation(0.0f, 0.0f, 0.0f);
		PartialEntity input = new PartialEntity(1, EntityType.PLAYER, location0, 0.0f, new EntityVolume(1.0f, 2.0f));
		MutablePartialEntity mutable = MutablePartialEntity.existing(input);
		mutable.newLocation = new EntityLocation(1.0f, 0.0f, 0.0f);
		mutable.newLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		PartialEntity output = mutable.freeze();
		
		Assert.assertTrue(input == output);
	}


	private static Entity _buildTestEntity()
	{
		MutableEntity mutable = MutableEntity.create(1);
		return mutable.freeze();
	}
}
