package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Craft;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * A special kind of crafting activity performed by a user.  This is similar to EntityChangeCraft but is specifically
 * related to crafting within a crafting station type of block.  A null operation will merely continue what is already
 * being crafted within the block or a non-null one will be started.
 * Note that this approach also means that multiple users can craft within the block at the same time.  In fact, the
 * reason why this can receive a null operation, while the in-entity crafting can't is to avoid racing with multiple
 * entities within the same tick (since that could result in one entity completing the operation while another starts
 * another one, instead of also helping to complete it).
 */
public class EntityChangeCraftInBlock implements IMutationEntity<IMutablePlayerEntity>
{
	public static final MutationEntityType TYPE = MutationEntityType.CRAFT_IN_BLOCK;

	public static EntityChangeCraftInBlock deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation targetBlock = CodecHelpers.readAbsoluteLocation(buffer);
		Craft craft = CodecHelpers.readCraft(buffer);
		buffer.getLong();
		return new EntityChangeCraftInBlock(targetBlock, craft);
	}


	private final AbsoluteLocation _targetBlock;
	private final Craft _craft;

	public EntityChangeCraftInBlock(AbsoluteLocation targetBlock, Craft craft)
	{
		Assert.assertTrue(null != targetBlock);
		// Note that craft can be null if it just means "continue".
		
		_targetBlock = targetBlock;
		_craft = craft;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		Environment env = Environment.getShared();
		// Make sure that the block is within range and is a crafting table.
		float distance = SpatialHelpers.distanceFromMutableEyeToBlockSurface(newEntity, _targetBlock);
		boolean isLocationClose = (distance <= MiscConstants.REACH_BLOCK);
		// Note that the cuboid could theoretically not be loaded (although this shouldn't happen in normal clients).
		BlockProxy proxy = context.previousBlockLookUp.apply(_targetBlock);
		boolean isCraftingTable = (null != proxy) && (env.stations.getManualMultiplier(proxy.getBlock()) > 0);
		
		boolean didApply = false;
		if (isLocationClose && isCraftingTable)
		{
			// Pass the mutation into the block.
			// (note that we verify that this is valid for the block type in MutationBlockCraft)
			MutationBlockCraft mutation = new MutationBlockCraft(_targetBlock, _craft, context.millisPerTick);
			context.mutationSink.next(mutation);
			didApply = true;
		}
		if (didApply)
		{
			// Crafting expends energy.
			int cost = EntityChangePeriodic.ENERGY_COST_PER_TICK_BLOCK_CRAFT;
			newEntity.applyEnergyCost(cost);
			
			// While this is an action which is considered primary, it should actually delay secondary actions, too.
			newEntity.setLastSpecialActionMillis(context.currentTickTimeMillis);
		}
		return didApply;
	}

	@Override
	public MutationEntityType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeAbsoluteLocation(buffer, _targetBlock);
		CodecHelpers.writeCraft(buffer, _craft);
		buffer.putLong(0L); // millis no longer stored.
	}

	@Override
	public boolean canSaveToDisk()
	{
		// The block may have changed so drop this.
		return false;
	}

	@Override
	public String toString()
	{
		return "Craft " + _craft + " in block " + _targetBlock;
	}
}
