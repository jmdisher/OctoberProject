package com.jeffdisher.october.changes;

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.worldgen.CuboidGenerator;


public class TestCommonChanges
{
	@Test
	public void moveSuccess()
	{
		// Check that the move works if the blocks are air.
		EntityLocation oldLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		EntityLocation newLocation = new EntityLocation(0.4f, 0.0f, 0.0f);
		EntityChangeMove move = new EntityChangeMove(oldLocation, 0L, 0.4f, 0.0f);
		CuboidData air = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ItemRegistry.AIR);
		CuboidData stone = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)-1), ItemRegistry.STONE);
		TickProcessingContext context = new TickProcessingContext(0L
				, (AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), (location.z() >= 0) ? air : stone)
				, null
				, null
		);
		Entity original = new Entity(1, oldLocation, 0.0f, new EntityVolume(1.2f, 0.5f), 0.4f, new Inventory(10, Map.of(), 0));
		MutableEntity newEntity = new MutableEntity(original);
		boolean didApply = move.applyChange(context, newEntity);
		Assert.assertTrue(didApply);
		Assert.assertEquals(newLocation, newEntity.newLocation);
	}

	@Test
	public void moveBarrier()
	{
		// Check that the move fails if the blocks are stone.
		EntityLocation oldLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		EntityChangeMove move = new EntityChangeMove(oldLocation, 0L, 0.4f, 0.0f);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ItemRegistry.STONE);
		TickProcessingContext context = new TickProcessingContext(0L
				, (AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid)
				, null
				, null
		);
		Entity original = new Entity(1, oldLocation, 0.0f, new EntityVolume(1.2f, 0.5f), 0.4f, new Inventory(10, Map.of(), 0));
		MutableEntity newEntity = new MutableEntity(original);
		boolean didApply = move.applyChange(context, newEntity);
		Assert.assertFalse(didApply);
		Assert.assertEquals(oldLocation, newEntity.newLocation);
	}

	@Test
	public void moveMissing()
	{
		// Check that the move fails if the target cuboid is missing.
		EntityLocation oldLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		EntityChangeMove move = new EntityChangeMove(oldLocation, 0L, 0.4f, 0.0f);
		TickProcessingContext context = new TickProcessingContext(0L
				, (AbsoluteLocation location) -> null
				, null
				, null
		);
		Entity original = new Entity(1, oldLocation, 0.0f, new EntityVolume(1.2f, 0.5f), 0.4f, new Inventory(10, Map.of(), 0));
		MutableEntity newEntity = new MutableEntity(original);
		boolean didApply = move.applyChange(context, newEntity);
		Assert.assertFalse(didApply);
		Assert.assertEquals(oldLocation, newEntity.newLocation);
	}

	@Test
	public void fallingThroughAir()
	{
		// Position us in an air block and make sure that we fall.
		EntityLocation oldLocation = new EntityLocation(0.0f, 0.0f, 10.0f);
		EntityChangeMove move = new EntityChangeMove(oldLocation, 0L, 0.4f, 0.0f);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ItemRegistry.AIR);
		TickProcessingContext context = new TickProcessingContext(0L
				, (AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), cuboid)
				, null
				, null
		);
		// We start with a zero z-vector since we should start falling.
		Entity original = new Entity(1, oldLocation, 0.0f, new EntityVolume(1.2f, 0.5f), 0.4f, new Inventory(10, Map.of(), 0));
		MutableEntity newEntity = new MutableEntity(original);
		boolean didApply = move.applyChange(context, newEntity);
		Assert.assertTrue(didApply);
		// We expect that we fell for 100 ms so we would have applied acceleration for 1/10 second.
		float expectedZVector = -0.98f;
		// This movement would then be applied for 1/10 second.
		EntityLocation expectedLocation = new EntityLocation(oldLocation.x() + 0.4f, oldLocation.y(), oldLocation.z() + (expectedZVector / 10.0f));
		Assert.assertEquals(expectedZVector, newEntity.newZVelocityPerSecond, 0.01f);
		Assert.assertEquals(expectedLocation, newEntity.newLocation);
	}

	@Test
	public void jumpAndFall()
	{
		// Jump into the air and fall back down.
		EntityLocation oldLocation = new EntityLocation(0.0f, 0.0f, 0.0f);
		CuboidData air = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ItemRegistry.AIR);
		CuboidData stone = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)-1), ItemRegistry.STONE);
		TickProcessingContext context = new TickProcessingContext(0L
				, (AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), (location.z() >= 0) ? air : stone)
				, null
				, null
		);
		Entity original = new Entity(1, oldLocation, 0.0f, new EntityVolume(1.2f, 0.5f), 0.4f, new Inventory(10, Map.of(), 0));
		MutableEntity newEntity = new MutableEntity(original);
		
		EntityChangeJump jump = new EntityChangeJump();
		boolean didApply = jump.applyChange(context, newEntity);
		Assert.assertTrue(didApply);
		
		// The jump doesn't move, just sets the vector.
		Assert.assertEquals(5.88f, newEntity.newZVelocityPerSecond, 0.01f);
		Assert.assertEquals(oldLocation, newEntity.newLocation);
		
		// Try a few falling steps to see how we sink back to the ground.
		for (int i = 0; i < 10; ++i)
		{
			EntityChangeMove fall = new EntityChangeMove(newEntity.newLocation, 100L, 0.0f, 0.0f);
			didApply = fall.applyChange(context, newEntity);
			Assert.assertTrue(didApply);
			Assert.assertTrue(newEntity.newLocation.z() > 0.0f);
		}
		// The 11th step puts us back on the ground.
		EntityChangeMove fall = new EntityChangeMove(newEntity.newLocation, 100L, 0.0f, 0.0f);
		didApply = fall.applyChange(context, newEntity);
		Assert.assertTrue(didApply);
		Assert.assertTrue(0.0f == newEntity.newLocation.z());
		// However, the vector is still drawing us down (since the vector is updated at the beginning of the move, not the end).
		Assert.assertEquals(-4.9f, newEntity.newZVelocityPerSecond, 0.01f);
		
		// Fall one last time to finalize "impact".
		fall = new EntityChangeMove(newEntity.newLocation, 100L, 0.0f, 0.0f);
		didApply = fall.applyChange(context, newEntity);
		Assert.assertTrue(didApply);
		Assert.assertTrue(0.0f == newEntity.newLocation.z());
		Assert.assertEquals(0.0f, newEntity.newZVelocityPerSecond, 0.01f);
	}
}
