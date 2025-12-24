package com.jeffdisher.october.mutations;

import java.util.HashSet;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.ContextBuilder;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestMultiBlockUtils
{
	private static Environment ENV;
	private static Block DOOR;
	private static Block STONE;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		DOOR = ENV.blocks.fromItem(ENV.items.getItemById("op.double_door_base"));
		STONE = ENV.blocks.fromItem(ENV.items.getItemById("op.stone"));
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void lookupMulti() throws Throwable
	{
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		AbsoluteLocation base = new AbsoluteLocation(1, 2, 3);
		
		FacingDirection direction = FacingDirection.NORTH;
		cuboid.setData15(AspectRegistry.BLOCK, base.getBlockAddress(), DOOR.item().number());
		cuboid.setData7(AspectRegistry.ORIENTATION, base.getBlockAddress(), FacingDirection.directionToByte(direction));
		for (AbsoluteLocation extension : ENV.multiBlocks.getExtensions(DOOR, base, direction))
		{
			cuboid.setData15(AspectRegistry.BLOCK, extension.getBlockAddress(), DOOR.item().number());
			cuboid.setDataSpecial(AspectRegistry.MULTI_BLOCK_ROOT, extension.getBlockAddress(), base);
		}
		
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation location) -> {
					return new BlockProxy(location.getBlockAddress(), cuboid);
				}, null, null)
				.finish()
		;
		
		MultiBlockUtils.Lookup lookup = MultiBlockUtils.getLoadedRoot(ENV, context, base);
		Assert.assertEquals(base, lookup.rootLocation());
		Assert.assertNotNull(lookup.rootProxy());
		Assert.assertEquals(3, lookup.extensions().size());
		
		lookup = MultiBlockUtils.getLoadedRoot(ENV, context, base.getRelative(0, 0, 1));
		Assert.assertEquals(base, lookup.rootLocation());
		Assert.assertNotNull(lookup.rootProxy());
		Assert.assertEquals(3, lookup.extensions().size());
	}

	@Test
	public void lookupSingle() throws Throwable
	{
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		AbsoluteLocation base = new AbsoluteLocation(1, 2, 3);
		
		cuboid.setData15(AspectRegistry.BLOCK, base.getBlockAddress(), STONE.item().number());
		
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation location) -> {
					return new BlockProxy(location.getBlockAddress(), cuboid);
				}, null, null)
				.finish()
		;
		
		MultiBlockUtils.Lookup lookup = MultiBlockUtils.getLoadedRoot(ENV, context, base);
		Assert.assertEquals(base, lookup.rootLocation());
		Assert.assertNotNull(lookup.rootProxy());
		Assert.assertEquals(0, lookup.extensions().size());
	}

	@Test
	public void callbacks() throws Throwable
	{
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		AbsoluteLocation base = new AbsoluteLocation(1, 2, 3);
		
		FacingDirection direction = FacingDirection.NORTH;
		cuboid.setData15(AspectRegistry.BLOCK, base.getBlockAddress(), DOOR.item().number());
		cuboid.setData7(AspectRegistry.ORIENTATION, base.getBlockAddress(), FacingDirection.directionToByte(direction));
		for (AbsoluteLocation extension : ENV.multiBlocks.getExtensions(DOOR, base, direction))
		{
			cuboid.setData15(AspectRegistry.BLOCK, extension.getBlockAddress(), DOOR.item().number());
			cuboid.setDataSpecial(AspectRegistry.MULTI_BLOCK_ROOT, extension.getBlockAddress(), base);
		}
		
		Set<AbsoluteLocation> locations = new HashSet<>();
		TickProcessingContext context = ContextBuilder.build()
				.sinks(new TickProcessingContext.IMutationSink() {
					@Override
					public boolean next(IMutationBlock mutation)
					{
						boolean didAdd = locations.add(mutation.getAbsoluteLocation());
						Assert.assertTrue(didAdd);
						return true;
					}
					@Override
					public boolean future(IMutationBlock mutation, long millisToDelay)
					{
						throw new AssertionError("Not expected in test");
					}
				}, null)
				.finish()
		;
		
		MultiBlockUtils.Lookup lookup = new MultiBlockUtils.Lookup(base, new BlockProxy(base.getBlockAddress(), cuboid), ENV.multiBlocks.getExtensions(DOOR, base, direction));
		MultiBlockUtils.sendMutationToAll(context, (AbsoluteLocation location) -> {
			return new MutationBlockReplace(location, STONE, DOOR);
		}, lookup);
		Assert.assertEquals(4, locations.size());
	}

	@Test
	public void multiCheck() throws Throwable
	{
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		AbsoluteLocation base = new AbsoluteLocation(1, 2, 3);
		
		FacingDirection direction = FacingDirection.NORTH;
		cuboid.setData15(AspectRegistry.BLOCK, base.getBlockAddress(), DOOR.item().number());
		cuboid.setData7(AspectRegistry.ORIENTATION, base.getBlockAddress(), FacingDirection.directionToByte(direction));
		for (AbsoluteLocation extension : ENV.multiBlocks.getExtensions(DOOR, base, direction))
		{
			cuboid.setData15(AspectRegistry.BLOCK, extension.getBlockAddress(), DOOR.item().number());
			cuboid.setDataSpecial(AspectRegistry.MULTI_BLOCK_ROOT, extension.getBlockAddress(), base);
		}
		
		Assert.assertFalse(MultiBlockUtils.isMultiBlockExtension(ENV, new BlockProxy(base.getBlockAddress(), cuboid)));
		Assert.assertTrue(MultiBlockUtils.isMultiBlockExtension(ENV, new BlockProxy(base.getRelative(0, 0, 1).getBlockAddress(), cuboid)));
	}

	@Test
	public void placeIdiom() throws Throwable
	{
		// Tests that the 2-phase multi-block placement idioms.
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		AbsoluteLocation base = new AbsoluteLocation(1, 2, 3);
		FacingDirection direction = FacingDirection.NORTH;
		
		Set<AbsoluteLocation> nextLocations = new HashSet<>();
		Set<AbsoluteLocation> futureLocations = new HashSet<>();
		TickProcessingContext context = ContextBuilder.build()
			.lookups((AbsoluteLocation location) -> {
				return new BlockProxy(location.getBlockAddress(), cuboid);
			}, null, null)
			.sinks(new TickProcessingContext.IMutationSink() {
				@Override
				public boolean next(IMutationBlock mutation)
				{
					Assert.assertTrue(mutation instanceof MutationBlockPlaceMultiBlock);
					boolean didAdd = nextLocations.add(mutation.getAbsoluteLocation());
					Assert.assertTrue(didAdd);
					return true;
				}
				@Override
				public boolean future(IMutationBlock mutation, long millisToDelay)
				{
					Assert.assertTrue(mutation instanceof MutationBlockPhase2Multi);
					Assert.assertEquals(ContextBuilder.DEFAULT_MILLIS_PER_TICK, millisToDelay);
					boolean didAdd = futureLocations.add(mutation.getAbsoluteLocation());
					Assert.assertTrue(didAdd);
					return true;
				}
			}, null)
			.finish()
		;
		
		MultiBlockUtils.send2PhaseMultiBlock(ENV, context, DOOR, base, direction, 0);
		Assert.assertEquals(4, nextLocations.size());
		Assert.assertEquals(4, futureLocations.size());
		Set<AbsoluteLocation> intersection = new HashSet<>(nextLocations);
		intersection.removeAll(futureLocations);
		Assert.assertEquals(0, intersection.size());
	}

	@Test
	public void replaceIdiom() throws Throwable
	{
		// Tests that the multi-block replacement idiom works.
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		AbsoluteLocation base = new AbsoluteLocation(1, 2, 3);
		
		FacingDirection direction = FacingDirection.NORTH;
		cuboid.setData15(AspectRegistry.BLOCK, base.getBlockAddress(), DOOR.item().number());
		cuboid.setData7(AspectRegistry.ORIENTATION, base.getBlockAddress(), FacingDirection.directionToByte(direction));
		for (AbsoluteLocation extension : ENV.multiBlocks.getExtensions(DOOR, base, direction))
		{
			cuboid.setData15(AspectRegistry.BLOCK, extension.getBlockAddress(), DOOR.item().number());
			cuboid.setDataSpecial(AspectRegistry.MULTI_BLOCK_ROOT, extension.getBlockAddress(), base);
		}
		
		Set<AbsoluteLocation> nextLocations = new HashSet<>();
		TickProcessingContext context = ContextBuilder.build()
			.lookups((AbsoluteLocation location) -> {
				return new BlockProxy(location.getBlockAddress(), cuboid);
			}, null, null)
			.sinks(new TickProcessingContext.IMutationSink() {
				@Override
				public boolean next(IMutationBlock mutation)
				{
					Assert.assertTrue(mutation instanceof MutationBlockReplace);
					boolean didAdd = nextLocations.add(mutation.getAbsoluteLocation());
					Assert.assertTrue(didAdd);
					return true;
				}
				@Override
				public boolean future(IMutationBlock mutation, long millisToDelay)
				{
					throw new AssertionError("Not expected in test");
				}
			}, null)
			.finish()
		;
		
		MultiBlockUtils.replaceMultiBlock(ENV, context, base, DOOR, ENV.special.AIR);
		Assert.assertEquals(4, nextLocations.size());
	}
}
