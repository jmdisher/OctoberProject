package com.jeffdisher.october.mutations;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.ContextBuilder;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.worldgen.CuboidGenerator;


public class TestTickUtils
{
	private static Environment ENV;
	private static Item STONE_ITEM;
	private static Block STONE;
	private static EntityType ORC;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		STONE_ITEM = ENV.items.getItemById("op.stone");
		STONE = ENV.blocks.fromItem(STONE_ITEM);
		ORC = ENV.creatures.getTypeById("op.orc");
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void fallFromHeights()
	{
		// Demonstrate the fall damage at different heights (this is likely to be adjusted over time).
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(16, 16, 0), STONE.item().number());
		Function<AbsoluteLocation, BlockProxy> previousBlockLookUp = (AbsoluteLocation location) -> {
			return new BlockProxy(location.getBlockAddress(), cuboid);
		};
		int[] outDamage = new int[1];
		TickUtils.IFallDamage fallDamage = (int damage) -> {
			Assert.assertEquals(0, outDamage[0]);
			outDamage[0] = damage;
		};
		long millisPerMove = 100L;
		byte health = 100;
		int creatureCount = 20;
		float spreadZ = 0.5f;
		EntityLocation startLocation = new EntityLocation(16.8f, 16.8f, 1.0f);
		Map<Integer, CreatureEntity> creatures = new HashMap<>();
		int id = -1;
		int[] iterationCount = new int[creatureCount];
		int[] damage = new int[creatureCount];
		for (int i = 0; i < creatureCount; ++i)
		{
			CreatureEntity creature = CreatureEntity.create(id, ORC, startLocation, health);
			creatures.put(creature.id(), creature);
			id -= 1;
			startLocation = new EntityLocation(startLocation.x(), startLocation.y(), startLocation.z() + spreadZ);
			
			MutableCreature mutable = MutableCreature.existing(creature);
			TickUtils.allowMovement(previousBlockLookUp, fallDamage, mutable, millisPerMove);
			int applications = 1;
			while (mutable.getVelocityVector().z() < 0.0f)
			{
				TickUtils.allowMovement(previousBlockLookUp, fallDamage, mutable, millisPerMove);
				applications += 1;
			}
			iterationCount[i] = applications;
			damage[i] = outDamage[0];
			outDamage[0] = 0;
		}
		int[] expectedIterations = new int[] {1, 4, 5, 6, 7, 8, 8, 9, 10, 10, 11, 11, 12, 12, 12, 13, 13, 14, 14, 14};
		int[] expectedDamage = new int[]     {0, 0, 0, 0, 0, 0, 0, 0,  3,  3,  7,  7, 11, 11, 11, 14, 14, 18, 18, 18};
		Assert.assertArrayEquals(expectedIterations, iterationCount);
		Assert.assertArrayEquals(expectedDamage, damage);
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
				.lookups(previousBlockLookUp, null)
				.eventSink(eventSink)
				.finish()
		;
		byte startHealth = 50;
		MutableEntity player = MutableEntity.createForTest(1);
		player.newBreath = 0;
		player.newHealth = startHealth;
		TickUtils.endOfTick(context, player);
		Assert.assertEquals(0, player.newBreath);
		Assert.assertEquals(startHealth - MiscConstants.SUFFOCATION_DAMAGE_PER_SECOND, player.newHealth);
		Assert.assertEquals(new EventRecord(EventRecord.Type.ENTITY_HURT, EventRecord.Cause.SUFFOCATION, player.newLocation.getBlockLocation(), player.getId(), 0), out_events[0]);
	}
}
