package com.jeffdisher.october.persistence;

import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.MutationBlockCraft;
import com.jeffdisher.october.mutations.MutationBlockExtractItems;
import com.jeffdisher.october.mutations.MutationBlockIncrementalBreak;
import com.jeffdisher.october.mutations.MutationBlockOverwrite;
import com.jeffdisher.october.mutations.MutationBlockStoreItems;
import com.jeffdisher.october.registries.Craft;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;


public class TestMutationBlockCodec
{
	@Test
	public void overwrite() throws Throwable
	{
		AbsoluteLocation location = new AbsoluteLocation(-1, 0, 1);
		Item type = ItemRegistry.STONE;
		MutationBlockOverwrite mutation = new MutationBlockOverwrite(location, type);
		
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
		Items items = new Items(ItemRegistry.STONE, 2);
		int returnEntityId = 1;
		MutationBlockExtractItems mutation = new MutationBlockExtractItems(location, items, returnEntityId);
		
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		MutationBlockCodec.serializeToBuffer(buffer, mutation);
		buffer.flip();
		IMutationBlock read = MutationBlockCodec.parseAndSeekFlippedBuffer(buffer);
		Assert.assertTrue(read instanceof MutationBlockExtractItems);
		Assert.assertEquals(0, buffer.remaining());
	}

	@Test
	public void storeItems() throws Throwable
	{
		AbsoluteLocation location = new AbsoluteLocation(-1, 0, 1);
		Items items = new Items(ItemRegistry.STONE, 2);
		MutationBlockStoreItems mutation = new MutationBlockStoreItems(location, items);
		
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
		MutationBlockIncrementalBreak mutation = new MutationBlockIncrementalBreak(location, damage);
		
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
		Craft craft = Craft.LOG_TO_PLANKS;
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
