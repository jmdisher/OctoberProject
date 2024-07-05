package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.MutationBlockPushToBlock;
import com.jeffdisher.october.mutations.MutationBlockStoreItems;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Difficulty;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.worldgen.CuboidGenerator;


public class TestHopperHelpers
{
	private static Environment ENV;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void basicNorthHopper()
	{
		// Create an air cuboid with 2 chests connected by a hopper and put some items in the top chest.
		Block chest = ENV.blocks.getAsPlaceableBlock(ENV.items.getItemById("op.chest"));
		Block hopperNorth = ENV.blocks.getAsPlaceableBlock(ENV.items.getItemById("op.hopper_north"));
		Item charcoal = ENV.items.getItemById("op.charcoal");
		Item sword = ENV.items.getItemById("op.iron_sword");
		CuboidAddress address = new CuboidAddress((short)10, (short)10, (short)10);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		AbsoluteLocation topLocation = address.getBase().getRelative(16, 16, 16);
		AbsoluteLocation hopperLocation = topLocation.getRelative(0, 0, -1);
		AbsoluteLocation northLocation = hopperLocation.getRelative(0, 1, 0);
		
		MutableBlockProxy proxy = new MutableBlockProxy(topLocation, cuboid);
		proxy.setBlockAndClear(chest);
		proxy.setInventory(Inventory.start(10).addStackable(charcoal, 3).addNonStackable(new NonStackableItem(sword, 10)).finish());
		proxy.writeBack(cuboid);
		
		// We will start with an item in the hopper to see it take 2 actions.
		proxy = new MutableBlockProxy(hopperLocation, cuboid);
		proxy.setBlockAndClear(hopperNorth);
		proxy.setInventory(Inventory.start(10).addStackable(charcoal, 1).finish());
		proxy.writeBack(cuboid);
		
		proxy = new MutableBlockProxy(northLocation, cuboid);
		proxy.setBlockAndClear(chest);
		proxy.writeBack(cuboid);
		
		List<IMutationBlock> outMutations = new ArrayList<>();
		TickProcessingContext context = new TickProcessingContext(1L
				, (AbsoluteLocation location) -> cuboid.getCuboidAddress().equals(location.getCuboidAddress()) ? new BlockProxy(location.getBlockAddress(), cuboid) : null
				, null
				, new TickProcessingContext.IMutationSink() {
					@Override
					public void next(IMutationBlock mutation)
					{
						outMutations.add(mutation);
					}
					@Override
					public void future(IMutationBlock mutation, long millisToDelay)
					{
						Assert.fail("Not expected in tets");
					}
				}
				, null
				, null
				, null
				, Difficulty.HOSTILE
				, 100L
		);
		
		// Now apply the hopper processing and verify that we see the 2 mutations and the item removed from the hopper.
		proxy = new MutableBlockProxy(hopperLocation, cuboid);
		HopperHelpers.tryProcessHopper(context, hopperLocation, proxy);
		proxy.writeBack(cuboid);
		
		Assert.assertEquals(2, outMutations.size());
		Assert.assertTrue(outMutations.get(0) instanceof MutationBlockStoreItems);
		Assert.assertEquals(northLocation, outMutations.get(0).getAbsoluteLocation());
		Assert.assertTrue(outMutations.get(1) instanceof MutationBlockPushToBlock);
		Assert.assertEquals(topLocation, outMutations.get(1).getAbsoluteLocation());
		proxy = new MutableBlockProxy(hopperLocation, cuboid);
		Assert.assertEquals(0, proxy.getInventory().currentEncumbrance);
	}
}
