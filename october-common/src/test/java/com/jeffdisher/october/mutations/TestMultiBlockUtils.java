package com.jeffdisher.october.mutations;

import java.util.HashSet;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.OrientationAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.ContextBuilder;
import com.jeffdisher.october.types.CuboidAddress;
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
		
		OrientationAspect.Direction direction = OrientationAspect.Direction.NORTH;
		cuboid.setData15(AspectRegistry.BLOCK, base.getBlockAddress(), DOOR.item().number());
		cuboid.setData7(AspectRegistry.ORIENTATION, base.getBlockAddress(), OrientationAspect.directionToByte(direction));
		for (AbsoluteLocation extension : ENV.multiBlocks.getExtensions(DOOR, base, direction))
		{
			cuboid.setData15(AspectRegistry.BLOCK, extension.getBlockAddress(), DOOR.item().number());
			cuboid.setDataSpecial(AspectRegistry.MULTI_BLOCK_ROOT, extension.getBlockAddress(), base);
		}
		
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation location) -> {
					return new BlockProxy(location.getBlockAddress(), cuboid);
				}, null)
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
				}, null)
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
		
		OrientationAspect.Direction direction = OrientationAspect.Direction.NORTH;
		cuboid.setData15(AspectRegistry.BLOCK, base.getBlockAddress(), DOOR.item().number());
		cuboid.setData7(AspectRegistry.ORIENTATION, base.getBlockAddress(), OrientationAspect.directionToByte(direction));
		for (AbsoluteLocation extension : ENV.multiBlocks.getExtensions(DOOR, base, direction))
		{
			cuboid.setData15(AspectRegistry.BLOCK, extension.getBlockAddress(), DOOR.item().number());
			cuboid.setDataSpecial(AspectRegistry.MULTI_BLOCK_ROOT, extension.getBlockAddress(), base);
		}
		
		Set<AbsoluteLocation> locations = new HashSet<>();
		TickProcessingContext context = ContextBuilder.build()
				.sinks(new TickProcessingContext.IMutationSink() {
					@Override
					public void next(IMutationBlock mutation)
					{
						boolean didAdd = locations.add(mutation.getAbsoluteLocation());
						Assert.assertTrue(didAdd);
					}
					@Override
					public void future(IMutationBlock mutation, long millisToDelay)
					{
						Assert.fail();
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
		
		OrientationAspect.Direction direction = OrientationAspect.Direction.NORTH;
		cuboid.setData15(AspectRegistry.BLOCK, base.getBlockAddress(), DOOR.item().number());
		cuboid.setData7(AspectRegistry.ORIENTATION, base.getBlockAddress(), OrientationAspect.directionToByte(direction));
		for (AbsoluteLocation extension : ENV.multiBlocks.getExtensions(DOOR, base, direction))
		{
			cuboid.setData15(AspectRegistry.BLOCK, extension.getBlockAddress(), DOOR.item().number());
			cuboid.setDataSpecial(AspectRegistry.MULTI_BLOCK_ROOT, extension.getBlockAddress(), base);
		}
		
		Assert.assertFalse(MultiBlockUtils.isMultiBlockExtension(ENV, new BlockProxy(base.getBlockAddress(), cuboid)));
		Assert.assertTrue(MultiBlockUtils.isMultiBlockExtension(ENV, new BlockProxy(base.getRelative(0, 0, 1).getBlockAddress(), cuboid)));
	}
}
