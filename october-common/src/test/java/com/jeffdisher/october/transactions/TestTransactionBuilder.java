package com.jeffdisher.october.transactions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.logic.CommonMutationSink;
import com.jeffdisher.october.logic.ScheduledMutation;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.MutationBlockOverwriteByEntity;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.ContextBuilder;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.CuboidGenerator;


/**
 * Tests the behaviour of TransactionBuilder and related transaction support.
 */
public class TestTransactionBuilder
{
	private static Environment ENV;
	private static Block STONE;
	@BeforeClass
	public static void setup() throws Throwable
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
	public void placeBlocksNoConflict()
	{
		// Show that we can run a transaction to place blocks in a single transaction, so long as there are no conflicts,
		List<AbsoluteLocation> locations = List.of(new AbsoluteLocation(5, 6, 7)
			, new AbsoluteLocation(8, 9, 40)
		);
		Map<CuboidAddress, CuboidData> cuboids = Map.of(CuboidAddress.fromInt(0, 0, 0), CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR)
			, CuboidAddress.fromInt(0, 0, 1), CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 1), ENV.special.AIR)
		);
		CommonMutationSink sink = new CommonMutationSink(cuboids.keySet());
		
		_ContextBuilder contextBuilder = new _ContextBuilder();
		for (IReadOnlyCuboidData cuboid : cuboids.values())
		{
			contextBuilder.addCuboid(cuboid);
		}
		contextBuilder.setMutationSink(sink);
		TickProcessingContext context = contextBuilder.finish();
		
		TransactionBuilder transactionBuilder = new TransactionBuilder();
		for (AbsoluteLocation location : locations)
		{
			transactionBuilder.addMutation(new MutationBlockOverwriteByEntity(location, STONE, null, 1));
		}
		boolean didStart = transactionBuilder.didStartTransaction(context);
		Assert.assertTrue(didStart);
		List<ScheduledMutation> mutations = sink.takeExportedMutations();
		Assert.assertEquals(2, mutations.size());
		
		// Add an unrelated mutation location.
		contextBuilder.scheduleMutation(new AbsoluteLocation(11, 12, 13));
		// Load in the mutations we are about to run.
		for (ScheduledMutation scheduled : mutations)
		{
			if (0L == scheduled.millisUntilReady())
			{
				contextBuilder.scheduleMutation(scheduled.mutation().getAbsoluteLocation());
			}
		}
		contextBuilder.setMutationSink(null);
		context = contextBuilder.finish();
		
		for (ScheduledMutation scheduled : mutations)
		{
			Assert.assertEquals(0L, scheduled.millisUntilReady());
			IMutationBlock mutation = scheduled.mutation();
			AbsoluteLocation location = mutation.getAbsoluteLocation();
			CuboidData cuboid = cuboids.get(location.getCuboidAddress());
			MutableBlockProxy proxy = new MutableBlockProxy(location, cuboid);
			mutation.applyMutation(context, proxy);
			Assert.assertTrue(proxy.didChange());
			proxy.writeBack(cuboid);
		}
	}

	@Test
	public void placeBlocksWithConflict()
	{
		// Show that conflicting transactions will fail.
		List<AbsoluteLocation> locations1 = List.of(new AbsoluteLocation(5, 6, 7)
			, new AbsoluteLocation(8, 9, 40)
		);
		List<AbsoluteLocation> locations2 = List.of(new AbsoluteLocation(5, 6, 7)
			, new AbsoluteLocation(8, 9, 41)
		);
		Map<CuboidAddress, CuboidData> cuboids = Map.of(CuboidAddress.fromInt(0, 0, 0), CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR)
			, CuboidAddress.fromInt(0, 0, 1), CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 1), ENV.special.AIR)
		);
		CommonMutationSink sink = new CommonMutationSink(cuboids.keySet());
		
		_ContextBuilder contextBuilder = new _ContextBuilder();
		for (IReadOnlyCuboidData cuboid : cuboids.values())
		{
			contextBuilder.addCuboid(cuboid);
		}
		contextBuilder.setMutationSink(sink);
		TickProcessingContext context = contextBuilder.finish();
		
		// Start both transactions.
		TransactionBuilder transactionBuilder = new TransactionBuilder();
		for (AbsoluteLocation location : locations1)
		{
			transactionBuilder.addMutation(new MutationBlockOverwriteByEntity(location, STONE, null, 1));
		}
		boolean didStart = transactionBuilder.didStartTransaction(context);
		Assert.assertTrue(didStart);
		transactionBuilder = new TransactionBuilder();
		for (AbsoluteLocation location : locations2)
		{
			transactionBuilder.addMutation(new MutationBlockOverwriteByEntity(location, STONE, null, 1));
		}
		didStart = transactionBuilder.didStartTransaction(context);
		Assert.assertTrue(didStart);
		List<ScheduledMutation> mutations = sink.takeExportedMutations();
		Assert.assertEquals(4, mutations.size());
		
		// Load in the mutations we are about to run.
		for (ScheduledMutation scheduled : mutations)
		{
			if (0L == scheduled.millisUntilReady())
			{
				contextBuilder.scheduleMutation(scheduled.mutation().getAbsoluteLocation());
			}
		}
		contextBuilder.setMutationSink(null);
		context = contextBuilder.finish();
		
		// Show that none of these do anything since they detect a conflict.
		for (ScheduledMutation scheduled : mutations)
		{
			Assert.assertEquals(0L, scheduled.millisUntilReady());
			IMutationBlock mutation = scheduled.mutation();
			AbsoluteLocation location = mutation.getAbsoluteLocation();
			CuboidData cuboid = cuboids.get(location.getCuboidAddress());
			MutableBlockProxy proxy = new MutableBlockProxy(location, cuboid);
			mutation.applyMutation(context, proxy);
			Assert.assertFalse(proxy.didChange());
		}
	}

	@Test
	public void placeBlocksFailUnload()
	{
		// Show that the transaction will fail if one of the target cuboids unloads.
		List<AbsoluteLocation> locations = List.of(new AbsoluteLocation(5, 6, 7)
			, new AbsoluteLocation(8, 9, 40)
		);
		Map<CuboidAddress, CuboidData> cuboids = Map.of(CuboidAddress.fromInt(0, 0, 0), CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR)
			, CuboidAddress.fromInt(0, 0, 1), CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 1), ENV.special.AIR)
		);
		CommonMutationSink sink = new CommonMutationSink(cuboids.keySet());
		
		_ContextBuilder contextBuilder = new _ContextBuilder();
		for (IReadOnlyCuboidData cuboid : cuboids.values())
		{
			contextBuilder.addCuboid(cuboid);
		}
		contextBuilder.setMutationSink(sink);
		TickProcessingContext context = contextBuilder.finish();
		
		TransactionBuilder transactionBuilder = new TransactionBuilder();
		for (AbsoluteLocation location : locations)
		{
			transactionBuilder.addMutation(new MutationBlockOverwriteByEntity(location, STONE, null, 1));
		}
		boolean didStart = transactionBuilder.didStartTransaction(context);
		Assert.assertTrue(didStart);
		List<ScheduledMutation> mutations = sink.takeExportedMutations();
		Assert.assertEquals(2, mutations.size());
		
		// Create the new context with only 1 cuboid.
		contextBuilder = new _ContextBuilder();
		contextBuilder.addCuboid(cuboids.get(CuboidAddress.fromInt(0, 0, 0)));
		
		// Load in the mutations we are about to run.
		for (ScheduledMutation scheduled : mutations)
		{
			if (0L == scheduled.millisUntilReady())
			{
				contextBuilder.scheduleMutation(scheduled.mutation().getAbsoluteLocation());
			}
		}
		context = contextBuilder.finish();
		
		// Show that these all fail since they see the missing cuboid.
		for (ScheduledMutation scheduled : mutations)
		{
			Assert.assertEquals(0L, scheduled.millisUntilReady());
			IMutationBlock mutation = scheduled.mutation();
			AbsoluteLocation location = mutation.getAbsoluteLocation();
			CuboidData cuboid = cuboids.get(location.getCuboidAddress());
			MutableBlockProxy proxy = new MutableBlockProxy(location, cuboid);
			mutation.applyMutation(context, proxy);
			Assert.assertFalse(proxy.didChange());
		}
	}


	private static class _ContextBuilder
	{
		private final List<IReadOnlyCuboidData> _cuboids = new ArrayList<>();
		private final List<AbsoluteLocation> _mutationLocations = new ArrayList<>();
		private TickProcessingContext.IMutationSink _mutationSink;
		
		public void addCuboid(IReadOnlyCuboidData cuboid)
		{
			_cuboids.add(cuboid);
		}
		public void scheduleMutation(AbsoluteLocation location)
		{
			_mutationLocations.add(location);
		}
		public void setMutationSink(TickProcessingContext.IMutationSink mutationSink)
		{
			_mutationSink = mutationSink;
		}
		public TickProcessingContext finish()
		{
			Map<CuboidAddress, IReadOnlyCuboidData> cuboids = _cuboids.stream()
				.collect(Collectors.toMap((IReadOnlyCuboidData cuboid) -> cuboid.getCuboidAddress(), (IReadOnlyCuboidData cuboid) -> cuboid))
			;
			Map<AbsoluteLocation, Integer> mutationsThisTick = new HashMap<>();
			for (AbsoluteLocation location : _mutationLocations)
			{
				int existing = mutationsThisTick.getOrDefault(location, 0);
				mutationsThisTick.put(location, existing + 1);
			}
			TickProcessingContext.ITransactionSupport transactions = (Collection<AbsoluteLocation> locations, int expectedMutations) -> {
				boolean didMatch = true;
				for (AbsoluteLocation location : locations)
				{
					if (cuboids.containsKey(location.getCuboidAddress()))
					{
						if (expectedMutations != mutationsThisTick.getOrDefault(location, 0))
						{
							didMatch = false;
							break;
						}
					}
					else
					{
						didMatch = false;
						break;
					}
				}
				return didMatch;
			};
			TickProcessingContext.IEventSink eventSink = (EventRecord event) -> {
			};
			return ContextBuilder.build()
				.lookups(ContextBuilder.buildFetcher((AbsoluteLocation location) -> {
					IReadOnlyCuboidData cuboid = cuboids.get(location.getCuboidAddress());
					return (null != cuboid)
						? BlockProxy.load(location.getBlockAddress(), cuboid)
						: null
					;
				}), null, null)
				.transactions(transactions)
				.sinks(_mutationSink, null)
				.eventSink(eventSink)
				.finish()
			;
		}
	}
}
