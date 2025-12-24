package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.CreatureExtendedData;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.logic.PropertyHelpers;
import com.jeffdisher.october.properties.PropertyRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.Craft;
import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EnchantingOperation;
import com.jeffdisher.october.types.Enchantment;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.FuelState;
import com.jeffdisher.october.types.Infusion;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.PartialPassive;
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
		DeserializationContext context = DeserializationContext.empty(Environment.getShared()
			, buffer
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
		DeserializationContext context = DeserializationContext.empty(Environment.getShared()
			, buffer
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
		CodecHelpers.writeEntityDisk(buffer, test);
		buffer.flip();
		Entity output = CodecHelpers.readEntityDisk(DeserializationContext.empty(ENV
			, buffer
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
		CodecHelpers.writeEntityNetwork(buffer, test);
		buffer.flip();
		Entity output = CodecHelpers.readEntityNetwork(DeserializationContext.empty(ENV
			, buffer
		));
		
		Assert.assertEquals(test.id(), output.id());
		Assert.assertEquals(test.ephemeralShared().localCraftOperation(), output.ephemeralShared().localCraftOperation());
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
		DeserializationContext context = DeserializationContext.empty(Environment.getShared()
			, buffer
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
		context = DeserializationContext.empty(Environment.getShared()
			, buffer
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
		NonStackableItem output = CodecHelpers.readNonStackableItem(DeserializationContext.empty(ENV
			, buffer
		));
		Assert.assertEquals(test, output);
		
		buffer.clear();
		CodecHelpers.writeNonStackableItem(buffer, null);
		buffer.flip();
		output = CodecHelpers.readNonStackableItem(DeserializationContext.empty(ENV
			, buffer
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
		FacingDirection north = FacingDirection.NORTH;
		FacingDirection missing = null;
		CodecHelpers.writeOrientation(buffer, north);
		CodecHelpers.writeOrientation(buffer, missing);
		buffer.flip();
		Assert.assertEquals(2, buffer.remaining());
		
		FacingDirection output = CodecHelpers.readOrientation(buffer);
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
		DeserializationContext context = DeserializationContext.empty(Environment.getShared()
			, buffer
		);
		Assert.assertNull(CodecHelpers.readSlot(context));
		
		buffer.clear();
		NonStackableItem sword = new NonStackableItem(IRON_SWORD_ITEM, Map.of(PropertyRegistry.DURABILITY, 103));
		CodecHelpers.writeSlot(buffer, ItemSlot.fromNonStack(sword));
		buffer.flip();
		context = DeserializationContext.empty(Environment.getShared()
			, buffer
		);
		ItemSlot output = CodecHelpers.readSlot(context);
		Assert.assertEquals(sword, output.nonStackable);
		
		buffer.clear();
		Items stack = new Items(STONE_ITEM, 5);
		CodecHelpers.writeSlot(buffer, ItemSlot.fromStack(stack));
		buffer.flip();
		context = DeserializationContext.empty(Environment.getShared()
			, buffer
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
		
		// Make sure that this works for the partial variant, as well (used for the network case, only).
		PartialPassive partial = new PartialPassive(input.id()
			, input.type()
			, input.location()
			, input.velocity()
			, input.extendedData()
		);
		buffer.clear();
		CodecHelpers.writePartialPassive(buffer, partial);
		
		buffer.flip();
		PartialPassive partialOut = CodecHelpers.readPartialPassive(buffer);
		Assert.assertFalse(buffer.hasRemaining());
		
		Assert.assertEquals(input.id(), partialOut.id());
		Assert.assertEquals(input.type(), partialOut.type());
		Assert.assertEquals(input.location(), partialOut.location());
		Assert.assertEquals(input.velocity(), partialOut.velocity());
		Assert.assertEquals(((ItemSlot)input.extendedData()).nonStackable, ((ItemSlot)partialOut.extendedData()).nonStackable);
	}

	@Test
	public void entitySerializationDifferences() throws Throwable
	{
		// Show that charge millis appears in the network but not the disk.
		MutableEntity mutable = MutableEntity.createForTest(1);
		mutable.chargeMillis = 1234;
		Entity test = mutable.freeze();
		
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		CodecHelpers.writeEntityNetwork(buffer, test);
		buffer.flip();
		Entity network = CodecHelpers.readEntityNetwork(DeserializationContext.empty(ENV
			, buffer
		));
		Assert.assertEquals(1234, network.ephemeralShared().chargeMillis());
		
		buffer = buffer.clear();
		CodecHelpers.writeEntityDisk(buffer, test);
		buffer.flip();
		Entity disk = CodecHelpers.readEntityDisk(DeserializationContext.empty(ENV
			, buffer
		));
		Assert.assertEquals(0, disk.ephemeralShared().chargeMillis());
	}

	@Test
	public void cowExtended() throws Throwable
	{
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		EntityType cowType = ENV.creatures.getTypeById("op.cow");
		CreatureEntity cow = new CreatureEntity(-1
			, cowType
			, new EntityLocation(0.0f, 0.0f, 0.0f)
			, new EntityLocation(0.0f, 0.0f, 0.0f)
			, (byte)0
			, (byte)0
			, (byte)50
			, (byte)100
			, new CreatureExtendedData.LivestockData(false, null, 50L)
			, null
		);
		CodecHelpers.writeCreatureEntity(buffer, cow, 0L);
		buffer.flip();
		CreatureEntity mid = CodecHelpers.readCreatureEntity(-2, buffer, 100L);
		
		Assert.assertEquals(false, ((CreatureExtendedData.LivestockData)mid.extendedData()).inLoveMode());
		Assert.assertEquals(null, ((CreatureExtendedData.LivestockData)mid.extendedData()).offspringLocation());
		Assert.assertEquals(150L, ((CreatureExtendedData.LivestockData)mid.extendedData()).breedingReadyMillis());
	}

	@Test
	public void passiveArrow() throws Throwable
	{
		int id = 1;
		EntityLocation location = new EntityLocation(-5.4f, 6.6f, 0.0f);
		EntityLocation velocity = new EntityLocation(-0.4f, 2.6f, -5.1f);
		long lastAliveMillis= 2000L;
		PassiveEntity input = new PassiveEntity(id
			, PassiveType.PROJECTILE_ARROW
			, location
			, velocity
			, null
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
			, false
		);
		
		int newId = 3;
		PassiveEntity output = CodecHelpers.readPassiveEntity(newId, context);
		Assert.assertFalse(buffer.hasRemaining());
		
		Assert.assertEquals(newId, output.id());
		Assert.assertEquals(PassiveType.PROJECTILE_ARROW, output.type());
		Assert.assertEquals(location, output.location());
		Assert.assertNull(output.extendedData());
		Assert.assertEquals(newAliveMillis, output.lastAliveMillis());
		
		// Make sure that this works for the partial variant, as well (used for the network case, only).
		PartialPassive partial = new PartialPassive(input.id()
			, input.type()
			, input.location()
			, input.velocity()
			, input.extendedData()
		);
		buffer.clear();
		CodecHelpers.writePartialPassive(buffer, partial);
		
		buffer.flip();
		PartialPassive partialOut = CodecHelpers.readPartialPassive(buffer);
		Assert.assertFalse(buffer.hasRemaining());
		
		Assert.assertEquals(input.id(), partialOut.id());
		Assert.assertEquals(input.type(), partialOut.type());
		Assert.assertEquals(input.location(), partialOut.location());
		Assert.assertEquals(input.velocity(), partialOut.velocity());
		Assert.assertNull(partialOut.extendedData());
	}

	@Test
	public void passiveFallingBlock() throws Throwable
	{
		int id = 1;
		EntityLocation location = new EntityLocation(-5.4f, 6.6f, 0.0f);
		EntityLocation velocity = new EntityLocation(-0.4f, 2.6f, -5.1f);
		Block sand = ENV.blocks.fromItem(ENV.items.getItemById("op.sand"));
		long lastAliveMillis= 2000L;
		PassiveEntity input = new PassiveEntity(id
			, PassiveType.FALLING_BLOCK
			, location
			, velocity
			, sand
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
			, false
		);
		
		int newId = 3;
		PassiveEntity output = CodecHelpers.readPassiveEntity(newId, context);
		Assert.assertFalse(buffer.hasRemaining());
		
		Assert.assertEquals(newId, output.id());
		Assert.assertEquals(PassiveType.FALLING_BLOCK, output.type());
		Assert.assertEquals(location, output.location());
		Assert.assertEquals(sand, output.extendedData());
		Assert.assertEquals(newAliveMillis, output.lastAliveMillis());
		
		// Make sure that this works for the partial variant, as well (used for the network case, only).
		PartialPassive partial = new PartialPassive(input.id()
			, input.type()
			, input.location()
			, input.velocity()
			, input.extendedData()
		);
		buffer.clear();
		CodecHelpers.writePartialPassive(buffer, partial);
		
		buffer.flip();
		PartialPassive partialOut = CodecHelpers.readPartialPassive(buffer);
		Assert.assertFalse(buffer.hasRemaining());
		
		Assert.assertEquals(input.id(), partialOut.id());
		Assert.assertEquals(input.type(), partialOut.type());
		Assert.assertEquals(input.location(), partialOut.location());
		Assert.assertEquals(input.velocity(), partialOut.velocity());
		Assert.assertEquals(sand, partialOut.extendedData());
	}

	@Test
	public void enchantmentRegistry() throws Throwable
	{
		// Show the basic serialization behaviour of records used by EnchantmentRegistry.
		Block enchantingTable = ENV.blocks.fromItem(ENV.items.getItemById("op.enchanting_table"));
		Enchantment enchantment = ENV.enchantments.getBlindEnchantment(enchantingTable, IRON_SWORD_ITEM, PropertyRegistry.ENCHANT_DURABILITY);
		Assert.assertNotNull(enchantment);
		Infusion infusion = ENV.enchantments.infusionForNumber(1);
		Assert.assertNotNull(infusion);
		
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		CodecHelpers.writeEnchantment(buffer, enchantment);
		CodecHelpers.writeEnchantment(buffer, null);
		CodecHelpers.writeInfusion(buffer, infusion);
		CodecHelpers.writeInfusion(buffer, null);
		
		buffer.flip();
		Enchantment eOut = CodecHelpers.readEnchantment(buffer);
		Assert.assertTrue(enchantment == eOut);
		Assert.assertNull(CodecHelpers.readEnchantment(buffer));
		Infusion iOut = CodecHelpers.readInfusion(buffer);
		Assert.assertTrue(infusion == iOut);
		Assert.assertNull(CodecHelpers.readInfusion(buffer));
		Assert.assertEquals(0, buffer.remaining());
		
		// We can also test the EnchantingOperation here since it is similar.
		buffer.clear();
		EnchantingOperation eOp = new EnchantingOperation(567L
			, enchantment
			, null
			, List.of(ItemSlot.fromStack(new Items(STONE_ITEM, 1)))
		);
		EnchantingOperation iOp = new EnchantingOperation(123L
			, null
			, infusion
			, List.of()
		);
		EnchantingOperation nullOp = null;
		CodecHelpers.writeEnchantingOperation(buffer, eOp);
		CodecHelpers.writeEnchantingOperation(buffer, iOp);
		CodecHelpers.writeEnchantingOperation(buffer, nullOp);
		
		buffer.flip();
		DeserializationContext context = DeserializationContext.empty(Environment.getShared()
			, buffer
		);
		EnchantingOperation eoOut = CodecHelpers.readEnchantingOperation(context);
		EnchantingOperation ioOut = CodecHelpers.readEnchantingOperation(context);
		EnchantingOperation nullOOut = CodecHelpers.readEnchantingOperation(context);
		Assert.assertEquals(eOp, eoOut);
		Assert.assertEquals(iOp, ioOut);
		Assert.assertNull(nullOOut);
	}
}
