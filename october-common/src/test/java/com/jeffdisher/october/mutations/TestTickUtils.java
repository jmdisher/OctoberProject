package com.jeffdisher.october.mutations;

import java.util.function.Function;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.ContextBuilder;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestTickUtils
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
	public void suffocate()
	{
		// Show that we take suffocation damage if not in a breathable space at the end of a tick.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), STONE);
		Function<AbsoluteLocation, BlockProxy> previousBlockLookUp = (AbsoluteLocation location) -> {
			return new BlockProxy(location.getBlockAddress(), cuboid);
		};
		long millisPerTick = 100L;
		EventRecord[] out_events = new EventRecord[1];
		TickProcessingContext.IEventSink eventSink = (EventRecord event) -> {
			Assert.assertNull(out_events[0]);
			out_events[0] = event;
		};
		TickProcessingContext context = ContextBuilder.build()
				.millisPerTick(millisPerTick)
				.tick(MiscConstants.DAMAGE_ENVIRONMENT_CHECK_MILLIS * millisPerTick)
				.lookups(previousBlockLookUp, null, null)
				.eventSink(eventSink)
				.finish()
		;
		byte startHealth = 50;
		MutableEntity player = MutableEntity.createForTest(1);
		player.newBreath = 0;
		player.newHealth = startHealth;
		Assert.assertTrue(TickUtils.canApplyEnvironmentalDamageInTick(context));
		TickUtils.applyEnvironmentalDamage(context, player);
		Assert.assertEquals(0, player.newBreath);
		Assert.assertEquals(startHealth - MiscConstants.SUFFOCATION_DAMAGE_PER_SECOND, player.newHealth);
		Assert.assertEquals(new EventRecord(EventRecord.Type.ENTITY_HURT, EventRecord.Cause.SUFFOCATION, player.newLocation.getBlockLocation(), player.getId(), 0), out_events[0]);
	}

	@Test
	public void lava()
	{
		// Show that we take lava damage at the end of a tick.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), LAVA_SOURCE);
		Function<AbsoluteLocation, BlockProxy> previousBlockLookUp = (AbsoluteLocation location) -> {
			return new BlockProxy(location.getBlockAddress(), cuboid);
		};
		long millisPerTick = 100L;
		EventRecord[] out_events = new EventRecord[1];
		TickProcessingContext.IEventSink eventSink = (EventRecord event) -> {
			Assert.assertNull(out_events[0]);
			out_events[0] = event;
		};
		TickProcessingContext context = ContextBuilder.build()
				.millisPerTick(millisPerTick)
				.tick(MiscConstants.DAMAGE_ENVIRONMENT_CHECK_MILLIS * millisPerTick)
				.lookups(previousBlockLookUp, null, null)
				.eventSink(eventSink)
				.finish()
		;
		byte startBreath = 50;
		byte startHealth = 50;
		MutableEntity player = MutableEntity.createForTest(1);
		player.newBreath = startBreath;
		player.newHealth = startHealth;
		Assert.assertTrue(TickUtils.canApplyEnvironmentalDamageInTick(context));
		TickUtils.applyEnvironmentalDamage(context, player);
		Assert.assertEquals(startBreath - MiscConstants.SUFFOCATION_BREATH_PER_SECOND, player.newBreath);
		Assert.assertEquals(startHealth - 10, player.newHealth);
		Assert.assertEquals(new EventRecord(EventRecord.Type.ENTITY_HURT, EventRecord.Cause.BLOCK_DAMAGE, player.newLocation.getBlockLocation(), player.getId(), 0), out_events[0]);
	}

	@Test
	public void fireDamage()
	{
		// Show that we take fire damage at the end of a tick.
		Block log = ENV.blocks.fromItem(ENV.items.getItemById("op.log"));
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		AbsoluteLocation platform = cuboid.getCuboidAddress().getBase().getRelative(16, 16, 16);
		cuboid.setData15(AspectRegistry.BLOCK, platform.getBlockAddress(), log.item().number());
		cuboid.setData7(AspectRegistry.FLAGS, platform.getBlockAddress(), FlagsAspect.FLAG_BURNING);
		Function<AbsoluteLocation, BlockProxy> previousBlockLookUp = (AbsoluteLocation location) -> {
			return new BlockProxy(location.getBlockAddress(), cuboid);
		};
		long millisPerTick = 100L;
		EventRecord[] out_events = new EventRecord[1];
		TickProcessingContext.IEventSink eventSink = (EventRecord event) -> {
			Assert.assertNull(out_events[0]);
			out_events[0] = event;
		};
		TickProcessingContext context = ContextBuilder.build()
				.millisPerTick(millisPerTick)
				.tick(MiscConstants.DAMAGE_ENVIRONMENT_CHECK_MILLIS * millisPerTick)
				.lookups(previousBlockLookUp, null, null)
				.eventSink(eventSink)
				.finish()
		;
		byte startHealth = 50;
		MutableEntity player = MutableEntity.createForTest(1);
		player.newHealth = startHealth;
		player.newLocation = platform.getRelative(0, 0, 1).toEntityLocation();
		Assert.assertTrue(TickUtils.canApplyEnvironmentalDamageInTick(context));
		TickUtils.applyEnvironmentalDamage(context, player);
		Assert.assertEquals(startHealth - MiscConstants.FIRE_DAMAGE_PER_SECOND, player.newHealth);
		Assert.assertEquals(new EventRecord(EventRecord.Type.ENTITY_HURT, EventRecord.Cause.BLOCK_DAMAGE, player.newLocation.getBlockLocation(), player.getId(), 0), out_events[0]);
	}
}
