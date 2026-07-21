package com.jeffdisher.october.subactions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MutableSlotManager;
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

	/**
	 * Maximum velocity of an arrow, in metres per second.
	 */
	public static final float PROJECTILE_POWER_MULTIPLIER = 25.0f;

	public static EntitySubActionReleaseWeapon deserializeFromContext(DeserializationContext context)
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
			MutableSlotManager slotManager = newEntity.getSlotManager();
			int selectedKey = slotManager.getSelectedKey();
			Assert.assertTrue(selectedKey > 0);
			NonStackableItem nonStack = slotManager.getSlot(selectedKey).nonStackable;
			Assert.assertTrue(null != nonStack);
			int maxCharge = env.tools.getChargeMillis(nonStack.type());
			Assert.assertTrue(maxCharge > 0);
			Assert.assertTrue(chargeMillis <= maxCharge);
			Item ammo = env.tools.getAmmunitionType(nonStack.type());
			Assert.assertTrue(null != ammo);
			int count = slotManager.getCount(ammo);
			Assert.assertTrue(count > 0);
			
			// Apply durability loss to the weapon.
			CommonEntitySubActionHelpers.decrementToolDurability(env, context, newEntity, slotManager, selectedKey, nonStack);
			
			// Remove the ammunition.
			slotManager.removeStackable(ammo, 1);
			
			// Create the projectile.
			float forceMultiplier = (float)chargeMillis / (float) maxCharge;
			float totalPower = PROJECTILE_POWER_MULTIPLIER * forceMultiplier;
			EntityLocation arrowLocation = SpatialHelpers.getEntityEye(newEntity);
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
		return "Release current weapon";
	}
}
