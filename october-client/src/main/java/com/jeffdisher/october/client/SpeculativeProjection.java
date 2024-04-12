package com.jeffdisher.october.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
import com.jeffdisher.october.logic.ScheduledChange;
import com.jeffdisher.october.logic.ScheduledMutation;
import com.jeffdisher.october.logic.SyncPoint;
import com.jeffdisher.october.logic.WorldProcessor;
import com.jeffdisher.october.mutations.IEntityUpdate;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
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
 * Note that, when pruning the changes which the server claims are already considered, it will record some number of
 * ticks worth of or follow-up changes scheduled from within the pruned changes and will apply them for that many game
 * ticks, going forward.  This is to ensure that operations which take multiple steps to take effect will not appear to
 * "revert" when only the first step has been committed.  This is limited, since some operations could result in
 * unbounded numbers of follow-ups which would take real time to be applied and couldn't be reasonably tracked.
 * 
 * When the client makes a local update, it adds it to its list of pending updates and applies it to its speculative
 * projection.  Note that the client can sometimes coalesce these updates (in the case of a move, for example).
 */
public class SpeculativeProjection
{
	/**
	 * When pruning a change from the list of speculative changes, we will store this many follow-up ticks' worth of
	 * changes for application on future ticks.
	 */
	public static final int MAX_FOLLOW_UP_TICKS = 3;

	private final int _localEntityId;
	private final ProcessorElement _singleThreadElement;
	private final IProjectionListener _listener;
	
	private final Map<CuboidAddress, IReadOnlyCuboidData> _shadowWorld;
	private final Map<Integer, Entity> _shadowCrowd;
	
	private Map<CuboidAddress, IReadOnlyCuboidData> _projectedWorld;
	private Map<Integer, Entity> _projectedCrowd;
	public final Function<AbsoluteLocation, BlockProxy> projectionBlockLoader;
	
	private final List<_SpeculativeWrapper> _speculativeChanges;
	private final List<_SpeculativeConsequences> _followUpTicks;
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
		_followUpTicks = new ArrayList<>();
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
	 * @param entityUpdates The map of per-entity state update lists which committed in this tick.
	 * @param cuboidUpdates The list of cuboid updates which committed in this tick.
	 * @param removedEntities The list of entities which were removed in this tick.
	 * @param removedCuboids The list of cuboids which were removed in this tick.
	 * @param latestLocalCommitIncluded The latest client-local commit number which was included in this tick.
	 * @param currentTimeMillis Current system time, in milliseconds.
	 * @return The number of speculative changes remaining in the local projection (only useful for testing).
	 */
	public int applyChangesForServerTick(long gameTick
			
			, List<Entity> addedEntities
			, List<IReadOnlyCuboidData> addedCuboids
			
			, Map<Integer, List<IEntityUpdate>> entityUpdates
			, List<MutationBlockSetBlock> cuboidUpdates
			
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
		// NOTE:  We must apply these in the same order they are in the TickRunner:  IEntityUpdate BEFORE IMutationBlock.
		Map<Integer, List<ScheduledChange>> convertedUpdates = new HashMap<>();
		for (Map.Entry<Integer, List<IEntityUpdate>> elt : entityUpdates.entrySet())
		{
			convertedUpdates.put(elt.getKey(), elt.getValue().stream().map(
					(IEntityUpdate update) -> new ScheduledChange((IMutationEntity)new EntityUpdateWrapper(update), 0L)).toList()
			);
		}
		// The time between ticks doesn't matter when replaying from server.
		long ignoredMillisBetweenTicks = 0L;
		CrowdProcessor.ProcessedGroup group = CrowdProcessor.processCrowdGroupParallel(_singleThreadElement
				, _shadowCrowd
				, this.projectionBlockLoader
				, gameTick
				, ignoredMillisBetweenTicks
				, convertedUpdates
		);
		
		// Split the incoming mutations into the expected map shape.
		List<IMutationBlock> cuboidMutations = cuboidUpdates.stream().map((MutationBlockSetBlock update) -> new BlockUpdateWrapper(update)).collect(Collectors.toList());
		Map<CuboidAddress, List<ScheduledMutation>> mutationsToRun = _createMutationMap(cuboidMutations, _shadowWorld.keySet());
		// We ignore the block updates in the speculative projection (although this is theoretically possible).
		Map<CuboidAddress, List<AbsoluteLocation>> modifiedBlocksByCuboidAddress = Map.of();
		Map<CuboidAddress, List<AbsoluteLocation>> potentialLightChangesByCuboid = Map.of();
		Set<CuboidAddress> cuboidsLoadedThisTick = Set.of();
		WorldProcessor.ProcessedFragment fragment = WorldProcessor.processWorldFragmentParallel(_singleThreadElement
				, _shadowWorld
				, this.projectionBlockLoader
				, gameTick
				, ignoredMillisBetweenTicks
				, mutationsToRun
				, modifiedBlocksByCuboidAddress
				, potentialLightChangesByCuboid
				, cuboidsLoadedThisTick
		);
		
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
		
		// Step forward the follow-ups before we add to them when processing speculative changes.
		if (_followUpTicks.size() > 0)
		{
			_followUpTicks.remove(0);
		}
		
		Set<CuboidAddress> modifiedCuboidAddresses = new HashSet<>();
		Set<Integer> modifiedEntityIds = new HashSet<>();
		List<_SpeculativeWrapper> previous = new ArrayList<>(_speculativeChanges);
		_speculativeChanges.clear();
		for (_SpeculativeWrapper wrapper : previous)
		{
			// Only consider this if it is more recent than the level we are applying.
			if (wrapper.commitLevel > latestLocalCommitIncluded)
			{
				_SpeculativeWrapper appliedWrapper = _forwardApplySpeculative(modifiedCuboidAddresses, modifiedEntityIds, wrapper.change, wrapper.commitLevel);
				// If this was applied, re-add the new wrapper.
				if (null != appliedWrapper)
				{
					_speculativeChanges.add(appliedWrapper);
				}
			}
			else
			{
				// We are removing this so promote any follow-ups.
				for (int i = 0; i < wrapper.followUpTicks.size(); ++i)
				{
					_SpeculativeConsequences followUp = wrapper.followUpTicks.get(i);
					_SpeculativeConsequences shared;
					if (i < _followUpTicks.size())
					{
						shared = _followUpTicks.get(i);
					}
					else
					{
						shared = new _SpeculativeConsequences(new HashMap<>(), new ArrayList<>());
						_followUpTicks.add(i, shared);
					}
					shared.absorb(followUp);
				}
			}
		}
		
		// Apply any remaining follow-up changes.
		for (int i = 0; i < _followUpTicks.size(); ++i)
		{
			_SpeculativeConsequences followUp = _followUpTicks.get(i);
			_applyFollowUp(modifiedCuboidAddresses, modifiedEntityIds, followUp);
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
		_SpeculativeWrapper appliedWrapper = _forwardApplySpeculative(modifiedCuboidAddresses, modifiedEntityIds, change, commitNumber);
		if (null != appliedWrapper)
		{
			_speculativeChanges.add(appliedWrapper);
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

	private _SpeculativeWrapper _forwardApplySpeculative(Set<CuboidAddress> modifiedCuboids, Set<Integer> modifiedEntityIds, IMutationEntity change, long commitNumber)
	{
		// We will apply this change to the projected state using the common logic mechanism, looping on any produced updates until complete.
		
		// Only the server can apply ticks so just provide 0.
		long gameTick = 0L;
		long ignoredMillisBetweenTicks = 0L;
		
		List<ScheduledChange> queue = new LinkedList<>();
		queue.add(new ScheduledChange(change, 0L));
		Map<Integer, List<ScheduledChange>> changesToRun = Map.of(_localEntityId, queue);
		CrowdProcessor.ProcessedGroup group = CrowdProcessor.processCrowdGroupParallel(_singleThreadElement
				, _projectedCrowd
				, this.projectionBlockLoader
				, gameTick
				, ignoredMillisBetweenTicks
				, changesToRun
		);
		_projectedCrowd.putAll(group.groupFragment());
		Map<Integer, List<IMutationEntity>> exportedChanges = _onlyImmediateChanges(group.exportedEntityChanges());
		List<IMutationBlock> exportedMutations = _onlyImmediateMutations(group.exportedMutations());
		
		// Now, loop on applying changes (we will batch the consequences of each step together - we aren't scheduling like the server would, either way).
		Set<Integer> locallyModifiedIds = new HashSet<>(group.updatedEntities().keySet());
		List<_SpeculativeConsequences> followUpTicks = new ArrayList<>();
		for (int i = 0; (i < MAX_FOLLOW_UP_TICKS) && (!exportedChanges.isEmpty() || !exportedMutations.isEmpty()); ++i)
		{
			_SpeculativeConsequences consequences = new _SpeculativeConsequences(exportedChanges, exportedMutations);
			followUpTicks.add(consequences);
			
			// Run these changes and mutations, collecting the resultant output from them.
			WorldProcessor.ProcessedFragment innerFragment = _applyFollowUpBlockMutations(modifiedCuboids, exportedMutations);
			CrowdProcessor.ProcessedGroup innerGroup = _applyFollowUpEntityMutations(locallyModifiedIds, exportedChanges);
			
			// Coalesce the results of these.
			exportedChanges = new HashMap<>(_onlyImmediateChanges(innerFragment.exportedEntityChanges()));
			for (Map.Entry<Integer, List<IMutationEntity>> entry : _onlyImmediateChanges(innerGroup.exportedEntityChanges()).entrySet())
			{
				int key = entry.getKey();
				List<IMutationEntity> value = entry.getValue();
				List<IMutationEntity> oneQueue = exportedChanges.get(key);
				if (null == oneQueue)
				{
					exportedChanges.put(key, new ArrayList<>(value));
				}
				else
				{
					oneQueue.addAll(value);
				}
			}
			exportedMutations = new ArrayList<>();
			// Note that we will ignore any "future" block mutations when running speculative.
			exportedMutations.addAll(innerFragment.exportedMutations().stream()
					.filter((ScheduledMutation scheduled) -> (0L == scheduled.millisUntilReady()))
					.map((ScheduledMutation scheduled) -> scheduled.mutation())
					.toList()
			);
			exportedMutations.addAll(_onlyImmediateMutations(innerGroup.exportedMutations()));
		}
		modifiedEntityIds.addAll(locallyModifiedIds);
		
		// Since we only provided a single mutation, we will assume it applied if we see 1 commit count.
		return (1 == group.committedMutationCount())
				? new _SpeculativeWrapper(commitNumber, change, followUpTicks)
				: null
		;
	}

	private Map<CuboidAddress, List<ScheduledMutation>> _createMutationMap(List<IMutationBlock> mutations, Set<CuboidAddress> loadedCuboids)
	{
		Map<CuboidAddress, List<ScheduledMutation>> mutationsToRun = new HashMap<>();
		for (IMutationBlock mutation : mutations)
		{
			CuboidAddress address = mutation.getAbsoluteLocation().getCuboidAddress();
			// We will filter out things which aren't loaded (since this may be a follow-up).
			if (loadedCuboids.contains(address))
			{
				List<ScheduledMutation> queue = mutationsToRun.get(address);
				if (null == queue)
				{
					queue = new LinkedList<>();
					mutationsToRun.put(address, queue);
				}
				queue.add(new ScheduledMutation(mutation, 0L));
			}
		}
		return mutationsToRun;
	}

	private void _applyFollowUp(Set<CuboidAddress> modifiedCuboids, Set<Integer> modifiedEntityIds, _SpeculativeConsequences followUp)
	{
		// We ignore the results of these.
		_applyFollowUpBlockMutations(modifiedCuboids, followUp.exportedMutations);
		_applyFollowUpEntityMutations(modifiedEntityIds, followUp.exportedChanges);
	}

	private WorldProcessor.ProcessedFragment _applyFollowUpBlockMutations(Set<CuboidAddress> modifiedCuboids, List<IMutationBlock> blockMutations)
	{
		// Ignored variables in speculative mode.
		long gameTick = 0L;
		long millisSinceLastTick = 0L;
		
		Map<CuboidAddress, List<ScheduledMutation>> innerMutations = _createMutationMap(blockMutations, _projectedWorld.keySet());
		// We ignore the block updates in the speculative projection (although this is theoretically possible).
		Map<CuboidAddress, List<AbsoluteLocation>> modifiedBlocksByCuboidAddress = Map.of();
		Map<CuboidAddress, List<AbsoluteLocation>> potentialLightChangesByCuboid = Map.of();
		Set<CuboidAddress> cuboidsLoadedThisTick = Set.of();
		WorldProcessor.ProcessedFragment innerFragment = WorldProcessor.processWorldFragmentParallel(_singleThreadElement
				, _projectedWorld
				, this.projectionBlockLoader
				, gameTick
				, millisSinceLastTick
				, innerMutations
				, modifiedBlocksByCuboidAddress
				, potentialLightChangesByCuboid
				, cuboidsLoadedThisTick
		);
		_projectedWorld.putAll(innerFragment.stateFragment());
		modifiedCuboids.addAll(innerFragment.blockChangesByCuboid().keySet());
		return innerFragment;
	}

	private CrowdProcessor.ProcessedGroup _applyFollowUpEntityMutations(Set<Integer> modifiedEntityIds, Map<Integer, List<IMutationEntity>> entityMutations)
	{
		long gameTick = 0L;
		// The time between ticks doesn't matter when replaying from server.
		long ignoredMillisBetweenTicks = 0L;
		CrowdProcessor.ProcessedGroup innerGroup = CrowdProcessor.processCrowdGroupParallel(_singleThreadElement
				, _projectedCrowd
				, this.projectionBlockLoader
				, gameTick
				, ignoredMillisBetweenTicks
				, _wrapInScheduled(entityMutations)
		);
		_projectedCrowd.putAll(innerGroup.groupFragment());
		modifiedEntityIds.addAll(innerGroup.updatedEntities().keySet());
		return innerGroup;
	}

	private Map<Integer, List<IMutationEntity>> _onlyImmediateChanges(Map<Integer, List<ScheduledChange>> changes)
	{
		Map<Integer, List<IMutationEntity>> result = new HashMap<>();
		for (Map.Entry<Integer, List<ScheduledChange>> elt : changes.entrySet())
		{
			List<IMutationEntity> list = elt.getValue().stream().filter(
					(ScheduledChange change) -> (0L == change.millisUntilReady())
			).map(
					(ScheduledChange change) -> change.change()
			).toList();
			if (!list.isEmpty())
			{
				result.put(elt.getKey(), list);
			}
		}
		return result;
	}

	private List<IMutationBlock> _onlyImmediateMutations(List<ScheduledMutation> mutations)
	{
		return mutations.stream().filter(
				(ScheduledMutation mutation) -> (0L == mutation.millisUntilReady())
		).map(
				(ScheduledMutation mutation) -> mutation.mutation()
		).toList();
	}

	private Map<Integer, List<ScheduledChange>> _wrapInScheduled(Map<Integer, List<IMutationEntity>> changes)
	{
		Map<Integer, List<ScheduledChange>> result = new HashMap<>();
		for (Map.Entry<Integer, List<IMutationEntity>> elt : changes.entrySet())
		{
			List<ScheduledChange> list = elt.getValue().stream().map(
					(IMutationEntity change) -> new ScheduledChange(change, 0L)
			).toList();
			result.put(elt.getKey(), list);
		}
		return result;
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

	private static record _SpeculativeWrapper(long commitLevel
			, IMutationEntity change
			, List<_SpeculativeConsequences> followUpTicks
	) {}

	private static record _SpeculativeConsequences(Map<Integer, List<IMutationEntity>> exportedChanges, List<IMutationBlock> exportedMutations)
	{
		public void absorb(_SpeculativeConsequences followUp)
		{
			for (Map.Entry<Integer, List<IMutationEntity>> change : followUp.exportedChanges.entrySet())
			{
				int key = change.getKey();
				List<IMutationEntity> value = change.getValue();
				List<IMutationEntity> list = this.exportedChanges.get(key);
				if (null != list)
				{
					list.addAll(value);
				}
				else
				{
					this.exportedChanges.put(key, value);
				}
			}
			this.exportedMutations.addAll(followUp.exportedMutations);
		}
	}
}
