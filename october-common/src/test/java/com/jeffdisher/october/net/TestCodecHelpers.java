package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.OrientationAspect;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.logic.PropertyHelpers;
import com.jeffdisher.october.properties.PropertyRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.Craft;
import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.FuelState;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.PassiveEntity;
import com.jeffdisher.october.types.PassiveType;


public class TestCodecHelpers
{
	private static Environment ENV;
	private static Item STONE_ITEM;
	private static Item PLANK_ITEM;
	private static Item IRON_SWORD_ITEM;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		STONE_ITEM = ENV.items.getItemById("op.stone");
		PLANK_ITEM = ENV.items.getItemById("op.plank");
		IRON_SWORD_ITEM = ENV.items.getItemById("op.iron_sword");
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
		CuboidAddress test = CuboidAddress.fromInt(0, 1, -2);
		CodecHelpers.writeCuboidAddress(buffer, test);
		buffer.flip();
		CuboidAddress output = CodecHelpers.readCuboidAddress(buffer);
		Assert.assertEquals(test, output);
	}

	@Test
	public void inventory() throws Throwable
	{
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		NonStackableItem sword = new NonStackableItem(IRON_SWORD_ITEM, Map.of(PropertyRegistry.DURABILITY, 103));
		Inventory test = Inventory.start(50)
				.addStackable(STONE_ITEM, 2)
				.addStackable(PLANK_ITEM, 4)
				.addNonStackable(sword)
				.finish()
		;
		CodecHelpers.writeInventory(buffer, test);
		buffer.flip();
		DeserializationContext context = new DeserializationContext(Environment.getShared()
			, buffer
			, 0L
			, false
		);
		Inventory output = CodecHelpers.readInventory(context);
		// Inventory has not .equals so check some internal data.
		Assert.assertEquals(50, output.maxEncumbrance);
		Assert.assertEquals(20, output.currentEncumbrance);
		Assert.assertEquals(3, output.sortedKeys().size());
		Assert.assertEquals(2, output.getCount(STONE_ITEM));
		Assert.assertEquals(4, output.getCount(PLANK_ITEM));
		NonStackableItem nonStack = output.getNonStackableForKey(3);
		Assert.assertEquals(IRON_SWORD_ITEM, nonStack.type());
		Assert.assertEquals(103, PropertyHelpers.getDurability(nonStack));
	}

	@Test
	public void nullInventory() throws Throwable
	{
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		Inventory test = null;
		CodecHelpers.writeInventory(buffer, test);
		buffer.flip();
		DeserializationContext context = new DeserializationContext(Environment.getShared()
			, buffer
			, 0L
			, false
		);
		Inventory output = CodecHelpers.readInventory(context);
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
		Item test = STONE_ITEM;
		CodecHelpers.writeItem(buffer, test);
		buffer.flip();
		Item output = CodecHelpers.readItem(buffer);
		Assert.assertEquals(test, output);
	}

	@Test
	public void block() throws Throwable
	{
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		Block test = ENV.blocks.fromItem(STONE_ITEM);
		CodecHelpers.writeBlock(buffer, test);
		buffer.flip();
		Block output = CodecHelpers.readBlock(buffer);
		Assert.assertEquals(test, output);
	}

	@Test
	public void items() throws Throwable
	{
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		Items test = new Items(STONE_ITEM, 2);
		CodecHelpers.writeItems(buffer, test);
		buffer.flip();
		Items output = CodecHelpers.readItems(buffer);
		Assert.assertEquals(test, output);
		
		buffer.clear();
		CodecHelpers.writeItems(buffer, null);
		buffer.flip();
		output = CodecHelpers.readItems(buffer);
		Assert.assertNull(output);
	}

	@Test
	public void craft() throws Throwable
	{
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		Craft test = ENV.crafting.getCraftById("op.log_to_planks");
		CodecHelpers.writeCraft(buffer, test);
		buffer.flip();
		Craft output = CodecHelpers.readCraft(buffer);
		Assert.assertEquals(test, output);
	}

	@Test
	public void entity() throws Throwable
	{
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		Entity test = MutableEntity.createForTest(1).freeze();
		CodecHelpers.writeEntity(buffer, test);
		buffer.flip();
		Entity output = CodecHelpers.readEntity(new DeserializationContext(ENV
			, buffer
			, 0L
			, false
		));
		// Entity contains Inventory, which has no .equals, so compare other parts.
		Assert.assertEquals(test.id(), output.id());
		Assert.assertEquals(test.location(), output.location());
		Assert.assertEquals(test.velocity(), output.velocity());
		Assert.assertEquals(test.hotbarItems()[test.hotbarIndex()], output.hotbarItems()[test.hotbarIndex()]);
	}

	@Test
	public void entityWithCraft() throws Throwable
	{
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		MutableEntity mutable = MutableEntity.createForTest(1);
		mutable.newLocalCraftOperation = new CraftOperation(ENV.crafting.getCraftById("op.stone_to_stone_brick"), 50L);
		Entity test = mutable.freeze();
		CodecHelpers.writeEntity(buffer, test);
		buffer.flip();
		Entity output = CodecHelpers.readEntity(new DeserializationContext(ENV
			, buffer
			, 0L
			, false
		));
		
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
		FuelState test = new FuelState(0, null, Inventory.start(10).addStackable(STONE_ITEM, 1).finish());
		CodecHelpers.writeFuelState(buffer, test);
		buffer.flip();
		DeserializationContext context = new DeserializationContext(Environment.getShared()
			, buffer
			, 0L
			, false
		);
		FuelState output = CodecHelpers.readFuelState(context);
		Assert.assertEquals(test.millisFuelled(), output.millisFuelled());
		Assert.assertEquals(test.currentFuel(), output.currentFuel());
		Assert.assertEquals(test.fuelInventory().getCount(STONE_ITEM), output.fuelInventory().getCount(STONE_ITEM));
		
		// Verify the null.
		buffer.clear();
		test = null;
		CodecHelpers.writeFuelState(buffer, test);
		buffer.flip();
		context = new DeserializationContext(Environment.getShared()
			, buffer
			, 0L
			, false
		);
		output = CodecHelpers.readFuelState(context);
		Assert.assertNull(output);
	}

	@Test
	public void nonStackable() throws Throwable
	{
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		NonStackableItem test = new NonStackableItem(STONE_ITEM, Map.of(PropertyRegistry.DURABILITY, 10));
		CodecHelpers.writeNonStackableItem(buffer, test);
		buffer.flip();
		NonStackableItem output = CodecHelpers.readNonStackableItem(new DeserializationContext(ENV
			, buffer
			, 0L
			, false
		));
		Assert.assertEquals(test, output);
		
		buffer.clear();
		CodecHelpers.writeNonStackableItem(buffer, null);
		buffer.flip();
		output = CodecHelpers.readNonStackableItem(new DeserializationContext(ENV
			, buffer
			, 0L
			, false
		));
		Assert.assertNull(output);
	}

	@Test
	public void bodyPart() throws Throwable
	{
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		BodyPart test = BodyPart.FEET;
		CodecHelpers.writeBodyPart(buffer, test);
		buffer.flip();
		BodyPart output = CodecHelpers.readBodyPart(buffer);
		Assert.assertTrue(test == output);
		
		buffer.clear();
		CodecHelpers.writeBodyPart(buffer, null);
		buffer.flip();
		output = CodecHelpers.readBodyPart(buffer);
		Assert.assertNull(output);
	}

	@Test
	public void orientation() throws Throwable
	{
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		OrientationAspect.Direction north = OrientationAspect.Direction.NORTH;
		OrientationAspect.Direction missing = null;
		CodecHelpers.writeOrientation(buffer, north);
		CodecHelpers.writeOrientation(buffer, missing);
		buffer.flip();
		Assert.assertEquals(2, buffer.remaining());
		
		OrientationAspect.Direction output = CodecHelpers.readOrientation(buffer);
		Assert.assertTrue(north == output);
		output = CodecHelpers.readOrientation(buffer);
		Assert.assertTrue(missing == output);
		Assert.assertEquals(0, buffer.remaining());
	}

	@Test
	public void slots() throws Throwable
	{
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		CodecHelpers.writeSlot(buffer, null);
		buffer.flip();
		DeserializationContext context = new DeserializationContext(Environment.getShared()
			, buffer
			, 0L
			, false
		);
		Assert.assertNull(CodecHelpers.readSlot(context));
		
		buffer.clear();
		NonStackableItem sword = new NonStackableItem(IRON_SWORD_ITEM, Map.of(PropertyRegistry.DURABILITY, 103));
		CodecHelpers.writeSlot(buffer, ItemSlot.fromNonStack(sword));
		buffer.flip();
		context = new DeserializationContext(Environment.getShared()
			, buffer
			, 0L
			, false
		);
		ItemSlot output = CodecHelpers.readSlot(context);
		Assert.assertEquals(sword, output.nonStackable);
		
		buffer.clear();
		Items stack = new Items(STONE_ITEM, 5);
		CodecHelpers.writeSlot(buffer, ItemSlot.fromStack(stack));
		buffer.flip();
		context = new DeserializationContext(Environment.getShared()
			, buffer
			, 0L
			, false
		);
		output = CodecHelpers.readSlot(context);
		Assert.assertEquals(stack, output.stack);
	}

	@Test
	public void passiveItemSlot() throws Throwable
	{
		int id = 1;
		EntityLocation location = new EntityLocation(-5.4f, 6.6f, 0.0f);
		EntityLocation velocity = new EntityLocation(-0.4f, 2.6f, -5.1f);
		NonStackableItem sword = new NonStackableItem(IRON_SWORD_ITEM, Map.of(PropertyRegistry.DURABILITY, 103));
		Object extendedData = ItemSlot.fromNonStack(sword);
		long lastAliveMillis= 2000L;
		PassiveEntity input = new PassiveEntity(id
			, PassiveType.ITEM_SLOT
			, location
			, velocity
			, extendedData
			, lastAliveMillis
		);
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		CodecHelpers.writePassiveEntity(buffer, input);
		
		buffer.flip();
		long newAliveMillis = 5500L;
		DeserializationContext context = new DeserializationContext(Environment.getShared()
			, buffer
			, newAliveMillis
			, false
		);
		
		int newId = 3;
		PassiveEntity output = CodecHelpers.readPassiveEntity(newId, context);
		Assert.assertFalse(buffer.hasRemaining());
		
		Assert.assertEquals(newId, output.id());
		Assert.assertEquals(PassiveType.ITEM_SLOT, output.type());
		Assert.assertEquals(location, output.location());
		Assert.assertEquals(sword, ((ItemSlot)output.extendedData()).nonStackable);
		Assert.assertEquals(newAliveMillis, output.lastAliveMillis());
	}
}
