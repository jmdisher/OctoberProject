package com.jeffdisher.october.ticks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.logic.ScheduledMutation;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.MutationBlockApplyGravity;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.CuboidColumnAddress;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestCommonTransactionSupport
{
	private static Environment ENV;
	@BeforeClass
	public static void setup() throws Throwable
	{
		ENV = Environment.createSharedInstance();
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void nothingRunningAllLoaded()
	{
		TickMaterials materials = _createMaterials(List.of(), Map.of(), Set.of());
		CommonTransactionSupport support = new CommonTransactionSupport(materials);
		Assert.assertTrue(support.checkScheduledMutationCount(List.of(new AbsoluteLocation(4, 5, 6), new AbsoluteLocation(7, 8, 40)), 0));
		Assert.assertFalse(support.checkScheduledMutationCount(List.of(new AbsoluteLocation(4, 5, 6), new AbsoluteLocation(7, 8, 40)), 1));
	}

	@Test
	public void runningTransactionFails()
	{
		TickMaterials materials = _createMaterials(List.of(new MutationBlockApplyGravity(new AbsoluteLocation(4, 5, 6)))
			, Map.of()
			, Set.of()
		);
		CommonTransactionSupport support = new CommonTransactionSupport(materials);
		Assert.assertFalse(support.checkScheduledMutationCount(List.of(new AbsoluteLocation(4, 5, 6), new AbsoluteLocation(7, 8, 40)), 0));
		Assert.assertFalse(support.checkScheduledMutationCount(List.of(new AbsoluteLocation(4, 5, 6), new AbsoluteLocation(7, 8, 40)), 1));
	}

	@Test
	public void periodicTransactionFails()
	{
		AbsoluteLocation periodicLocation = new AbsoluteLocation(4, 5, 6);
		TickMaterials materials = _createMaterials(List.of()
			, Map.of(periodicLocation.getCuboidAddress(), Map.of(periodicLocation.getBlockAddress(), 0L))
			, Set.of()
		);
		CommonTransactionSupport support = new CommonTransactionSupport(materials);
		Assert.assertFalse(support.checkScheduledMutationCount(List.of(new AbsoluteLocation(4, 5, 6), new AbsoluteLocation(7, 8, 40)), 0));
		Assert.assertFalse(support.checkScheduledMutationCount(List.of(new AbsoluteLocation(4, 5, 6), new AbsoluteLocation(7, 8, 40)), 1));
	}

	@Test
	public void expectedSingleTransaction()
	{
		TickMaterials materials = _createMaterials(List.of(new MutationBlockApplyGravity(new AbsoluteLocation(4, 5, 6)), new MutationBlockApplyGravity(new AbsoluteLocation(7, 8, 40)))
			, Map.of()
			, Set.of()
		);
		CommonTransactionSupport support = new CommonTransactionSupport(materials);
		Assert.assertFalse(support.checkScheduledMutationCount(List.of(new AbsoluteLocation(4, 5, 6), new AbsoluteLocation(7, 8, 40)), 0));
		Assert.assertTrue(support.checkScheduledMutationCount(List.of(new AbsoluteLocation(4, 5, 6), new AbsoluteLocation(7, 8, 40)), 1));
	}

	@Test
	public void expectedSinglePeriodic()
	{
		AbsoluteLocation periodicLocation0 = new AbsoluteLocation(4, 5, 6);
		AbsoluteLocation periodicLocation1 = new AbsoluteLocation(7, 8, 40);
		TickMaterials materials = _createMaterials(List.of()
			, Map.of(periodicLocation0.getCuboidAddress(), Map.of(periodicLocation0.getBlockAddress(), 0L)
				, periodicLocation1.getCuboidAddress(), Map.of(periodicLocation1.getBlockAddress(), 0L)
			)
			, Set.of()
		);
		CommonTransactionSupport support = new CommonTransactionSupport(materials);
		Assert.assertFalse(support.checkScheduledMutationCount(List.of(new AbsoluteLocation(4, 5, 6), new AbsoluteLocation(7, 8, 40)), 0));
		Assert.assertTrue(support.checkScheduledMutationCount(List.of(new AbsoluteLocation(4, 5, 6), new AbsoluteLocation(7, 8, 40)), 1));
	}

	@Test
	public void unloadedBlockFails()
	{
		TickMaterials materials = _createMaterials(List.of(), Map.of(), Set.of());
		CommonTransactionSupport support = new CommonTransactionSupport(materials);
		Assert.assertFalse(support.checkScheduledMutationCount(List.of(new AbsoluteLocation(4, 5, 6), new AbsoluteLocation(7, 40, 40)), 0));
	}

	@Test
	public void failsOnBlockUpdate()
	{
		TickMaterials materials = _createMaterials(List.of(), Map.of(), Set.of(new AbsoluteLocation(4, 5, 7)));
		CommonTransactionSupport support = new CommonTransactionSupport(materials);
		Assert.assertFalse(support.checkScheduledMutationCount(List.of(new AbsoluteLocation(4, 5, 6), new AbsoluteLocation(7, 8, 40)), 0));
		Assert.assertFalse(support.checkScheduledMutationCount(List.of(new AbsoluteLocation(4, 5, 6), new AbsoluteLocation(7, 8, 40)), 1));
	}


	private static TickMaterials _createMaterials(List<IMutationBlock> mutations
		, Map<CuboidAddress, Map<BlockAddress, Long>> periodic
		, Set<AbsoluteLocation> previouslyUpdatedLocations
	)
	{
		CuboidAddress address0 = CuboidAddress.fromInt(0, 0, 0);
		CuboidAddress address1 = CuboidAddress.fromInt(0, 0, 1);
		CuboidData cuboid0 = CuboidGenerator.createFilledCuboid(address0, ENV.special.AIR);
		CuboidData cuboid1 = CuboidGenerator.createFilledCuboid(address1, ENV.special.AIR);
		CuboidColumnAddress column = address0.getColumn();
		
		Map<CuboidAddress, List<ScheduledMutation>> mutationMap = new HashMap<>();
		for (IMutationBlock mutation : mutations)
		{
			CuboidAddress address = mutation.getAbsoluteLocation().getCuboidAddress();
			List<ScheduledMutation> list = mutationMap.get(address);
			if (null == list)
			{
				list = new ArrayList<>();
				mutationMap.put(address, list);
			}
			list.add(new ScheduledMutation(mutation, 0L));
		}
		
		TickInput.CuboidInput cuboidInput0 = new TickInput.CuboidInput(cuboid0, null, mutationMap.getOrDefault(address0, List.of()), periodic.getOrDefault(address0, Map.of()), null, null, null);
		TickInput.CuboidInput cuboidInput1 = new TickInput.CuboidInput(cuboid1, null, mutationMap.getOrDefault(address1, List.of()), periodic.getOrDefault(address1, Map.of()), null, null, null);
		TickInput.ColumnInput columnInput = new TickInput.ColumnInput(column, Map.of(), List.of(cuboidInput0, cuboidInput1), 0);
		TickInput tickInput = new TickInput(List.of(columnInput), List.of());
		
		Map<CuboidAddress, IReadOnlyCuboidData> completedCuboids = Map.of(address0, cuboid0
			, address1, cuboid1
		);
		return new TickMaterials(0L
			, completedCuboids
			, Map.of()
			, Map.of()
			, Map.of()
			, Map.of()
			, Map.of()
			, List.of()
			, Map.of()
			, Map.of()
			, Map.of()
			, Set.of()
			
			, previouslyUpdatedLocations
			
			, EntityCollection.emptyCollection()
			, tickInput
			
			, 0L
			, 0L
			, 0L
			, 0L
			, 0L
		);
	}
}
