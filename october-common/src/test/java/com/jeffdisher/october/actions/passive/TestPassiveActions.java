package com.jeffdisher.october.actions.passive;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.ContextBuilder;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.PassiveEntity;
import com.jeffdisher.october.types.PassiveType;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestPassiveActions
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
	public void passiveNoMove()
	{
		// Show that a passive item slot instance doesn't change when not moving.
		int id = 1;
		EntityLocation location = new EntityLocation(10.0f, 10.0f, 10.0f);
		EntityLocation velocity = new EntityLocation(0.0f, 0.0f, 0.0f);
		ItemSlot slot = ItemSlot.fromStack(new Items(STONE_ITEM, 5));
		long createMillis = 1000L;
		PassiveEntity start = new PassiveEntity(id, PassiveType.ITEM_SLOT, location, velocity, slot, createMillis);
		
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(10, 10, 9), STONE_ITEM.number());
		TickProcessingContext context = _createSingleCuboidContext(cuboid, 1L);
		
		PassiveActionEveryTick action = new PassiveActionEveryTick();
		PassiveEntity result = action.applyChange(context, start);
		
		Assert.assertTrue(start == result);
	}

	@Test
	public void passiveFall()
	{
		// Show an item slot passive which falls.
		int id = 1;
		EntityLocation location = new EntityLocation(10.0f, 10.0f, 10.0f);
		EntityLocation velocity = new EntityLocation(0.0f, 0.0f, 0.0f);
		ItemSlot slot = ItemSlot.fromStack(new Items(STONE_ITEM, 5));
		long createMillis = 1000L;
		PassiveEntity start = new PassiveEntity(id, PassiveType.ITEM_SLOT, location, velocity, slot, createMillis);
		
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		TickProcessingContext context = _createSingleCuboidContext(cuboid, 1L);
		
		PassiveActionEveryTick action = new PassiveActionEveryTick();
		PassiveEntity result = action.applyChange(context, start);
		
		Assert.assertEquals(id, result.id());
		Assert.assertEquals(PassiveType.ITEM_SLOT, result.type());
		Assert.assertEquals(new EntityLocation(10.0f, 10.0f, 9.9f), result.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -0.98f), result.velocity());
		Assert.assertEquals(slot, result.extendedData());
		Assert.assertEquals(createMillis, result.lastAliveMillis());
	}

	@Test
	public void passiveDespawn()
	{
		// Show that a passive item slot instance despawns after a time.
		int id = 1;
		EntityLocation location = new EntityLocation(10.0f, 10.0f, 10.0f);
		EntityLocation velocity = new EntityLocation(0.0f, 0.0f, 0.0f);
		ItemSlot slot = ItemSlot.fromStack(new Items(STONE_ITEM, 5));
		long createMillis = 1000L;
		PassiveEntity start = new PassiveEntity(id, PassiveType.ITEM_SLOT, location, velocity, slot, createMillis);
		
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(10, 10, 9), STONE_ITEM.number());
		long testTick = (createMillis + PassiveType.ITEM_SLOT_DESPAWN_MILLIS) / ContextBuilder.DEFAULT_MILLIS_PER_TICK;
		TickProcessingContext context = _createSingleCuboidContext(cuboid, testTick);
		
		PassiveActionEveryTick action = new PassiveActionEveryTick();
		PassiveEntity result = action.applyChange(context, start);
		
		Assert.assertNull(result);
	}


	private static TickProcessingContext _createSingleCuboidContext(CuboidData cuboid, long tickNumber)
	{
		TickProcessingContext context = ContextBuilder.build()
			.lookups((AbsoluteLocation location) -> {
				return (location.getCuboidAddress().equals(cuboid.getCuboidAddress()))
						? new BlockProxy(location.getBlockAddress(), cuboid)
						: null
				;
			}, null)
			.tick(tickNumber)
			.finish()
		;
		return context;
	}
}
