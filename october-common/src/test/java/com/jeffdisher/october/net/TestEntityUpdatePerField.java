package com.jeffdisher.october.net;

import java.nio.ByteBuffer;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.PropertyHelpers;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.Craft;
import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MutableEntity;


public class TestEntityUpdatePerField
{
	private static Environment ENV;
	private static Item STONE_ITEM;
	private static Item PLANK_ITEM;
	private static Item IRON_SWORD_ITEM;
	private static Item IRON_HELMET;
	@BeforeClass
	public static void setup() throws Throwable
	{
		ENV = Environment.createSharedInstance();
		STONE_ITEM = ENV.items.getItemById("op.stone");
		PLANK_ITEM = ENV.items.getItemById("op.plank");
		IRON_SWORD_ITEM = ENV.items.getItemById("op.iron_sword");
		IRON_HELMET = ENV.items.getItemById("op.iron_helmet");
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void empty() throws Throwable
	{
		MutableEntity mutable = MutableEntity.createForTest(1);
		Entity first = mutable.freeze();
		mutable.newYaw = (byte)5;
		Entity second = mutable.freeze();
		EntityUpdatePerField update = EntityUpdatePerField.update(first, second);
		
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		update.serializeToNetworkBuffer(buffer);
		Assert.assertEquals(4, buffer.position());
		buffer.flip();
		EntityUpdatePerField read = EntityUpdatePerField.deserializeFromNetworkBuffer(buffer);
		
		MutableEntity baseline = MutableEntity.createForTest(1);
		read.applyToEntity(baseline);
		Entity output = baseline.freeze();
		
		Assert.assertEquals(second.location(), output.location());
		Assert.assertEquals(second.inventory().currentEncumbrance, output.inventory().currentEncumbrance);
		Assert.assertEquals(second.yaw(), output.yaw());
	}

	@Test
	public void inventory() throws Throwable
	{
		MutableEntity mutable = MutableEntity.createForTest(1);
		Entity first = mutable.freeze();
		mutable.newInventory.addAllItems(PLANK_ITEM, 2);
		mutable.newInventory.addNonStackableAllowingOverflow(PropertyHelpers.newItemWithDefaults(ENV, IRON_SWORD_ITEM));
		Entity second = mutable.freeze();
		EntityUpdatePerField update = EntityUpdatePerField.update(first, second);
		
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		update.serializeToNetworkBuffer(buffer);
		Assert.assertEquals(31, buffer.position());
		buffer.flip();
		EntityUpdatePerField read = EntityUpdatePerField.deserializeFromNetworkBuffer(buffer);
		
		MutableEntity baseline = MutableEntity.createForTest(1);
		read.applyToEntity(baseline);
		Entity output = baseline.freeze();
		
		Assert.assertEquals(second.location(), output.location());
		Assert.assertEquals(second.inventory().currentEncumbrance, output.inventory().currentEncumbrance);
	}

	@Test
	public void entityWithCraft() throws Throwable
	{
		MutableEntity mutable = MutableEntity.createForTest(1);
		Entity first = mutable.freeze();
		mutable.newLocalCraftOperation = new CraftOperation(ENV.crafting.getCraftById("op.stone_to_stone_brick"), 50L);
		Entity second = mutable.freeze();
		EntityUpdatePerField update = EntityUpdatePerField.update(first, second);
		
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		update.serializeToNetworkBuffer(buffer);
		Assert.assertEquals(14, buffer.position());
		buffer.flip();
		EntityUpdatePerField read = EntityUpdatePerField.deserializeFromNetworkBuffer(buffer);
		
		MutableEntity baseline = MutableEntity.createForTest(1);
		read.applyToEntity(baseline);
		Entity output = baseline.freeze();
		
		Assert.assertEquals(second.location(), output.location());
		Assert.assertEquals(second.ephemeralShared().localCraftOperation(), output.ephemeralShared().localCraftOperation());
	}

	@Test
	public void basicCoverage() throws Throwable
	{
		MutableEntity mutable = MutableEntity.createForTest(1);
		Entity first = mutable.freeze();
		mutable.isCreativeMode = true;
		mutable.newLocation = new EntityLocation(1.0f, 2.0f, -5.6f);
		mutable.newVelocity = new EntityLocation(-0.4f, 5.7f, 0.0f);
		mutable.newInventory.addAllItems(STONE_ITEM, 2);
		mutable.newHotbar[3] = 1;
		mutable.newHotbarIndex = 3;
		mutable.newArmour[BodyPart.HEAD.ordinal()] = PropertyHelpers.newItemWithDefaults(ENV, IRON_HELMET);
		mutable.newHealth = (byte)61;
		mutable.newFood = (byte)77;
		mutable.newBreath = (byte)8;
		mutable.newSpawn = new EntityLocation(14.1f, -22.5f, -5.6f);
		mutable.newLocalCraftOperation = new CraftOperation(ENV.crafting.getCraftById("op.stone_to_stone_brick"), 50L);
		mutable.chargeMillis = 1234;
		Entity second = mutable.freeze();
		EntityUpdatePerField update = EntityUpdatePerField.update(first, second);
		
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		update.serializeToNetworkBuffer(buffer);
		Assert.assertEquals(127, buffer.position());
		buffer.flip();
		EntityUpdatePerField read = EntityUpdatePerField.deserializeFromNetworkBuffer(buffer);
		
		MutableEntity baseline = MutableEntity.createForTest(1);
		read.applyToEntity(baseline);
		Entity output = baseline.freeze();
		
		Assert.assertEquals(second.isCreativeMode(), output.isCreativeMode());
		Assert.assertEquals(second.location(), output.location());
		Assert.assertEquals(second.velocity(), output.velocity());
		Assert.assertEquals(second.inventory().currentEncumbrance, output.inventory().currentEncumbrance);
		Assert.assertArrayEquals(second.hotbarItems(), output.hotbarItems());
		Assert.assertEquals(second.hotbarIndex(), output.hotbarIndex());
		Assert.assertArrayEquals(second.armourSlots(), output.armourSlots());
		Assert.assertEquals(second.health(), output.health());
		Assert.assertEquals(second.food(), output.food());
		Assert.assertEquals(second.breath(), output.breath());
		Assert.assertEquals(second.spawnLocation(), output.spawnLocation());
		Assert.assertEquals(second.ephemeralShared().localCraftOperation(), output.ephemeralShared().localCraftOperation());
		Assert.assertEquals(second.ephemeralShared().chargeMillis(), output.ephemeralShared().chargeMillis());
	}

	@Test
	public void completeCraft() throws Throwable
	{
		MutableEntity mutable = MutableEntity.createForTest(1);
		Craft craft = ENV.crafting.getCraftById("op.stone_to_stone_brick");
		mutable.newLocalCraftOperation = new CraftOperation(craft, craft.millisPerCraft - 50L);
		Entity first = mutable.freeze();
		mutable.newLocalCraftOperation = null;
		Entity second = mutable.freeze();
		EntityUpdatePerField update = EntityUpdatePerField.update(first, second);
		
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		update.serializeToNetworkBuffer(buffer);
		Assert.assertEquals(12, buffer.position());
		buffer.flip();
		EntityUpdatePerField read = EntityUpdatePerField.deserializeFromNetworkBuffer(buffer);
		
		MutableEntity baseline = MutableEntity.existing(first);
		read.applyToEntity(baseline);
		Entity output = baseline.freeze();
		
		Assert.assertEquals(second.location(), output.location());
		Assert.assertNull(output.ephemeralShared().localCraftOperation());
	}
}
