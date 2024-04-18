package com.jeffdisher.october.net;

import java.nio.ByteBuffer;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Craft;
import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.FuelState;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableEntity;


public class TestCodecHelpers
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
				.add(ENV.items.STONE, 2)
				.add(ENV.items.PLANK, 4)
				.finish()
		;
		CodecHelpers.writeInventory(buffer, test);
		buffer.flip();
		Inventory output = CodecHelpers.readInventory(buffer);
		// Inventory has not .equals so check some internal data.
		Assert.assertEquals(50, output.maxEncumbrance);
		Assert.assertEquals(2, output.sortedItems().size());
		Assert.assertEquals(2, output.getCount(ENV.items.STONE));
		Assert.assertEquals(4, output.getCount(ENV.items.PLANK));
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
		Item test = ENV.items.STONE;
		CodecHelpers.writeItem(buffer, test);
		buffer.flip();
		Item output = CodecHelpers.readItem(buffer);
		Assert.assertEquals(test, output);
	}

	@Test
	public void items() throws Throwable
	{
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		Items test = new Items(ENV.items.STONE, 2);
		CodecHelpers.writeItems(buffer, test);
		buffer.flip();
		Items output = CodecHelpers.readItems(buffer);
		Assert.assertEquals(test, output);
	}

	@Test
	public void craft() throws Throwable
	{
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		Craft test = ENV.crafting.LOG_TO_PLANKS;
		CodecHelpers.writeCraft(buffer, test);
		buffer.flip();
		Craft output = CodecHelpers.readCraft(buffer);
		Assert.assertEquals(test, output);
	}

	@Test
	public void entity() throws Throwable
	{
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		Entity test = MutableEntity.create(1).freeze();
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
		MutableEntity mutable = MutableEntity.create(1);
		mutable.newLocalCraftOperation = new CraftOperation(ENV.crafting.STONE_TO_STONE_BRICK, 50L);
		Entity test = mutable.freeze();
		CodecHelpers.writeEntity(buffer, test);
		buffer.flip();
		Entity output = CodecHelpers.readEntity(buffer);
		
		Assert.assertEquals(test.id(), output.id());
		Assert.assertEquals(test.localCraftOperation(), output.localCraftOperation());
	}

	@Test
	public void nullCraft() throws Throwable
	{
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		Craft test = null;
		CodecHelpers.writeCraft(buffer, test);
		buffer.flip();
		Craft output = CodecHelpers.readCraft(buffer);
		Assert.assertNull(output);
	}

	@Test
	public void fuelState() throws Throwable
	{
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		FuelState test = new FuelState(0, null, Inventory.start(10).add(ENV.items.STONE, 1).finish());
		CodecHelpers.writeFuelState(buffer, test);
		buffer.flip();
		FuelState output = CodecHelpers.readFuelState(buffer);
		Assert.assertEquals(test.millisFueled(), output.millisFueled());
		Assert.assertEquals(test.currentFuel(), output.currentFuel());
		Assert.assertEquals(test.fuelInventory().getCount(ENV.items.STONE), output.fuelInventory().getCount(ENV.items.STONE));
		
		// Verify the null.
		buffer.clear();
		test = null;
		CodecHelpers.writeFuelState(buffer, test);
		buffer.flip();
		output = CodecHelpers.readFuelState(buffer);
		Assert.assertNull(output);
	}
}
