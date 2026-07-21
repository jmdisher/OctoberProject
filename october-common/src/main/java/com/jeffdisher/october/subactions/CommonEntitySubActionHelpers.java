package com.jeffdisher.october.subactions;

import com.jeffdisher.october.aspects.BlockMaterial;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.PropertyHelpers;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.MutableSlotManager;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * A collection of helpers used by various sub-actions.
 */
public class CommonEntitySubActionHelpers
{
	/**
	 * A common helper to reduce the active tool durability, accounting for possible durability enchantments, also
	 * clearing the tool from the inventory and hotbar if it breaks.  Note that no durability is lost in creative mode.
	 * 
	 * @param env The environment.
	 * @param context The current tick context.
	 * @param mutableEntity The mutable entity using the tool.
	 * @param slotManager The mutable slot manager of the entity.
	 * @param toolInventoryKey The key where the inventory is located in the entity's inventory.
	 * @param tool The tool instance used.
	 */
	public static void decrementToolDurability(Environment env
		, TickProcessingContext context
		, IMutablePlayerEntity mutableEntity
		, MutableSlotManager slotManager
		, int toolInventoryKey
		, NonStackableItem tool
	)
	{
		// There is no random on the client so just leave the tool durability unchanged (this avoids a gratuitous client
		// data update when we rely on the server, anyway).
		if ((null != tool) && !mutableEntity.isCreativeMode() && (null != context.randomInt))
		{
			int totalDurability = env.durability.getDurability(tool.type());
			if (totalDurability > 0)
			{
				// The durability to remove is different if this is a tool (since that durability is in millis) versus a weapon (since that durability is in uses).
				int durabilityToRemove = (BlockMaterial.NO_MATERIAL == env.tools.toolTargetMaterial(tool.type()))
					? 1
					: (int)context.millisPerTick
				;
				int randomNumberTo255 = context.randomInt.applyAsInt(256);
				NonStackableItem updated = PropertyHelpers.reduceDurabilityOrBreak(tool, durabilityToRemove, randomNumberTo255);
				if (null != updated)
				{
					// Write this back.
					slotManager.replaceNonStackable(toolInventoryKey, updated);
				}
				else
				{
					// Remove this and clear the selection.
					slotManager.removeNonStackable(toolInventoryKey);
				}
			}
		}
	}
}
