package com.jeffdisher.october.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.jeffdisher.october.aspects.Aspect;
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
	 * changes applied on top.  This call will read and write projectedBlockChanges.
	 * @param incomingChangesFromServer Authoritative changes which have come in from the server.
	 * @param localChangesApplied The union of all local-only changes which have been applied to the projected state.
	 * @param knownHeightMaps The current height maps from the updated projected state.
	 */
	public static void notifyCuboidChangesFromServer(IProjectionListener listener
			, ProjectedState currentProjectedState
			, Map<AbsoluteLocation, MutationBlockSetBlock> incomingChangesFromServer
			, Map<AbsoluteLocation, MutationBlockSetBlock> localChangesApplied
			, Map<CuboidColumnAddress, ColumnHeightMap> knownHeightMaps
	)
	{
		Map<CuboidAddress, IReadOnlyCuboidData> cuboidsToReport = new HashMap<>();
		Map<CuboidAddress, Set<BlockAddress>> changedBlocks = new HashMap<>();
		Map<CuboidAddress, Set<Aspect<?, ?>>> changedAspects = new HashMap<>();
		
		// This call rebuilds the projected changes but we want to store the old version to know what changed.
		Map<AbsoluteLocation, MutationBlockSetBlock> previousProjectedChanges = currentProjectedState.projectedBlockChanges;
		currentProjectedState.projectedBlockChanges = new HashMap<>();
		
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
			currentProjectedState.projectedBlockChanges.put(key, change);
		}
		
		// Add in any local changes we didn't merge in above.
		for (Map.Entry<AbsoluteLocation, MutationBlockSetBlock> local : localChangesApplied.entrySet())
		{
			AbsoluteLocation key = local.getKey();
			if (!currentProjectedState.projectedBlockChanges.containsKey(key))
			{
				MutationBlockSetBlock value = local.getValue();
				currentProjectedState.projectedBlockChanges.put(key, value);
			}
		}
		
		// We will report anything which has new changes since last report.
		for (Map.Entry<AbsoluteLocation, MutationBlockSetBlock> change : currentProjectedState.projectedBlockChanges.entrySet())
		{
			AbsoluteLocation key = change.getKey();
			MutationBlockSetBlock current = change.getValue();
			if (previousProjectedChanges.containsKey(key))
			{
				// See if this has changed something new.
				MutationBlockSetBlock previous = previousProjectedChanges.get(key);
				if (!current.doesDataMatch(previous))
				{
					_updateCollectionsWithChange(cuboidsToReport, changedBlocks, changedAspects, currentProjectedState, key, current, previous);
				}
			}
			else
			{
				// This is a new change so just report it.
				MutationBlockSetBlock empty = new MutationBlockSetBlock(key, new byte[0]);
				_updateCollectionsWithChange(cuboidsToReport, changedBlocks, changedAspects, currentProjectedState, key, current, empty);
			}
		}
		
		// We now want to check everything we have retired from last time, reporting them if their data isn't reflected in the current projected state (means they were reverted).
		for (Map.Entry<AbsoluteLocation, MutationBlockSetBlock> previous : previousProjectedChanges.entrySet())
		{
			AbsoluteLocation key = previous.getKey();
			if (!currentProjectedState.projectedBlockChanges.containsKey(key))
			{
				// This is a retired case so check it.
				// Note that we do NOT add this to projectedBlockChanges.
				MutationBlockSetBlock current = previous.getValue();
				CuboidAddress address = key.getCuboidAddress();
				IReadOnlyCuboidData cuboid = currentProjectedState.projectedWorld.get(address);
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
			Set<Aspect<?, ?>> aspects = changedAspects.get(address);
			Assert.assertTrue(!aspects.isEmpty());
			IReadOnlyCuboidData data = elt.getValue();
			Set<BlockAddress> blocksChanged = changedBlocks.get(address);
			Assert.assertTrue(!blocksChanged.isEmpty());
			listener.cuboidDidChange(data
					, allHeightMaps.get(address.getColumn())
					, blocksChanged
					, aspects
			);
		}
	}

	/**
	 * Processes new changes applied by the local user, to the projected state, and converts those into calls out to the
	 * listener.
	 * 
	 * @param listener The listener which will receive the callbacks.
	 * @param currentProjectedState The projected state updated with most recent local changes.
	 * @param localChangesApplied The union of all local-only changes which have been applied to the projected state.
	 */
	public static void notifyCuboidChangesFromLocal(IProjectionListener listener
			, ProjectedState currentProjectedState
			, Map<AbsoluteLocation, MutationBlockSetBlock> localChangesApplied
	)
	{
		Map<CuboidAddress, IReadOnlyCuboidData> cuboidsToReport = new HashMap<>();
		Map<CuboidAddress, Set<BlockAddress>> changedBlocks = new HashMap<>();
		Map<CuboidAddress, Set<Aspect<?, ?>>> changedAspects = new HashMap<>();
		
		// Merge the new changes into the existing changes we have reported, collecting data to notify on anything new or expanded.
		for (Map.Entry<AbsoluteLocation, MutationBlockSetBlock> local : localChangesApplied.entrySet())
		{
			AbsoluteLocation key = local.getKey();
			MutationBlockSetBlock value = local.getValue();
			if (currentProjectedState.projectedBlockChanges.containsKey(key))
			{
				// See if this needs to be expanded to account for new changes.
				MutationBlockSetBlock previous = currentProjectedState.projectedBlockChanges.get(key);
				if (!value.doesDataMatch(previous))
				{
					_updateCollectionsWithChange(cuboidsToReport, changedBlocks, changedAspects, currentProjectedState, key, value, previous);
					currentProjectedState.projectedBlockChanges.put(key, value);
				}
			}
			else
			{
				// We can just inject this change, directly.
				MutationBlockSetBlock empty = new MutationBlockSetBlock(key, new byte[0]);
				_updateCollectionsWithChange(cuboidsToReport, changedBlocks, changedAspects, currentProjectedState, key, value, empty);
				currentProjectedState.projectedBlockChanges.put(key, value);
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
			Assert.assertTrue(!blocksChanged.isEmpty());
			Set<Aspect<?, ?>> aspects = changedAspects.get(address);
			Assert.assertTrue(!aspects.isEmpty());
			listener.cuboidDidChange(data
					, allHeightMaps.get(address.getColumn())
					, blocksChanged
					, aspects
			);
		}
	}


	private static void _updateCollectionsWithChange(Map<CuboidAddress, IReadOnlyCuboidData> cuboidsToReport
			, Map<CuboidAddress, Set<BlockAddress>> changedBlocks
			, Map<CuboidAddress, Set<Aspect<?, ?>>> changedAspects
			, ProjectedState currentProjectedState
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
				cuboidsToReport.put(address, currentProjectedState.projectedWorld.get(address));
			}
		}
	}
}
