package com.jeffdisher.october.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.CuboidColumnAddress;


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
	 * @param lookups Access to look up cuboids and column height maps.
	 * @param previousNotication The data describing the last notification we sent, to avoid duplication.
	 * changes applied on top.  This call will read and write projectedBlockChanges.
	 * @param incomingChangesFromServer Authoritative changes which have come in from the server.
	 * @param localChangesApplied The union of all local-only changes which have been applied to the projected state.
	 * @param knownHeightMaps The current height maps from the updated projected state.
	 */
	public static void notifyCuboidChangesFromServer(IProjectionListener listener
			, ILookup lookups
			, PreviousNotificationDetails previousNotication
			, Map<AbsoluteLocation, MutationBlockSetBlock> incomingChangesFromServer
			, Map<AbsoluteLocation, MutationBlockSetBlock> localChangesApplied
			, Map<CuboidColumnAddress, ColumnHeightMap> knownHeightMaps
	)
	{
		Map<CuboidAddress, IReadOnlyCuboidData> cuboidsToReport = new HashMap<>();
		Map<CuboidAddress, Set<BlockAddress>> changedBlocks = new HashMap<>();
		Map<CuboidAddress, Set<Aspect<?, ?>>> changedAspects = new HashMap<>();
		
		// This call rebuilds the projected changes but we want to store the old version to know what changed.
		Map<AbsoluteLocation, MutationBlockSetBlock> previousProjectedChanges = previousNotication.clearPreviousMap();
		Map<AbsoluteLocation, MutationBlockSetBlock> newChanges = new HashMap<>();
		
		// First thing, we will merge the incoming changes from the server and remaining local changes into the projected state (as this is what will be considered "previous" in the next call).
		for (Map.Entry<AbsoluteLocation, MutationBlockSetBlock> fromServer : incomingChangesFromServer.entrySet())
		{
			AbsoluteLocation key = fromServer.getKey();
			MutationBlockSetBlock value = fromServer.getValue();
			MutationBlockSetBlock change;
			if (localChangesApplied.containsKey(key))
			{
				// We need to merge the local change on top of this.
				MutationBlockSetBlock other = localChangesApplied.get(key);
				change = MutationBlockSetBlock.merge(value, other);
			}
			else
			{
				// Just use this change.
				change = value;
			}
			newChanges.put(key, change);
		}
		
		// Add in any local changes we didn't merge in above.
		for (Map.Entry<AbsoluteLocation, MutationBlockSetBlock> local : localChangesApplied.entrySet())
		{
			AbsoluteLocation key = local.getKey();
			if (!newChanges.containsKey(key))
			{
				MutationBlockSetBlock value = local.getValue();
				newChanges.put(key, value);
			}
		}
		
		// We will report anything which has new changes since last report.
		for (Map.Entry<AbsoluteLocation, MutationBlockSetBlock> change : newChanges.entrySet())
		{
			AbsoluteLocation key = change.getKey();
			MutationBlockSetBlock current = change.getValue();
			if (previousProjectedChanges.containsKey(key))
			{
				// See if this has changed something new.
				MutationBlockSetBlock previous = previousProjectedChanges.get(key);
				if (!current.doesDataMatch(previous))
				{
					_updateCollectionsWithChange(cuboidsToReport, changedBlocks, changedAspects, lookups, key, current, previous);
				}
			}
			else
			{
				// This is a new change so just report it.
				MutationBlockSetBlock empty = new MutationBlockSetBlock(key, new byte[0]);
				_updateCollectionsWithChange(cuboidsToReport, changedBlocks, changedAspects, lookups, key, current, empty);
			}
		}
		
		// We now want to check everything we have retired from last time, reporting them if their data isn't reflected in the current projected state (means they were reverted).
		for (Map.Entry<AbsoluteLocation, MutationBlockSetBlock> previous : previousProjectedChanges.entrySet())
		{
			AbsoluteLocation key = previous.getKey();
			if (!newChanges.containsKey(key))
			{
				// This is a retired case so check it.
				// Note that we do NOT add this to projectedBlockChanges.
				MutationBlockSetBlock current = previous.getValue();
				CuboidAddress address = key.getCuboidAddress();
				IReadOnlyCuboidData cuboid = lookups.getLatestCuboid(address);
				Set<Aspect<?, ?>> changes = current.changedAspectsVersusCuboid(cuboid);
				if (!changes.isEmpty())
				{
					// If this would cause a change, it must have been reverted so notify.
					if (!changedAspects.containsKey(address))
					{
						changedAspects.put(address, new HashSet<>());
					}
					changedAspects.get(address).addAll(changes);
					if (!changedBlocks.containsKey(address))
					{
						changedBlocks.put(address, new HashSet<>());
					}
					changedBlocks.get(address).add(key.getBlockAddress());
					if (!cuboidsToReport.containsKey(address))
					{
						cuboidsToReport.put(address, cuboid);
					}
				}
			}
		}
		previousNotication.storePreviousMap(newChanges);
		
		// Generate any of the missing columns.
		Set<CuboidColumnAddress> missingColumns = cuboidsToReport.keySet().stream()
				.map((CuboidAddress address) -> address.getColumn())
				.filter((CuboidColumnAddress column) -> !knownHeightMaps.containsKey(column))
				.collect(Collectors.toUnmodifiableSet())
		;
		Map<CuboidColumnAddress, ColumnHeightMap> addedHeightMaps = lookups.generateAllHeightMaps(missingColumns);
		Map<CuboidColumnAddress, ColumnHeightMap> allHeightMaps = new HashMap<>();
		allHeightMaps.putAll(knownHeightMaps);
		allHeightMaps.putAll(addedHeightMaps);
		
		Set<CuboidAddress> unsafeLighting = previousNotication.clearUnsafeLighting();
		for (Map.Entry<CuboidAddress, IReadOnlyCuboidData> elt : cuboidsToReport.entrySet())
		{
			CuboidAddress address = elt.getKey();
			Set<Aspect<?, ?>> aspects = changedAspects.get(address);
			if (unsafeLighting.contains(address))
			{
				// We already reported a light change for this so remove that.
				aspects = new HashSet<>(aspects);
				aspects.remove(AspectRegistry.LIGHT);
			}
			if (!aspects.isEmpty())
			{
				IReadOnlyCuboidData data = elt.getValue();
				Set<BlockAddress> blocksChanged = changedBlocks.get(address);
				listener.cuboidDidChange(data
					, allHeightMaps.get(address.getColumn())
					, blocksChanged
					, aspects
				);
			}
		}
		previousNotication.storeUnsafeLighting(unsafeLighting);
	}

	/**
	 * Processes new changes applied by the local user, to the projected state, and converts those into calls out to the
	 * listener.
	 * 
	 * @param listener The listener which will receive the callbacks.
	 * @param lookups Access to look up cuboids and column height maps.
	 * @param previousNotication The data describing the last notification we sent, to avoid duplication.
	 * @param localChangesApplied The union of all local-only changes which have been applied to the projected state.
	 * @param lightingOptChanges The set of cuboids using unsafe lighting optimization, instead of normal change reporting.
	 */
	public static void notifyCuboidChangesFromLocal(IProjectionListener listener
			, ILookup lookups
			, PreviousNotificationDetails previousNotication
			, Map<AbsoluteLocation, MutationBlockSetBlock> localChangesApplied
			, Set<CuboidAddress> lightingOptChanges
	)
	{
		Map<CuboidAddress, IReadOnlyCuboidData> cuboidsToReport = new HashMap<>();
		Map<CuboidAddress, Set<BlockAddress>> changedBlocks = new HashMap<>();
		Map<CuboidAddress, Set<Aspect<?, ?>>> changedAspects = new HashMap<>();
		
		// This call rebuilds the projected lighting changes but we want to store the old version to know what changed.
		Set<CuboidAddress> previouslightingOptChanges = previousNotication.clearUnsafeLighting();
		
		// Merge the new changes into the existing changes we have reported, collecting data to notify on anything new or expanded.
		Map<AbsoluteLocation, MutationBlockSetBlock> existingProjectedChanges = previousNotication.clearPreviousMap();
		for (Map.Entry<AbsoluteLocation, MutationBlockSetBlock> local : localChangesApplied.entrySet())
		{
			AbsoluteLocation key = local.getKey();
			MutationBlockSetBlock value = local.getValue();
			if (existingProjectedChanges.containsKey(key))
			{
				// See if this needs to be expanded to account for new changes.
				MutationBlockSetBlock previous = existingProjectedChanges.get(key);
				if (!value.doesDataMatch(previous))
				{
					_updateCollectionsWithChange(cuboidsToReport, changedBlocks, changedAspects, lookups, key, value, previous);
					existingProjectedChanges.put(key, value);
				}
			}
			else
			{
				// We can just inject this change, directly.
				MutationBlockSetBlock empty = new MutationBlockSetBlock(key, new byte[0]);
				_updateCollectionsWithChange(cuboidsToReport, changedBlocks, changedAspects, lookups, key, value, empty);
				existingProjectedChanges.put(key, value);
			}
		}
		previousNotication.storePreviousMap(existingProjectedChanges);
		
		// Generate any of the missing columns.
		Set<CuboidColumnAddress> missingColumns = cuboidsToReport.keySet().stream()
				.map((CuboidAddress address) -> address.getColumn())
				.collect(Collectors.toUnmodifiableSet())
		;
		// Note that we need to add any light-opt changes to this height map set, too, since it needs to be passed into the notification.
		if (!lightingOptChanges.isEmpty())
		{
			missingColumns = new HashSet<>(missingColumns);
			missingColumns.addAll(lightingOptChanges.stream()
				.map((CuboidAddress address) -> address.getColumn())
				.toList()
			);
		}
		Map<CuboidColumnAddress, ColumnHeightMap> addedHeightMaps = lookups.generateAllHeightMaps(missingColumns);
		Map<CuboidColumnAddress, ColumnHeightMap> allHeightMaps = new HashMap<>();
		allHeightMaps.putAll(addedHeightMaps);
		
		Set<CuboidAddress> newLightChanges = new HashSet<>(lightingOptChanges);
		newLightChanges.removeAll(previouslightingOptChanges);
		for (Map.Entry<CuboidAddress, IReadOnlyCuboidData> elt : cuboidsToReport.entrySet())
		{
			CuboidAddress address = elt.getKey();
			IReadOnlyCuboidData data = elt.getValue();
			Set<BlockAddress> blocksChanged = changedBlocks.get(address);
			Set<Aspect<? ,?>> changedInCuboid = changedAspects.get(address);
			if (newLightChanges.contains(address))
			{
				// Be sure to add the lighting change to this.
				changedInCuboid = new HashSet<>(changedInCuboid);
				changedInCuboid.add(AspectRegistry.LIGHT);
			}
			listener.cuboidDidChange(data
				, allHeightMaps.get(address.getColumn())
				, blocksChanged
				, changedInCuboid
			);
		}
		// Unsafe lighting changes not already reported must also be reported.
		for (CuboidAddress lightChanges : newLightChanges)
		{
			if (!cuboidsToReport.containsKey(lightChanges))
			{
				listener.cuboidDidChange(lookups.getLatestCuboid(lightChanges)
					, allHeightMaps.get(lightChanges.getColumn())
					, Set.of()
					, Set.of(AspectRegistry.LIGHT)
				);
			}
		}
		previousNotication.storeUnsafeLighting(lightingOptChanges);
	}


	private static void _updateCollectionsWithChange(Map<CuboidAddress, IReadOnlyCuboidData> cuboidsToReport
			, Map<CuboidAddress, Set<BlockAddress>> changedBlocks
			, Map<CuboidAddress, Set<Aspect<?, ?>>> changedAspects
			, ILookup lookups
			, AbsoluteLocation location
			, MutationBlockSetBlock current
			, MutationBlockSetBlock previous
	)
	{
		Set<Aspect<?, ?>> changes = current.getChangedAspectsAfter(previous);
		// This could be empty if current is a proper subset of previous.
		if (!changes.isEmpty())
		{
			CuboidAddress address = location.getCuboidAddress();
			if (!changedAspects.containsKey(address))
			{
				changedAspects.put(address, new HashSet<>());
			}
			changedAspects.get(address).addAll(changes);
			if (!changedBlocks.containsKey(address))
			{
				changedBlocks.put(address, new HashSet<>());
			}
			changedBlocks.get(address).add(location.getBlockAddress());
			if (!cuboidsToReport.containsKey(address))
			{
				cuboidsToReport.put(address, lookups.getLatestCuboid(address));
			}
		}
	}


	public static interface ILookup
	{
		IReadOnlyCuboidData getLatestCuboid(CuboidAddress address);
		Map<CuboidColumnAddress, ColumnHeightMap> generateAllHeightMaps(Set<CuboidColumnAddress> columns);
	}
}
