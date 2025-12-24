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
import com.jeffdisher.october.types.ContextBuilder;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableInventory;
import com.jeffdisher.october.types.PassiveEntity;
import com.jeffdisher.october.types.PassiveType;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.CuboidGenerator;


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
		Block hopper = ENV.blocks.getAsPlaceableBlock(ENV.items.getItemById("op.hopper"));
		Item charcoal = ENV.items.getItemById("op.charcoal");
		Item sword = ENV.items.getItemById("op.iron_sword");
		CuboidAddress address = CuboidAddress.fromInt(10, 10, 10);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		AbsoluteLocation topLocation = address.getBase().getRelative(16, 16, 16);
		AbsoluteLocation hopperLocation = topLocation.getRelative(0, 0, -1);
		AbsoluteLocation northLocation = hopperLocation.getRelative(0, 1, 0);
		
		MutableBlockProxy proxy = new MutableBlockProxy(topLocation, cuboid);
		proxy.setBlockAndClear(chest);
		proxy.setInventory(Inventory.start(10).addStackable(charcoal, 3).addNonStackable(PropertyHelpers.newItemWithDefaults(ENV, sword)).finish());
		proxy.writeBack(cuboid);
		
		// We will start with an item in the hopper to see it take 2 actions.
		proxy = new MutableBlockProxy(hopperLocation, cuboid);
		proxy.setBlockAndClear(hopper);
		proxy.setOrientation(FacingDirection.NORTH);
		proxy.setInventory(Inventory.start(10).addStackable(charcoal, 1).finish());
		proxy.writeBack(cuboid);
		
		proxy = new MutableBlockProxy(northLocation, cuboid);
		proxy.setBlockAndClear(chest);
		proxy.writeBack(cuboid);
		
		List<IMutationBlock> outMutations = new ArrayList<>();
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation location) -> cuboid.getCuboidAddress().equals(location.getCuboidAddress()) ? new BlockProxy(location.getBlockAddress(), cuboid) : null
					, null
					, null
				)
				.sinks(new TickProcessingContext.IMutationSink() {
						@Override
						public boolean next(IMutationBlock mutation)
						{
							outMutations.add(mutation);
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

	@Test
	public void absorbPassives()
	{
		// Verify our expected behaviour of passives.
		Block hopper = ENV.blocks.getAsPlaceableBlock(ENV.items.getItemById("op.hopper"));
		Item sword = ENV.items.getItemById("op.iron_sword");
		CuboidAddress address = CuboidAddress.fromInt(10, 10, 10);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		AbsoluteLocation nearlyEmptyHopper = address.getBase().getRelative(16, 16, 16);
		AbsoluteLocation mostlyFullHopper = address.getBase().getRelative(15, 16, 16);
		AbsoluteLocation fullHopper = address.getBase().getRelative(14, 16, 16);
		
		Item wheatSeed = ENV.items.getItemById("op.wheat_seed");
		Assert.assertEquals(1, ENV.encumbrance.getEncumbrance(wheatSeed));
		int capacity = ENV.stations.getNormalInventorySize(hopper);
		int nearlyEmpty = (int) (0.2f * (float)capacity);
		int mostlyFull = (int) (0.8f * (float)capacity);
		
		MutableBlockProxy proxy = new MutableBlockProxy(nearlyEmptyHopper, cuboid);
		proxy.setBlockAndClear(hopper);
		MutableInventory mutable = new MutableInventory(proxy.getInventory());
		mutable.addAllItems(wheatSeed, nearlyEmpty);
		proxy.setInventory(mutable.freeze());
		proxy.writeBack(cuboid);
		
		proxy = new MutableBlockProxy(mostlyFullHopper, cuboid);
		proxy.setBlockAndClear(hopper);
		mutable = new MutableInventory(proxy.getInventory());
		mutable.addAllItems(wheatSeed, mostlyFull);
		proxy.setInventory(mutable.freeze());
		proxy.writeBack(cuboid);
		
		proxy = new MutableBlockProxy(fullHopper, cuboid);
		proxy.setBlockAndClear(hopper);
		mutable = new MutableInventory(proxy.getInventory());
		mutable.addAllItems(wheatSeed, capacity);
		proxy.setInventory(mutable.freeze());
		proxy.writeBack(cuboid);
		
		MutationBlockStoreItems[] out_mutations = new MutationBlockStoreItems[1];
		TickProcessingContext context = ContextBuilder.build()
			.lookups((AbsoluteLocation location) -> cuboid.getCuboidAddress().equals(location.getCuboidAddress()) ? new BlockProxy(location.getBlockAddress(), cuboid) : null
				, null
				, null
			)
			.sinks(new TickProcessingContext.IMutationSink() {
				@Override
				public boolean next(IMutationBlock mutation)
				{
					Assert.assertNull(out_mutations[0]);
					out_mutations[0] = (MutationBlockStoreItems) mutation;
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
		
		ItemSlot oneSword = ItemSlot.fromNonStack(PropertyHelpers.newItemWithDefaults(ENV, sword));
		ItemSlot fullWheat = ItemSlot.fromStack(new Items(wheatSeed, capacity));
		ItemSlot halfWheat = ItemSlot.fromStack(new Items(wheatSeed, capacity / 2));
		
		// Show that all of these fit into the nearly empty (since we will over-fill if less than half full).
		PassiveEntity result = HopperHelpers.tryAbsorbingIntoHopper(context, _passiveAbove(nearlyEmptyHopper, oneSword));
		Assert.assertNull(result);
		Assert.assertNotNull(out_mutations[0]);
		out_mutations[0] = null;
		result = HopperHelpers.tryAbsorbingIntoHopper(context, _passiveAbove(nearlyEmptyHopper, fullWheat));
		Assert.assertNull(result);
		Assert.assertNotNull(out_mutations[0]);
		out_mutations[0] = null;
		result = HopperHelpers.tryAbsorbingIntoHopper(context, _passiveAbove(nearlyEmptyHopper, halfWheat));
		Assert.assertNull(result);
		Assert.assertNotNull(out_mutations[0]);
		out_mutations[0] = null;
		
		// Show that full and half doesn't fit in half full.
		result = HopperHelpers.tryAbsorbingIntoHopper(context, _passiveAbove(mostlyFullHopper, oneSword));
		Assert.assertNull(result);
		Assert.assertNotNull(out_mutations[0]);
		out_mutations[0] = null;
		result = HopperHelpers.tryAbsorbingIntoHopper(context, _passiveAbove(mostlyFullHopper, fullWheat));
		Assert.assertNotNull(result);
		Assert.assertNull(out_mutations[0]);
		result = HopperHelpers.tryAbsorbingIntoHopper(context, _passiveAbove(mostlyFullHopper, halfWheat));
		Assert.assertNotNull(result);
		Assert.assertNull(out_mutations[0]);
		
		// Show that nothing fits in full.
		result = HopperHelpers.tryAbsorbingIntoHopper(context, _passiveAbove(fullHopper, oneSword));
		Assert.assertNotNull(result);
		Assert.assertNull(out_mutations[0]);
		result = HopperHelpers.tryAbsorbingIntoHopper(context, _passiveAbove(fullHopper, fullWheat));
		Assert.assertNotNull(result);
		Assert.assertNull(out_mutations[0]);
		result = HopperHelpers.tryAbsorbingIntoHopper(context, _passiveAbove(fullHopper, halfWheat));
		Assert.assertNotNull(result);
		Assert.assertNull(out_mutations[0]);
	}


	private static PassiveEntity _passiveAbove(AbsoluteLocation blockUnder, ItemSlot slot)
	{
		EntityLocation location = blockUnder.getRelative(0, 0, 1).toEntityLocation();
		return new PassiveEntity(1
			, PassiveType.ITEM_SLOT
			, location
			, new EntityLocation(0.0f, 0.0f, 0.0f)
			, slot
			, 1000L
		);
	}
}
