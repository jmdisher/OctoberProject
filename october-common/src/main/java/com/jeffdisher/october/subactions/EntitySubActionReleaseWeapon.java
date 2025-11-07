package com.jeffdisher.october.subactions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.mutations.CommonEntityMutationHelpers;
import com.jeffdisher.october.mutations.EntitySubActionType;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutableInventory;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.PassiveType;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Releases the charge on a weapon, allowing it to trigger.
 */
public class EntitySubActionReleaseWeapon implements IEntitySubAction<IMutablePlayerEntity>
{
	public static final EntitySubActionType TYPE = EntitySubActionType.TRIGGER_CHARGED_WEAPON;

	public static final float PROJECTILE_POWER_MULTIPLIER = 10.0f;

	public static EntitySubActionReleaseWeapon deserializeFromBuffer(ByteBuffer buffer)
	{
		return new EntitySubActionReleaseWeapon();
	}


	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		boolean didApply = false;
		
		// So long as we have a positive charge accumulated, are wielding a charged weapon, and possess ammunition, we will create the projectile and consume the ammunition.
		int chargeMillis = newEntity.getCurrentChargeMillis();
		if (chargeMillis > 0)
		{
			Environment env = Environment.getShared();
			
			// Note that we shouldn't be able to have a positive charge without a weapon and ammunition so we will assert that.
			int selectedKey = newEntity.getSelectedKey();
			Assert.assertTrue(selectedKey > 0);
			NonStackableItem nonStack = newEntity.accessMutableInventory().getNonStackableForKey(selectedKey);
			Assert.assertTrue(null != nonStack);
			int maxCharge = env.tools.getChargeMillis(nonStack.type());
			Assert.assertTrue(maxCharge > 0);
			Assert.assertTrue(chargeMillis <= maxCharge);
			Item ammo = env.tools.getAmmunitionType(nonStack.type());
			Assert.assertTrue(null != ammo);
			IMutableInventory mutableInventory = newEntity.accessMutableInventory();
			int count = mutableInventory.getCount(ammo);
			Assert.assertTrue(count > 0);
			
			// Apply durability loss to the weapon.
			CommonEntityMutationHelpers.decrementToolDurability(env, context, newEntity, mutableInventory, selectedKey, nonStack);
			
			// Remove the ammunition.
			mutableInventory.removeStackableItems(ammo, 1);
			if (1 == count)
			{
				CommonEntityMutationHelpers.rationalizeHotbar(newEntity);
			}
			
			// Create the projectile.
			float forceMultiplier = (float)chargeMillis / (float) maxCharge;
			float totalPower = PROJECTILE_POWER_MULTIPLIER * forceMultiplier;
			EntityLocation arrowLocation = SpatialHelpers.getEyeLocation(newEntity);
			EntityLocation unitFacingVector = SpatialHelpers.getUnitFacingVector(newEntity.getYaw(), newEntity.getPitch());
			EntityLocation arrowVelocity = new EntityLocation(totalPower * unitFacingVector.x(), totalPower * unitFacingVector.y(), totalPower * unitFacingVector.z());
			context.passiveSpawner.spawnPassive(PassiveType.PROJECTILE_ARROW, arrowLocation, arrowVelocity, null);
			
			// Reset the charge.
			newEntity.setCurrentChargeMillis(0);
			
			didApply = true;
		}
		return didApply;
	}

	@Override
	public EntitySubActionType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Default case.
		return true;
	}

	@Override
	public String toString()
	{
		return "Charge current weapon";
	}
}
