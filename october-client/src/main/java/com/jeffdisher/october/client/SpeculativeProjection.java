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
import com.jeffdisher.october.logic.BasicBlockProxyCache;
import com.jeffdisher.october.logic.CommonChangeSink;
import com.jeffdisher.october.logic.CommonMutationSink;
import com.jeffdisher.october.logic.CrowdProcessor;
import com.jeffdisher.october.logic.ProcessorElement;
import com.jeffdisher.october.logic.ScheduledChange;
import com.jeffdisher.october.logic.ScheduledMutation;
import com.jeffdisher.october.logic.SyncPoint;
import com.jeffdisher.october.logic.WorldProcessor;
import com.jeffdisher.october.mutations.IEntityUpdate;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.mutations.IPartialEntityUpdate;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Difficulty;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.MutablePartialEntity;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.types.TickProcessingContext;
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
	
	private Entity _thisShadowEntity;
	private final Map<CuboidAddress, IReadOnlyCuboidData> _shadowWorld;
	private final Map<Integer, PartialEntity> _shadowCrowd;
	
	// Note that we don't keep a projection of the crowd since we never apply speculative operations to them.
	// So, we keep a projected version of the local entity and just fall-back to the shadow data for any read-only operations.
	private Entity _projectedLocalEntity;
	private Map<CuboidAddress, IReadOnlyCuboidData> _projectedWorld;
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
	 * Sets the local entity.  Note that this can only be called once and must be called before the first end of tick is
	 * delivered.
	 * 
	 * @param thisEntity The initial state to use for the local entity.
	 */
	public void setThisEntity(Entity thisEntity)
	{
		// This can only be set once.
		Assert.assertTrue(null == _thisShadowEntity);
		_thisShadowEntity = thisEntity;
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
	 * @param entityUpdates The list of updates made to this entity which committed in this tick.
	 * @param partialEntityUpdates The map of per-entity state update lists which committed in this tick.
	 * @param cuboidUpdates The list of cuboid updates which committed in this tick.
	 * @param removedEntities The list of entities which were removed in this tick.
	 * @param removedCuboids The list of cuboids which were removed in this tick.
	 * @param latestLocalCommitIncluded The latest client-local commit number which was included in this tick.
	 * @param currentTimeMillis Current system time, in milliseconds.
	 * @return The number of speculative changes remaining in the local projection (only useful for testing).
	 */
	public int applyChangesForServerTick(long gameTick
			
			, List<PartialEntity> addedEntities
			, List<IReadOnlyCuboidData> addedCuboids
			
			, List<IEntityUpdate> entityUpdates
			, Map<Integer, List<IPartialEntityUpdate>> partialEntityUpdates
			, List<MutationBlockSetBlock> cuboidUpdates
			
			, List<Integer> removedEntities
			, List<CuboidAddress> removedCuboids
			
			, long latestLocalCommitIncluded
			, long currentTimeMillis
	)
	{
		// We assume that we must have been told about ourselves before this first tick.
		Assert.assertTrue(null != _thisShadowEntity);
		
		// Before applying the updates, add the new data.
		_shadowCrowd.putAll(addedEntities.stream().collect(Collectors.toMap((PartialEntity entity) -> entity.id(), (PartialEntity entity) -> entity)));
		_shadowWorld.putAll(addedCuboids.stream().collect(Collectors.toMap((IReadOnlyCuboidData cuboid) -> cuboid.getCuboidAddress(), (IReadOnlyCuboidData cuboid) -> cuboid)));
		
		// Apply all of these to the shadow state, much like TickRunner.  We ONLY change the shadow state in response to these authoritative changes.
		// NOTE:  We must apply these in the same order they are in the TickRunner:  IEntityUpdate BEFORE IMutationBlock.
		
		// TODO:  Determine if we want to apply any immediately mutations or changes (we currently capture them but do nothing with them).
		CommonMutationSink newMutationSink = new CommonMutationSink();
		CommonChangeSink newChangeSink = new CommonChangeSink();
		TickProcessingContext context = _createContext(gameTick, newMutationSink, newChangeSink);
		
		// We won't use the CrowdProcessor here since it applies IMutationEntity but the IEntityUpdate instances are simpler.
		Entity updatedShadowEntity = null;
		if (!entityUpdates.isEmpty())
		{
			Entity entityToChange = _thisShadowEntity;
			// These must already exist if they are being updated.
			Assert.assertTrue(null != entityToChange);
			MutableEntity mutable = MutableEntity.existing(entityToChange);
			for (IEntityUpdate update : entityUpdates)
			{
				update.applyToEntity(context, mutable);
			}
			Entity frozen = mutable.freeze();
			if (entityToChange != frozen)
			{
				updatedShadowEntity = frozen;
			}
		}
		
		Map<Integer, PartialEntity> entitiesChangedInTick = new HashMap<>();
		for (Map.Entry<Integer, List<IPartialEntityUpdate>> elt : partialEntityUpdates.entrySet())
		{
			int entityId = elt.getKey();
			PartialEntity partialEntityToChange = _shadowCrowd.get(entityId);
			// These must already exist if they are being updated.
			Assert.assertTrue(null != partialEntityToChange);
			MutablePartialEntity mutable = MutablePartialEntity.existing(partialEntityToChange);
			for (IPartialEntityUpdate update : elt.getValue())
			{
				update.applyToEntity(context, mutable);
			}
			PartialEntity frozen = mutable.freeze();
			if (partialEntityToChange != frozen)
			{
				entitiesChangedInTick.put(entityId, frozen);
			}
		}

		// The time between ticks doesn't matter when replaying from server.
		long ignoredMillisBetweenTicks = 0L;
		// Split the incoming mutations into the expected map shape.
		List<IMutationBlock> cuboidMutations = cuboidUpdates.stream().map((MutationBlockSetBlock update) -> new BlockUpdateWrapper(update)).collect(Collectors.toList());
		Map<CuboidAddress, List<ScheduledMutation>> mutationsToRun = _createMutationMap(cuboidMutations, _shadowWorld.keySet());
		// We ignore the block updates in the speculative projection (although this is theoretically possible).
		Map<CuboidAddress, List<AbsoluteLocation>> modifiedBlocksByCuboidAddress = Map.of();
		Map<CuboidAddress, List<AbsoluteLocation>> potentialLightChangesByCuboid = Map.of();
		Set<CuboidAddress> cuboidsLoadedThisTick = Set.of();
		WorldProcessor.ProcessedFragment fragment = WorldProcessor.processWorldFragmentParallel(_singleThreadElement
				, _shadowWorld
				, context
				, ignoredMillisBetweenTicks
				, mutationsToRun
				, modifiedBlocksByCuboidAddress
				, potentialLightChangesByCuboid
				, cuboidsLoadedThisTick
		);
		
		// Apply these to the shadow collections.
		// (we ignore exported changes or mutations since we will wait for the server to send those to us, once it commits them)
		if (null != updatedShadowEntity)
		{
			_thisShadowEntity = updatedShadowEntity;
		}
		_shadowCrowd.putAll(entitiesChangedInTick);
		_shadowWorld.putAll(fragment.stateFragment());
		
		// Remove before moving on to our projection.
		_shadowCrowd.keySet().removeAll(removedEntities);
		_shadowWorld.keySet().removeAll(removedCuboids);
		
		// Build the initial modified sets just by looking at what top-level elements of the shadow world deviate from our old projection (and we will add to this as we apply our local updates.
		// Note that these are all immutable so instance comparison is sufficient.
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
		boolean isFirstRun = (null == _projectedLocalEntity);
		_projectedWorld = new HashMap<>(_shadowWorld);
		Entity previousLocalEntity = _projectedLocalEntity;
		_projectedLocalEntity = _thisShadowEntity;
		
		// Step forward the follow-ups before we add to them when processing speculative changes.
		if (_followUpTicks.size() > 0)
		{
			_followUpTicks.remove(0);
		}
		
		Set<CuboidAddress> modifiedCuboidAddresses = new HashSet<>();
		List<_SpeculativeWrapper> previous = new ArrayList<>(_speculativeChanges);
		_speculativeChanges.clear();
		for (_SpeculativeWrapper wrapper : previous)
		{
			// Only consider this if it is more recent than the level we are applying.
			if (wrapper.commitLevel > latestLocalCommitIncluded)
			{
				_SpeculativeWrapper appliedWrapper = _forwardApplySpeculative(modifiedCuboidAddresses, wrapper.change, wrapper.commitLevel);
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
						shared = new _SpeculativeConsequences(new ArrayList<>(), new ArrayList<>());
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
			_applyFollowUp(modifiedCuboidAddresses, followUp);
		}
		
		// Notify the listener of was added.
		if (isFirstRun)
		{
			_listener.thisEntityDidLoad(_projectedLocalEntity);
			// We won't say that this changed.
			updatedShadowEntity = null;
		}
		for (PartialEntity entity : addedEntities)
		{
			_listener.otherEntityDidLoad(entity);
		}
		for (IReadOnlyCuboidData cuboid : addedCuboids)
		{
			_listener.cuboidDidLoad(cuboid);
		}
		
		// Use the common path to describe what was changed.
		Entity changedLocalEntity = ((null != previousLocalEntity) && (previousLocalEntity != _projectedLocalEntity)) ? _projectedLocalEntity : null;
		Set<Integer> otherEntitiesChanges = new HashSet<>(entitiesChangedInTick.keySet());
		otherEntitiesChanges.remove(_localEntityId);
		_notifyChanges(revertedCuboidAddresses, modifiedCuboidAddresses, changedLocalEntity, otherEntitiesChanges);
		
		// Notify the listeners of what was removed.
		for (Integer id : removedEntities)
		{
			// We shouldn't see ourself unload.
			Assert.assertTrue(_localEntityId != id);
			_listener.otherEntityDidUnload(id);
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
	public long applyLocalChange(IMutationEntity<IMutablePlayerEntity> change, long currentTimeMillis)
	{
		// Create the new commit number although we will reverse this if we can merge.
		long commitNumber = _nextLocalCommitNumber;
		_nextLocalCommitNumber += 1;
		
		// Create the tracking for modifications.
		Entity previousLocalEntity = _projectedLocalEntity;
		Set<CuboidAddress> modifiedCuboidAddresses = new HashSet<>();
		
		// Attempt to apply the change.
		_SpeculativeWrapper appliedWrapper = _forwardApplySpeculative(modifiedCuboidAddresses, change, commitNumber);
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
		Entity changedLocalEntity = ((null != previousLocalEntity) && (previousLocalEntity != _projectedLocalEntity)) ? _projectedLocalEntity : null;
		_notifyChanges(Set.of(), modifiedCuboidAddresses, changedLocalEntity, Set.of());
		return commitNumber;
	}


	private void _notifyChanges(Set<CuboidAddress> revertedCuboidAddresses, Set<CuboidAddress> changedCuboidAddresses, Entity updatedLocalEntity, Set<Integer> otherEntityIds)
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
		// Reverted addresses are reported all the time, though.
		for (CuboidAddress address : revertedCuboidAddresses)
		{
			if (!cuboidsToReport.containsKey(address))
			{
				cuboidsToReport.put(address, _projectedWorld.get(address));
			}
		}
		for (IReadOnlyCuboidData data : cuboidsToReport.values())
		{
			_listener.cuboidDidChange(data);
		}
		if (null != updatedLocalEntity)
		{
			_listener.thisEntityDidChange(updatedLocalEntity);
		}
		for (Integer entityId : otherEntityIds)
		{
			_listener.otherEntityDidChange(_shadowCrowd.get(entityId));
		}
	}

	// Note that we populate modifiedCuboids with cuboids changed by this change but only the local entity could change (others are all read-only).
	private _SpeculativeWrapper _forwardApplySpeculative(Set<CuboidAddress> modifiedCuboids, IMutationEntity<IMutablePlayerEntity> change, long commitNumber)
	{
		// We will apply this change to the projected state using the common logic mechanism, looping on any produced updates until complete.
		
		// Only the server can apply ticks so just provide 0.
		long gameTick = 0L;
		
		CommonMutationSink newMutationSink = new CommonMutationSink();
		CommonChangeSink newChangeSink = new CommonChangeSink();
		TickProcessingContext context = _createContext(gameTick
				, newMutationSink
				, newChangeSink
		);
		
		Entity[] changedProjectedEntity = _runChangesOnEntity(_singleThreadElement, context, _localEntityId, _projectedLocalEntity, List.of(change));
		boolean changeDidPass = (null != changedProjectedEntity);
		if ((null != changedProjectedEntity) && (null != changedProjectedEntity[0]))
		{
			_projectedLocalEntity = changedProjectedEntity[0];
		}
		
		// We only bother capturing the changes for this entity since those are the only ones we speculatively apply.
		List<IMutationEntity<IMutablePlayerEntity>> exportedChanges = new ArrayList<>();
		List<ScheduledChange> thisEntityChanges = newChangeSink.takeExportedChanges().get(_localEntityId);
		if (null != thisEntityChanges)
		{
			exportedChanges.addAll(_onlyImmediateChanges(thisEntityChanges));
		}
		List<IMutationBlock> exportedMutations = _onlyImmediateMutations(newMutationSink.takeExportedMutations());
		
		// Now, loop on applying changes (we will batch the consequences of each step together - we aren't scheduling like the server would, either way).
		List<_SpeculativeConsequences> followUpTicks = new ArrayList<>();
		for (int i = 0; (i < MAX_FOLLOW_UP_TICKS) && (!exportedChanges.isEmpty() || !exportedMutations.isEmpty()); ++i)
		{
			_SpeculativeConsequences consequences = new _SpeculativeConsequences(exportedChanges, exportedMutations);
			followUpTicks.add(consequences);
			
			CommonMutationSink innerNewMutationSink = new CommonMutationSink();
			CommonChangeSink innerNewChangeSink = new CommonChangeSink();
			TickProcessingContext innerContext = _createContext(gameTick
					, innerNewMutationSink
					, innerNewChangeSink
			);
			
			// Run these changes and mutations, collecting the resultant output from them.
			_applyFollowUpBlockMutations(innerContext, modifiedCuboids, exportedMutations);
			_applyFollowUpEntityMutations(innerContext, exportedChanges);
			
			// Coalesce the results of these (again, only for this entity).
			exportedChanges = new ArrayList<>();
			thisEntityChanges = innerNewChangeSink.takeExportedChanges().get(_localEntityId);
			if (null != thisEntityChanges)
			{
				exportedChanges.addAll(_onlyImmediateChanges(thisEntityChanges));
			}
			exportedMutations = new ArrayList<>(_onlyImmediateMutations(innerNewMutationSink.takeExportedMutations()));
		}
		
		// Since we only provided a since entity change, return success if it passed.
		return changeDidPass
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

	private void _applyFollowUp(Set<CuboidAddress> modifiedCuboids, _SpeculativeConsequences followUp)
	{
		long gameTick = 0L;
		CommonMutationSink innerNewMutationSink = new CommonMutationSink();
		CommonChangeSink innerNewChangeSink = new CommonChangeSink();
		TickProcessingContext innerContext = _createContext(gameTick
				, innerNewMutationSink
				, innerNewChangeSink
		);
		
		// We ignore the results of these.
		_applyFollowUpBlockMutations(innerContext, modifiedCuboids, followUp.exportedMutations);
		_applyFollowUpEntityMutations(innerContext, followUp.exportedChanges);
	}

	private void _applyFollowUpBlockMutations(TickProcessingContext context, Set<CuboidAddress> modifiedCuboids, List<IMutationBlock> blockMutations)
	{
		// Ignored variables in speculative mode.
		long millisSinceLastTick = 0L;
		
		Map<CuboidAddress, List<ScheduledMutation>> innerMutations = _createMutationMap(blockMutations, _projectedWorld.keySet());
		// We ignore the block updates in the speculative projection (although this is theoretically possible).
		Map<CuboidAddress, List<AbsoluteLocation>> modifiedBlocksByCuboidAddress = Map.of();
		Map<CuboidAddress, List<AbsoluteLocation>> potentialLightChangesByCuboid = Map.of();
		Set<CuboidAddress> cuboidsLoadedThisTick = Set.of();
		WorldProcessor.ProcessedFragment innerFragment = WorldProcessor.processWorldFragmentParallel(_singleThreadElement
				, _projectedWorld
				, context
				, millisSinceLastTick
				, innerMutations
				, modifiedBlocksByCuboidAddress
				, potentialLightChangesByCuboid
				, cuboidsLoadedThisTick
		);
		_projectedWorld.putAll(innerFragment.stateFragment());
		modifiedCuboids.addAll(innerFragment.blockChangesByCuboid().keySet());
	}

	private void _applyFollowUpEntityMutations(TickProcessingContext context, List<IMutationEntity<IMutablePlayerEntity>> thisEntityMutations)
	{
		if (null != thisEntityMutations)
		{
			Entity[] result = _runChangesOnEntity(_singleThreadElement, context, _localEntityId, _projectedLocalEntity, thisEntityMutations);
			if ((null != result) && (null != result[0]))
			{
				_projectedLocalEntity = result[0];
			}
		}
	}

	private List<IMutationEntity<IMutablePlayerEntity>> _onlyImmediateChanges(List<ScheduledChange> thisChanges)
	{
		List<IMutationEntity<IMutablePlayerEntity>> list = thisChanges.stream().filter(
				(ScheduledChange change) -> (0L == change.millisUntilReady())
		).map(
				(ScheduledChange change) -> change.change()
		).toList();
		return list;
	}

	private List<IMutationBlock> _onlyImmediateMutations(List<ScheduledMutation> mutations)
	{
		return mutations.stream().filter(
				(ScheduledMutation mutation) -> (0L == mutation.millisUntilReady())
		).map(
				(ScheduledMutation mutation) -> mutation.mutation()
		).toList();
	}

	private static Entity[] _runChangesOnEntity(ProcessorElement processor, TickProcessingContext context, int entityId, Entity entity, List<IMutationEntity<IMutablePlayerEntity>> entityMutations)
	{
		// The time between ticks doesn't matter when replaying from server.
		long ignoredMillisBetweenTicks = 0L;
		
		List<ScheduledChange> scheduled = _scheduledChangeList(entityMutations);
		CrowdProcessor.ProcessedGroup innerGroup = CrowdProcessor.processCrowdGroupParallel(processor
				, (null != entity) ? Map.of(entityId, entity) : Map.of()
				, context
				, ignoredMillisBetweenTicks
				, Map.of(entityId, scheduled)
		);
		return (innerGroup.committedMutationCount() > 0)
				? new Entity[] { innerGroup.updatedEntities().get(entityId) }
				: null
		;
	}

	private static List<ScheduledChange> _scheduledChangeList(List<IMutationEntity<IMutablePlayerEntity>> changes)
	{
		return changes.stream().map(
				(IMutationEntity<IMutablePlayerEntity> change) -> new ScheduledChange(change, 0L)
		).toList();
	}

	private TickProcessingContext _createContext(long gameTick
			, CommonMutationSink newMutationSink
			, CommonChangeSink newChangeSink
	)
	{
		BasicBlockProxyCache cachingLoader = new BasicBlockProxyCache(this.projectionBlockLoader);
		TickProcessingContext context = new TickProcessingContext(gameTick
				, cachingLoader
				, (Integer entityId) -> (_localEntityId == entityId)
					? MinimalEntity.fromEntity(_thisShadowEntity)
					: MinimalEntity.fromPartialEntity(_shadowCrowd.get(entityId))
				, newMutationSink
				, newChangeSink
				// We never spawn creatures on the client so no ID assigner.
				, null
				// We need a random number generator for a few cases (like attack) but the server will send us the authoritative result.
				, (int bound) -> 0
				// By default, we run in hostile mode.
				, Difficulty.HOSTILE
		);
		return context;
	}


	public static interface IProjectionListener
	{
		void cuboidDidLoad(IReadOnlyCuboidData cuboid);
		void cuboidDidChange(IReadOnlyCuboidData cuboid);
		void cuboidDidUnload(CuboidAddress address);
		
		void thisEntityDidLoad(Entity entity);
		void thisEntityDidChange(Entity entity);
		
		void otherEntityDidLoad(PartialEntity entity);
		void otherEntityDidChange(PartialEntity entity);
		void otherEntityDidUnload(int id);
	}

	private static record _SpeculativeWrapper(long commitLevel
			, IMutationEntity<IMutablePlayerEntity> change
			, List<_SpeculativeConsequences> followUpTicks
	) {}

	private static record _SpeculativeConsequences(List<IMutationEntity<IMutablePlayerEntity>> exportedChanges, List<IMutationBlock> exportedMutations)
	{
		public void absorb(_SpeculativeConsequences followUp)
		{
			this.exportedChanges.addAll(followUp.exportedChanges);
			this.exportedMutations.addAll(followUp.exportedMutations);
		}
	}
}
