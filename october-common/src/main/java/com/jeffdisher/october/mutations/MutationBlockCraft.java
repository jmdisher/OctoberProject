package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;
import java.util.Set;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.registries.Craft;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.MutableInventory;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Starts or continues a crafting operation within the given block.
 */
public class MutationBlockCraft implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.CRAFT_IN_BLOCK;
	/**
	 * We will say that a crafting table gets a 10x speed boost over in-inventory crafting.
	 */
	public static final long CRAFTING_SPEED_MODIFIER = 10L;

	public static MutationBlockCraft deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		Craft craft = CodecHelpers.readCraft(buffer);
		long millisToApply = buffer.getLong();
		return new MutationBlockCraft(location, craft, millisToApply);
	}


	private final AbsoluteLocation _location;
	private final Craft _craft;
	private final long _millisToApply;

	public MutationBlockCraft(AbsoluteLocation location, Craft craft, long millisToApply)
	{
		_location = location;
		_craft = craft;
		_millisToApply = millisToApply;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _location;
	}

	@Override
	public boolean applyMutation(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		boolean didApply = false;
		
		// Make sure that we are a crafting table.
		if (BlockAspect.CRAFTING_TABLE == newBlock.getBlock())
		{
			// See if this is something new or if we are continuing.
			CraftOperation currentOperation = newBlock.getCrafting();
			Craft currentCraft = (null != currentOperation) ? currentOperation.selectedCraft() : null;
			Set<Craft.Classification> classifications = Set.of(Craft.Classification.TRIVIAL, Craft.Classification.COMMON);
			if ((null == _craft) || (_craft == currentCraft))
			{
				// We are continuing but we may have already finished.
				if (null != currentOperation)
				{
					long millisToApply = CRAFTING_SPEED_MODIFIER * _millisToApply;
					long completedMillis = currentOperation.completedMillis() + millisToApply;
					if (completedMillis >= currentCraft.millisPerCraft)
					{
						// We are done so try to apply the craft.
						Inventory inventory = newBlock.getInventory();
						if (classifications.contains(currentOperation.selectedCraft().classification) && currentCraft.canApply(inventory))
						{
							MutableInventory mutable = new MutableInventory(inventory);
							currentCraft.craft(mutable);
							newBlock.setInventory(mutable.freeze());
							newBlock.setCrafting(null);
						}
					}
					else
					{
						// Just save this back.
						CraftOperation updated = new CraftOperation(currentCraft, completedMillis);
						newBlock.setCrafting(updated);
					}
					// We changed something so say we applied.
					didApply = true;
				}
			}
			else
			{
				// We are changing so see if the craft makes sense and then start it.
				// Make sure that this crafting operation can be done within a table.
				Inventory inventory = newBlock.getInventory();
				if (classifications.contains(_craft.classification) && _craft.canApply(inventory))
				{
					long millisToApply = CRAFTING_SPEED_MODIFIER * _millisToApply;
					CraftOperation updated = new CraftOperation(_craft, millisToApply);
					newBlock.setCrafting(updated);
					// We changed something so say we applied.
					didApply = true;
				}
			}
		}
		return didApply;
	}

	@Override
	public MutationBlockType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeAbsoluteLocation(buffer, _location);
		CodecHelpers.writeCraft(buffer, _craft);
		buffer.putLong(_millisToApply);
	}
}
