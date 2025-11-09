package com.jeffdisher.october.engine;

import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.actions.EntityActionStoreToInventory;
import com.jeffdisher.october.actions.passive.PassiveActionPickUp;
import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.MutationBlockReplaceDropExisting;
import com.jeffdisher.october.mutations.MutationBlockStoreItems;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.ContextBuilder;
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


public class TestEnginePassives
{
	private static Environment ENV;
	private static Item STONE_ITEM;
	private static Block STONE;
	private static Block LAVA_SOURCE;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		STONE_ITEM = ENV.items.getItemById("op.stone");
		STONE = ENV.blocks.fromItem(STONE_ITEM);
		LAVA_SOURCE = ENV.blocks.fromItem(ENV.items.getItemById("op.lava_source"));
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void doNothing()
	{
		int passiveId = 1;
		EntityLocation location = new EntityLocation(0.0f, 0.0f, 0.0f);
		ItemSlot slot = ItemSlot.fromStack(new Items(STONE_ITEM, 2));
		long lastAliveMillis = PassiveType.ITEM_SLOT_DESPAWN_MILLIS + 1L;
		PassiveEntity passive = new PassiveEntity(passiveId
			, PassiveType.ITEM_SLOT
			, location
			, new EntityLocation(0.0f, 0.0f, 0.0f)
			, slot
			, lastAliveMillis
		);
		
		TickProcessingContext context = _createContextWithSink(null);
		EntityCollection entityCollection = EntityCollection.emptyCollection();
		
		PassiveEntity result = EnginePassives.processOneCreature(context, entityCollection, passive, List.of());
		Assert.assertNotNull(result);
	}

	@Test
	public void falling()
	{
		int passiveId = 1;
		EntityLocation location = new EntityLocation(0.0f, 0.0f, 5.0f);
		ItemSlot slot = ItemSlot.fromStack(new Items(STONE_ITEM, 2));
		long lastAliveMillis = PassiveType.ITEM_SLOT_DESPAWN_MILLIS + 1L;
		PassiveEntity passive = new PassiveEntity(passiveId
			, PassiveType.ITEM_SLOT
			, location
			, new EntityLocation(0.0f, 0.0f, 0.0f)
			, slot
			, lastAliveMillis
		);
		
		TickProcessingContext context = _createContextWithSink(null);
		EntityCollection entityCollection = EntityCollection.emptyCollection();
		
		PassiveEntity result = EnginePassives.processOneCreature(context, entityCollection, passive, List.of());
		Assert.assertNotNull(result);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 4.9f), result.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -0.98f), result.velocity());
	}

	@Test
	public void despawn()
	{
		int passiveId = 1;
		EntityLocation location = new EntityLocation(0.0f, 0.0f, 0.0f);
		ItemSlot slot = ItemSlot.fromStack(new Items(STONE_ITEM, 2));
		long lastAliveMillis = PassiveType.ITEM_SLOT_DESPAWN_MILLIS - 1L;
		PassiveEntity passive = new PassiveEntity(passiveId
			, PassiveType.ITEM_SLOT
			, location
			, new EntityLocation(0.0f, 0.0f, 0.0f)
			, slot
			, lastAliveMillis
		);
		
		TickProcessingContext context = _createContextWithSink(null);
		EntityCollection entityCollection = EntityCollection.emptyCollection();
		
		PassiveEntity result = EnginePassives.processOneCreature(context, entityCollection, passive, List.of());
		Assert.assertNull(result);
	}

	@Test
	public void pickedUp()
	{
		int passiveId = 1;
		EntityLocation location = new EntityLocation(0.0f, 0.0f, 0.0f);
		ItemSlot slot = ItemSlot.fromStack(new Items(STONE_ITEM, 2));
		long lastAliveMillis = PassiveType.ITEM_SLOT_DESPAWN_MILLIS + 1L;
		PassiveEntity passive = new PassiveEntity(passiveId
			, PassiveType.ITEM_SLOT
			, location
			, new EntityLocation(0.0f, 0.0f, 0.0f)
			, slot
			, lastAliveMillis
		);
		
		int entityId = 1;
		EntityActionStoreToInventory[] out = new EntityActionStoreToInventory[1];
		_ChangeSink sink = new _ChangeSink() {
			@Override
			public boolean next(int targetEntityId, IEntityAction<IMutablePlayerEntity> change)
			{
				Assert.assertEquals(entityId, targetEntityId);
				Assert.assertNull(out[0]);
				out[0] = (EntityActionStoreToInventory) change;
				return true;
			}
		};
		TickProcessingContext context = _createContextWithSink(sink);
		EntityCollection entityCollection = EntityCollection.emptyCollection();
		
		PassiveActionPickUp pickUp = new PassiveActionPickUp(entityId);
		PassiveEntity result = EnginePassives.processOneCreature(context, entityCollection, passive, List.of(pickUp));
		Assert.assertNull(result);
		Assert.assertNotNull(out[0]);
	}

	@Test
	public void failPickedUp()
	{
		int passiveId = 1;
		EntityLocation location = new EntityLocation(0.0f, 0.0f, 0.0f);
		ItemSlot slot = ItemSlot.fromStack(new Items(STONE_ITEM, 2));
		long lastAliveMillis = PassiveType.ITEM_SLOT_DESPAWN_MILLIS + 1L;
		PassiveEntity passive = new PassiveEntity(passiveId
			, PassiveType.ITEM_SLOT
			, location
			, new EntityLocation(0.0f, 0.0f, 0.0f)
			, slot
			, lastAliveMillis
		);
		
		int entityId = 1;
		EntityActionStoreToInventory[] out = new EntityActionStoreToInventory[1];
		_ChangeSink sink = new _ChangeSink() {
			@Override
			public boolean next(int targetEntityId, IEntityAction<IMutablePlayerEntity> change)
			{
				Assert.assertEquals(entityId, targetEntityId);
				Assert.assertNull(out[0]);
				out[0] = (EntityActionStoreToInventory) change;
				// We return false here so that it won't despawn.
				return false;
			}
		};
		TickProcessingContext context = _createContextWithSink(sink);
		EntityCollection entityCollection = EntityCollection.emptyCollection();
		
		PassiveActionPickUp pickUp = new PassiveActionPickUp(entityId);
		PassiveEntity result = EnginePassives.processOneCreature(context, entityCollection, passive, List.of(pickUp));
		Assert.assertNotNull(result);
		Assert.assertNotNull(out[0]);
	}

	@Test
	public void damageLava()
	{
		int passiveId = 1;
		EntityLocation location = new EntityLocation(0.95f, 0.95f, 0.0f);
		ItemSlot slot = ItemSlot.fromStack(new Items(STONE_ITEM, 2));
		long lastAliveMillis = PassiveType.ITEM_SLOT_DESPAWN_MILLIS + 1L;
		PassiveEntity passive = new PassiveEntity(passiveId
			, PassiveType.ITEM_SLOT
			, location
			, new EntityLocation(0.0f, 0.0f, 0.0f)
			, slot
			, lastAliveMillis
		);
		
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		airCuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(1, 1, 0), LAVA_SOURCE.item().number());
		TickProcessingContext context = _createContextWithTopCuboid(airCuboid);
		EntityCollection entityCollection = EntityCollection.emptyCollection();
		
		PassiveEntity result = EnginePassives.processOneCreature(context, entityCollection, passive, List.of());
		Assert.assertNull(result);
	}

	@Test
	public void popOut()
	{
		int passiveId = 1;
		EntityLocation location = new EntityLocation(0.0f, 0.0f, -1.0f);
		ItemSlot slot = ItemSlot.fromStack(new Items(STONE_ITEM, 2));
		long lastAliveMillis = PassiveType.ITEM_SLOT_DESPAWN_MILLIS + 1L;
		PassiveEntity passive = new PassiveEntity(passiveId
			, PassiveType.ITEM_SLOT
			, location
			, new EntityLocation(0.0f, 0.0f, 0.0f)
			, slot
			, lastAliveMillis
		);
		
		TickProcessingContext context = _createContextWithSink(null);
		EntityCollection entityCollection = EntityCollection.emptyCollection();
		
		PassiveEntity result = EnginePassives.processOneCreature(context, entityCollection, passive, List.of());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), result.location());
	}

	@Test
	public void enterHopper()
	{
		int passiveId = 1;
		EntityLocation passiveLocation = new EntityLocation(5.1f, 6.2f, 7.3f);
		ItemSlot slot = ItemSlot.fromStack(new Items(STONE_ITEM, 2));
		long lastAliveMillis = PassiveType.ITEM_SLOT_DESPAWN_MILLIS + 1L;
		PassiveEntity passive = new PassiveEntity(passiveId
			, PassiveType.ITEM_SLOT
			, passiveLocation
			, new EntityLocation(0.0f, 0.0f, 0.0f)
			, slot
			, lastAliveMillis
		);
		
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		Block hopper = ENV.blocks.fromItem(ENV.items.getItemById("op.hopper"));
		AbsoluteLocation hopperLocation = passiveLocation.getBlockLocation().getRelative(0, 0, -1);
		cuboid.setData15(AspectRegistry.BLOCK, hopperLocation.getBlockAddress(), hopper.item().number());
		long tickNumber = 1L;
		MutationBlockStoreItems[] out_mutation = new MutationBlockStoreItems[1];
		TickProcessingContext context = ContextBuilder.build()
			.tick(tickNumber)
			.sinks(new TickProcessingContext.IMutationSink() {
				@Override
				public boolean next(IMutationBlock mutation)
				{
					Assert.assertNull(out_mutation[0]);
					out_mutation[0] = (MutationBlockStoreItems) mutation;
					return true;
				}
				@Override
				public boolean future(IMutationBlock mutation, long millisToDelay)
				{
					throw new AssertionError("Not in test");
				}
			}, null)
			.lookups((AbsoluteLocation location) -> {
				return cuboid.getCuboidAddress().equals(location.getCuboidAddress())
					? new BlockProxy(location.getBlockAddress(), cuboid)
					: null
				;
			} , null, null)
			.finish()
		;
		EntityCollection entityCollection = EntityCollection.emptyCollection();
		
		PassiveEntity result = EnginePassives.processOneCreature(context, entityCollection, passive, List.of());
		Assert.assertNull(result);
		Assert.assertNotNull(out_mutation[0]);
		Assert.assertEquals(hopperLocation, out_mutation[0].getAbsoluteLocation());
	}

	@Test
	public void arrowFly()
	{
		int passiveId = 1;
		EntityLocation location = new EntityLocation(10.0f, 10.0f, 10.0f);
		EntityLocation velocity = new EntityLocation(2.0f, 0.0f, 0.0f);
		PassiveEntity passive = new PassiveEntity(passiveId
			, PassiveType.PROJECTILE_ARROW
			, location
			, velocity
			, null
			, 0L
		);
		
		TickProcessingContext context = _createContextWithSink(null);
		EntityCollection entityCollection = EntityCollection.emptyCollection();
		
		PassiveEntity result = EnginePassives.processOneCreature(context, entityCollection, passive, List.of());
		Assert.assertEquals(new EntityLocation(10.2f, 10.0f, 9.9f), result.location());
		Assert.assertEquals(new EntityLocation(2.0f, 0.0f, -0.98f), result.velocity());
	}

	@Test
	public void dropBlock()
	{
		int passiveId = 1;
		EntityLocation passiveLocation = new EntityLocation(3.0f, 3.0f, 5.1f);
		long lastAliveMillis = PassiveType.ITEM_SLOT_DESPAWN_MILLIS + 1L;
		PassiveEntity passive = new PassiveEntity(passiveId
			, PassiveType.FALLING_BLOCK
			, passiveLocation
			, new EntityLocation(0.0f, 0.0f, 0.0f)
			, STONE
			, lastAliveMillis
		);
		
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), STONE);
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(3, 3, 5), ENV.special.AIR.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(3, 3, 6), ENV.special.AIR.item().number());
		long tickNumber = 1L;
		MutationBlockReplaceDropExisting[] out_mutation = new MutationBlockReplaceDropExisting[1];
		TickProcessingContext context = ContextBuilder.build()
			.tick(tickNumber)
			.sinks(new TickProcessingContext.IMutationSink() {
				@Override
				public boolean next(IMutationBlock mutation)
				{
					Assert.assertNull(out_mutation[0]);
					out_mutation[0] = (MutationBlockReplaceDropExisting) mutation;
					return true;
				}
				@Override
				public boolean future(IMutationBlock mutation, long millisToDelay)
				{
					throw new AssertionError("Not in test");
				}
			}, null)
			.lookups((AbsoluteLocation location) -> {
				return cuboid.getCuboidAddress().equals(location.getCuboidAddress())
					? new BlockProxy(location.getBlockAddress(), cuboid)
					: null
				;
			} , null, null)
			.finish()
		;
		EntityCollection entityCollection = EntityCollection.emptyCollection();
		
		// Show that it will fall a bit before hitting bottom.
		PassiveEntity result = EnginePassives.processOneCreature(context, entityCollection, passive, List.of());
		Assert.assertNotNull(result);
		Assert.assertEquals(new EntityLocation(3.0f, 3.0f, 5.0f), result.location());
		Assert.assertNull(out_mutation[0]);
		
		passive = result;
		result = EnginePassives.processOneCreature(context, entityCollection, passive, List.of());
		Assert.assertNull(result);
		Assert.assertEquals(new AbsoluteLocation(3, 3, 5), out_mutation[0].getAbsoluteLocation());
	}


	private static TickProcessingContext _createContextWithTopCuboid(CuboidData airCuboid)
	{
		return _createContextLowLevel(null, airCuboid);
	}

	private static TickProcessingContext _createContextWithSink(TickProcessingContext.IChangeSink changeSink)
	{
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		return _createContextLowLevel(changeSink, airCuboid);
	}

	private static TickProcessingContext _createContextLowLevel(TickProcessingContext.IChangeSink changeSink, CuboidData airCuboid)
	{
		CuboidData stoneCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), STONE);
		long millisPerTick = 100L;
		long tickNumber = (2L * PassiveType.ITEM_SLOT_DESPAWN_MILLIS) / millisPerTick;
		TickProcessingContext context = ContextBuilder.build()
			.tick(tickNumber)
			.sinks(null, changeSink)
			.lookups((AbsoluteLocation location) -> {
				return ((short)-1 == location.z())
					? new BlockProxy(location.getBlockAddress(), stoneCuboid)
					: new BlockProxy(location.getBlockAddress(), airCuboid)
				;
			} , null, null)
			// We return a fixed "1" for the random generator to make sure that we select a reasonable plan for all tests.
			.fixedRandom(1)
			.finish()
		;
		return context;
	}

	// This is abstract just to fail on every call so tests can selectively opt-in to functionality they need.
	private static abstract class _ChangeSink implements TickProcessingContext.IChangeSink
	{
		@Override
		public boolean next(int targetEntityId, IEntityAction<IMutablePlayerEntity> change)
		{
			throw new AssertionError("Implement if needed");
		}
		@Override
		public boolean future(int targetEntityId, IEntityAction<IMutablePlayerEntity> change, long millisToDelay)
		{
			throw new AssertionError("Implement if needed");
		}
		@Override
		public boolean creature(int targetCreatureId, IEntityAction<IMutableCreatureEntity> change)
		{
			throw new AssertionError("Implement if needed");
		}
		@Override
		public boolean passive(int targetPassiveId, IPassiveAction action)
		{
			throw new AssertionError("Implement if needed");
		}
	}
}
