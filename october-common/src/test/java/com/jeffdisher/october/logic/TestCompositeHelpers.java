package com.jeffdisher.october.logic;

import java.util.Set;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.aspects.OrientationAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.ContextBuilder;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestCompositeHelpers
{
	private static Environment ENV;
	private static Block VOID_STONE;
	private static Block VOID_LAMP;
	private static Block PORTAL_KEYSTONE;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		VOID_STONE = ENV.blocks.fromItem(ENV.items.getItemById("op.void_stone"));
		VOID_LAMP = ENV.blocks.fromItem(ENV.items.getItemById("op.void_lamp"));
		PORTAL_KEYSTONE = ENV.blocks.fromItem(ENV.items.getItemById("op.portal_keystone"));
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void voidLamp()
	{
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		AbsoluteLocation centre = cuboid.getCuboidAddress().getBase().getRelative(16, 16, 16);
		AbsoluteLocation onLamp = centre.getRelative(1, 0, 0);
		AbsoluteLocation offLamp = centre.getRelative(-1, 0, 0);
		cuboid.setData15(AspectRegistry.BLOCK, onLamp.getBlockAddress(), VOID_LAMP.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, onLamp.getRelative(0, 0, -1).getBlockAddress(), VOID_STONE.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, offLamp.getBlockAddress(), VOID_LAMP.item().number());
		TickProcessingContext context = ContextBuilder.build()
			.lookups((AbsoluteLocation location) -> {
				return new BlockProxy(location.getBlockAddress(), cuboid);
			}, null)
			.finish()
		;
		MutableBlockProxy onProxy = new MutableBlockProxy(onLamp, cuboid);
		CompositeHelpers.processCornerstoneUpdate(ENV, context, onLamp, onProxy);
		Assert.assertTrue(onProxy.didChange());
		onProxy.writeBack(cuboid);
		byte flags = cuboid.getData7(AspectRegistry.FLAGS, onLamp.getBlockAddress());
		Assert.assertEquals(FlagsAspect.FLAG_ACTIVE, flags);
		Assert.assertEquals(CompositeHelpers.COMPOSITE_CHECK_FREQUENCY, onProxy.periodicDelayMillis);
		
		MutableBlockProxy offProxy = new MutableBlockProxy(offLamp, cuboid);
		CompositeHelpers.processCornerstoneUpdate(ENV, context, offLamp, offProxy);
		Assert.assertFalse(offProxy.didChange());
		Assert.assertEquals(CompositeHelpers.COMPOSITE_CHECK_FREQUENCY, offProxy.periodicDelayMillis);
	}

	@Test
	public void portalFrame()
	{
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		AbsoluteLocation centre = cuboid.getCuboidAddress().getBase().getRelative(16, 16, 16);
		Set<AbsoluteLocation> stoneSet = Set.of(
			centre.getRelative(0, -1, 0)
			, centre.getRelative(0, -2, 0)
			, centre.getRelative(0, -2, 1)
			, centre.getRelative(0, -2, 2)
			, centre.getRelative(0, -2, 3)
			, centre.getRelative(0, -2, 4)
			, centre.getRelative(0, -1, 4)
			, centre.getRelative(0,  0, 4)
			, centre.getRelative(0,  1, 4)
			, centre.getRelative(0,  2, 4)
			, centre.getRelative(0,  2, 3)
			, centre.getRelative(0,  2, 2)
			, centre.getRelative(0,  2, 1)
			, centre.getRelative(0,  2, 0)
			, centre.getRelative(0,  1, 0)
		);
		cuboid.setData15(AspectRegistry.BLOCK, centre.getBlockAddress(), PORTAL_KEYSTONE.item().number());
		cuboid.setData7(AspectRegistry.ORIENTATION, centre.getBlockAddress(), OrientationAspect.directionToByte(OrientationAspect.Direction.EAST));
		for (AbsoluteLocation location : stoneSet)
		{
			cuboid.setData15(AspectRegistry.BLOCK, location.getBlockAddress(), VOID_STONE.item().number());
		}
		
		TickProcessingContext context = ContextBuilder.build()
			.lookups((AbsoluteLocation location) -> {
				return new BlockProxy(location.getBlockAddress(), cuboid);
			}, null)
			.finish()
		;
		MutableBlockProxy onProxy = new MutableBlockProxy(centre, cuboid);
		CompositeHelpers.processCornerstoneUpdate(ENV, context, centre, onProxy);
		Assert.assertTrue(onProxy.didChange());
		onProxy.writeBack(cuboid);
		byte flags = cuboid.getData7(AspectRegistry.FLAGS, centre.getBlockAddress());
		Assert.assertEquals(FlagsAspect.FLAG_ACTIVE, flags);
		Assert.assertEquals(CompositeHelpers.COMPOSITE_CHECK_FREQUENCY, onProxy.periodicDelayMillis);
	}
}
