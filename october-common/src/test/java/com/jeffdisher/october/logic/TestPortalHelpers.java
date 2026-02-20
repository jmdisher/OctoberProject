package com.jeffdisher.october.logic;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.MutationBlockPhase2Multi;
import com.jeffdisher.october.mutations.MutationBlockPlaceMultiBlock;
import com.jeffdisher.october.mutations.MutationBlockReplace;
import com.jeffdisher.october.properties.PropertyRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.ContextBuilder;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestPortalHelpers
{
	private static Environment ENV;
	private static Item ORB_ITEM;
	private static Block VOID_STONE;
	private static Block KEYSTONE;
	private static Block PORTAL_SURFACE;
	@BeforeClass
	public static void setup() throws Throwable
	{
		ENV = Environment.createSharedInstance();
		ORB_ITEM = ENV.items.getItemById("op.portal_orb");
		VOID_STONE = ENV.blocks.fromItem(ENV.items.getItemById("op.portal_stone"));
		KEYSTONE = ENV.blocks.fromItem(ENV.items.getItemById("op.portal_keystone"));
		PORTAL_SURFACE = ENV.blocks.fromItem(ENV.items.getItemById("op.portal_surface"));
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void keystone()
	{
		// Checks that the keystone type check helper works.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		AbsoluteLocation voidStone = cuboid.getCuboidAddress().getBase().getRelative(16, 16, 16);
		AbsoluteLocation keystoneLocation = voidStone.getRelative(1, 0, 0);
		cuboid.setData15(AspectRegistry.BLOCK, voidStone.getBlockAddress(), VOID_STONE.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, keystoneLocation.getBlockAddress(), KEYSTONE.item().number());
		
		MutableBlockProxy proxy = new MutableBlockProxy(voidStone, cuboid);
		Assert.assertFalse(PortalHelpers.isKeystone(proxy));
		proxy = new MutableBlockProxy(keystoneLocation, cuboid);
		Assert.assertTrue(PortalHelpers.isKeystone(proxy));
	}

	@Test
	public void handleActionSurfaceNoChange() throws Throwable
	{
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		NonStackableItem portalOrb = new NonStackableItem(ORB_ITEM, Map.of(PropertyRegistry.LOCATION, cuboidAddress.getBase()));
		AbsoluteLocation keystoneLocation = new AbsoluteLocation(1, 2, 3);
		AbsoluteLocation surfaceBase = keystoneLocation.getRelative(0, 0, 1);
		cuboid.setData15(AspectRegistry.BLOCK, keystoneLocation.getBlockAddress(), KEYSTONE.item().number());
		cuboid.setData7(AspectRegistry.FLAGS, keystoneLocation.getBlockAddress(), FlagsAspect.FLAG_ACTIVE);
		cuboid.setDataSpecial(AspectRegistry.SPECIAL_ITEM_SLOT, keystoneLocation.getBlockAddress(), ItemSlot.fromNonStack(portalOrb));
		cuboid.setData15(AspectRegistry.BLOCK, surfaceBase.getBlockAddress(), PORTAL_SURFACE.item().number());
		
		TickProcessingContext context = ContextBuilder.build()
			.lookups((AbsoluteLocation location) -> {
				return BlockProxy.load(location.getBlockAddress(), cuboid);
			}, null, null)
			.finish()
		;
		
		// This should do nothing since everything is in the right shape.
		MutableBlockProxy proxy = new MutableBlockProxy(keystoneLocation, cuboid);
		PortalHelpers.handlePortalSurface(ENV, context, keystoneLocation, proxy);
	}

	@Test
	public void handleActionCreateSurface() throws Throwable
	{
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		NonStackableItem portalOrb = new NonStackableItem(ORB_ITEM, Map.of(PropertyRegistry.LOCATION, cuboidAddress.getBase()));
		AbsoluteLocation keystoneLocation = new AbsoluteLocation(1, 2, 3);
		cuboid.setData15(AspectRegistry.BLOCK, keystoneLocation.getBlockAddress(), KEYSTONE.item().number());
		cuboid.setData7(AspectRegistry.FLAGS, keystoneLocation.getBlockAddress(), FlagsAspect.FLAG_ACTIVE);
		cuboid.setDataSpecial(AspectRegistry.SPECIAL_ITEM_SLOT, keystoneLocation.getBlockAddress(), ItemSlot.fromNonStack(portalOrb));
		
		Set<AbsoluteLocation> nextLocations = new HashSet<>();
		Set<AbsoluteLocation> futureLocations = new HashSet<>();
		TickProcessingContext context = ContextBuilder.build()
			.lookups((AbsoluteLocation location) -> {
				return BlockProxy.load(location.getBlockAddress(), cuboid);
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
		
		// We should see the 2-phase commits.
		MutableBlockProxy proxy = new MutableBlockProxy(keystoneLocation, cuboid);
		PortalHelpers.handlePortalSurface(ENV, context, keystoneLocation, proxy);
		Assert.assertEquals(9, nextLocations.size());
		Assert.assertEquals(9, futureLocations.size());
	}

	@Test
	public void handleActionDestroySurface() throws Throwable
	{
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		AbsoluteLocation keystoneLocation = new AbsoluteLocation(1, 2, 3);
		AbsoluteLocation surfaceBase = keystoneLocation.getRelative(0, 0, 1);
		cuboid.setData15(AspectRegistry.BLOCK, keystoneLocation.getBlockAddress(), KEYSTONE.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, surfaceBase.getBlockAddress(), PORTAL_SURFACE.item().number());
		
		Set<AbsoluteLocation> nextLocations = new HashSet<>();
		TickProcessingContext context = ContextBuilder.build()
			.lookups((AbsoluteLocation location) -> {
				return BlockProxy.load(location.getBlockAddress(), cuboid);
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
					throw new AssertionError("Not in test");
				}
			}, null)
			.finish()
		;
		
		// We should see the replace calls to break the surface.
		MutableBlockProxy proxy = new MutableBlockProxy(keystoneLocation, cuboid);
		PortalHelpers.handlePortalSurface(ENV, context, keystoneLocation, proxy);
		Assert.assertEquals(9, nextLocations.size());
	}

	@Test
	public void handleUpdateWhenPartiallyUnloaded() throws Throwable
	{
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		AbsoluteLocation keystoneLocation = new AbsoluteLocation(0, 0, 5);
		AbsoluteLocation surfaceBase = keystoneLocation.getRelative(0, 0, 1);
		cuboid.setData15(AspectRegistry.BLOCK, keystoneLocation.getBlockAddress(), KEYSTONE.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, surfaceBase.getBlockAddress(), PORTAL_SURFACE.item().number());
		
		Set<AbsoluteLocation> nextLocations = new HashSet<>();
		TickProcessingContext context = ContextBuilder.build()
			.lookups((AbsoluteLocation location) -> {
				return location.getCuboidAddress().equals(cuboidAddress) ? BlockProxy.load(location.getBlockAddress(), cuboid) : null;
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
					throw new AssertionError("Not in test");
				}
			}, null)
			.finish()
		;
		
		// This call should do nothing since it isn't fully loaded.
		MutableBlockProxy proxy = new MutableBlockProxy(keystoneLocation, cuboid);
		PortalHelpers.handlePortalSurface(ENV, context, keystoneLocation, proxy);
		Assert.assertEquals(0, nextLocations.size());
	}
}
