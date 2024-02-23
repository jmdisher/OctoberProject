package com.jeffdisher.october.net;

import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.aspects.InventoryAspect;
import com.jeffdisher.october.logic.EntityActionValidator;
import com.jeffdisher.october.registries.Craft;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;


public class TestCodecHelpers
{
	@Test
	public void string() throws Throwable
	{
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		String test = "test string";
		CodecHelpers.writeString(buffer, test);
		buffer.flip();
		String output = CodecHelpers.readString(buffer);
		Assert.assertEquals(test, output);
	}

	@Test
	public void cuboidAddress() throws Throwable
	{
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		CuboidAddress test = new CuboidAddress((short)0, (short)1, (short)-2);
		CodecHelpers.writeCuboidAddress(buffer, test);
		buffer.flip();
		CuboidAddress output = CodecHelpers.readCuboidAddress(buffer);
		Assert.assertEquals(test, output);
	}

	@Test
	public void inventory() throws Throwable
	{
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		Inventory test = Inventory.start(50)
				.add(ItemRegistry.STONE, 2)
				.add(ItemRegistry.PLANK, 4)
				.finish()
		;
		CodecHelpers.writeInventory(buffer, test);
		buffer.flip();
		Inventory output = CodecHelpers.readInventory(buffer);
		// Inventory has not .equals so check some internal data.
		Assert.assertEquals(50, output.maxEncumbrance);
		Assert.assertEquals(2, output.items.size());
		Assert.assertEquals(2, output.getCount(ItemRegistry.STONE));
		Assert.assertEquals(4, output.getCount(ItemRegistry.PLANK));
	}

	@Test
	public void nullInventory() throws Throwable
	{
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		Inventory test = null;
		CodecHelpers.writeInventory(buffer, test);
		buffer.flip();
		Inventory output = CodecHelpers.readInventory(buffer);
		Assert.assertNull(output);
	}

	@Test
	public void entityLocation() throws Throwable
	{
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		EntityLocation test = new EntityLocation(0.0f, 1.0f, -2.0f);
		CodecHelpers.writeEntityLocation(buffer, test);
		buffer.flip();
		EntityLocation output = CodecHelpers.readEntityLocation(buffer);
		Assert.assertEquals(test, output);
	}

	@Test
	public void absoluteLocation() throws Throwable
	{
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		AbsoluteLocation test = new AbsoluteLocation(0, 1, -2);
		CodecHelpers.writeAbsoluteLocation(buffer, test);
		buffer.flip();
		AbsoluteLocation output = CodecHelpers.readAbsoluteLocation(buffer);
		Assert.assertEquals(test, output);
	}

	@Test
	public void item() throws Throwable
	{
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		Item test = ItemRegistry.STONE;
		CodecHelpers.writeItem(buffer, test);
		buffer.flip();
		Item output = CodecHelpers.readItem(buffer);
		Assert.assertEquals(test, output);
	}

	@Test
	public void items() throws Throwable
	{
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		Items test = new Items(ItemRegistry.STONE, 2);
		CodecHelpers.writeItems(buffer, test);
		buffer.flip();
		Items output = CodecHelpers.readItems(buffer);
		Assert.assertEquals(test, output);
	}

	@Test
	public void craft() throws Throwable
	{
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		Craft test = Craft.LOG_TO_PLANKS;
		CodecHelpers.writeCraft(buffer, test);
		buffer.flip();
		Craft output = CodecHelpers.readCraft(buffer);
		Assert.assertEquals(test, output);
	}

	@Test
	public void entity() throws Throwable
	{
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		Entity test = EntityActionValidator.buildDefaultEntity(1);
		CodecHelpers.writeEntity(buffer, test);
		buffer.flip();
		Entity output = CodecHelpers.readEntity(buffer);
		// Entity contains Inventory, which has no .equals, so compare other parts.
		Assert.assertEquals(test.id(), output.id());
		Assert.assertEquals(test.location(), output.location());
		Assert.assertEquals(test.zVelocityPerSecond(), output.zVelocityPerSecond(), 0.01f);
		Assert.assertEquals(test.volume(), output.volume());
		Assert.assertEquals(test.blocksPerTickSpeed(), output.blocksPerTickSpeed(), 0.01f);
		Assert.assertEquals(test.selectedItem(), output.selectedItem());
	}

	@Test
	public void entityWithCraft() throws Throwable
	{
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		Inventory inventory = Inventory.start(InventoryAspect.CAPACITY_PLAYER).finish();
		Entity test = new Entity(1
				, EntityActionValidator.DEFAULT_LOCATION
				, 0.0f
				, EntityActionValidator.DEFAULT_VOLUME
				, EntityActionValidator.DEFAULT_BLOCKS_PER_TICK_SPEED
				, inventory
				, null
				, new CraftOperation(Craft.STONE_TO_STONE_BRICK, 50L)
		);
		CodecHelpers.writeEntity(buffer, test);
		buffer.flip();
		Entity output = CodecHelpers.readEntity(buffer);
		
		Assert.assertEquals(test.id(), output.id());
		Assert.assertEquals(test.localCraftOperation(), output.localCraftOperation());
	}
}
