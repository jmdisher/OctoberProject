package com.jeffdisher.october.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.CuboidColumnAddress;
import com.jeffdisher.october.utils.Assert;


/**
 * The logic for managing what cuboid change notifications to send to the SpeculativeProjection's listener is
 * complicated so has been extracted here for clarity and testing.
 */
public class ClientChangeNotifier
{
	/**
	 * Processes incoming changes from the server and converts those into calls out to the listener.
	 * 
	 * @param listener The listener which will receive the callbacks.
	 * @param currentProjectedState The projected state created from the updated shadow state with any remaining local
	 * changes applied on top.
	 * @param shadowCuboidReader Function for loading cuboids from the updated shadow state.
	 * @param staleShadowWorld The old shadow state used during the last call.
	 * @param authoritativeChangesByCuboid The list of changes which were applied in this update.
	 * @param locallyChangedBlocksByCuboid The changes which were applied to the new projected state after applying the
	 * updates from the server to the shadow state.
	 * @param knownHeightMaps The current height maps from the updated projected state.
	 */
	public static void notifyCuboidChangesFromServer(IProjectionListener listener
			, ProjectedState currentProjectedState
			, Function<CuboidAddress, IReadOnlyCuboidData> shadowCuboidReader
			, Map<CuboidAddress, IReadOnlyCuboidData> staleShadowWorld
			, Map<CuboidAddress, List<MutationBlockSetBlock>> authoritativeChangesByCuboid
			, Map<CuboidAddress, List<AbsoluteLocation>> locallyChangedBlocksByCuboid
			, Map<CuboidColumnAddress, ColumnHeightMap> knownHeightMaps
	)
	{
		Map<CuboidAddress, IReadOnlyCuboidData> cuboidsToReport = new HashMap<>();
		Map<CuboidAddress, Set<BlockAddress>> changedBlocks = new HashMap<>();
		Set<Aspect<?, ?>> changedAspects = new HashSet<>();
		
		// This call rebuilds the projected changes but we want to store the old version to know what changed.
		Map<AbsoluteLocation, BlockProxy> previousProjectedChanges = currentProjectedState.projectedBlockChanges;
		currentProjectedState.projectedBlockChanges = new HashMap<>();
		
		// We want to add all changes which can in from the server.
		for (Map.Entry<CuboidAddress, List<MutationBlockSetBlock>> changed : authoritativeChangesByCuboid.entrySet())
		{
			CuboidAddress address = changed.getKey();
			Set<BlockAddress> set = changedBlocks.get(address);
			IReadOnlyCuboidData activeVersion = currentProjectedState.projectedWorld.get(address);
			IReadOnlyCuboidData staleCuboid = staleShadowWorld.get(address);
			// This should never be null (the server shouldn't describe what it is unloading).
			Assert.assertTrue(null != activeVersion);
			Assert.assertTrue(null != staleCuboid);
			for (MutationBlockSetBlock setBlock : changed.getValue())
			{
				// We want to report this if it isn't just an echo of something we already reported.
				AbsoluteLocation location = setBlock.getAbsoluteLocation();
				// See if there is an entry from what we are rebuilding (since we will already handle that, below).
				if (!previousProjectedChanges.containsKey(location))
				{
					BlockAddress block = location.getBlockAddress();
					BlockProxy projected = new BlockProxy(block, activeVersion);
					BlockProxy previousReport = currentProjectedState.projectedBlockChanges.get(location);
					if ((null == previousReport) || !previousReport.doAspectsMatch(projected))
					{
						if (null == set)
						{
							set = new HashSet<>();
							changedBlocks.put(address, set);
						}
						set.add(block);
						
						// See what aspects changed (may require loading the proxy for real data).
						Set<Aspect<?, ?>> mismatches;
						if (null == previousReport)
						{
							BlockProxy stale = new BlockProxy(block, staleCuboid);
							mismatches = stale.checkMismatchedAspects(projected);
						}
						else
						{
							mismatches = previousReport.checkMismatchedAspects(projected);
						}
						// Note that these mismatches can be empty if the result is part of something the local user already did.
						changedAspects.addAll(mismatches);
					}
				}
			}
			if (null != set)
			{
				cuboidsToReport.put(address, activeVersion);
			}
		}
		
		// Check the local changes to see if any should be reported.
		for (Map.Entry<CuboidAddress, List<AbsoluteLocation>> changed : locallyChangedBlocksByCuboid.entrySet())
		{
			// The changed cuboid addresses is just a set of cuboids where something _may_ have changed so see if it modified the actual data.
			// Remember that these are copy-on-write so we can just instance-compare.
			CuboidAddress address = changed.getKey();
			IReadOnlyCuboidData activeVersion = currentProjectedState.projectedWorld.get(address);
			IReadOnlyCuboidData shadowVersion = shadowCuboidReader.apply(address);
			if (activeVersion != shadowVersion)
			{
				// Also, record any of the changed locations here for future reverts.
				for (AbsoluteLocation location : changed.getValue())
				{
					// See if there is an entry from what we are rebuilding (since we will already handle that, below).
					if (!previousProjectedChanges.containsKey(location))
					{
						BlockAddress block = location.getBlockAddress();
						BlockProxy projected = new BlockProxy(block, activeVersion);
						BlockProxy previousReport = currentProjectedState.projectedBlockChanges.get(location);
						if (null == previousReport)
						{
							// We will claim that we are comparing against the shadow version so we don't report non-changes.
							previousReport = new BlockProxy(block, shadowVersion);
						}
						
						if (!previousReport.doAspectsMatch(projected))
						{
							currentProjectedState.projectedBlockChanges.put(location, projected);
							
							// We also need to report this cuboid as changed.
							cuboidsToReport.put(address, activeVersion);
							Set<BlockAddress> set = changedBlocks.get(address);
							if (null == set)
							{
								set = new HashSet<>();
								changedBlocks.put(address, set);
							}
							set.add(block);
							
							// See what aspects changed.
							Set<Aspect<?, ?>> mismatches = previousReport.checkMismatchedAspects(projected);
							Assert.assertTrue(!mismatches.isEmpty());
							changedAspects.addAll(mismatches);
						}
					}
				}
			}
		}
		
		// See if any of the previously projected changes are still changed in the new projected world.
		for (Map.Entry<AbsoluteLocation, BlockProxy> changeEntry : previousProjectedChanges.entrySet())
		{
			AbsoluteLocation blockLocation = changeEntry.getKey();
			CuboidAddress cuboidAddress = blockLocation.getCuboidAddress();
			BlockProxy proxy = changeEntry.getValue();
			IReadOnlyCuboidData activeVersion = currentProjectedState.projectedWorld.get(cuboidAddress);
			IReadOnlyCuboidData shadowVersion = shadowCuboidReader.apply(cuboidAddress);
			BlockAddress block = blockLocation.getBlockAddress();
			BlockProxy projected = new BlockProxy(block, activeVersion);
			BlockProxy shadow = new BlockProxy(block, shadowVersion);
			
			if (!projected.doAspectsMatch(shadow))
			{
				// This still differs so it still counts as a change.
				currentProjectedState.projectedBlockChanges.put(blockLocation, projected);
			}
			if (!projected.doAspectsMatch(proxy))
			{
				// This has changed since we last reported.
				cuboidsToReport.put(cuboidAddress, activeVersion);
				Set<BlockAddress> set = changedBlocks.get(cuboidAddress);
				if (null == set)
				{
					set = new HashSet<>();
					changedBlocks.put(cuboidAddress, set);
				}
				set.add(block);
				
				// See what aspects changed.
				Set<Aspect<?, ?>> mismatches = projected.checkMismatchedAspects(proxy);
				Assert.assertTrue(!mismatches.isEmpty());
				changedAspects.addAll(mismatches);
			}
		}
		
		// Generate any of the missing columns.
		Set<CuboidColumnAddress> missingColumns = cuboidsToReport.keySet().stream()
				.map((CuboidAddress address) -> address.getColumn())
				.filter((CuboidColumnAddress column) -> !knownHeightMaps.containsKey(column))
				.collect(Collectors.toUnmodifiableSet())
		;
		Map<CuboidColumnAddress, ColumnHeightMap> addedHeightMaps = currentProjectedState.buildColumnMaps(missingColumns);
		Map<CuboidColumnAddress, ColumnHeightMap> allHeightMaps = new HashMap<>();
		allHeightMaps.putAll(knownHeightMaps);
		allHeightMaps.putAll(addedHeightMaps);
		
		for (Map.Entry<CuboidAddress, IReadOnlyCuboidData> elt : cuboidsToReport.entrySet())
		{
			CuboidAddress address = elt.getKey();
			IReadOnlyCuboidData data = elt.getValue();
			Set<BlockAddress> blocksChanged = changedBlocks.get(address);
			listener.cuboidDidChange(data
					, allHeightMaps.get(address.getColumn())
					, blocksChanged
					, changedAspects
			);
		}
	}

	/**
	 * Processes new changes applied by the local user, to the projected state, and converts those into calls out to the
	 * listener.
	 * 
	 * @param listener The listener which will receive the callbacks.
	 * @param currentProjectedState The projected state updated with most recent local changes.
	 * @param shadowCuboidReader Function for loading cuboids from the current shadow state.
	 * @param locallyChangedBlocksByCuboid The union of all local-only changes which have been applied to the projected
	 * state.
	 */
	public static void notifyCuboidChangesFromLocal(IProjectionListener listener
			, ProjectedState currentProjectedState
			, Function<CuboidAddress, IReadOnlyCuboidData> shadowCuboidReader
			, Map<CuboidAddress, List<AbsoluteLocation>> locallyChangedBlocksByCuboid
	)
	{
		Map<CuboidAddress, IReadOnlyCuboidData> cuboidsToReport = new HashMap<>();
		Map<CuboidAddress, Set<BlockAddress>> changedBlocks = new HashMap<>();
		Set<Aspect<?, ?>> changedAspects = new HashSet<>();
		
		// Check the local changes to see if any should be reported.
		for (Map.Entry<CuboidAddress, List<AbsoluteLocation>> changed : locallyChangedBlocksByCuboid.entrySet())
		{
			// The changed cuboid addresses is just a set of cuboids where something _may_ have changed so see if it modified the actual data.
			// Remember that these are copy-on-write so we can just instance-compare.
			CuboidAddress address = changed.getKey();
			IReadOnlyCuboidData activeVersion = currentProjectedState.projectedWorld.get(address);
			IReadOnlyCuboidData shadowVersion = shadowCuboidReader.apply(address);
			if (activeVersion != shadowVersion)
			{
				// Also, record any of the changed locations here for future reverts.
				for (AbsoluteLocation location : changed.getValue())
				{
					BlockAddress block = location.getBlockAddress();
					BlockProxy projected = new BlockProxy(block, activeVersion);
					BlockProxy previousReport = currentProjectedState.projectedBlockChanges.get(location);
					if (null == previousReport)
					{
						// We will claim that we are comparing against the shadow version so we don't report non-changes.
						previousReport = new BlockProxy(block, shadowVersion);
					}
					
					if (!previousReport.doAspectsMatch(projected))
					{
						currentProjectedState.projectedBlockChanges.put(location, projected);
						
						// We also need to report this cuboid as changed.
						cuboidsToReport.put(address, activeVersion);
						Set<BlockAddress> set = changedBlocks.get(address);
						if (null == set)
						{
							set = new HashSet<>();
							changedBlocks.put(address, set);
						}
						set.add(block);
						
						// See what aspects changed.
						Set<Aspect<?, ?>> mismatches = previousReport.checkMismatchedAspects(projected);
						Assert.assertTrue(!mismatches.isEmpty());
						changedAspects.addAll(mismatches);
					}
				}
			}
		}
		
		// Generate any of the missing columns.
		Set<CuboidColumnAddress> missingColumns = cuboidsToReport.keySet().stream()
				.map((CuboidAddress address) -> address.getColumn())
				.collect(Collectors.toUnmodifiableSet())
		;
		Map<CuboidColumnAddress, ColumnHeightMap> addedHeightMaps = currentProjectedState.buildColumnMaps(missingColumns);
		Map<CuboidColumnAddress, ColumnHeightMap> allHeightMaps = new HashMap<>();
		allHeightMaps.putAll(addedHeightMaps);
		
		for (Map.Entry<CuboidAddress, IReadOnlyCuboidData> elt : cuboidsToReport.entrySet())
		{
			CuboidAddress address = elt.getKey();
			IReadOnlyCuboidData data = elt.getValue();
			Set<BlockAddress> blocksChanged = changedBlocks.get(address);
			listener.cuboidDidChange(data
					, allHeightMaps.get(address.getColumn())
					, blocksChanged
					, changedAspects
			);
		}
	}
}
