package com.jeffdisher.october.engine;

import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.actions.EntityActionSimpleMove;
import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.logic.OrientationHelpers;
import com.jeffdisher.october.logic.ScheduledChange;
import com.jeffdisher.october.mutations.TickUtils;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.ContextBuilder;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestEnginePlayers
{
	private static Environment ENV;
	private static Block STONE;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		STONE = ENV.blocks.fromItem(ENV.items.getItemById("op.stone"));
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void fallDamage()
	{
		// Show that an entity will take damage when falling, even if they are lagging.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(16, 16, 15), STONE.item().number());
		
		byte health = 100;
		float fallingVelocity = TickUtils.DECELERATION_DAMAGE_THRESHOLD + (TickUtils.DECELERATION_DAMAGE_RANGE / 2.0f);
		EntityLocation startLocation = new EntityLocation(16.8f, 16.8f, 16.1f);
		EntityLocation startVelocity = new EntityLocation(0.0f, 0.0f, fallingVelocity);
		int entityId = 1;
		MutableEntity mutable = MutableEntity.createForTest(entityId);
		mutable.newLocation = startLocation;
		mutable.newVelocity = startVelocity;
		mutable.newHealth = health;
		Entity entity = mutable.freeze();
		
		_Events events = new _Events();
		TickProcessingContext context = ContextBuilder.build()
			.tick(MiscConstants.DAMAGE_TAKEN_TIMEOUT_MILLIS / ContextBuilder.DEFAULT_MILLIS_PER_TICK)
			.lookups((AbsoluteLocation location) -> {
				return (cuboid.getCuboidAddress().equals(location.getCuboidAddress()))
					? new BlockProxy(location.getBlockAddress(), cuboid)
					: null
				;
			}, null)
			.eventSink(events)
			.finish()
		;
		events.expected(new EventRecord(EventRecord.Type.ENTITY_HURT, EventRecord.Cause.FALL, entity.location().getBlockLocation(), entity.id(), 0));
		
		// We will run an iteration with no top-level events, to emulate lag, to show that nothing happens.
		EnginePlayers.SinglePlayerResult result = EnginePlayers.processOnePlayer(context
			, EntityCollection.emptyCollection()
			, entity
			, List.of()
		);
		
		// We should see the one output (since we processed this) but it should have a null updated instance.
		Assert.assertNull(result.changedEntityOrNull());
		
		// Then, we will run another call with a standing change, and show that the fall damage is applied.
		EntityActionSimpleMove<IMutablePlayerEntity> topLevel = new EntityActionSimpleMove<>(0.0f
			, 0.0f
			, EntityActionSimpleMove.Intensity.STANDING
			, OrientationHelpers.YAW_NORTH
			, OrientationHelpers.PITCH_FLAT
			, null
		);
		EntityLocation fallTarget = new EntityLocation(16.8f, 16.8f, 16.0f);
		EntityLocation allStop = new EntityLocation(0.0f, 0.0f, 0.0f);
		ScheduledChange singleChange = new ScheduledChange(topLevel, 0L);
		result = EnginePlayers.processOnePlayer(context
			, EntityCollection.emptyCollection()
			, entity
			, List.of(singleChange)
		);
		entity = result.changedEntityOrNull();
		
		Assert.assertTrue(events.didPost());
		Assert.assertEquals(fallTarget, entity.location());
		Assert.assertEquals(allStop, entity.velocity());
		Assert.assertEquals((byte)37, entity.health());
	}


	private static class _Events implements TickProcessingContext.IEventSink
	{
		private EventRecord _expected;
		public void expected(EventRecord expected)
		{
			Assert.assertNull(_expected);
			_expected = expected;
		}
		public boolean didPost()
		{
			return (null == _expected);
		}
		@Override
		public void post(EventRecord event)
		{
			Assert.assertEquals(_expected, event);
			_expected = null;
		}
	}
}
