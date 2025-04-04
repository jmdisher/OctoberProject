package com.jeffdisher.october.client;

import java.nio.ByteBuffer;
import java.util.List;
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
		CuboidData shadow = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		CuboidData projected = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		AbsoluteLocation location = address.getBase();
		projected.setData15(AspectRegistry.BLOCK, location.getBlockAddress(), STONE.item().number());
		int[] count = new int[1];
		_Listener listener = new _Listener() {
			@Override
			public void cuboidDidChange(IReadOnlyCuboidData cuboid, ColumnHeightMap heightMap, Set<BlockAddress> changedBlocks, Set<Aspect<?, ?>> changedAspects)
			{
				Assert.assertEquals(1, changedBlocks.size());
				Assert.assertEquals(location.getBlockAddress(), changedBlocks.iterator().next());
				Assert.assertEquals(1, changedAspects.size());
				Assert.assertEquals(AspectRegistry.BLOCK, changedAspects.iterator().next());
				count[0] += 1;
			}
		};
		ProjectedState state = new ProjectedState(null, Map.of(address, projected), Map.of(address, HeightMapHelpers.buildHeightMap(projected)), Map.of());
		ClientChangeNotifier.notifyCuboidChangesFromLocal(listener
				, state
				, (CuboidAddress localAddress) -> shadow
				, Map.of(address, List.of(location))
		);
		Assert.assertEquals(1, count[0]);
		
		// A redundant change should not send any callbacks.
		ClientChangeNotifier.notifyCuboidChangesFromLocal(listener, state, (CuboidAddress localAddress) -> shadow, Map.of(address, List.of(location)));
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
			public void cuboidDidChange(IReadOnlyCuboidData cuboid, ColumnHeightMap heightMap, Set<BlockAddress> changedBlocks, Set<Aspect<?, ?>> changedAspects)
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
		MutationBlockSetBlock setBlock = MutationBlockSetBlock.extractFromProxy(ByteBuffer.allocate(64), proxy);
		// We need to apply the change before running the notifications.
		setBlock.applyState(changed);
		// We build the projected state from this shadow and re-apply local changes (none)
		CuboidData projected = CuboidData.mutableClone(changed);
		ProjectedState state = new ProjectedState(null, Map.of(address, projected), Map.of(address, HeightMapHelpers.buildHeightMap(projected)), Map.of());
		ClientChangeNotifier.notifyCuboidChangesFromServer(listener
				, state
				, (CuboidAddress localAddress) -> changed
				, Map.of(address, shadow)
				, Map.of(address, List.of(setBlock))
				, Map.of()
				, HeightMapHelpers.buildColumnMaps(Map.of(address, HeightMapHelpers.buildHeightMap(changed)))
		);
		
		Assert.assertEquals(1, count[0]);
	}


	private static abstract class _Listener implements IProjectionListener
	{
		@Override
		public void cuboidDidLoad(IReadOnlyCuboidData cuboid, ColumnHeightMap heightMap)
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
		public void thisEntityDidChange(Entity authoritativeEntity, Entity projectedEntity)
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
