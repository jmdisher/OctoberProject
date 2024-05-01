package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;
import java.util.Random;

import com.jeffdisher.october.aspects.BlockMaterial;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Issues EntityChangeTakeDamage to the given entity and it will determine if the damage source was in range.  This may
 * change to check the range on the sender side (here), in the future.
 * In the future, we will need this to have some time cost but this is just to get the first step working.
 */
public class EntityChangeAttackEntity implements IMutationEntity
{
	public static final MutationEntityType TYPE = MutationEntityType.ATTACK_ENTITY;

	public static EntityChangeAttackEntity deserializeFromBuffer(ByteBuffer buffer)
	{
		int targetEntityId = buffer.getInt();
		return new EntityChangeAttackEntity(targetEntityId);
	}


	private final int _targetEntityId;

	public EntityChangeAttackEntity(int targetEntityId)
	{
		// Make sure that this is positive (currently no other entity types).
		Assert.assertTrue(targetEntityId > 0);
		
		_targetEntityId = targetEntityId;
	}

	@Override
	public long getTimeCostMillis()
	{
		// TODO:  Make this a real cost.
		return 0L;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		// Check that the target is in range.  We will use block breaking distance.
		boolean isInRange;
		MinimalEntity targetEntity = context.previousEntityLookUp.apply(_targetEntityId);
		if (null != targetEntity)
		{
			// The target is loaded so check the distances.
			EntityLocation targetCentre = SpatialHelpers.getEntityCentre(targetEntity.location(), targetEntity.volume());
			EntityLocation entityCentre = SpatialHelpers.getEntityCentre(newEntity.newLocation, newEntity.original.volume());
			float absX = Math.abs(targetCentre.x() - entityCentre.x());
			float absY = Math.abs(targetCentre.y() - entityCentre.y());
			float absZ = Math.abs(targetCentre.z() - entityCentre.z());
			isInRange = ((absX <= EntityChangeIncrementalBlockBreak.MAX_REACH) && (absY <= EntityChangeIncrementalBlockBreak.MAX_REACH) && (absZ <= EntityChangeIncrementalBlockBreak.MAX_REACH));
		}
		else
		{
			// Not loaded so just say no.
			isInRange = false;
		}
		if (isInRange)
		{
			// We will just use the tool speed modifier of the selected item to figure out the damage.
			// TODO:  Filter this based on some kind of target type so a sword hits harder than a pick-axe.
			NonStackableItem nonStack = newEntity.newInventory.getNonStackableForKey(newEntity.getSelectedKey());
			Item toolType = (null != nonStack)
					? nonStack.type()
					: null
			;
			Environment env = Environment.getShared();
			byte damageToApply;
			if (BlockMaterial.SWORD == env.tools.toolTargetMaterial(toolType))
			{
				int toolSpeedMultiplier = env.tools.toolSpeedModifier(toolType);
				Assert.assertTrue(toolSpeedMultiplier <= Byte.MAX_VALUE);
				damageToApply = (byte) toolSpeedMultiplier;
			}
			else
			{
				damageToApply = 1;
			}
			// Choose the target body part at random - TODO:  Handle this random value differently.
			int index = new Random().nextInt(BodyPart.values().length);
			BodyPart target = BodyPart.values()[index];
			EntityChangeTakeDamage takeDamage = new EntityChangeTakeDamage(target, damageToApply);
			context.newChangeSink.next(_targetEntityId, takeDamage);
			
			// If we have a tool with finite durability equipped, apply this amount of time to wear it down.
			if (null != nonStack)
			{
				int totalDurability = env.durability.getDurability(toolType);
				if (totalDurability > 0)
				{
					// For now, we will just apply whatever the damage was as the durability loss, but this should change later.
					int newDurability = nonStack.durability() - damageToApply;
					int selectedKey = newEntity.getSelectedKey();
					if (newDurability > 0)
					{
						// Write this back.
						NonStackableItem updated = new NonStackableItem(toolType, newDurability);
						newEntity.newInventory.replaceNonStackable(selectedKey, updated);
					}
					else
					{
						// Remove this and clear the selection.
						newEntity.newInventory.removeNonStackableItems(selectedKey);
						newEntity.setSelectedKey(Entity.NO_SELECTION);
					}
				}
			}
		}
		return isInRange;
	}

	@Override
	public MutationEntityType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		buffer.putInt(_targetEntityId);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Target entity may have moved so don't save this.
		return false;
	}
}
