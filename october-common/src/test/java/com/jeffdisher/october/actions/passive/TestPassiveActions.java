package com.jeffdisher.october.actions.passive;

import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.actions.EntityActionStoreToInventory;
import com.jeffdisher.october.actions.EntityActionTakeDamageFromEntity;
import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.ContextBuilder;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.IPassiveAction;
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
	private static Block LOG_BLOCK;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		STONE_ITEM = ENV.items.getItemById("op.stone");
		LOG_BLOCK = ENV.blocks.fromItem(ENV.items.getItemById("op.log"));
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
		
		PassiveEntity result = PassiveSynth_ItemSlot.applyChange(context, start);
		
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
		
		PassiveEntity result = PassiveSynth_ItemSlot.applyChange(context, start);
		
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
		
		PassiveEntity result = PassiveSynth_ItemSlot.applyChange(context, start);
		
		Assert.assertNull(result);
	}

	@Test
	public void passiveDespawnOnPickUp() throws Throwable
	{
		// We will show that a passive item slot will request that it despawn when picked up so long as the requesting player is still online.
		PassiveEntity stackIngot = new PassiveEntity(1
			, PassiveType.ITEM_SLOT
			, new EntityLocation(5.0f, 5.0f, 5.0f)
			, new EntityLocation(0.0f, 0.0f, 0.0f)
			, ItemSlot.fromStack(new Items(STONE_ITEM, 2))
			, 1000L
		);
		
		int idLoaded = 1;
		TickProcessingContext context = ContextBuilder.build()
			.sinks(null, new TickProcessingContext.IChangeSink()
			{
				@Override
				public boolean passive(int targetPassiveId, IPassiveAction action)
				{
					throw new AssertionError("Not in test");
				}
				@Override
				public boolean next(int targetEntityId, IEntityAction<IMutablePlayerEntity> change)
				{
					Assert.assertTrue(change instanceof EntityActionStoreToInventory);
					return (idLoaded == targetEntityId);
				}
				@Override
				public boolean future(int targetEntityId, IEntityAction<IMutablePlayerEntity> change, long millisToDelay)
				{
					throw new AssertionError("Not in test");
				}
				@Override
				public boolean creature(int targetCreatureId, IEntityAction<IMutableCreatureEntity> change)
				{
					throw new AssertionError("Not in test");
				}
			})
			.finish()
		;
		
		// This should despawn if we ack the pick-up to the loaded ID, only.
		Assert.assertNull(new PassiveActionPickUp(idLoaded).applyChange(context, stackIngot));
		Assert.assertEquals(stackIngot, new PassiveActionPickUp(2).applyChange(context, stackIngot));
	}

	@Test
	public void passiveBurnUp()
	{
		// Show that a passive item slot instance despawns when in a block above a burning block.
		int id = 1;
		EntityLocation location = new EntityLocation(10.0f, 10.0f, 10.5f);
		EntityLocation velocity = new EntityLocation(0.0f, 0.0f, 0.0f);
		ItemSlot slot = ItemSlot.fromStack(new Items(STONE_ITEM, 5));
		long createMillis = 1000L;
		PassiveEntity start = new PassiveEntity(id, PassiveType.ITEM_SLOT, location, velocity, slot, createMillis);
		
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(10, 10, 9), LOG_BLOCK.item().number());
		cuboid.setData7(AspectRegistry.FLAGS, BlockAddress.fromInt(10, 10, 9), FlagsAspect.FLAG_BURNING);
		TickProcessingContext context = _createSingleCuboidContext(cuboid, 1L);
		
		PassiveEntity result = PassiveSynth_ItemSlot.applyChange(context, start);
		
		Assert.assertNull(result);
	}

	@Test
	public void passiveArrowFly()
	{
		// Show that an arrow will move if it doesn't hit anything.
		int id = 1;
		EntityLocation location = new EntityLocation(10.0f, 10.0f, 10.5f);
		EntityLocation velocity = new EntityLocation(2.0f, 0.0f, 0.0f);
		long createMillis = 1000L;
		PassiveEntity start = new PassiveEntity(id, PassiveType.PROJECTILE_ARROW, location, velocity, null, createMillis);
		
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		TickProcessingContext context = _createSingleCuboidContext(cuboid, 1L);
		
		PassiveEntity result = PassiveSynth_ProjectileArrow.applyChange(context, EntityCollection.emptyCollection(), start);
		Assert.assertEquals(new EntityLocation(10.2f, 10.0f, 10.4f), result.location());
		Assert.assertEquals(new EntityLocation(2.0f, 0.0f, -0.98f), result.velocity());
	}

	@Test
	public void passiveArrowHitCreature()
	{
		// Show that an arrow will despawn and deal damage to an entity it hits.
		int id = 1;
		EntityLocation location = new EntityLocation(10.0f, 10.0f, 10.5f);
		EntityLocation velocity = new EntityLocation(2.0f, 0.0f, 0.0f);
		long createMillis = 1000L;
		PassiveEntity start = new PassiveEntity(id, PassiveType.PROJECTILE_ARROW, location, velocity, null, createMillis);
		CreatureEntity creature = new CreatureEntity(-1
			, ENV.creatures.getTypeById("op.cow")
			, new EntityLocation(10.1f, 10.0f, 10.2f)
			, new EntityLocation(0.0f, 0.0f, 0.0f)
			, (byte)0
			, (byte)0
			, (byte)100
			, (byte)100
			, null
			, null
		);
		
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		EntityCollection entityCollection = EntityCollection.fromMaps(Map.of(), Map.of(creature.id(), creature));
		@SuppressWarnings("unchecked")
		EntityActionTakeDamageFromEntity<IMutableCreatureEntity>[] out = new EntityActionTakeDamageFromEntity[1];
		TickProcessingContext context = ContextBuilder.build()
			.lookups((AbsoluteLocation l) -> {
				return (l.getCuboidAddress().equals(cuboid.getCuboidAddress()))
						? new BlockProxy(l.getBlockAddress(), cuboid)
						: null
				;
			}, null, null)
			.sinks(null, new TickProcessingContext.IChangeSink() {
				@Override
				public boolean next(int targetEntityId, IEntityAction<IMutablePlayerEntity> change)
				{
					throw new AssertionError("Not in test");
				}
				@Override
				public boolean future(int targetEntityId, IEntityAction<IMutablePlayerEntity> change, long millisToDelay)
				{
					throw new AssertionError("Not in test");
				}
				@Override
				public boolean creature(int targetCreatureId, IEntityAction<IMutableCreatureEntity> change)
				{
					Assert.assertNull(out[0]);
					Assert.assertEquals(creature.id(), targetCreatureId);
					out[0] = (EntityActionTakeDamageFromEntity<IMutableCreatureEntity>) change;
					return true;
				}
				@Override
				public boolean passive(int targetPassiveId, IPassiveAction action)
				{
					throw new AssertionError("Not in test");
				}
			})
			.tick(1L)
			.finish()
		;
		
		PassiveEntity result = PassiveSynth_ProjectileArrow.applyChange(context, entityCollection, start);
		Assert.assertNull(result);
		Assert.assertNotNull(out[0]);
	}

	@Test
	public void passiveArrowHitWall()
	{
		// Show that an arrow will respawn as an item if it hits a solid surface.
		int id = 1;
		EntityLocation location = new EntityLocation(10.8f, 10.0f, 10.5f);
		EntityLocation velocity = new EntityLocation(2.0f, 0.0f, 0.0f);
		long createMillis = 1000L;
		PassiveEntity start = new PassiveEntity(id, PassiveType.PROJECTILE_ARROW, location, velocity, null, createMillis);
		
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, location.getBlockLocation().getRelative(1, 0, 0).getBlockAddress(), STONE_ITEM.number());
		PassiveEntity[] out = new PassiveEntity[1];
		TickProcessingContext context = ContextBuilder.build()
			.lookups((AbsoluteLocation l) -> {
				return (l.getCuboidAddress().equals(cuboid.getCuboidAddress()))
						? new BlockProxy(l.getBlockAddress(), cuboid)
						: null
				;
			}, null, null)
			.passive((PassiveType type, EntityLocation l, EntityLocation v, Object extendedData) -> {
				Assert.assertNull(out[0]);
				out[0] = new PassiveEntity(2
					, type
					, l
					, v
					, extendedData
					, createMillis
				);
			})
			.tick(1L)
			.finish()
		;
		
		PassiveEntity result = PassiveSynth_ProjectileArrow.applyChange(context, EntityCollection.emptyCollection(), start);
		Assert.assertNull(result);
		Assert.assertEquals("op.arrow", ((ItemSlot)out[0].extendedData()).stack.type().id());
	}


	private static TickProcessingContext _createSingleCuboidContext(CuboidData cuboid, long tickNumber)
	{
		TickProcessingContext context = ContextBuilder.build()
			.lookups((AbsoluteLocation location) -> {
				return (location.getCuboidAddress().equals(cuboid.getCuboidAddress()))
						? new BlockProxy(location.getBlockAddress(), cuboid)
						: null
				;
			}, null, null)
			.tick(tickNumber)
			.finish()
		;
		return context;
	}
}
