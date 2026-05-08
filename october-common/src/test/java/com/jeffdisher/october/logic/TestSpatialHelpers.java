package com.jeffdisher.october.logic;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
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
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.LazyLocationCache;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.CuboidGenerator;
import com.jeffdisher.october.utils.Encoding;


public class TestSpatialHelpers
{
	private static final EntityVolume VOLUME = new EntityVolume(1.8f, 0.5f);
	private static Environment ENV;
	private static Block STONE;
	private static EntityType COW;
	@BeforeClass
	public static void setup() throws Throwable
	{
		ENV = Environment.createSharedInstance();
		STONE = ENV.blocks.fromItem(ENV.items.getItemById("op.stone"));
		COW = ENV.creatures.getTypeById("op.cow");
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void canExistInAir()
	{
		// Just ask if they can exist when only air is present.
		EntityLocation location = new EntityLocation(0.0f, 0.0f, 0.0f);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		TickProcessingContext.IBlockFetcher blockTypeReader = ContextBuilder.buildFetcher((AbsoluteLocation l) -> BlockProxy.load(l.getBlockAddress(), cuboid));
		ViscosityReader reader = new ViscosityReader(ENV, blockTypeReader);
		boolean canExist = SpatialHelpers.canExistInLocation(reader, location, VOLUME);
		Assert.assertTrue(canExist);
	}

	@Test
	public void cantExistInStone()
	{
		// Just ask if they can exist when only stone is present.
		EntityLocation location = new EntityLocation(0.0f, 0.0f, 0.0f);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), STONE);
		TickProcessingContext.IBlockFetcher blockTypeReader = ContextBuilder.buildFetcher((AbsoluteLocation l) -> BlockProxy.load(l.getBlockAddress(), cuboid));
		ViscosityReader reader = new ViscosityReader(ENV, blockTypeReader);
		boolean canExist = SpatialHelpers.canExistInLocation(reader, location, VOLUME);
		Assert.assertFalse(canExist);
	}

	@Test
	public void cantStandInAir()
	{
		// Just ask if they can stand when only air is present (as this doesn't check the blocks they occupy).
		EntityLocation location = new EntityLocation(0.0f, 0.0f, 0.0f);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		TickProcessingContext.IBlockFetcher blockTypeReader = ContextBuilder.buildFetcher((AbsoluteLocation l) -> BlockProxy.load(l.getBlockAddress(), cuboid));
		ViscosityReader reader = new ViscosityReader(ENV, blockTypeReader);
		boolean isStanding = SpatialHelpers.isStandingOnGround(reader, location, VOLUME);
		Assert.assertFalse(isStanding);
	}

	@Test
	public void canStandInStone()
	{
		// Just ask if they can stand when only stone is present (as this doesn't check the blocks they occupy).
		EntityLocation location = new EntityLocation(0.0f, 0.0f, 0.0f);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), STONE);
		TickProcessingContext.IBlockFetcher blockTypeReader = ContextBuilder.buildFetcher((AbsoluteLocation l) -> BlockProxy.load(l.getBlockAddress(), cuboid));
		ViscosityReader reader = new ViscosityReader(ENV, blockTypeReader);
		boolean isStanding = SpatialHelpers.isStandingOnGround(reader, location, VOLUME);
		Assert.assertTrue(isStanding);
	}

	@Test
	public void entityEyeDistances()
	{
		EntityLocation location1 = new EntityLocation(1.0f, -1.0f, 12.0f);
		MutableEntity entity1 = MutableEntity.createWithLocation(1, location1, location1);
		EntityLocation location2 = new EntityLocation(1.8f, -1.8f, 12.6f);
		MinimalEntity entity2 = new MinimalEntity(2, COW, location2);
		AbsoluteLocation block1 = new AbsoluteLocation(-3, 2, -1);
		
		EntityLocation sourceEyeLocation = SpatialHelpers.getEyeLocation(entity1.newLocation, ENV.creatures.PLAYER.volume());
		float entityDistance = SpatialHelpers.distanceFromLocationToVolume(sourceEyeLocation, entity2.location(), entity2.type().volume());
		Assert.assertEquals(0.6f, entityDistance, 0.01f);
		float blockDistanceMutable = SpatialHelpers.distanceFromLocationToBlockSurface(sourceEyeLocation, block1);
		Assert.assertEquals(14.18f, blockDistanceMutable, 0.01f);
		float blockDistancePlayer = SpatialHelpers.distanceFromLocationToBlockSurface(sourceEyeLocation, block1);
		Assert.assertEquals(14.18f, blockDistancePlayer, 0.01f);
	}

	@Test
	public void sourceToRegionDistance()
	{
		EntityLocation location1 = new EntityLocation(1.0f, -1.0f, 12.0f);
		EntityLocation base1 = location1;
		EntityVolume volume1 = ENV.creatures.PLAYER.volume();
		EntityLocation location2 = new EntityLocation(1.8f, -1.8f, 12.6f);
		EntityLocation base2 = location2;
		EntityVolume volume2 = COW.volume();
		
		float distance1 = SpatialHelpers.distanceFromLocationToVolume(location1, base2, volume2);
		float distance2 = SpatialHelpers.distanceFromLocationToVolume(location2, base1, volume1);
		Assert.assertEquals(1.00f, distance1, 0.01f);
		Assert.assertEquals(0.89f, distance2, 0.01f);
	}

	@Test
	public void groundCollision()
	{
		CuboidData stoneCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), STONE);
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		TickProcessingContext.IBlockFetcher blockTypeReader = ContextBuilder.buildFetcher((AbsoluteLocation l) -> {
			BlockProxy proxy;
			if (((1 == l.x()) && (1 == l.y())) || (l.z() >= 2))
			{
				proxy = BlockProxy.load(l.getBlockAddress(), airCuboid);
			}
			else
			{
				proxy = BlockProxy.load(l.getBlockAddress(), stoneCuboid);
			}
			return proxy;
		});
		ViscosityReader reader = new ViscosityReader(ENV, blockTypeReader);
		
		EntityLocation onLedge = new EntityLocation(1.6f, 1.6f, 2.0f);
		EntityLocation offLedge = new EntityLocation(1.59f, 1.59f, 2.0f);
		EntityVolume volume = new EntityVolume(0.8f, 0.4f);
		
		// First, verify that the interactive helper gives the expected result.
		EntityMovementHelpers.interactiveEntityMove(onLedge, volume, new EntityLocation(0.0f, 0.0f, -1.0f), new _EndpointHelper(onLedge, true));
		EntityMovementHelpers.interactiveEntityMove(offLedge, volume, new EntityLocation(0.0f, 0.0f, -1.0f), new _EndpointHelper(new EntityLocation(1.59f, 1.59f, 1.0f), false));
		
		// Now, verify that the spatial helper is consistent.
		Assert.assertTrue(SpatialHelpers.isStandingOnGround(reader, onLedge, volume));
		Assert.assertFalse(SpatialHelpers.isStandingOnGround(reader, offLedge, volume));
	}

	@Test
	public void stuckInBlocks()
	{
		// Show how these helpers respond when we are stuck in a block.
		EntityLocation location = new EntityLocation(5.1f, -6.4f, 7.2f);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, -1, 0), STONE);
		TickProcessingContext.IBlockFetcher blockTypeReader = ContextBuilder.buildFetcher((AbsoluteLocation l) -> BlockProxy.load(l.getBlockAddress(), cuboid));
		ViscosityReader reader = new ViscosityReader(ENV, blockTypeReader);
		
		// We are considered standing on ground, since we can't move down.
		Assert.assertTrue(SpatialHelpers.isStandingOnGround(reader, location, VOLUME));
		
		// We can't exist in this location since it intersects with solid blocks.
		Assert.assertFalse(SpatialHelpers.canExistInLocation(reader, location, VOLUME));
	}

	@Test
	public void floatFraction()
	{
		Assert.assertEquals(0.4f, SpatialHelpers.getPositiveFractionalComponent(1.4f), 0.01f);
		Assert.assertEquals(0.8f, SpatialHelpers.getPositiveFractionalComponent(10.8f), 0.01f);
		Assert.assertEquals(0.8f, SpatialHelpers.getPositiveFractionalComponent(-1.2f), 0.01f);
		Assert.assertEquals(0.6f, SpatialHelpers.getPositiveFractionalComponent(-10.4f), 0.01f);
	}

	@Test
	public void centreOfRegion()
	{
		EntityLocation base = new EntityLocation(5.1f, -6.4f, 7.2f);
		EntityVolume volume = new EntityVolume(0.5f, 0.4f);
		
		EntityLocation centre = SpatialHelpers.getCentreOfRegion(base, volume);
		Assert.assertEquals(new EntityLocation(5.3f, -6.2f, 7.45f), centre);
	}

	@Test
	public void distanceFromPlayerToEntity()
	{
		// Test these helpers as one is mostly just a convenience on the other.
		EntityLocation targetLocation = new EntityLocation(1.2f, -2.3f, 3.4f);
		CreatureEntity creature = new CreatureEntity(-1
			, COW
			, targetLocation
			, new EntityLocation(0.0f, 0.0f, 0.0f)
			, (byte)0
			, (byte)0
			, (byte)50
			, (byte)50
			, null
			, null
		);
		MinimalEntity target = MinimalEntity.fromCreature(creature);
		
		EntityLocation playerBase = new EntityLocation(5.1f, -6.4f, 7.2f);
		EntityLocation sourceEyeLocation = SpatialHelpers.getEyeLocation(playerBase, ENV.creatures.PLAYER.volume());
		float distanceEntity = SpatialHelpers.distanceFromLocationToVolume(sourceEyeLocation, target.location(), target.type().volume());
		float distanceManual = SpatialHelpers.distanceFromLocationToVolume(sourceEyeLocation, targetLocation, COW.volume());
		
		Assert.assertEquals(6.58f, distanceEntity, 0.01f);
		Assert.assertEquals(6.58f, distanceManual, 0.01f);
	}

	@Test
	public void facingVectors()
	{
		Assert.assertEquals(new EntityLocation(0.0f, 1.0f, 0.0f), SpatialHelpers.getUnitFacingVector((byte)0, (byte)0));
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 1.0f), SpatialHelpers.getUnitFacingVector((byte)0, (byte)64));
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -1.0f), SpatialHelpers.getUnitFacingVector((byte)0, (byte)-64));
		Assert.assertEquals(new EntityLocation(0.0f, -1.0f, 0.0f), SpatialHelpers.getUnitFacingVector((byte)-128, (byte)0));
		Assert.assertEquals(new EntityLocation(-1.0f, 0.0f, 0.0f), SpatialHelpers.getUnitFacingVector((byte)64, (byte)0));
		Assert.assertEquals(new EntityLocation(0.71f, 0.71f, 0.0f), SpatialHelpers.getUnitFacingVector((byte)-32, (byte)0));
		Assert.assertEquals(new EntityLocation(0.5f, 0.5f, 0.71f), SpatialHelpers.getUnitFacingVector((byte)-32, (byte)32));
	}

	@Test
	public void ballisticVector()
	{
		Assert.assertEquals(new EntityLocation(0.0f, 9.95f, 0.98f), SpatialHelpers.getBallisticVector(new EntityLocation(0.0f, 0.0f, 0.0f), new EntityLocation(0.0f, 2.0f, 0.0f), 10.0f));
		Assert.assertEquals(new EntityLocation(0.0f, -9.95f, 0.98f), SpatialHelpers.getBallisticVector(new EntityLocation(0.0f, 0.0f, 0.0f), new EntityLocation(0.0f, -2.0f, 0.0f), 10.0f));
		Assert.assertEquals(new EntityLocation(0.0f, 7.74f, 6.33f), SpatialHelpers.getBallisticVector(new EntityLocation(0.0f, 0.0f, 0.0f), new EntityLocation(0.0f, 10.0f, 0.0f), 10.0f));
		Assert.assertEquals(null, SpatialHelpers.getBallisticVector(new EntityLocation(0.0f, 0.0f, 0.0f), new EntityLocation(0.0f, 10.0f, 5.0f), 10.0f));
		Assert.assertEquals(new EntityLocation(0.0f, 10.0f, -0.1f), SpatialHelpers.getBallisticVector(new EntityLocation(0.0f, 0.0f, 0.0f), new EntityLocation(0.0f, 10.0f, -5.0f), 10.0f));
		Assert.assertEquals(new EntityLocation(4.46f, 4.46f, 7.76f), SpatialHelpers.getBallisticVector(new EntityLocation(0.0f, 0.0f, 0.0f), new EntityLocation(3.0f, 3.0f, 3.0f), 10.0f));
	}

	@Test
	public void perf_groundCheck()
	{
		// A test intended for performance analysis to examine the hot-point that is SpatialHelpers.isStandingOnGround.
		// Configure these variables based on analysis being performed.
		boolean useBlockCache = false;
		boolean infiniteLoopForProfiler = false;
		boolean longLoopForObjectiveScore = false;
		
		// We want to show how this behaves when checking on some complex cuboids.
		EntityLocation location = new EntityLocation(31.5f, 31.5f, 0.0f);
		EntityVolume volume = new EntityVolume(1.8f, 0.8f);
		
		// Note that these CuboidAddress instances aren't going to be honoured since we duplicate them in the map.
		CuboidData complexCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), STONE);
		Block dirtBlock = ENV.blocks.fromItem(ENV.items.getItemById("op.dirt"));
		CuboidGenerator.fillPlane(complexCuboid, (byte)31, dirtBlock);
		Block logBlock = ENV.blocks.fromItem(ENV.items.getItemById("op.log"));
		CuboidGenerator.fillPlane(complexCuboid, (byte)29, logBlock);
		Block coalOreBlock = ENV.blocks.fromItem(ENV.items.getItemById("op.coal_ore"));
		CuboidGenerator.fillPlane(complexCuboid, (byte)27, coalOreBlock);
		Block ironOreBlock = ENV.blocks.fromItem(ENV.items.getItemById("op.iron_ore"));
		CuboidGenerator.fillPlane(complexCuboid, (byte)25, ironOreBlock);
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		Map<CuboidAddress, CuboidData> mappedWorld = new HashMap<>();
		mappedWorld.put(CuboidAddress.fromInt(0, 0, -1), complexCuboid);
		mappedWorld.put(CuboidAddress.fromInt(0, 1, -1), complexCuboid);
		mappedWorld.put(CuboidAddress.fromInt(1, 0, -1), complexCuboid);
		mappedWorld.put(CuboidAddress.fromInt(1, 1, -1), complexCuboid);
		mappedWorld.put(CuboidAddress.fromInt(0, 0, 0), airCuboid);
		mappedWorld.put(CuboidAddress.fromInt(0, 1, 0), airCuboid);
		mappedWorld.put(CuboidAddress.fromInt(1, 0, 0), airCuboid);
		mappedWorld.put(CuboidAddress.fromInt(1, 1, 0), airCuboid);
		
		Function<AbsoluteLocation, BlockProxy> blockTypeReader = (AbsoluteLocation l) -> {
			CuboidData cuboid = mappedWorld.get(l.getCuboidAddress());
			return (null != cuboid)
				? BlockProxy.load(l.getBlockAddress(), cuboid)
				: null
			;
		};
		ViscosityReader reader;
		if (useBlockCache)
		{
			LazyLocationCache<BlockProxy> cachingLoader = new LazyLocationCache<>(blockTypeReader);
			TickProcessingContext.IBlockFetcher blockFetcher = new TickProcessingContext.IBlockFetcher() {
				@Override
				public BlockProxy readBlock(AbsoluteLocation location)
				{
					return cachingLoader.apply(location);
				}
				@Override
				public Map<AbsoluteLocation, BlockProxy> readBlockBatch(Collection<AbsoluteLocation> locations)
				{
					Map<AbsoluteLocation, BlockProxy> completed = new HashMap<>();
					for (AbsoluteLocation location : locations)
					{
						BlockProxy proxy = cachingLoader.apply(location);
						if (null != proxy)
						{
							completed.put(location, proxy);
						}
					}
					return completed;
				}
			};
			reader = new ViscosityReader(ENV, blockFetcher);
		}
		else
		{
			TickProcessingContext.IBlockFetcher blockFetcher = ContextBuilder.buildFetcher(blockTypeReader);
			reader = new ViscosityReader(ENV, blockFetcher);
		}
		
		// Do the appropriate run type.
		if (infiniteLoopForProfiler)
		{
			while(true)
			{
				Assert.assertTrue(SpatialHelpers.isStandingOnGround(reader, location, volume));
			}
		}
		else if (longLoopForObjectiveScore)
		{
			int iterationCount = 1_000_000;
			long startNanos = System.nanoTime();
			for (int i = 0; i < iterationCount; ++i)
			{
				Assert.assertTrue(SpatialHelpers.isStandingOnGround(reader, location, volume));
			}
			long endNanos = System.nanoTime();
			System.out.println("Nanos per: " + ((endNanos - startNanos) / iterationCount));
		}
		else
		{
			// We are just using this as a unit test, not a performance measurement, so just verify that it is correct.
			Assert.assertTrue(SpatialHelpers.isStandingOnGround(reader, location, volume));
		}
	}

	@Test
	public void slabGeometry()
	{
		// Show how some of these basic helpers interpret the slab with its sub-block collision.
		EntityLocation locationFloor = new EntityLocation(2.0f, 2.0f, 1.5f);
		Block slab = ENV.blocks.fromItem(ENV.items.getItemById("op.stone_brick_slab"));
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboid, (byte)0, STONE);
		
		// Create the floor data.
		byte slabZ = 1;
		CuboidGenerator.fillPlane(cuboid, slabZ, slab);
		for (byte y = 0; y < Encoding.CUBOID_EDGE_SIZE; ++y)
		{
			for (byte x = 0; x < Encoding.CUBOID_EDGE_SIZE; ++x)
			{
				BlockAddress address = new BlockAddress(x, y, slabZ);
				cuboid.setData7(AspectRegistry.ORIENTATION, address, FacingDirection.directionToByte(FacingDirection.DOWN));
			}
		}
		
		// Create the wall data.
		EntityLocation locationWall = new EntityLocation(2.5f, 2.0f, 10.0f);
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(2, 2, 9), STONE.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(2, 2, 10), slab.item().number());
		cuboid.setData7(AspectRegistry.ORIENTATION, BlockAddress.fromInt(2, 2, 10), FacingDirection.directionToByte(FacingDirection.WEST));
		
		// Now, verify these.
		TickProcessingContext.IBlockFetcher blockTypeReader = ContextBuilder.buildFetcher((AbsoluteLocation l) -> BlockProxy.load(l.getBlockAddress(), cuboid));
		ViscosityReader reader = new ViscosityReader(ENV, blockTypeReader);
		Assert.assertTrue(SpatialHelpers.canExistInLocation(reader, locationFloor, VOLUME));
		Assert.assertTrue(SpatialHelpers.canExistInLocation(reader, locationWall, VOLUME));
		Assert.assertFalse(SpatialHelpers.canExistInLocation(reader, new EntityLocation(2.0f, 2.0f, 1.4f), VOLUME));
		Assert.assertFalse(SpatialHelpers.canExistInLocation(reader, new EntityLocation(2.4f, 2.0f, 10.0f), VOLUME));
	}

	@Test
	public void doorCollision()
	{
		// Show how door collision works when active or not.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboid, (byte)0, STONE);
		
		// Write the door in its inactive state.
		Block door = ENV.blocks.fromItem(ENV.items.getItemById("op.door"));
		AbsoluteLocation doorBase = new AbsoluteLocation(5, 5, 1);
		FacingDirection orientation = FacingDirection.WEST;
		cuboid.setData15(AspectRegistry.BLOCK, doorBase.getBlockAddress(), door.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, doorBase.getRelative(0, 0, 1).getBlockAddress(), door.item().number());
		cuboid.setData7(AspectRegistry.ORIENTATION, doorBase.getBlockAddress(), FacingDirection.directionToByte(orientation));
		cuboid.setData7(AspectRegistry.ORIENTATION, doorBase.getRelative(0, 0, 1).getBlockAddress(), FacingDirection.directionToByte(orientation));
		cuboid.setDataSpecial(AspectRegistry.MULTI_BLOCK_ROOT, doorBase.getRelative(0, 0, 1).getBlockAddress(), doorBase);
		
		// Check collision of the a player in the frame or stuck in the door.
		EntityLocation playerInFrame = new EntityLocation(5.3f, 5.0f, 1.0f);
		EntityLocation playerStuck = new EntityLocation(5.1f, 5.0f, 1.0f);
		TickProcessingContext.IBlockFetcher blockTypeReader = ContextBuilder.buildFetcher((AbsoluteLocation l) -> BlockProxy.load(l.getBlockAddress(), cuboid));
		ViscosityReader reader = new ViscosityReader(ENV, blockTypeReader);
		Assert.assertTrue(SpatialHelpers.canExistInLocation(reader, playerInFrame, VOLUME));
		Assert.assertFalse(SpatialHelpers.canExistInLocation(reader, playerStuck, VOLUME));
		
		// Now, set these to active (open) and make sure that neither of these collides.
		cuboid.setData7(AspectRegistry.FLAGS, doorBase.getBlockAddress(), FlagsAspect.FLAG_ACTIVE);
		cuboid.setData7(AspectRegistry.FLAGS, doorBase.getRelative(0, 0, 1).getBlockAddress(), FlagsAspect.FLAG_ACTIVE);
		reader = new ViscosityReader(ENV, blockTypeReader);
		Assert.assertTrue(SpatialHelpers.canExistInLocation(reader, playerInFrame, VOLUME));
		Assert.assertTrue(SpatialHelpers.canExistInLocation(reader, playerStuck, VOLUME));
	}


	private static class _EndpointHelper implements EntityMovementHelpers.IInteractiveHelper
	{
		private final EntityLocation _expected;
		private final boolean _isOnGround;
		public _EndpointHelper(EntityLocation expected, boolean isOnGround)
		{
			_expected = expected;
			_isOnGround = isOnGround;
		}
		@Override
		public void setLocationAndCancelVelocity(EntityLocation finalLocation, boolean cancelX, boolean cancelY, boolean cancelZ)
		{
			Assert.assertEquals(_expected, finalLocation);
			Assert.assertFalse(cancelX);
			Assert.assertFalse(cancelY);
			Assert.assertEquals(_isOnGround, cancelZ);
		}
		@Override
		public boolean isSolid(EntityLocation base, EntityVolume volume, boolean fromAbove)
		{
			List<AbsoluteLocation> locations = VolumeIterator.getAllInVolume(base, volume);
			
			boolean isSolid = false;
			for (AbsoluteLocation l : locations)
			{
				if (((1 == l.x()) && (1 == l.y())) || (l.z() >= 2))
				{
					// This is our non-solid block.
				}
				else
				{
					isSolid = true;
					break;
				}
			}
			return isSolid;
		}
	}
}
