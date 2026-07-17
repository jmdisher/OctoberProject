package com.jeffdisher.october.block_movement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.transactions.MutationBlockTransactionWrapper;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.ContextBuilder;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.IMutationBlock;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestMovableBlockHelpers
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
	public void pushAir()
	{
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		TickProcessingContext context = _buildNoMoveContext(cuboid);
		AbsoluteLocation pushingBlockLocation = new AbsoluteLocation(1, 1, 1);
		FacingDirection pushDirection = FacingDirection.EAST;
		boolean didPush = MovableBlockHelpers.didSchedulePushTransaction(ENV
			, context
			, pushingBlockLocation
			, pushDirection
		);
		
		// This should do nothing since there is nothing to push.
		Assert.assertFalse(didPush);
	}

	@Test
	public void pullAir()
	{
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		TickProcessingContext context = _buildNoMoveContext(cuboid);
		AbsoluteLocation pullingBlockLocation = new AbsoluteLocation(1, 1, 1);
		FacingDirection faceToPull = FacingDirection.EAST;
		boolean didPull = MovableBlockHelpers.didSchedulePullTransaction(ENV
			, context
			, pullingBlockLocation
			, faceToPull
		);
		
		// This should do nothing since there is nothing to pull.
		Assert.assertFalse(didPull);
	}

	@Test
	public void pushMedium()
	{
		AbsoluteLocation pushingBlockLocation = new AbsoluteLocation(1, 1, 1);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, pushingBlockLocation.getRelative(1, 0, 0).getBlockAddress(), STONE.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, pushingBlockLocation.getRelative(2, 0, 0).getBlockAddress(), STONE.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, pushingBlockLocation.getRelative(3, 0, 0).getBlockAddress(), STONE.item().number());
		_MutationCapture capture = new _MutationCapture();
		TickProcessingContext context = _buildMovingContext(cuboid, capture);
		FacingDirection pushDirection = FacingDirection.EAST;
		boolean didPush = MovableBlockHelpers.didSchedulePushTransaction(ENV
			, context
			, pushingBlockLocation
			, pushDirection
		);
		
		// This is a valid push so make sure that we see it.
		Assert.assertTrue(didPush);
		Assert.assertEquals(4, capture.mutations.size());
		Assert.assertEquals(pushingBlockLocation.getRelative(1, 0, 0), capture.mutations.get(0).getAbsoluteLocation());
		Assert.assertEquals(2, capture.mutations.get(0).test_getMutationCount());
		Assert.assertEquals(pushingBlockLocation.getRelative(2, 0, 0), capture.mutations.get(1).getAbsoluteLocation());
		Assert.assertEquals(2, capture.mutations.get(1).test_getMutationCount());
		Assert.assertEquals(pushingBlockLocation.getRelative(3, 0, 0), capture.mutations.get(2).getAbsoluteLocation());
		Assert.assertEquals(2, capture.mutations.get(2).test_getMutationCount());
		Assert.assertEquals(pushingBlockLocation.getRelative(4, 0, 0), capture.mutations.get(3).getAbsoluteLocation());
		Assert.assertEquals(2, capture.mutations.get(3).test_getMutationCount());
	}

	@Test
	public void pullBlock()
	{
		AbsoluteLocation pushingBlockLocation = new AbsoluteLocation(1, 1, 1);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, pushingBlockLocation.getRelative(2, 0, 0).getBlockAddress(), STONE.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, pushingBlockLocation.getRelative(3, 0, 0).getBlockAddress(), STONE.item().number());
		_MutationCapture capture = new _MutationCapture();
		TickProcessingContext context = _buildMovingContext(cuboid, capture);
		AbsoluteLocation pullingBlockLocation = new AbsoluteLocation(1, 1, 1);
		FacingDirection faceToPull = FacingDirection.EAST;
		boolean didPull = MovableBlockHelpers.didSchedulePullTransaction(ENV
			, context
			, pullingBlockLocation
			, faceToPull
		);
		
		// This is a valid pull so make sure that we see it.
		Assert.assertTrue(didPull);
		Assert.assertEquals(2, capture.mutations.size());
		Assert.assertEquals(pushingBlockLocation.getRelative(1, 0, 0), capture.mutations.get(0).getAbsoluteLocation());
		Assert.assertEquals(1, capture.mutations.get(0).test_getMutationCount());
		Assert.assertEquals(pushingBlockLocation.getRelative(2, 0, 0), capture.mutations.get(1).getAbsoluteLocation());
		Assert.assertEquals(1, capture.mutations.get(1).test_getMutationCount());
	}

	@Test
	public void pushTooMany()
	{
		AbsoluteLocation pushingBlockLocation = new AbsoluteLocation(1, 1, 1);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		for (int i = 0; i < (MovableBlockHelpers.BLOCK_PUSH_MAX + 1); ++i)
		{
			cuboid.setData15(AspectRegistry.BLOCK, pushingBlockLocation.getRelative(1 + i, 0, 0).getBlockAddress(), STONE.item().number());
		}
		TickProcessingContext context = _buildNoMoveContext(cuboid);
		FacingDirection pushDirection = FacingDirection.EAST;
		boolean didPush = MovableBlockHelpers.didSchedulePushTransaction(ENV
			, context
			, pushingBlockLocation
			, pushDirection
		);
		
		// This should not push.
		Assert.assertFalse(didPush);
	}

	@Test
	public void pullBlocked()
	{
		AbsoluteLocation pushingBlockLocation = new AbsoluteLocation(1, 1, 1);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, pushingBlockLocation.getRelative(1, 0, 0).getBlockAddress(), STONE.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, pushingBlockLocation.getRelative(2, 0, 0).getBlockAddress(), STONE.item().number());
		TickProcessingContext context = _buildNoMoveContext(cuboid);
		AbsoluteLocation pullingBlockLocation = new AbsoluteLocation(1, 1, 1);
		FacingDirection faceToPull = FacingDirection.EAST;
		boolean didPull = MovableBlockHelpers.didSchedulePullTransaction(ENV
			, context
			, pullingBlockLocation
			, faceToPull
		);
		
		// This should not pull.
		Assert.assertFalse(didPull);
	}


	private static TickProcessingContext _buildNoMoveContext(CuboidData cuboid)
	{
		return _buildCommonContext(cuboid, null);
	}

	private static TickProcessingContext _buildMovingContext(CuboidData cuboid, TickProcessingContext.IMutationSink mutationSink)
	{
		return _buildCommonContext(cuboid, mutationSink);
	}

	private static TickProcessingContext _buildCommonContext(CuboidData cuboid, TickProcessingContext.IMutationSink mutationSink)
	{
		TickProcessingContext.ITransactionSupport transactions = (null != mutationSink)
			? (Collection<AbsoluteLocation> locations, int expectedMutations) -> true
			: null
		;
		TickProcessingContext context = ContextBuilder.build()
			.lookups(new TickProcessingContext.IBlockFetcher() {
				@Override
				public BlockProxy readBlock(AbsoluteLocation location)
				{
					throw new AssertionError("Not in test");
				}
				@Override
				public Map<AbsoluteLocation, BlockProxy> readBlockBatch(Collection<AbsoluteLocation> locations)
				{
					Map<AbsoluteLocation, BlockProxy> map = new HashMap<>();
					for (AbsoluteLocation location : locations)
					{
						BlockProxy proxy = BlockProxy.load(location.getBlockAddress(), cuboid);
						map.put(location, proxy);
					}
					return map;
				}
			}, null, null)
			.transactions(transactions)
			.sinks(mutationSink, null)
			.finish()
		;
		return context;
	}


	private static class _MutationCapture implements TickProcessingContext.IMutationSink
	{
		public List<MutationBlockTransactionWrapper> mutations = new ArrayList<>();
		@Override
		public boolean next(IMutationBlock mutation)
		{
			this.mutations.add((MutationBlockTransactionWrapper) mutation);
			return true;
		}
		@Override
		public boolean future(IMutationBlock mutation, long millisToDelay)
		{
			throw new AssertionError("Not in test");
		}
	}
}
