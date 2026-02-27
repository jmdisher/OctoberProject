package com.jeffdisher.october.engine;

import java.util.List;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.actions.EntityActionStoreToInventory;
import com.jeffdisher.october.actions.passive.PassiveActionPickUp;
import com.jeffdisher.october.actions.passive.PassiveActionRequestStoreToInventory;
import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.MutationBlockReplaceDropExisting;
import com.jeffdisher.october.mutations.MutationBlockStoreItems;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.ContextBuilder;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.IPassiveAction;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableEntity;
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
	private static Block WATER_SOURCE;
	private static Block WATER_STRONG;
	@BeforeClass
	public static void setup() throws Throwable
	{
		ENV = Environment.createSharedInstance();
		STONE_ITEM = ENV.items.getItemById("op.stone");
		STONE = ENV.blocks.fromItem(STONE_ITEM);
		LAVA_SOURCE = ENV.blocks.fromItem(ENV.items.getItemById("op.lava_source"));
		WATER_SOURCE = ENV.blocks.fromItem(ENV.items.getItemById("op.water_source"));
		WATER_STRONG = ENV.blocks.fromItem(ENV.items.getItemById("op.water_strong"));
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
		int passiveId = 2;
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
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		EventRecord[] out_events = new EventRecord[1];
		TickProcessingContext context = ContextBuilder.build()
			.tick(1L)
			.lookups(ContextBuilder.buildFetcher((AbsoluteLocation lookupLocation) -> {
				Assert.assertEquals(airCuboid.getCuboidAddress(), lookupLocation.getCuboidAddress());
				return BlockProxy.load(lookupLocation.getBlockAddress(), airCuboid);
			}), null, null)
			.sinks(null, sink)
			.eventSink((EventRecord event) -> {
				Assert.assertNull(out_events[0]);
				out_events[0] = event;
			})
			.finish()
		;
		EntityCollection entityCollection = EntityCollection.emptyCollection();
		
		PassiveActionPickUp pickUp = new PassiveActionPickUp(entityId);
		PassiveEntity result = EnginePassives.processOneCreature(context, entityCollection, passive, List.of(pickUp));
		Assert.assertNull(result);
		Assert.assertNotNull(out[0]);
		Assert.assertEquals(new EventRecord(EventRecord.Type.ENTITY_PICKED_UP_PASSIVE
			, EventRecord.Cause.NONE
			, passive.location().getBlockLocation()
			, entityId
			, passive.id()
		), out_events[0]);
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
			.lookups(ContextBuilder.buildFetcher((AbsoluteLocation location) -> {
				return cuboid.getCuboidAddress().equals(location.getCuboidAddress())
					? BlockProxy.load(location.getBlockAddress(), cuboid)
					: null
				;
			}), null, null)
			.finish()
		;
		EntityCollection entityCollection = EntityCollection.emptyCollection();
		
		PassiveActionRequestStoreToInventory request = new PassiveActionRequestStoreToInventory(hopperLocation);
		PassiveEntity result = EnginePassives.processOneCreature(context, entityCollection, passive, List.of(request));
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
			.lookups(ContextBuilder.buildFetcher((AbsoluteLocation location) -> {
				return cuboid.getCuboidAddress().equals(location.getCuboidAddress())
					? BlockProxy.load(location.getBlockAddress(), cuboid)
					: null
				;
			}), null, null)
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

	@Test
	public void waterStreamItems()
	{
		// Show that a water stream will push items.
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
		AbsoluteLocation strongLocation = passiveLocation.getBlockLocation();
		AbsoluteLocation sourceLocation = strongLocation.getRelative(1, 0, 0);
		cuboid.setData15(AspectRegistry.BLOCK, sourceLocation.getBlockAddress(), WATER_SOURCE.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, strongLocation.getBlockAddress(), WATER_STRONG.item().number());
		long tickNumber = 1L;
		TickProcessingContext context = ContextBuilder.build()
			.tick(tickNumber)
			.lookups(ContextBuilder.buildFetcher((AbsoluteLocation location) -> {
				return cuboid.getCuboidAddress().equals(location.getCuboidAddress())
					? BlockProxy.load(location.getBlockAddress(), cuboid)
					: null
				;
			}), null, null)
			.finish()
		;
		EntityCollection entityCollection = EntityCollection.emptyCollection();
		
		// We should see them get pushed slightly to the West (since the flow is running East->West).
		PassiveEntity result = EnginePassives.processOneCreature(context, entityCollection, passive, List.of());
		Assert.assertEquals(new EntityLocation(5.08f, 6.2f, 7.25f), result.location());
		Assert.assertEquals(new EntityLocation(-0.25f, 0.0f, -0.49f), result.velocity());
	}

	@Test
	public void dropBlockUnderPlacedBlock()
	{
		// Show that we don't intersect with a block which isn't touching the bottom of a falling block.
		int passiveId = 1;
		EntityLocation passiveLocation = new EntityLocation(3.0f, 3.0f, 4.9f);
		long lastAliveMillis = PassiveType.ITEM_SLOT_DESPAWN_MILLIS + 1L;
		PassiveEntity passive = new PassiveEntity(passiveId
			, PassiveType.FALLING_BLOCK
			, passiveLocation
			, new EntityLocation(0.0f, 0.0f, 0.0f)
			, STONE
			, lastAliveMillis
		);
		
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), STONE);
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(3, 3, 4), ENV.special.AIR.item().number());
		long tickNumber = 1L;
		TickProcessingContext context = ContextBuilder.build()
			.tick(tickNumber)
			.lookups(ContextBuilder.buildFetcher((AbsoluteLocation location) -> {
				return cuboid.getCuboidAddress().equals(location.getCuboidAddress())
					? BlockProxy.load(location.getBlockAddress(), cuboid)
					: null
				;
			}), null, null)
			.finish()
		;
		EntityCollection entityCollection = EntityCollection.emptyCollection();
		
		// Show that we continue falling since 4 is air, even though we intersect with 5 which is stone.
		PassiveEntity result = EnginePassives.processOneCreature(context, entityCollection, passive, List.of());
		Assert.assertNotNull(result);
		Assert.assertEquals(new EntityLocation(3.0f, 3.0f, 4.8f), result.location());
	}

	@Test
	public void arrowFlightAndCollision()
	{
		// Show an arrow fly through the air and collide with the ground.
		int passiveId = 1;
		EntityLocation startLocation = new EntityLocation(10.35f, -6.89f, 1.57f);
		EntityLocation startVelocity = new EntityLocation(8.1f, -5.7f, 1.2f);
		PassiveEntity passive = new PassiveEntity(passiveId
			, PassiveType.PROJECTILE_ARROW
			, startLocation
			, startVelocity
			, null
			, 0L
		);
		
		PassiveEntity[] out = new PassiveEntity[1];
		TickProcessingContext context = _createContextWithPassiveSpawner((PassiveType type, EntityLocation location, EntityLocation velocity, Object extendedData) -> {
			Assert.assertNull(out[0]);
			out[0] = new PassiveEntity(2
				, type
				, location
				, velocity
				, extendedData
				, 0L
			);
		});
		EntityCollection entityCollection = EntityCollection.emptyCollection();
		
		int ticks = 0;
		while (null != passive)
		{
			passive = EnginePassives.processOneCreature(context, entityCollection, passive, List.of());
			ticks += 1;
		}
		// These checks are experimentally verified.
		Assert.assertEquals(7, ticks);
		Assert.assertEquals(PassiveType.ITEM_SLOT, out[0].type());
		Assert.assertEquals(new EntityLocation(16.02f, -10.88f, 0.0f), out[0].location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), out[0].velocity());
	}

	@Test
	public void arrowAvoidShooter()
	{
		// Show that the arrow does not collide with its shooter, in all 4 cardinal directions.
		MutableEntity mutable = MutableEntity.createForTest(1);
		Entity entity = mutable.freeze();
		EntityLocation eyeLocation = SpatialHelpers.getEyeLocation(mutable);
		PassiveEntity north = new PassiveEntity(1
			, PassiveType.PROJECTILE_ARROW
			, eyeLocation
			, new EntityLocation(0.0f, 25.0f, 0.0f)
			, null
			, 0L
		);
		PassiveEntity south = new PassiveEntity(2
			, PassiveType.PROJECTILE_ARROW
			, eyeLocation
			, new EntityLocation(0.0f, -25.0f, 0.0f)
			, null
			, 0L
		);
		PassiveEntity east = new PassiveEntity(3
			, PassiveType.PROJECTILE_ARROW
			, eyeLocation
			, new EntityLocation(25.0f, 0.0f, 0.0f)
			, null
			, 0L
		);
		PassiveEntity west = new PassiveEntity(4
			, PassiveType.PROJECTILE_ARROW
			, eyeLocation
			, new EntityLocation(-25.0f, 0.0f, 0.0f)
			, null
			, 0L
		);
		
		TickProcessingContext context = _createContextWithSink(null);
		EntityCollection entityCollection = EntityCollection.fromMaps(Map.of(entity.id(), entity), Map.of());
		north = EnginePassives.processOneCreature(context, entityCollection, north, List.of());
		south = EnginePassives.processOneCreature(context, entityCollection, south, List.of());
		east = EnginePassives.processOneCreature(context, entityCollection, east, List.of());
		west = EnginePassives.processOneCreature(context, entityCollection, west, List.of());
		
		Assert.assertEquals(new EntityLocation(0.2f, 2.7f, 1.43f), north.location());
		Assert.assertEquals(new EntityLocation(0.0f, 25.0f, -0.98f), north.velocity());
		Assert.assertEquals(new EntityLocation(0.2f, -2.3f, 1.43f), south.location());
		Assert.assertEquals(new EntityLocation(0.0f, -25.0f, -0.98f), south.velocity());
		Assert.assertEquals(new EntityLocation(2.7f, 0.2f, 1.43f), east.location());
		Assert.assertEquals(new EntityLocation(25.0f, 0.0f, -0.98f), east.velocity());
		Assert.assertEquals(new EntityLocation(-2.3f, 0.2f, 1.43f), west.location());
		Assert.assertEquals(new EntityLocation(-25.0f, 0.0f, -0.98f), west.velocity());
	}


	private static TickProcessingContext _createContextWithTopCuboid(CuboidData airCuboid)
	{
		// We return a fixed "1" for the random generator to make sure that we select a reasonable plan for all tests.
		return _createContextLowLevel(null, null, 1, airCuboid);
	}

	private static TickProcessingContext _createContextWithSink(TickProcessingContext.IChangeSink changeSink)
	{
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		// We return a fixed "1" for the random generator to make sure that we select a reasonable plan for all tests.
		return _createContextLowLevel(changeSink, null, 1, airCuboid);
	}

	private static TickProcessingContext _createContextWithPassiveSpawner(TickProcessingContext.IPassiveSpawner spawner)
	{
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		return _createContextLowLevel(null, spawner, 0, airCuboid);
	}

	private static TickProcessingContext _createContextLowLevel(TickProcessingContext.IChangeSink changeSink
		, TickProcessingContext.IPassiveSpawner spawner
		, int fixedRandom
		, CuboidData airCuboid
	)
	{
		CuboidData stoneCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), STONE);
		long millisPerTick = 100L;
		long tickNumber = (2L * PassiveType.ITEM_SLOT_DESPAWN_MILLIS) / millisPerTick;
		TickProcessingContext context = ContextBuilder.build()
			.tick(tickNumber)
			.sinks(null, changeSink)
			.lookups(ContextBuilder.buildFetcher((AbsoluteLocation location) -> {
				return ((short)-1 == location.z())
					? BlockProxy.load(location.getBlockAddress(), stoneCuboid)
					: BlockProxy.load(location.getBlockAddress(), airCuboid)
				;
			}), null, null)
			.passive(spawner)
			.fixedRandom(fixedRandom)
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
