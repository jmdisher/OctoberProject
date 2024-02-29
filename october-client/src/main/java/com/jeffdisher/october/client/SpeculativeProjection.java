package com.jeffdisher.october.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.CrowdProcessor;
import com.jeffdisher.october.logic.ProcessorElement;
import com.jeffdisher.october.logic.SyncPoint;
import com.jeffdisher.october.logic.WorldProcessor;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.IMutationEntity;
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
	
	private Map<CuboidAddress, IReadOnlyCuboidData> _projectedWorld;
	private Map<Integer, Entity> _projectedCrowd;
	public final Function<AbsoluteLocation, BlockProxy> projectionBlockLoader;
	
	private final List<SpeculativeWrapper> _speculativeChanges;
	private long _nextLocalCommitNumber;

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
		
		_projectedWorld = new HashMap<>();
		_projectedCrowd = new HashMap<>();
		this.projectionBlockLoader = (AbsoluteLocation location) -> {
			CuboidAddress address = location.getCuboidAddress();
			IReadOnlyCuboidData cuboid = _projectedWorld.get(address);
			return (null != cuboid)
					? new BlockProxy(location.getBlockAddress(), cuboid)
					: null
			;
		};
		
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
			
			, Map<Integer, List<IMutationEntity>> entityChanges
			, List<IMutationBlock> cuboidMutations
			
			, List<Integer> removedEntities
			, List<CuboidAddress> removedCuboids
			
			, long latestLocalCommitIncluded
			, long currentTimeMillis
	)
	{
		// Before applying the updates, add the new data.
		_shadowCrowd.putAll(addedEntities.stream().collect(Collectors.toMap((Entity entity) -> entity.id(), (Entity entity) -> entity)));
		_shadowWorld.putAll(addedCuboids.stream().collect(Collectors.toMap((IReadOnlyCuboidData cuboid) -> cuboid.getCuboidAddress(), (IReadOnlyCuboidData cuboid) -> cuboid)));
		
		// Apply all of these to the shadow state, much like TickRunner.  We ONLY change the shadow state in response to these authoritative changes.
		// NOTE:  We must apply these in the same order they are in the TickRunner:  IEntityChange BEFORE IMutation.
		CrowdProcessor.ProcessedGroup group = CrowdProcessor.processCrowdGroupParallel(_singleThreadElement, _shadowCrowd, this.projectionBlockLoader, gameTick, entityChanges);
		
		// Split the incoming mutations into the expected map shape.
		Map<CuboidAddress, List<IMutationBlock>> mutationsToRun = _createMutationMap(cuboidMutations);
		WorldProcessor.ProcessedFragment fragment = WorldProcessor.processWorldFragmentParallel(_singleThreadElement, _shadowWorld, this.projectionBlockLoader, gameTick, mutationsToRun);
		
		// Apply these to the shadow collections.
		// (we ignore exported changes or mutations since we will wait for the server to send those to us, once it commits them)
		_shadowCrowd.putAll(group.groupFragment());
		_shadowWorld.putAll(fragment.stateFragment());
		
		// Remove before moving on to our projection.
		_shadowCrowd.keySet().removeAll(removedEntities);
		_shadowWorld.keySet().removeAll(removedCuboids);
		
		// Build the initial modified sets just by looking at what top-level elements of the shadow world deviate from our old projection (and we will add to this as we apply our local updates.
		// Note that these are all immutable so instance comparison is sufficient.
		Set<Integer> revertedEntityIds = new HashSet<>();
		for (Map.Entry<Integer, Entity> elt : _shadowCrowd.entrySet())
		{
			Integer key = elt.getKey();
			Entity projectedEntity = _projectedCrowd.get(key);
			if ((null != projectedEntity) && (projectedEntity != elt.getValue()))
			{
				revertedEntityIds.add(key);
			}
		}
		Set<CuboidAddress> revertedCuboidAddresses = new HashSet<>();
		for (Map.Entry<CuboidAddress, IReadOnlyCuboidData> elt : _shadowWorld.entrySet())
		{
			CuboidAddress key = elt.getKey();
			IReadOnlyCuboidData projectedCuboid = _projectedWorld.get(key);
			if ((null != projectedCuboid) && (projectedCuboid != elt.getValue()))
			{
				revertedCuboidAddresses.add(key);
			}
		}
		
		// Rebuild our projection from these collections.
		_projectedCrowd = new HashMap<>(_shadowCrowd);
		_projectedWorld = new HashMap<>(_shadowWorld);
		
		// (we use an iterator to remove commits which reject).
		Set<CuboidAddress> modifiedCuboidAddresses = new HashSet<>();
		Set<Integer> modifiedEntityIds = new HashSet<>();
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
		
		// Notify the listener of what changed.
		for (Entity entity : addedEntities)
		{
			_listener.entityDidLoad(entity);
		}
		for (IReadOnlyCuboidData cuboid : addedCuboids)
		{
			_listener.cuboidDidLoad(cuboid);
		}
		_notifyChanges(revertedCuboidAddresses, revertedEntityIds, modifiedCuboidAddresses, modifiedEntityIds);
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
	public long applyLocalChange(IMutationEntity change, long currentTimeMillis)
	{
		// Create the new commit number although we will reverse this if we can merge.
		long commitNumber = _nextLocalCommitNumber;
		_nextLocalCommitNumber += 1;
		
		// Create the tracking for modifications.
		Set<Integer> modifiedEntityIds = new HashSet<>();
		Set<CuboidAddress> modifiedCuboidAddresses = new HashSet<>();
		
		// Attempt to apply the change.
		boolean didApply = _forwardApplySpeculative(modifiedCuboidAddresses, modifiedEntityIds, change);
		if (didApply)
		{
			_speculativeChanges.add(new SpeculativeWrapper(commitNumber, change));
		}
		else
		{
			// We failed to apply a local immediate commit so just revert the commit number.
			_nextLocalCommitNumber -= 1;
			commitNumber = 0L;
		}
		
		// Notify the listener of what changed.
		_notifyChanges(null, null, modifiedCuboidAddresses, modifiedEntityIds);
		return commitNumber;
	}


	private void _notifyChanges(Set<CuboidAddress> revertedCuboidAddresses, Set<Integer> revertedEntityIds, Set<CuboidAddress> changedCuboidAddresses, Set<Integer> entityIds)
	{
		Map<CuboidAddress, IReadOnlyCuboidData> cuboidsToReport = new HashMap<>();
		for (CuboidAddress address : changedCuboidAddresses)
		{
			// The changed cuboid addresses is just a set of cuboids where something _may_ have changed so see if it modified the actual data.
			// Remember that these are copy-on-write so we can just instance-compare.
			IReadOnlyCuboidData activeVersion = _projectedWorld.get(address);
			if (activeVersion != _shadowWorld.get(address))
			{
				cuboidsToReport.put(address, activeVersion);
			}
		}
		if (null != revertedCuboidAddresses)
		{
			// Reverted addresses are reported all the time, though.
			for (CuboidAddress address : revertedCuboidAddresses)
			{
				if (!cuboidsToReport.containsKey(address))
				{
					cuboidsToReport.put(address, _projectedWorld.get(address));
				}
			}
		}
		Map<Integer, Entity> entitiesToReport = new HashMap<>();
		for (Integer entityId : entityIds)
		{
			Entity activeVersion = _projectedCrowd.get(entityId);
			if (activeVersion != _shadowCrowd.get(entityId))
			{
				entitiesToReport.put(entityId, activeVersion);
			}
		}
		if (null != revertedEntityIds)
		{
			for (Integer entityId : revertedEntityIds)
			{
				if (!entitiesToReport.containsKey(entityId))
				{
					entitiesToReport.put(entityId, _projectedCrowd.get(entityId));
				}
			}
		}
		
		for (IReadOnlyCuboidData data : cuboidsToReport.values())
		{
			_listener.cuboidDidChange(data);
		}
		for (Entity entity : entitiesToReport.values())
		{
			_listener.entityDidChange(entity);
		}
	}

	private boolean _forwardApplySpeculative(Set<CuboidAddress> modifiedCuboids, Set<Integer> modifiedEntityIds, IMutationEntity change)
	{
		// We will apply this change to the projected state using the common logic mechanism, looping on any produced updates until complete.
		
		// Only the server can apply ticks so just provide 0.
		long gameTick = 0L;
		
		List<IMutationEntity> queue = new LinkedList<IMutationEntity>();
		queue.add(change);
		Map<Integer, List<IMutationEntity>> changesToRun = Map.of(_localEntityId, queue);
		CrowdProcessor.ProcessedGroup group = CrowdProcessor.processCrowdGroupParallel(_singleThreadElement, _projectedCrowd, this.projectionBlockLoader, gameTick, changesToRun);
		_projectedCrowd.putAll(group.groupFragment());
		Map<Integer, List<IMutationEntity>> exportedChanges = group.exportedChanges();
		List<IMutationBlock> exportedMutations = group.exportedMutations();
		
		// Now, loop on applying changes (we will batch the consequences of each step together - we aren't scheduling like the server would, either way).
		Set<Integer> locallyModifiedIds = new HashSet<>(group.resultantMutationsById().keySet());
		while (!exportedChanges.isEmpty() || !exportedMutations.isEmpty())
		{
			// Run these changes and mutations, collecting the resultant output from them.
			Map<CuboidAddress, List<IMutationBlock>> innerMutations = _createMutationMap(exportedMutations);
			WorldProcessor.ProcessedFragment innerFragment = WorldProcessor.processWorldFragmentParallel(_singleThreadElement, _projectedWorld, this.projectionBlockLoader, gameTick, innerMutations);
			_projectedWorld.putAll(innerFragment.stateFragment());
			modifiedCuboids.addAll(innerFragment.resultantMutationsByCuboid().keySet());
			
			CrowdProcessor.ProcessedGroup innerGroup = CrowdProcessor.processCrowdGroupParallel(_singleThreadElement, _projectedCrowd, this.projectionBlockLoader, gameTick, exportedChanges);
			_projectedCrowd.putAll(innerGroup.groupFragment());
			locallyModifiedIds.addAll(innerGroup.resultantMutationsById().keySet());
			
			// Coalesce the results of these.
			exportedChanges = new HashMap<>(innerFragment.exportedEntityChanges());
			for (Map.Entry<Integer, List<IMutationEntity>> entry : innerGroup.exportedChanges().entrySet())
			{
				List<IMutationEntity> oneQueue = exportedChanges.get(entry.getKey());
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
		modifiedEntityIds.addAll(locallyModifiedIds);
		
		// Since we only provided a single mutation, we will assume it applied if we see 1 commit count.
		return (1 == group.committedMutationCount());
	}

	private Map<CuboidAddress, List<IMutationBlock>> _createMutationMap(List<IMutationBlock> mutations)
	{
		Map<CuboidAddress, List<IMutationBlock>> mutationsToRun = new HashMap<>();
		for (IMutationBlock mutation : mutations)
		{
			CuboidAddress address = mutation.getAbsoluteLocation().getCuboidAddress();
			List<IMutationBlock> queue = mutationsToRun.get(address);
			if (null == queue)
			{
				queue = new LinkedList<>();
				mutationsToRun.put(address, queue);
			}
			queue.add(mutation);
		}
		return mutationsToRun;
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
			, IMutationEntity change
	) {}
}
