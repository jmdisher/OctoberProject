package com.jeffdisher.october.worldgen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.LightAspect;
import com.jeffdisher.october.aspects.OrientationAspect;
import com.jeffdisher.october.aspects.PlantRegistry;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.logic.CompositeHelpers;
import com.jeffdisher.october.mutations.MutationBlockOverwriteInternal;
import com.jeffdisher.october.mutations.MutationBlockPeriodic;
import com.jeffdisher.october.properties.PropertyRegistry;
import com.jeffdisher.october.properties.PropertyType;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.utils.Assert;
import com.jeffdisher.october.utils.Encoding;


/**
 * A definition of a structure which can be injected into a cuboid when it is generated.
 * Note that these are loaded by StructureLoader and need to account for not just block types but also special follow-up
 * mutations related to the blocks added to the structure, such as lighting updates and growth behaviour.
 */
public class Structure
{
	public static final short REPLACE_ALL = -1;
	private final AspectData[][] _allLayerBlocks;
	private final int _width;

	/**
	 * Creates the structure with the block type values in allLayerBlocks and the given width to use when interpreting
	 * rows.
	 * 
	 * @param allLayerBlocks The aspect data of the structure (null values will be skipped).
	 * @param width The width of a single row within the inner-most array.
	 */
	public Structure(AspectData[][] allLayerBlocks, int width)
	{
		_allLayerBlocks = allLayerBlocks;
		_width = width;
	}

	/**
	 * Determines the total volume occupied by the structure.
	 * 
	 * @return The volume, as an AbsoluteLocation.
	 */
	public AbsoluteLocation totalVolume()
	{
		return _totalVolume();
	}

	/**
	 * Checks if this structure intersects with the cuboid at cuboidAddress, based in globalRoot, and rotated.
	 * 
	 * @param cuboidAddress The cuboid being generated.
	 * @param globalRoot The global root location where the structure should be generated.
	 * @param rotation The rotation of the structure.
	 * @return True if any part of the receiver would intersect the given cuboid when injected at globalRoot with
	 * rotation.
	 */
	public boolean doesIntersectCuboid(CuboidAddress cuboidAddress, AbsoluteLocation globalRoot, OrientationAspect.Direction rotation)
	{
		AbsoluteLocation rotatedVolume = rotation.rotateAboutZ(_totalVolume());
		int minX;
		int maxX;
		if (rotatedVolume.x() > 0)
		{
			minX = globalRoot.x();
			maxX = globalRoot.x() + rotatedVolume.x() - 1;
		}
		else
		{
			minX = (rotatedVolume.x() + 1);
			maxX = globalRoot.x();
		}
		int minY;
		int maxY;
		if (rotatedVolume.y() > 0)
		{
			minY = globalRoot.y();
			maxY = globalRoot.y() + rotatedVolume.y() - 1;
		}
		else
		{
			minY = (rotatedVolume.y() + 1);
			maxY = globalRoot.y();
		}
		int minZ = globalRoot.z();
		int maxZ = globalRoot.z() + rotatedVolume.z() - 1;
		
		AbsoluteLocation base = cuboidAddress.getBase();
		int baseX = base.x();
		int highX = base.x() + Encoding.CUBOID_EDGE_SIZE;
		int baseY = base.y();
		int highY = base.y() + Encoding.CUBOID_EDGE_SIZE;
		int baseZ = base.z();
		int highZ = base.z() + Encoding.CUBOID_EDGE_SIZE;
		return (((baseX <= minX) && (minX <= highX)) || ((baseX <= maxX) && (maxX <= highX)))
			&& (((baseY <= minY) && (minY <= highY)) || ((baseY <= maxY) && (maxY <= highY)))
			&& (((baseZ <= minZ) && (minZ <= highZ)) || ((baseZ <= maxZ) && (maxZ <= highZ)))
		;
	}

	/**
	 * Applies the receiver structure to the given cuboid, rooting it at rootLocation.  It is possible that the
	 * structure is not inside the cuboid.
	 * Given that some block types need periodic updates scheduled (plants, for example), a map of periodic update
	 * requirements is returned.
	 * 
	 * @param cuboid The cuboid to modify.
	 * @param rootLocation The root location, in absolute coordinates, where the bottom-south-west corner of this
	 * structure should be written.  Note that this is the bottom-south-west corner of the input but the other blocks
	 * will extend from this block based on the rotation parameter.
	 * @param rotation The direction to rotate (from NORTH), about the south-west corner.
	 * @param replaceTypeMask If >=0, only blocks with this type number will be replaced (pass -1 to replace
	 * everything).
	 * @return Follow-up changes to apply after this structure after it is loaded.
	 */
	public FollowUp applyToCuboid(CuboidData cuboid, AbsoluteLocation rootLocation, OrientationAspect.Direction rotation, short replaceTypeMask)
	{
		int sizeX = _width;
		int sizeY = _getYSize();
		int sizeZ = _allLayerBlocks.length;
		
		AbsoluteLocation baseCuboidLocation = cuboid.getCuboidAddress().getBase();
		int rootX = rootLocation.x();
		int rootY = rootLocation.y();
		int rootZ = rootLocation.z();
		
		// Determine the bounds in our local coordinates (baseOffset is relative to the cuboid, so negative numbers mean we start in the middle of our data).
		// Rotation is in the XY plane so we can avoid extra work in Z but not X or Y (at least not without making the code very confusing).
		int writeX = rootX - baseCuboidLocation.x();
		int writeY = rootY - baseCuboidLocation.y();
		int readZ;
		int writeZ;
		int countZ;
		int baseZ = rootZ - baseCuboidLocation.z();
		if (baseZ >= 0)
		{
			readZ = 0;
			writeZ = baseZ;
			countZ = Math.min(sizeZ, Encoding.CUBOID_EDGE_SIZE - writeZ);
		}
		else
		{
			readZ = -baseZ;
			writeZ = 0;
			countZ = Math.min(sizeZ - readZ, Encoding.CUBOID_EDGE_SIZE);
		}
		
		// Now we can copy, bearing in mind that we need to synthesize events to run after loading.
		Environment env = Environment.getShared();
		LightAspect lights = env.lighting;
		PlantRegistry plants = env.plants;
		short replacementBlock = env.special.AIR.item().number();
		List<MutationBlockOverwriteInternal> overwriteMutations = new ArrayList<>();
		Map<BlockAddress, Long> periodicMutationMillis = new HashMap<>();
		for (int c = 0; c < countZ; ++c)
		{
			AspectData[] layer = _allLayerBlocks[readZ + c];
			for (int readY = 0; readY < sizeY; ++readY)
			{
				for (int readX = 0; readX < sizeX; ++readX)
				{
					int readIndex = (readY * _width) + readX;
					AspectData aspectData = layer[readIndex];
					// null means "ignore".
					if (null != aspectData)
					{
						AbsoluteLocation offsetLocation = rotation.rotateAboutZ(new AbsoluteLocation(readX, readY, c));
						// We only write if this offsetLocation will write into the cuboid.
						int localX = writeX + offsetLocation.x();
						int localY = writeY + offsetLocation.y();
						if ((localX >= 0) && (localX < Encoding.CUBOID_EDGE_SIZE)
							&& (localY >= 0) && (localY < Encoding.CUBOID_EDGE_SIZE)
						)
						{
							AbsoluteLocation thisBlock = baseCuboidLocation.getRelative(localX, localY, writeZ + offsetLocation.z());
							BlockAddress blockAddress = thisBlock.getBlockAddress();
							// We will only replace this if it is the mast type or there is no mask type.
							if ((replaceTypeMask < 0) || (replaceTypeMask == cuboid.getData15(AspectRegistry.BLOCK, blockAddress)))
							{
								// The block is required in the aspect data.
								Block block = aspectData.block;
								Assert.assertTrue(null != block);
								
								// Lighting updates are handled somewhat specially (not just as a simple mutation - since they
								// are so common, they are specially optimized).  Therefore, we will just handle lighting
								// updates and growth mutation requirements the same way:  Make the block air and return a
								// replace block mutation.
								boolean isActive = false;
								boolean needsLightUpdate = (lights.getLightEmission(block, isActive) > 0);
								boolean needsGrowth = (plants.growthDivisor(block) > 0);
								boolean isComposite = CompositeHelpers.isCornerstone(block);
								
								// Schedule the periodic updates for this block type based on what type it is.
								if (needsLightUpdate)
								{
									// Lighting updates require that the block be placed to trigger the lighting update.
									cuboid.setData15(AspectRegistry.BLOCK, blockAddress, replacementBlock);
									overwriteMutations.add(new MutationBlockOverwriteInternal(thisBlock, block));
									
									// We can't have other data if we are using this special path.
									Assert.assertTrue(null == aspectData.normalInventory);
									Assert.assertTrue(null == aspectData.orientation);
									Assert.assertTrue(null == aspectData.specialItemSlot);
								}
								else
								{
									// Anything other than a lighting update can be placed directly and some require periodic updates.
									cuboid.setData15(AspectRegistry.BLOCK, blockAddress, block.item().number());
									if (null != aspectData.normalInventory)
									{
										Assert.assertTrue((env.stations.getNormalInventorySize(block) > 0) || env.blocks.hasEmptyBlockInventory(block, isActive));
										cuboid.setDataSpecial(AspectRegistry.INVENTORY, blockAddress, aspectData.normalInventory);
									}
									if (null != aspectData.orientation)
									{
										if (OrientationAspect.Direction.DOWN == aspectData.orientation)
										{
											Assert.assertTrue(OrientationAspect.doesAllowDownwardOutput(block));
										}
										else
										{
											Assert.assertTrue(OrientationAspect.HAS_ORIENTATION.contains(block.item().id()));
										}
										OrientationAspect.Direction rotatedOrientation = rotation.rotateOrientation(aspectData.orientation);
										cuboid.setData7(AspectRegistry.ORIENTATION, blockAddress, OrientationAspect.directionToByte(rotatedOrientation));
									}
									if (null != aspectData.specialItemSlot)
									{
										Assert.assertTrue(env.specialSlot.hasSpecialSlot(block));
										// Check the special slot since location references need to be re-interpreted (we consider them relative to the current block by north).
										ItemSlot specialItemSlot = aspectData.specialItemSlot;
										if ((null != specialItemSlot.nonStackable) && specialItemSlot.nonStackable.properties().containsKey(PropertyRegistry.LOCATION))
										{
											Map<PropertyType<?>, Object> properties = new HashMap<>(specialItemSlot.nonStackable.properties());
											AbsoluteLocation placeholder = (AbsoluteLocation) properties.get(PropertyRegistry.LOCATION);
											AbsoluteLocation rotatedPlaceholder = rotation.rotateAboutZ(placeholder);
											AbsoluteLocation updated = thisBlock.getRelative(rotatedPlaceholder.x(), rotatedPlaceholder.y(), rotatedPlaceholder.z());
											properties.put(PropertyRegistry.LOCATION, updated);
											NonStackableItem finalItem = new NonStackableItem(specialItemSlot.nonStackable.type(), properties);
											specialItemSlot = ItemSlot.fromNonStack(finalItem);
										}
										cuboid.setDataSpecial(AspectRegistry.SPECIAL_ITEM_SLOT, blockAddress, specialItemSlot);
									}
									
									long perioidicMillisDelay = 0L;
									if (needsGrowth)
									{
										perioidicMillisDelay = MutationBlockPeriodic.MILLIS_BETWEEN_GROWTH_CALLS;
									}
									else if (isComposite)
									{
										perioidicMillisDelay = MutationBlockPeriodic.MILLIS_BETWEEN_GROWTH_CALLS;
									}
									
									if (perioidicMillisDelay > 0L)
									{
										periodicMutationMillis.put(thisBlock.getBlockAddress(), perioidicMillisDelay);
									}
								}
							}
						}
					}
				}
			}
		}
		return new FollowUp(overwriteMutations, periodicMutationMillis);
	}


	private int _getYSize()
	{
		return _allLayerBlocks[0].length / _width;
	}

	private AbsoluteLocation _totalVolume()
	{
		return new AbsoluteLocation(_width
			, _getYSize()
			, _allLayerBlocks.length
		);
	}


	public static record FollowUp(List<MutationBlockOverwriteInternal> overwriteMutations
		, Map<BlockAddress, Long> periodicMutationMillis
	) {
		public boolean isEmpty()
		{
			return overwriteMutations.isEmpty() && periodicMutationMillis.isEmpty();
		}
	}

	public static record AspectData(Block block, Inventory normalInventory, OrientationAspect.Direction orientation, ItemSlot specialItemSlot) {}
}
