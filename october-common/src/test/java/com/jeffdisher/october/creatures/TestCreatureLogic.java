package com.jeffdisher.october.creatures;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.types.WorldConfig;
import com.jeffdisher.october.worldgen.CuboidGenerator;


public class TestCreatureLogic
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
	public void idleWithoutJumping()
	{
		// Verify that all possible idle paths end on the ground.
		EntityLocation entityLocation = new EntityLocation(16.0f, 16.0f, 1.0f);
		CreatureEntity entity = CreatureEntity.create(-1, EntityType.ORC, entityLocation, (byte)100);
		CuboidAddress cuboidAddress = new CuboidAddress((short) 0, (short) 0, (short) 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		_setLayer(input, (byte)0, "op.stone");
		
		// We should see 25 possible locations, all at z-level 1 as their final destination.
		for (int i = 0; i < 25; ++i)
		{
			TickProcessingContext context = _createContext((AbsoluteLocation location) -> {
				return location.getCuboidAddress().equals(cuboidAddress)
						? new BlockProxy(location.getBlockAddress(), input)
						: null
				;
			}, i);
			List<AbsoluteLocation> path = CreatureLogic.test_findPathToRandomSpot(context, entity);
			AbsoluteLocation target = path.get(path.size() - 1);
			Assert.assertEquals(1, target.z());
		}
		// Verify that there are only 25.
		try
		{
			TickProcessingContext context = _createContext((AbsoluteLocation location) -> {
				return location.getCuboidAddress().equals(cuboidAddress)
						? new BlockProxy(location.getBlockAddress(), input)
						: null
				;
			}, 25);
			CreatureLogic.test_findPathToRandomSpot(context, entity);
		}
		catch (AssertionError e)
		{
			// Expected from test.
		}
	}

	@Test
	public void avoidDrowning()
	{
		// Verify that idle movement will avoid stopping in the water (these should all jump).
		EntityLocation entityLocation = new EntityLocation(16.0f, 16.0f, 1.0f);
		CreatureEntity entity = CreatureEntity.create(-1, EntityType.ORC, entityLocation, (byte)100);
		CuboidAddress cuboidAddress = new CuboidAddress((short) 0, (short) 0, (short) 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		_setLayer(input, (byte)0, "op.stone");
		_setLayer(input, (byte)1, "op.water_source");
		
		// We should see 13 possible locations, all at z-level 2 as their final destination.
		for (int i = 0; i < 13; ++i)
		{
			TickProcessingContext context = _createContext((AbsoluteLocation location) -> {
				return location.getCuboidAddress().equals(cuboidAddress)
						? new BlockProxy(location.getBlockAddress(), input)
						: null
				;
			}, i);
			List<AbsoluteLocation> path = CreatureLogic.test_findPathToRandomSpot(context, entity);
			AbsoluteLocation target = path.get(path.size() - 1);
			Assert.assertEquals(2, target.z());
		}
		// Verify that there are only 13.
		try
		{
			TickProcessingContext context = _createContext((AbsoluteLocation location) -> {
				return location.getCuboidAddress().equals(cuboidAddress)
						? new BlockProxy(location.getBlockAddress(), input)
						: null
				;
			}, 13);
			CreatureLogic.test_findPathToRandomSpot(context, entity);
		}
		catch (AssertionError e)
		{
			// Expected from test.
		}
	}

	@Test
	public void leaveWaterWhenDrowning()
	{
		// Verify that idle movement will avoid stopping in the water (these should all jump).
		EntityLocation entityLocation = new EntityLocation(16.0f, 16.0f, 1.0f);
		CreatureEntity entity = CreatureEntity.create(-1, EntityType.ORC, entityLocation, (byte)100);
		CuboidAddress cuboidAddress = new CuboidAddress((short) 0, (short) 0, (short) 0);
		CuboidData input = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		_setLayer(input, (byte)0, "op.stone");
		_setLayer(input, (byte)1, "op.water_source");
		
		// Even though this is only the idle timeout, we will see a plan made and that it ends above water.
		TickProcessingContext context = _createContext((AbsoluteLocation location) -> {
			return location.getCuboidAddress().equals(cuboidAddress)
					? new BlockProxy(location.getBlockAddress(), input)
					: null
			;
		}, 12);
		MutableCreature mutable = MutableCreature.existing(entity);
		mutable.newBreath -= 1;
		List<IMutationEntity<IMutableCreatureEntity>> actions = CreatureLogic.planNextActions(context
				, null
				, new EntityCollection(Set.of(), Set.of(entity))
				, CreatureLogic.MINIMUM_MILLIS_TO_DELIBERATE_ACTION
				, mutable
		);
		Assert.assertTrue(actions.size() > 0);
		OrcStateMachine machine = OrcStateMachine.extractFromData(mutable.newExtendedData);
		List<AbsoluteLocation> plan = machine.getMovementPlan();
		Assert.assertEquals(2, plan.get(plan.size() - 1).z());
	}


	private static TickProcessingContext _createContext(Function<AbsoluteLocation, BlockProxy> previousBlockLookUp, int random)
	{
		TickProcessingContext context = new TickProcessingContext(1L
				, previousBlockLookUp
				, null
				, null
				, null
				, null
				, (int limit) -> {
					// We will assert that the limit is greater than our number to verify it isn't unexpected.
					Assert.assertTrue(limit > random);
					return random;
				}
				, new WorldConfig()
				, 100L
		);
		return context;
	}

	private void _setLayer(CuboidData input, byte z, String name)
	{
		short stoneNumber = ENV.items.getItemById(name).number();
		for (byte x = 0; x < 32; ++x)
		{
			for (byte y = 0; y < 32; ++y)
			{
				input.setData15(AspectRegistry.BLOCK, new BlockAddress(x, y, z), stoneNumber);
			}
		}
	}
}
