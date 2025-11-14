package com.jeffdisher.october.types;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;


public class TestMutableEntity
{
	private static Environment ENV;
	private static Item STONE_ITEM;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		STONE_ITEM = ENV.items.getItemById("op.stone");
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
		mutable.newInventory.addAllItems(STONE_ITEM, 1);
		mutable.newInventory.removeStackableItems(STONE_ITEM, 1);
		Entity output = mutable.freeze();
		
		Assert.assertTrue(input == output);
	}

	// Technically, this is a MutablePartialEntity test, but it is the only one worth having so we leave it here with related tests.
	@Test
	public void partialEntity_revertedChange()
	{
		EntityLocation location0 = new EntityLocation(0.0f, 0.0f, 0.0f);
		byte yaw = 64;
		byte pitch = 0;
		byte health = 50;
		Object extendedData = ENV.creatures.PLAYER.extendedCodec().buildDefault(0L);
		PartialEntity input = new PartialEntity(1, ENV.creatures.PLAYER, location0, yaw, pitch, health, extendedData);
		MutablePartialEntity mutable = MutablePartialEntity.existing(input);
		mutable.newLocation = new EntityLocation(1.0f, 0.0f, 0.0f);
		mutable.newLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		PartialEntity output = mutable.freeze();
		
		Assert.assertTrue(input == output);
	}

	@Test
	public void ephermalSpecialAction()
	{
		// Shows that the ephemeral special action time will cause a new instance to be created, even though it isn't persisted or sent over the network.
		Entity input = _buildTestEntity();
		MutableEntity mutable = MutableEntity.existing(input);
		Assert.assertEquals(0L, mutable.getLastSpecialActionMillis());
		mutable.setLastSpecialActionMillis(1L);
		Assert.assertEquals(1L, mutable.getLastSpecialActionMillis());
		Entity output = mutable.freeze();
		
		Assert.assertTrue(input != output);
	}


	private static Entity _buildTestEntity()
	{
		MutableEntity mutable = MutableEntity.createForTest(1);
		return mutable.freeze();
	}
}
