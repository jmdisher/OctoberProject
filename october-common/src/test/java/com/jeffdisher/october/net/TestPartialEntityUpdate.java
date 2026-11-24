package com.jeffdisher.october.net;

import java.nio.ByteBuffer;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.creatures.CommonBreedingLogic;
import com.jeffdisher.october.creatures.ExtensionLivestock;
import com.jeffdisher.october.creatures.ExtensionVillager;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.MutablePartialEntity;
import com.jeffdisher.october.types.PartialEntity;


public class TestPartialEntityUpdate
{
	private static Environment ENV;
	private static EntityType COW;
	@BeforeClass
	public static void setup() throws Throwable
	{
		ENV = Environment.createSharedInstance();
		COW = ENV.creatures.getTypeById("op.cow");
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void basicEntityChange() throws Throwable
	{
		MutableEntity mutable = MutableEntity.createForTest(1);
		Entity initial = mutable.freeze();
		mutable.setCreativeMode(true);
		Entity unchanged = mutable.freeze();
		mutable.newYaw = (byte)66;
		Entity changed = mutable.freeze();
		Assert.assertTrue(PartialEntityUpdate.canDescribeChange(initial, changed));
		Assert.assertFalse(PartialEntityUpdate.canDescribeChange(initial, unchanged));
		
		PartialEntity partial = PartialEntity.fromEntity(changed);
		PartialEntityUpdate update = new PartialEntityUpdate(partial);
		
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		update.serializeToNetworkBuffer(buffer);
		Assert.assertEquals(21, buffer.position());
		buffer.flip();
		PartialEntityUpdate read = PartialEntityUpdate.deserializeFromNetworkBuffer(buffer);
		
		MutablePartialEntity toUpdate = MutablePartialEntity.existing(PartialEntity.fromEntity(initial));
		read.applyToEntity(toUpdate);
		PartialEntity output = toUpdate.freeze();
		
		Assert.assertEquals(partial.yaw(), output.yaw());
	}

	@Test
	public void basicCreatureChange() throws Throwable
	{
		EntityLocation location = new EntityLocation(1.2f, -5.6f, 10.0f);
		EntityLocation newLocation = new EntityLocation(-71.2f, -25.6f, 10.6f);
		CreatureEntity initial = CreatureEntity.create(-1, COW, location, 1000L);
		MutableCreature mutable = MutableCreature.existing(initial);
		CreatureEntity unchanged = mutable.freeze();
		mutable.newLocation = newLocation;
		CreatureEntity changed = mutable.freeze();
		Assert.assertTrue(PartialEntityUpdate.canDescribeCreatureChange(initial, changed));
		Assert.assertFalse(PartialEntityUpdate.canDescribeCreatureChange(initial, unchanged));
		
		PartialEntity partial = PartialEntity.fromCreature(changed);
		PartialEntityUpdate update = new PartialEntityUpdate(partial);
		
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		update.serializeToNetworkBuffer(buffer);
		Assert.assertEquals(27, buffer.position());
		buffer.flip();
		PartialEntityUpdate read = PartialEntityUpdate.deserializeFromNetworkBuffer(buffer);
		
		MutablePartialEntity toUpdate = MutablePartialEntity.existing(PartialEntity.fromCreature(initial));
		read.applyToEntity(toUpdate);
		PartialEntity output = toUpdate.freeze();
		
		Assert.assertEquals(partial.location(), output.location());
	}

	@Test
	public void extendedDataCreatureChange() throws Throwable
	{
		EntityLocation location = new EntityLocation(1.2f, -5.6f, 10.0f);
		CreatureEntity initial = CreatureEntity.create(-1, COW, location, 1000L);
		MutableCreature mutable = MutableCreature.existing(initial);
		CreatureEntity unchanged = mutable.freeze();
		
		ExtensionLivestock.LivestockData oldData = (ExtensionLivestock.LivestockData) initial.extendedData();
		boolean inLoveMode = true;
		mutable.newExtendedData = new ExtensionLivestock.LivestockData(new CommonBreedingLogic.Data(inLoveMode
			, oldData.breeding().offspringLocation()
			, oldData.breeding().breedingReadyMillis()
		));
		CreatureEntity changed = mutable.freeze();
		Assert.assertTrue(PartialEntityUpdate.canDescribeCreatureChange(initial, changed));
		Assert.assertFalse(PartialEntityUpdate.canDescribeCreatureChange(initial, unchanged));
		
		PartialEntity partial = PartialEntity.fromCreature(changed);
		PartialEntityUpdate update = new PartialEntityUpdate(partial);
		
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		update.serializeToNetworkBuffer(buffer);
		Assert.assertEquals(27, buffer.position());
		buffer.flip();
		PartialEntityUpdate read = PartialEntityUpdate.deserializeFromNetworkBuffer(buffer);
		
		MutablePartialEntity toUpdate = MutablePartialEntity.existing(PartialEntity.fromCreature(initial));
		read.applyToEntity(toUpdate);
		PartialEntity output = toUpdate.freeze();
		
		Assert.assertTrue(((ExtensionLivestock.LivestockData)output.extendedData()).breeding().inLoveMode());
	}

	@Test
	public void extendedDataVillagerChange() throws Throwable
	{
		// Show that a change in the villager's non-serialized extension data does not register as a change which needs to be captured.
		EntityType villager = ENV.creatures.getTypeById("op.villager");
		CreatureEntity initial = CreatureEntity.create(-1, villager, new EntityLocation(1.2f, -5.6f, 10.0f), 1000L);
		AbsoluteLocation doorLocation = new AbsoluteLocation(1, 2, 3);
		MutableCreature mutable = MutableCreature.existing(initial);
		mutable.newExtendedData = ExtensionVillager.DataBuilder.existing((ExtensionVillager.Data) mutable.newExtendedData)
			.homeDoor(doorLocation)
			.finish()
		;
		CreatureEntity onlyDoor = mutable.freeze();
		mutable.newExtendedData = ExtensionVillager.DataBuilder.existing((ExtensionVillager.Data) mutable.newExtendedData)
			.foodValue(50)
			.finish()
		;
		CreatureEntity doorAndFood = mutable.freeze();
		
		Assert.assertFalse(PartialEntityUpdate.canDescribeCreatureChange(initial, onlyDoor));
		Assert.assertTrue(PartialEntityUpdate.canDescribeCreatureChange(initial, doorAndFood));
		
		// Now, try to apply this.
		PartialEntity partialChanged = PartialEntity.fromCreature(doorAndFood);
		PartialEntityUpdate update = new PartialEntityUpdate(partialChanged);
		
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		update.serializeToNetworkBuffer(buffer);
		Assert.assertEquals(33, buffer.position());
		buffer.flip();
		PartialEntityUpdate read = PartialEntityUpdate.deserializeFromNetworkBuffer(buffer);
		
		MutablePartialEntity toUpdate = MutablePartialEntity.existing(PartialEntity.fromCreature(initial));
		read.applyToEntity(toUpdate);
		PartialEntity output = toUpdate.freeze();
		
		Assert.assertNull(((ExtensionVillager.Data)output.extendedData()).homeDoorLocation());
		Assert.assertEquals(50, ((ExtensionVillager.Data)output.extendedData()).foodValueInStomach());
	}
}
