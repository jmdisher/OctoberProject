package com.jeffdisher.october.client;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.logic.HeightMapHelpers;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.types.PartialPassive;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestClientChangeNotifier
{
	private static Environment ENV;
	private static Block STONE;
	@BeforeClass
	public static void setup()
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
	public void localUpdate()
	{
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData projected = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		AbsoluteLocation location = address.getBase();
		MutableBlockProxy proxy = new MutableBlockProxy(location, projected);
		proxy.setBlockAndClear(STONE);
		// We need to check didChange() in order to clear redundant writes.
		Assert.assertTrue(proxy.didChange());
		proxy.writeBack(projected);
		MutationBlockSetBlock setBlock = MutationBlockSetBlock.extractFromProxy(ByteBuffer.allocate(64), proxy);
		int[] count = new int[1];
		_Listener listener = new _Listener() {
			@Override
			public void cuboidDidChange(IReadOnlyCuboidData cuboid, CuboidHeightMap cuboidHeightMap, ColumnHeightMap columnHeightMap, Set<BlockAddress> changedBlocks, Set<Aspect<?, ?>> changedAspects)
			{
				Assert.assertEquals(1, changedBlocks.size());
				Assert.assertEquals(location.getBlockAddress(), changedBlocks.iterator().next());
				Assert.assertEquals(1, changedAspects.size());
				Assert.assertEquals(AspectRegistry.BLOCK, changedAspects.iterator().next());
				count[0] += 1;
			}
		};
		ProjectedState state = new ProjectedState(null, Map.of(address, projected), Map.of(address, HeightMapHelpers.buildHeightMap(projected)), Map.of(), Set.of());
		ClientChangeNotifier.notifyCuboidChangesFromLocal(listener
				, state
				, Map.of(location, setBlock)
				, Set.of()
		);
		Assert.assertEquals(1, count[0]);
		
		// A redundant change should not send any callbacks.
		ClientChangeNotifier.notifyCuboidChangesFromLocal(listener, state, Map.of(location, setBlock), Set.of());
		Assert.assertEquals(1, count[0]);
	}

	@Test
	public void serverUpdate()
	{
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData shadow = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		CuboidData changed = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		AbsoluteLocation location = address.getBase();
		int[] count = new int[1];
		_Listener listener = new _Listener() {
			@Override
			public void cuboidDidChange(IReadOnlyCuboidData cuboid, CuboidHeightMap cuboidHeightMap, ColumnHeightMap columnHeightMap, Set<BlockAddress> changedBlocks, Set<Aspect<?, ?>> changedAspects)
			{
				Assert.assertEquals(1, changedBlocks.size());
				Assert.assertEquals(location.getBlockAddress(), changedBlocks.iterator().next());
				Assert.assertEquals(1, changedAspects.size());
				Assert.assertEquals(AspectRegistry.BLOCK, changedAspects.iterator().next());
				count[0] += 1;
			}
		};
		MutableBlockProxy proxy = new MutableBlockProxy(location, shadow);
		proxy.setBlockAndClear(STONE);
		// We need to check didChange() in order to clear redundant writes.
		Assert.assertTrue(proxy.didChange());
		proxy.writeBack(shadow);
		MutationBlockSetBlock setBlock = MutationBlockSetBlock.extractFromProxy(ByteBuffer.allocate(64), proxy);
		// We need to apply the change before running the notifications.
		setBlock.applyState(changed);
		// We build the projected state from this shadow and re-apply local changes (none)
		CuboidData projected = CuboidData.mutableClone(changed);
		ProjectedState state = new ProjectedState(null, Map.of(address, projected), Map.of(address, HeightMapHelpers.buildHeightMap(projected)), Map.of(), Set.of());
		ClientChangeNotifier.notifyCuboidChangesFromServer(listener
				, state
				, Map.of(location, setBlock)
				, Map.of()
				, HeightMapHelpers.buildColumnMaps(Map.of(address, HeightMapHelpers.buildHeightMap(changed)))
		);
		
		Assert.assertEquals(1, count[0]);
	}

	@Test
	public void serverUpdateMultipleCuboids()
	{
		CuboidAddress address0 = CuboidAddress.fromInt(0, 0, 0);
		CuboidAddress address1 = CuboidAddress.fromInt(1, 0, 0);
		CuboidData shadow0 = CuboidGenerator.createFilledCuboid(address0, ENV.special.AIR);
		CuboidData changed0 = CuboidGenerator.createFilledCuboid(address0, ENV.special.AIR);
		CuboidData shadow1 = CuboidGenerator.createFilledCuboid(address1, ENV.special.AIR);
		CuboidData changed1 = CuboidGenerator.createFilledCuboid(address1, ENV.special.AIR);
		AbsoluteLocation location0 = address0.getBase();
		AbsoluteLocation location1 = address1.getBase();
		int[] count = new int[1];
		_Listener listener = new _Listener() {
			@Override
			public void cuboidDidChange(IReadOnlyCuboidData cuboid, CuboidHeightMap cuboidHeightMap, ColumnHeightMap columnHeightMap, Set<BlockAddress> changedBlocks, Set<Aspect<?, ?>> changedAspects)
			{
				Assert.assertEquals(1, changedBlocks.size());
				Assert.assertEquals(1, changedAspects.size());
				if (cuboid.getCuboidAddress().equals(address0))
				{
					Assert.assertEquals(location0.getBlockAddress(), changedBlocks.iterator().next());
					Assert.assertEquals(AspectRegistry.BLOCK, changedAspects.iterator().next());
				}
				else
				{
					Assert.assertEquals(location1.getBlockAddress(), changedBlocks.iterator().next());
					Assert.assertEquals(AspectRegistry.LOGIC, changedAspects.iterator().next());
				}
				count[0] += 1;
			}
		};
		// Create the block change.
		MutableBlockProxy proxy0 = new MutableBlockProxy(location0, shadow0);
		proxy0.setBlockAndClear(STONE);
		Assert.assertTrue(proxy0.didChange());
		MutationBlockSetBlock setBlock0 = MutationBlockSetBlock.extractFromProxy(ByteBuffer.allocate(64), proxy0);
		// Create the logic change.
		MutableBlockProxy proxy1 = new MutableBlockProxy(location1, shadow1);
		proxy1.setLogic((byte)5);
		MutationBlockSetBlock setBlock1 = MutationBlockSetBlock.extractFromProxy(ByteBuffer.allocate(64), proxy1);
		
		
		// We need to apply the changes before running the notifications.
		setBlock0.applyState(changed0);
		setBlock1.applyState(changed1);
		// We build the projected state from this shadow and re-apply local changes (none)
		CuboidData projected0 = CuboidData.mutableClone(changed0);
		CuboidData projected1 = CuboidData.mutableClone(changed1);
		ProjectedState state = new ProjectedState(null
				, Map.of(address0, projected0, address1, projected1)
				, Map.of(address0, HeightMapHelpers.buildHeightMap(projected0), address1, HeightMapHelpers.buildHeightMap(projected1))
				, Map.of()
				, Set.of()
		);
		ClientChangeNotifier.notifyCuboidChangesFromServer(listener
				, state
				, Map.of(location0, setBlock0, location1, setBlock1)
				, Map.of()
				, HeightMapHelpers.buildColumnMaps(Map.of(address0, HeightMapHelpers.buildHeightMap(changed0), address1, HeightMapHelpers.buildHeightMap(changed1)))
		);
		
		Assert.assertEquals(2, count[0]);
	}

	@Test
	public void localLightingUpdate()
	{
		CuboidAddress address = CuboidAddress.fromInt(0, 0, 0);
		CuboidData projected = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		int[] count = new int[1];
		_Listener listener = new _Listener() {
			@Override
			public void cuboidDidChange(IReadOnlyCuboidData cuboid, CuboidHeightMap cuboidHeightMap, ColumnHeightMap columnHeightMap, Set<BlockAddress> changedBlocks, Set<Aspect<?, ?>> changedAspects)
			{
				// This is the only case where no changed blocks will be reported - lighting change somewhere in the cuboid.
				Assert.assertEquals(0, changedBlocks.size());
				Assert.assertEquals(1, changedAspects.size());
				Assert.assertEquals(AspectRegistry.LIGHT, changedAspects.iterator().next());
				count[0] += 1;
			}
		};
		ProjectedState state = new ProjectedState(null, Map.of(address, projected), Map.of(address, HeightMapHelpers.buildHeightMap(projected)), Map.of(), Set.of());
		ClientChangeNotifier.notifyCuboidChangesFromLocal(listener
				, state
				, Map.of()
				, Set.of(address)
		);
		Assert.assertEquals(1, count[0]);
		
		// A redundant change should not send any callbacks.
		ClientChangeNotifier.notifyCuboidChangesFromLocal(listener
				, state
				, Map.of()
				, Set.of(address)
		);
		Assert.assertEquals(1, count[0]);
	}


	private static abstract class _Listener implements IProjectionListener
	{
		@Override
		public void cuboidDidLoad(IReadOnlyCuboidData cuboid, CuboidHeightMap cuboidHeightMap, ColumnHeightMap columnHeightMap)
		{
			throw new AssertionError("Not in test");
		}
		@Override
		public void cuboidDidUnload(CuboidAddress address)
		{
			throw new AssertionError("Not in test");
		}
		@Override
		public void thisEntityDidLoad(Entity authoritativeEntity)
		{
			throw new AssertionError("Not in test");
		}
		@Override
		public void thisEntityDidChange(Entity projectedEntity)
		{
			throw new AssertionError("Not in test");
		}
		@Override
		public void otherEntityDidLoad(PartialEntity entity)
		{
			throw new AssertionError("Not in test");
		}
		@Override
		public void otherEntityDidChange(PartialEntity entity)
		{
			throw new AssertionError("Not in test");
		}
		@Override
		public void otherEntityDidUnload(int id)
		{
			throw new AssertionError("Not in test");
		}
		@Override
		public void passiveEntityDidLoad(PartialPassive entity)
		{
			throw new AssertionError("Not in test");
		}
		@Override
		public void passiveEntityDidChange(PartialPassive entity)
		{
			throw new AssertionError("Not in test");
		}
		@Override
		public void passiveEntityDidUnload(int id)
		{
			throw new AssertionError("Not in test");
		}
		@Override
		public void tickDidComplete(long gameTick)
		{
			throw new AssertionError("Not in test");
		}
		@Override
		public void handleEvent(EventRecord event)
		{
			throw new AssertionError("Not in test");
		}
	}
}
