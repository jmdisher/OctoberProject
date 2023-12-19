package com.jeffdisher.october.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.jeffdisher.october.changes.IEntityChange;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.CrowdProcessor;
import com.jeffdisher.october.logic.ProcessorElement;
import com.jeffdisher.october.logic.SyncPoint;
import com.jeffdisher.october.logic.WorldProcessor;
import com.jeffdisher.october.mutations.IMutation;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.utils.Assert;


/**
 * Instances of this class are used by clients to manage their local interpretation of the world.  It has their loaded
 * cuboids, known entities, and whatever updates they have made, which haven't yet been committed by the server.
 * 
 * When the server commits the updates from a given game tick, it sends any which it accepted and applied to the
 * clients, along with the last update number it applied from that specific client.  The client then applies this list
 * of updates to its local shadow state of the server, prunes its list of local updates to remove any which were
 * accounted for by the last applied number, and applies the remaining to a copy of the shadow state to produce its
 * speculative projection.
 * 
 * When the client makes a local update, it adds it to its list of pending updates and applies it to its speculative
 * projection.  Note that the client can sometimes coalesce these updates (in the case of a move, for example).
 */
public class SpeculativeProjection
{
	private final int _localEntityId;
	private final ProcessorElement _singleThreadElement;
	private final IProjectionListener _listener;
	
	private final Map<CuboidAddress, IReadOnlyCuboidData> _shadowWorld;
	private final Map<Integer, Entity> _shadowCrowd;
	private final Function<AbsoluteLocation, BlockProxy> _shadowBlockLoader;
	
	private Map<CuboidAddress, IReadOnlyCuboidData> _projectedWorld;
	private Map<Integer, Entity> _projectedCrowd;
	
	private final List<SpeculativeWrapper> _speculativeChanges;
	private long _nextLocalCommitNumber;
	private SpeculativeWrapper _inProgress;
	private long _inProgressCompletionTime;

	/**
	 * Creates a speculative projection for a single client.
	 * 
	 * @param localEntityId The ID of the local entity where all local changes will be applied.
	 * @param listener The listener for updates to the local projection.
	 */
	public SpeculativeProjection(int localEntityId, IProjectionListener listener)
	{
		Assert.assertTrue(null != listener);
		_localEntityId = localEntityId;
		_singleThreadElement = new ProcessorElement(0, new SyncPoint(1), new AtomicInteger(0));
		_listener = listener;
		
		// The initial states start as empty and are populated by the server.
		_shadowWorld = new HashMap<>();
		_shadowCrowd = new HashMap<>();
		_shadowBlockLoader = (AbsoluteLocation location) -> {
			CuboidAddress address = location.getCuboidAddress();
			IReadOnlyCuboidData cuboid = _shadowWorld.get(address);
			return (null != cuboid)
					? new BlockProxy(location.getBlockAddress(), cuboid)
					: null
			;
		};
		
		_projectedWorld = new HashMap<>();
		_projectedCrowd = new HashMap<>();
		_speculativeChanges = new ArrayList<>();
		_nextLocalCommitNumber = 1L;
	}

	/**
	 * This method is called when an update from a game tick comes in from the server.  It is responsible for using
	 * these updates to change the local shadow copy of the server state, then rebuilding the local projected state
	 * based on this shadow state plus any remaining speculative changes which weren't pruned as a result of being
	 * accounted for in this update from the server.
	 * 
	 * @param gameTick The server's game tick number where these changes were made (only useful for debugging).
	 * @param addedEntities The list of entities which were added in this tick.
	 * @param addedCuboids The list of cuboids which were loaded in this tick.
	 * @param entityChanges The map of per-entity change queues which committed in this tick.
	 * @param cuboidMutations The list of cuboid mutations which committed in this tick.
	 * @param removedEntities The list of entities which were removed in this tick.
	 * @param removedCuboids The list of cuboids which were removed in this tick.
	 * @param latestLocalCommitIncluded The latest client-local commit number which was included in this tick.
	 * @param currentTimeMillis Current system time, in milliseconds.
	 * @return The number of speculative changes remaining in the local projection (only useful for testing).
	 */
	public int applyChangesForServerTick(long gameTick
			
			, List<Entity> addedEntities
			, List<IReadOnlyCuboidData> addedCuboids
			
			, Map<Integer, Queue<IEntityChange>> entityChanges
			, List<IMutation> cuboidMutations
			
			, List<Integer> removedEntities
			, List<CuboidAddress> removedCuboids
			
			, long latestLocalCommitIncluded
			, long currentTimeMillis
	)
	{
		// Before applying the updates, add the new data.
		_shadowCrowd.putAll(addedEntities.stream().collect(Collectors.toMap((Entity entity) -> entity.id(), (Entity entity) -> entity)));
		_shadowWorld.putAll(addedCuboids.stream().collect(Collectors.toMap((IReadOnlyCuboidData cuboid) -> cuboid.getCuboidAddress(), (IReadOnlyCuboidData cuboid) -> cuboid)));
		
		// Create empty listeners.
		CrowdProcessor.IEntityChangeListener entityListener = new CrowdProcessor.IEntityChangeListener() {
			@Override
			public void changeApplied(int targetEntityId, IEntityChange change)
			{
			}
			@Override
			public void changeDropped(int targetEntityId, IEntityChange change)
			{
			}
		};
		WorldProcessor.IBlockChangeListener worldListener = new WorldProcessor.IBlockChangeListener() {
			@Override
			public void mutationApplied(IMutation mutation)
			{
			}
			@Override
			public void mutationDropped(IMutation mutation)
			{
			}
		};
		
		// Apply all of these to the shadow state, much like TickRunner.  We ONLY change the shadow state in response to these authoritative changes.
		// NOTE:  We must apply these in the same order they are in the TickRunner:  IEntityChange BEFORE IMutation.
		CrowdProcessor.ProcessedGroup group = CrowdProcessor.processCrowdGroupParallel(_singleThreadElement, _shadowCrowd, entityListener, _shadowBlockLoader, gameTick, entityChanges);
		
		// Split the incoming mutations into the expected map shape.
		Map<CuboidAddress, Queue<IMutation>> mutationsToRun = _createMutationMap(cuboidMutations);
		WorldProcessor.ProcessedFragment fragment = WorldProcessor.processWorldFragmentParallel(_singleThreadElement, _shadowWorld, worldListener, _shadowBlockLoader, gameTick, mutationsToRun);
		
		// Apply these to the shadow collections.
		// (we ignore exported changes or mutations since we will wait for the server to send those to us, once it commits them)
		_shadowCrowd.putAll(group.groupFragment());
		_shadowWorld.putAll(fragment.stateFragment());
		
		// Remove before moving on to our projection.
		_shadowCrowd.keySet().removeAll(removedEntities);
		_shadowWorld.keySet().removeAll(removedCuboids);
		
		// Build the initial modified sets just by looking at what top-level elements of the shadow world deviate from our old projection (and we will add to this as we apply our local updates.
		// Note that these are all immutable so instance comparison is sufficient.
		Set<Integer> modifiedEntityIds = new HashSet<>();
		for (Map.Entry<Integer, Entity> elt : _shadowCrowd.entrySet())
		{
			Integer key = elt.getKey();
			if (_projectedCrowd.get(key) != elt.getValue())
			{
				modifiedEntityIds.add(key);
			}
		}
		Set<CuboidAddress> modifiedCuboidAddresses = new HashSet<>();
		for (Map.Entry<CuboidAddress, IReadOnlyCuboidData> elt : _shadowWorld.entrySet())
		{
			CuboidAddress key = elt.getKey();
			if (_projectedWorld.get(key) != elt.getValue())
			{
				modifiedCuboidAddresses.add(key);
			}
		}
		
		// Rebuild our projection from these collections.
		_projectedCrowd = new HashMap<>(_shadowCrowd);
		_projectedWorld = new HashMap<>(_shadowWorld);
		
		// (we use an iterator to remove commits which reject).
		Iterator<SpeculativeWrapper> iter = _speculativeChanges.iterator();
		while (iter.hasNext())
		{
			SpeculativeWrapper wrapper = iter.next();
			// Only consider this if it is more recent than the level we are applying.
			if (wrapper.commitLevel > latestLocalCommitIncluded)
			{
				boolean didApply = _forwardApplySpeculative(modifiedCuboidAddresses, modifiedEntityIds, wrapper.change);
				if (!didApply)
				{
					// This must have been conflicted with server changes so remove it.
					iter.remove();
				}
			}
			else
			{
				iter.remove();
			}
		}
		
		// See if we have an in-progress change which is ready to run.
		_checkInProgress(modifiedCuboidAddresses, modifiedEntityIds, currentTimeMillis);
		
		// Notify the listener of what changed.
		for (Entity entity : addedEntities)
		{
			_listener.entityDidLoad(entity);
		}
		for (IReadOnlyCuboidData cuboid : addedCuboids)
		{
			_listener.cuboidDidLoad(cuboid);
		}
		_notifyChanges(modifiedCuboidAddresses, modifiedEntityIds);
		for (Integer id : removedEntities)
		{
			_listener.entityDidUnload(id);
		}
		for (CuboidAddress address : removedCuboids)
		{
			_listener.cuboidDidUnload(address);
		}
		
		return _speculativeChanges.size();
	}

	/**
	 * Applies the given change to the local state as speculative and returns the local commit number associated.  Note
	 * that the returned number may be the same as that returned by the previous call if the implementation decided that
	 * they could be coalesced.  In this case, the caller should replace the change it has buffered to send to the
	 * server with this one.
	 * 
	 * @param change The entity change to apply.
	 * @param currentTimeMillis Current system time, in milliseconds.
	 * @return The local commit number for this change, 0L if it failed to applied and should be rejected.
	 */
	public long applyLocalChange(IEntityChange change, long currentTimeMillis)
	{
		// Create the new commit number although we will reverse this if we can merge.
		long commitNumber = _nextLocalCommitNumber;
		_nextLocalCommitNumber += 1;
		
		// Create the tracking for modifications.
		Set<Integer> modifiedEntityIds = new HashSet<>();
		Set<CuboidAddress> modifiedCuboidAddresses = new HashSet<>();
		
		// See if there is an in-progress change.
		_checkInProgress(modifiedCuboidAddresses, modifiedEntityIds, currentTimeMillis);
		
		// We shouldn't see something in progress, which didn't complete above and wasn't cancelled, at this point.
		Assert.assertTrue(null == _inProgress);
		
		// See if this should run yet.
		long timeCostMillis = change.getTimeCostMillis();
		if (timeCostMillis > 0L)
		{
			// This is a change which consumes time to complete so we just hold on to it before putting it in our local queue.
			_inProgress = new SpeculativeWrapper(commitNumber, change);
			_inProgressCompletionTime = timeCostMillis + currentTimeMillis;
		}
		else
		{
			// This can happen right away.
			// (if this is a cancellation, it should have come in another path.
			Assert.assertTrue(0L == timeCostMillis);
			
			boolean didApply = _forwardApplySpeculative(modifiedCuboidAddresses, modifiedEntityIds, change);
			if (didApply)
			{
				_speculativeChanges.add(new SpeculativeWrapper(commitNumber, change));
				
				// Notify the listener of what changed.
				_notifyChanges(modifiedCuboidAddresses, modifiedEntityIds);
			}
			else
			{
				// We failed to apply a local immediate commit so just revert the commit number.
				_nextLocalCommitNumber -= 1;
				commitNumber = 0L;
			}
		}
		return commitNumber;
	}

	/**
	 * Called to notify the projection of the current time so that it can complete any active activities, if due.
	 * 
	 * @param currentTimeMillis Current system time, in milliseconds.
	 */
	public void checkCurrentActivity(long currentTimeMillis)
	{
		Set<Integer> modifiedEntityIds = new HashSet<>();
		Set<CuboidAddress> modifiedCuboidAddresses = new HashSet<>();
		_checkInProgress(modifiedCuboidAddresses, modifiedEntityIds, currentTimeMillis);
	}

	/**
	 * Cancels any in-progress entity change which is still waiting to complete, returning its commit number or 0, if
	 * there was no in-progress change.
	 * 
	 * @return The commit number of the in-progress change, or 0L if nothing was in progress.
	 */
	public long cancelCurrentActivity()
	{
		long commitOfCancelled = 0L;
		if (null != _inProgress)
		{
			commitOfCancelled = _inProgress.commitLevel;
			_inProgress = null;
		}
		return commitOfCancelled;
	}


	private void _notifyChanges(Set<CuboidAddress> changedCuboidAddresses, Set<Integer> entityIds)
	{
		for (CuboidAddress address : changedCuboidAddresses)
		{
			_listener.cuboidDidChange(_projectedWorld.get(address));
		}
		for (Integer id : entityIds)
		{
			_listener.entityDidChange(_projectedCrowd.get(id));
		}
	}

	private boolean _forwardApplySpeculative(Set<CuboidAddress> modifiedCuboids, Set<Integer> modifiedEntityIds, IEntityChange change)
	{
		// We will apply this change to the projected state using the common logic mechanism, looping on any produced updates until complete.
		
		// Only the server can apply ticks so just provide 0.
		long gameTick = 0L;
		
		// We want to collect the sets of cuboids and entities we changed so we use special listeners.
		Set<Integer> locallyModifiedIds = new HashSet<>();
		CrowdProcessor.IEntityChangeListener specialChangeListener = new CrowdProcessor.IEntityChangeListener() {
			@Override
			public void changeApplied(int targetEntityId, IEntityChange change)
			{
				locallyModifiedIds.add(targetEntityId);
			}
			@Override
			public void changeDropped(int targetEntityId, IEntityChange change)
			{
			}
		};
		WorldProcessor.IBlockChangeListener specialMutationListener = new WorldProcessor.IBlockChangeListener() {
			@Override
			public void mutationApplied(IMutation mutation)
			{
				modifiedCuboids.add(mutation.getAbsoluteLocation().getCuboidAddress());
			}
			@Override
			public void mutationDropped(IMutation mutation)
			{
			}
		};
		
		Queue<IEntityChange> queue = new LinkedList<IEntityChange>();
		queue.add(change);
		Map<Integer, Queue<IEntityChange>> changesToRun = Map.of(_localEntityId, queue);
		CrowdProcessor.ProcessedGroup group = CrowdProcessor.processCrowdGroupParallel(_singleThreadElement, _projectedCrowd, specialChangeListener, _shadowBlockLoader, gameTick, changesToRun);
		_projectedCrowd.putAll(group.groupFragment());
		Map<Integer, Queue<IEntityChange>> exportedChanges = group.exportedChanges();
		List<IMutation> exportedMutations = group.exportedMutations();
		
		// Now, loop on applying changes (we will batch the consequences of each step together - we aren't scheduling like the server would, either way).
		while (!exportedChanges.isEmpty() || !exportedMutations.isEmpty())
		{
			// Run these changes and mutations, collecting the resultant output from them.
			Map<CuboidAddress, Queue<IMutation>> innerMutations = _createMutationMap(exportedMutations);
			WorldProcessor.ProcessedFragment innerFragment = WorldProcessor.processWorldFragmentParallel(_singleThreadElement, _projectedWorld, specialMutationListener, _shadowBlockLoader, gameTick, innerMutations);
			_projectedWorld.putAll(innerFragment.stateFragment());
			
			CrowdProcessor.ProcessedGroup innerGroup = CrowdProcessor.processCrowdGroupParallel(_singleThreadElement, _projectedCrowd, specialChangeListener, _shadowBlockLoader, gameTick, exportedChanges);
			_projectedCrowd.putAll(innerGroup.groupFragment());
			
			// Coalesce the results of these.
			exportedChanges = new HashMap<>(innerFragment.exportedEntityChanges());
			for (Map.Entry<Integer, Queue<IEntityChange>> entry : innerGroup.exportedChanges().entrySet())
			{
				Queue<IEntityChange> oneQueue = exportedChanges.get(entry.getKey());
				if (null == oneQueue)
				{
					exportedChanges.put(entry.getKey(), entry.getValue());
				}
				else
				{
					oneQueue.addAll(entry.getValue());
				}
			}
			exportedMutations = new ArrayList<>();
			exportedMutations.addAll(innerFragment.exportedMutations());
			exportedMutations.addAll(innerGroup.exportedMutations());
		}
		
		// We will assume that the initial change was applied if we see them in the modified set.
		modifiedEntityIds.addAll(locallyModifiedIds);
		return locallyModifiedIds.contains(_localEntityId);
	}

	private Map<CuboidAddress, Queue<IMutation>> _createMutationMap(List<IMutation> mutations)
	{
		Map<CuboidAddress, Queue<IMutation>> mutationsToRun = new HashMap<>();
		for (IMutation mutation : mutations)
		{
			CuboidAddress address = mutation.getAbsoluteLocation().getCuboidAddress();
			Queue<IMutation> queue = mutationsToRun.get(address);
			if (null == queue)
			{
				queue = new LinkedList<>();
				mutationsToRun.put(address, queue);
			}
			queue.add(mutation);
		}
		return mutationsToRun;
	}

	private void _checkInProgress(Set<CuboidAddress> modifiedCuboidAddresses, Set<Integer> modifiedEntityIds, long currentTimeMillis)
	{
		if (null != _inProgress)
		{
			if (_inProgressCompletionTime <= currentTimeMillis)
			{
				// This is due so try applying it.
				boolean didApply = _forwardApplySpeculative(modifiedCuboidAddresses, modifiedEntityIds, _inProgress.change);
				if (didApply)
				{
					// This has completed to move it to the normal list.
					_speculativeChanges.add(_inProgress);
					
					// Notify the listener of what changed.
					_notifyChanges(modifiedCuboidAddresses, modifiedEntityIds);
				}
				// Whether this passed or not, we are done tracking it.
				_inProgress = null;
			}
		}
	}


	public static interface IProjectionListener
	{
		void cuboidDidLoad(IReadOnlyCuboidData cuboid);
		void cuboidDidChange(IReadOnlyCuboidData cuboid);
		void cuboidDidUnload(CuboidAddress address);
		
		void entityDidLoad(Entity entity);
		void entityDidChange(Entity entity);
		void entityDidUnload(int id);
	}

	private static record SpeculativeWrapper(long commitLevel
			, IEntityChange change
	) {}
}
