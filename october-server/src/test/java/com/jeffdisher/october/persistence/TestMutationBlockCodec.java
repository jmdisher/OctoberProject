package com.jeffdisher.october.persistence;

import java.nio.ByteBuffer;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.MutationBlockCraft;
import com.jeffdisher.october.mutations.MutationBlockExtractItems;
import com.jeffdisher.october.mutations.MutationBlockIncrementalBreak;
import com.jeffdisher.october.mutations.MutationBlockOverwrite;
import com.jeffdisher.october.mutations.MutationBlockStoreItems;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Craft;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.NonStackableItem;


public class TestMutationBlockCodec
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
	public void overwrite() throws Throwable
	{
		AbsoluteLocation location = new AbsoluteLocation(-1, 0, 1);
		MutationBlockOverwrite mutation = new MutationBlockOverwrite(location, ENV.blocks.fromItem(ENV.items.getItemById("op.stone")));
		
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		MutationBlockCodec.serializeToBuffer(buffer, mutation);
		buffer.flip();
		IMutationBlock read = MutationBlockCodec.parseAndSeekFlippedBuffer(buffer);
		Assert.assertTrue(read instanceof MutationBlockOverwrite);
		Assert.assertEquals(0, buffer.remaining());
	}

	@Test
	public void extractItems() throws Throwable
	{
		AbsoluteLocation location = new AbsoluteLocation(-1, 0, 1);
		int blockInventoryKey = 1;
		int countRequested = 2;
		int returnEntityId = 1;
		MutationBlockExtractItems mutation = new MutationBlockExtractItems(location, blockInventoryKey, countRequested, Inventory.INVENTORY_ASPECT_INVENTORY, returnEntityId);
		
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		MutationBlockCodec.serializeToBuffer(buffer, mutation);
		buffer.flip();
		IMutationBlock read = MutationBlockCodec.parseAndSeekFlippedBuffer(buffer);
		Assert.assertTrue(read instanceof MutationBlockExtractItems);
		Assert.assertEquals(0, buffer.remaining());
	}

	@Test
	public void storeItemsStack() throws Throwable
	{
		AbsoluteLocation location = new AbsoluteLocation(-1, 0, 1);
		Items items = new Items(ENV.items.STONE, 2);
		MutationBlockStoreItems mutation = new MutationBlockStoreItems(location, items, null, Inventory.INVENTORY_ASPECT_INVENTORY);
		
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		MutationBlockCodec.serializeToBuffer(buffer, mutation);
		buffer.flip();
		IMutationBlock read = MutationBlockCodec.parseAndSeekFlippedBuffer(buffer);
		Assert.assertTrue(read instanceof MutationBlockStoreItems);
		Assert.assertEquals(0, buffer.remaining());
	}

	@Test
	public void storeItemsNonStack() throws Throwable
	{
		AbsoluteLocation location = new AbsoluteLocation(-1, 0, 1);
		Item pickItem = ENV.items.getItemById("op.iron_pickaxe");
		NonStackableItem items = new NonStackableItem(pickItem, ENV.durability.getDurability(pickItem));
		MutationBlockStoreItems mutation = new MutationBlockStoreItems(location, null, items, Inventory.INVENTORY_ASPECT_INVENTORY);
		
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		MutationBlockCodec.serializeToBuffer(buffer, mutation);
		buffer.flip();
		IMutationBlock read = MutationBlockCodec.parseAndSeekFlippedBuffer(buffer);
		Assert.assertTrue(read instanceof MutationBlockStoreItems);
		Assert.assertEquals(0, buffer.remaining());
	}

	@Test
	public void incrementalBreak() throws Throwable
	{
		AbsoluteLocation location = new AbsoluteLocation(-1, 0, 1);
		short damage = 10;
		MutationBlockIncrementalBreak mutation = new MutationBlockIncrementalBreak(location, damage, MutationBlockIncrementalBreak.NO_STORAGE_ENTITY);
		
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		MutationBlockCodec.serializeToBuffer(buffer, mutation);
		buffer.flip();
		IMutationBlock read = MutationBlockCodec.parseAndSeekFlippedBuffer(buffer);
		Assert.assertTrue(read instanceof MutationBlockIncrementalBreak);
		Assert.assertEquals(0, buffer.remaining());
	}

	@Test
	public void blockCraft() throws Throwable
	{
		AbsoluteLocation location = new AbsoluteLocation(-1, 0, 1);
		Craft craft = ENV.crafting.getCraftById("op.log_to_planks");
		long millis = 100L;
		MutationBlockCraft mutation = new MutationBlockCraft(location, craft, millis);
		
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		MutationBlockCodec.serializeToBuffer(buffer, mutation);
		buffer.flip();
		IMutationBlock read = MutationBlockCodec.parseAndSeekFlippedBuffer(buffer);
		Assert.assertTrue(read instanceof MutationBlockCraft);
		Assert.assertEquals(0, buffer.remaining());
	}
}
