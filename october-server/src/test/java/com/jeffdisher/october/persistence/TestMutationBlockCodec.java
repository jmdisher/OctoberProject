package com.jeffdisher.october.persistence;

import java.nio.ByteBuffer;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.logic.PropertyHelpers;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.MutationBlockCraft;
import com.jeffdisher.october.mutations.MutationBlockExtractItems;
import com.jeffdisher.october.mutations.MutationBlockIncrementalBreak;
import com.jeffdisher.october.mutations.MutationBlockIncrementalRepair;
import com.jeffdisher.october.mutations.MutationBlockOverwriteByEntity;
import com.jeffdisher.october.mutations.MutationBlockOverwriteInternal;
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
	public void overwriteInternal() throws Throwable
	{
		AbsoluteLocation location = new AbsoluteLocation(-1, 0, 1);
		MutationBlockOverwriteInternal mutation = new MutationBlockOverwriteInternal(location, ENV.blocks.fromItem(ENV.items.getItemById("op.stone")));
		
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		MutationBlockCodec.serializeToBuffer(buffer, mutation);
		buffer.flip();
		IMutationBlock read = MutationBlockCodec.parseAndSeekContext(new DeserializationContext(ENV
			, buffer
			, false
		));
		Assert.assertTrue(read instanceof MutationBlockOverwriteInternal);
		Assert.assertEquals(0, buffer.remaining());
	}

	@Test
	public void overwriteByEntity() throws Throwable
	{
		AbsoluteLocation location = new AbsoluteLocation(-1, 0, 1);
		int entityId = 1;
		MutationBlockOverwriteByEntity mutation = new MutationBlockOverwriteByEntity(location, ENV.blocks.fromItem(ENV.items.getItemById("op.stone")), null, entityId);
		
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		MutationBlockCodec.serializeToBuffer(buffer, mutation);
		buffer.flip();
		IMutationBlock read = MutationBlockCodec.parseAndSeekContext(new DeserializationContext(ENV
			, buffer
			, false
		));
		Assert.assertTrue(read instanceof MutationBlockOverwriteByEntity);
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
		IMutationBlock read = MutationBlockCodec.parseAndSeekContext(new DeserializationContext(ENV
			, buffer
			, false
		));
		Assert.assertTrue(read instanceof MutationBlockExtractItems);
		Assert.assertEquals(0, buffer.remaining());
	}

	@Test
	public void storeItemsStack() throws Throwable
	{
		AbsoluteLocation location = new AbsoluteLocation(-1, 0, 1);
		Item stoneItem = ENV.items.getItemById("op.stone");
		Items items = new Items(stoneItem, 2);
		MutationBlockStoreItems mutation = new MutationBlockStoreItems(location, items, null, Inventory.INVENTORY_ASPECT_INVENTORY);
		
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		MutationBlockCodec.serializeToBuffer(buffer, mutation);
		buffer.flip();
		IMutationBlock read = MutationBlockCodec.parseAndSeekContext(new DeserializationContext(ENV
			, buffer
			, false
		));
		Assert.assertTrue(read instanceof MutationBlockStoreItems);
		Assert.assertEquals(0, buffer.remaining());
	}

	@Test
	public void storeItemsNonStack() throws Throwable
	{
		AbsoluteLocation location = new AbsoluteLocation(-1, 0, 1);
		Item pickItem = ENV.items.getItemById("op.iron_pickaxe");
		NonStackableItem items = PropertyHelpers.newItemWithDefaults(ENV, pickItem);
		MutationBlockStoreItems mutation = new MutationBlockStoreItems(location, null, items, Inventory.INVENTORY_ASPECT_INVENTORY);
		
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		MutationBlockCodec.serializeToBuffer(buffer, mutation);
		buffer.flip();
		IMutationBlock read = MutationBlockCodec.parseAndSeekContext(new DeserializationContext(ENV
			, buffer
			, false
		));
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
		IMutationBlock read = MutationBlockCodec.parseAndSeekContext(new DeserializationContext(ENV
			, buffer
			, false
		));
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
		IMutationBlock read = MutationBlockCodec.parseAndSeekContext(new DeserializationContext(ENV
			, buffer
			, false
		));
		Assert.assertTrue(read instanceof MutationBlockCraft);
		Assert.assertEquals(0, buffer.remaining());
	}

	@Test
	public void repair() throws Throwable
	{
		AbsoluteLocation location = new AbsoluteLocation(-1, 0, 1);
		short damage = 150;
		MutationBlockIncrementalRepair mutation = new MutationBlockIncrementalRepair(location, damage);
		
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		MutationBlockCodec.serializeToBuffer(buffer, mutation);
		buffer.flip();
		IMutationBlock read = MutationBlockCodec.parseAndSeekContext(new DeserializationContext(ENV
			, buffer
			, false
		));
		Assert.assertTrue(read instanceof MutationBlockIncrementalRepair);
		Assert.assertEquals(0, buffer.remaining());
	}
}
