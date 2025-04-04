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

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.data.OctreeInflatedByte;
import com.jeffdisher.october.logic.BlockChangeDescription;
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
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.CuboidColumnAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.LazyLocationCache;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.types.WorldConfig;
import com.jeffdisher.october.utils.Assert;


/**
 * Instances of this class are used by clients to manage their local interpretation of the world.  It has their loaded
 * cuboids, known entities, and whatever updates they have made, which haven't yet been committed by the server.
 * 
 * When the server accepts messages from clients, it enqueues them into the next tick.  Once that tick is completed, the
 * server sends back any updates (or at least the ones they can see) to the connected clients.  This includes the last
 * commit number from the receiving client (the client assigns these commit numbers to changes it sends to the server).
 * 
 * When the client receives these updates, it applies them to the client's server shadow state.  It then builds a new
 * projected state on top of this shadow state and walks its lists of local changes not yet committed by the server.
 * Any which are at least as old as the commit number the server sent are discarded from its local change list.  Any
 * which are more recent are then applied to its projected state, reporting this to the client's listener.
 * 
 * Any changes made by the client are first applied to the projected state and, if successful, are added to the client's
 * local change list, are assigned commit number, and are sent to the server.  Associated changes are then sent to the
 * client's listener.
 * 
 * Note that, when pruning the changes which the server claims are already considered, it will record some number of
 * ticks worth of or follow-up changes scheduled from within the pruned changes and will apply them for that many game
 * ticks, going forward.  This is to ensure that operations which take multiple steps to take effect will not appear to
 * "revert" when only the first step has been committed.  This is limited, since some operations could result in
 * unbounded numbers of follow-ups which would take real time to be applied and couldn't be reasonably tracked.
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
	private final long _serverMillisPerTick;
	private final Set<EventRecord.Type> _eventTypesToAlwaysReport;

	private final ShadowState _shadowState;
	private ProjectedState _projectedState;
	/**
	 * The block loader is exposed publicly since it is often used by clients to view the world.  Note that this always
	 * points to current _projectedWorld, symbolically.
	 */
	public final Function<AbsoluteLocation, BlockProxy> projectionBlockLoader;
	
	private final List<_SpeculativeWrapper> _speculativeChanges;
	private final List<_SpeculativeConsequences> _followUpTicks;
	private Map<CuboidAddress, OctreeInflatedByte> _followUpLightChanges;
	private long _nextLocalCommitNumber;

	/**
	 * Creates a speculative projection for a single client.
	 * 
	 * @param localEntityId The ID of the local entity where all local changes will be applied.
	 * @param listener The listener for updates to the local projection.
	 * @param serverMillisPerTick The number of millis the server will wait for each tick (for emulating future ticks).
	 */
	public SpeculativeProjection(int localEntityId, IProjectionListener listener, long serverMillisPerTick)
	{
		Assert.assertTrue(null != listener);
		_localEntityId = localEntityId;
		_singleThreadElement = new ProcessorElement(0, new SyncPoint(1), new AtomicInteger(0));
		_listener = listener;
		_serverMillisPerTick = serverMillisPerTick;
		// We want to always pass through the events which are related to entities (since we don't speculate on other entities).
		_eventTypesToAlwaysReport = Set.of(EventRecord.Type.ENTITY_HURT, EventRecord.Type.ENTITY_KILLED);
		
		_shadowState = new ShadowState();
		this.projectionBlockLoader = (AbsoluteLocation location) -> {
			CuboidAddress address = location.getCuboidAddress();
			IReadOnlyCuboidData cuboid = _projectedState.projectedWorld.get(address);
			return (null != cuboid)
					? new BlockProxy(location.getBlockAddress(), cuboid)
					: null
			;
		};
		
		_speculativeChanges = new ArrayList<>();
		_followUpTicks = new ArrayList<>();
		_followUpLightChanges = Map.of();
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
		_shadowState.setThisEntity(thisEntity);
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
	 * @param thisEntityUpdate The update made to this entity which committed in this tick.
	 * @param partialEntityUpdates The map of per-entity state update lists which committed in this tick.
	 * @param cuboidUpdates The list of cuboid updates which committed in this tick.
	 * @param removedEntities The list of entities which were removed in this tick.
	 * @param removedCuboids The list of cuboids which were removed in this tick.
	 * @param events The list of events which were generated in this tick.
	 * @param latestLocalCommitIncluded The latest client-local commit number which was included in this tick.
	 * @param currentTimeMillis Current system time, in milliseconds.
	 * @return The number of speculative changes remaining in the local projection (only useful for testing).
	 */
	public int applyChangesForServerTick(long gameTick
			
			, List<PartialEntity> addedEntities
			, List<IReadOnlyCuboidData> addedCuboids
			
			, IEntityUpdate thisEntityUpdate
			, Map<Integer, List<IPartialEntityUpdate>> partialEntityUpdates
			, List<MutationBlockSetBlock> cuboidUpdates
			
			, List<Integer> removedEntities
			, List<CuboidAddress> removedCuboids
			
			, List<EventRecord> events
			
			, long latestLocalCommitIncluded
			, long currentTimeMillis
	)
	{
		// We will take a snapshot of the previous shadowWorld so we can use it to describe what is changing in the authoritative set.
		Map<CuboidAddress, IReadOnlyCuboidData> staleShadowWorld = _shadowState.getCopyOfWorld();
		
		// Before applying the updates, add the new data.
		ShadowState.ApplicationSummary summary = _shadowState.absorbAuthoritativeChanges(addedEntities, addedCuboids
				, thisEntityUpdate, partialEntityUpdates, cuboidUpdates
				, removedEntities, removedCuboids
		);
		
		// Verify that all state changes to the shadow data actually did something (since this would be a needless change we should prune, otherwise).
		// In the future, we may want to remove this since it is a non-trivial check.
		for (MutationBlockSetBlock update : cuboidUpdates)
		{
			AbsoluteLocation location = update.getAbsoluteLocation();
			CuboidAddress address = location.getCuboidAddress();
			BlockAddress block = location.getBlockAddress();
			IReadOnlyCuboidData staleCuboid = staleShadowWorld.get(address);
			IReadOnlyCuboidData shadowCuboid = _shadowState.getCuboid(address);
			BlockProxy stale = new BlockProxy(block, staleCuboid);
			BlockProxy shadow = new BlockProxy(block, shadowCuboid);
			Assert.assertTrue(!stale.doAspectsMatch(shadow));
		}
		
		// ***** By this point, the shadow state has been updated so we can rebuild the projected state.
		
		// First, we can send the events since they are unrelated to our projected state.
		int thisEntityId = _shadowState.getThisEntity().id();
		for (EventRecord event : events)
		{
			// We may need to strip off some events to avoid redundant reporting:  If the event has this entity as its source AND it is a block-based event, it should be skipped.
			// Events related to other entities are still let through since we don't apply speculative changes to other entities (only the local entity and the world).
			boolean isSourceLocal = thisEntityId == event.entitySource();
			boolean shouldReport = !isSourceLocal || _eventTypesToAlwaysReport.contains(event.type());
			if (shouldReport)
			{
				_listener.handleEvent(event);
			}
		}
		
		// Rebuild our projection from these collections.
		boolean isFirstRun = (null == _projectedState);
		Map<AbsoluteLocation, BlockProxy> previousProjectedChanges;
		Entity previousLocalEntity;
		if (null != _projectedState)
		{
			previousProjectedChanges = _projectedState.projectedBlockChanges;
			previousLocalEntity = _projectedState.projectedLocalEntity;
			// If any cuboids were removed, strip any updates from the previous block changes so we don't try to reverse-verify them.
			if (!removedCuboids.isEmpty())
			{
				Set<CuboidAddress> removed = Set.copyOf(removedCuboids);
				Iterator<AbsoluteLocation> iter = previousProjectedChanges.keySet().iterator();
				while (iter.hasNext())
				{
					if (removed.contains(iter.next().getCuboidAddress()))
					{
						iter.remove();
					}
				}
			}
		}
		else
		{
			previousProjectedChanges = Map.of();
			previousLocalEntity = null;
		}
		_projectedState = _shadowState.buildProjectedState(previousProjectedChanges);
		
		// Step forward the follow-ups before we add to them when processing speculative changes.
		if (_followUpTicks.size() > 0)
		{
			_followUpTicks.remove(0);
		}
		
		Map<CuboidAddress, List<AbsoluteLocation>> modifiedBlocksByCuboid = Map.of();
		for (int i = 0; i < _followUpTicks.size(); ++i)
		{
			_SpeculativeConsequences followUp = _followUpTicks.get(i);
			Map<CuboidAddress, List<AbsoluteLocation>> blocks = _applyFollowUp(followUp);
			modifiedBlocksByCuboid = _mergeChanges(modifiedBlocksByCuboid, blocks);
		}
		// Inject any of the lighting changes before retiring this with the follow-up ticks.
		_applyLightingCapture(_followUpLightChanges);
		_followUpLightChanges = Map.of();
		
		List<_SpeculativeWrapper> previous = new ArrayList<>(_speculativeChanges);
		_speculativeChanges.clear();
		for (_SpeculativeWrapper wrapper : previous)
		{
			// Only consider this if it is more recent than the level we are applying.
			if (wrapper.commitLevel > latestLocalCommitIncluded)
			{
				_SpeculativeOutput output = _forwardApplySpeculative(wrapper.change, false, wrapper.commitLevel, wrapper.lightChanges, wrapper.currentTickTimeMillis);
				// If this was applied, re-add the new wrapper.
				if (null != output)
				{
					_speculativeChanges.add(output.wrapper);
					modifiedBlocksByCuboid = _mergeChanges(modifiedBlocksByCuboid, output.modifiedBlocksByCuboid);
				}
			}
			else
			{
				// Apply this as a follow-up.
				for (_SpeculativeConsequences followUp : wrapper.followUpTicks)
				{
					Map<CuboidAddress, List<AbsoluteLocation>> blocks = _applyFollowUp(followUp);
					modifiedBlocksByCuboid = _mergeChanges(modifiedBlocksByCuboid, blocks);
				}
				_applyLightingCapture(wrapper.lightChanges);
				
				// We are removing this so promote any follow-ups.
				for (int i = 0; i < wrapper.followUpTicks.size(); ++i)
				{
					_SpeculativeConsequences followUp = wrapper.followUpTicks.get(i);
					_SpeculativeConsequences shared;
					if (i < _followUpTicks.size())
					{
						// We will be able to merge this into an existing follow-up.
						shared = _followUpTicks.get(i);
					}
					else
					{
						// We don't have follow-up tracking for this element so create a new one and merge into that.
						shared = new _SpeculativeConsequences(new ArrayList<>(), new ArrayList<>());
						_followUpTicks.add(i, shared);
					}
					shared.absorb(followUp);
				}
				
				// Take the last lighting map from this change.
				_followUpLightChanges = wrapper.lightChanges;
			}
		}
		
		// ***** By this point, the projected state has been replaced so we need to determine what to send to the listener
		
		// We don't keep the height maps, only generating them for the cuboids we need to notify.
		Set<CuboidColumnAddress> columnsToGenerate = addedCuboids.stream().map((IReadOnlyCuboidData cuboid) -> cuboid.getCuboidAddress().getColumn()).collect(Collectors.toUnmodifiableSet());
		Map<CuboidColumnAddress, ColumnHeightMap> columnHeightMaps = _projectedState.buildColumnMaps(columnsToGenerate);
		
		// Notify the listener of what was added.
		if (isFirstRun)
		{
			// This is the first load so nothing should have changed.
			Assert.assertTrue(_shadowState.getThisEntity() == _projectedState.projectedLocalEntity);
			_listener.thisEntityDidLoad(_shadowState.getThisEntity());
		}
		for (PartialEntity entity : addedEntities)
		{
			_listener.otherEntityDidLoad(entity);
		}
		for (IReadOnlyCuboidData cuboid : addedCuboids)
		{
			_listener.cuboidDidLoad(cuboid, columnHeightMaps.get(cuboid.getCuboidAddress().getColumn()));
		}
		
		// Use the common path to describe what was changed.
		Entity changedLocalEntity = ((null != previousLocalEntity) && (previousLocalEntity != _projectedState.projectedLocalEntity)) ? _projectedState.projectedLocalEntity : null;
		Assert.assertTrue(!summary.partialEntitiesChanged().contains(_localEntityId));
		ClientChangeNotifier.notifyCuboidChangesFromServer(_listener
				, _projectedState
				, (CuboidAddress address) -> _shadowState.getCuboid(address)
				, staleShadowWorld
				, summary.changesByCuboid()
				, modifiedBlocksByCuboid
				, columnHeightMaps
		);
		_notifyEntityChanges(_shadowState.getThisEntity(), changedLocalEntity, summary.partialEntitiesChanged());
		
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
		_listener.tickDidComplete(gameTick);
		
		return _speculativeChanges.size();
	}

	/**
	 * Applies the given change to the local state as speculative and returns the local commit number associated.  Note
	 * that the returned number may be the same as that returned by the previous call if the implementation decided that
	 * they could be coalesced.  In this case, the caller should replace the change it has buffered to send to the
	 * server with this one.
	 * Note that this will internally account for time passing if the change takes more than 0 milliseconds.
	 * 
	 * @param change The entity change to apply.
	 * @param currentTickTimeMillis The current time, in milliseconds.
	 * @return The local commit number for this change, 0L if it failed to applied and should be rejected.
	 */
	public long applyLocalChange(IMutationEntity<IMutablePlayerEntity> change, long currentTickTimeMillis)
	{
		// Create the new commit number although we will reverse this if we can merge.
		long commitNumber = _nextLocalCommitNumber;
		_nextLocalCommitNumber += 1;
		
		// Create the tracking for modifications.
		Entity previousLocalEntity = _projectedState.projectedLocalEntity;
		
		// Attempt to apply the change.
		_SpeculativeOutput output = _forwardApplySpeculative(change, true, commitNumber, null, currentTickTimeMillis);
		if (null != output)
		{
			_speculativeChanges.add(output.wrapper);
			Map<CuboidAddress, List<AbsoluteLocation>> modifiedBlocksByCuboid = output.modifiedBlocksByCuboid;
			
			// Notify the listener of what changed.
			Entity changedLocalEntity = ((null != previousLocalEntity) && (previousLocalEntity != _projectedState.projectedLocalEntity)) ? _projectedState.projectedLocalEntity : null;
			ClientChangeNotifier.notifyCuboidChangesFromLocal(_listener
					, _projectedState
					, (CuboidAddress address) -> _shadowState.getCuboid(address)
					, modifiedBlocksByCuboid
			);
			_notifyEntityChanges(_shadowState.getThisEntity(), changedLocalEntity, Set.of());
		}
		else
		{
			// We failed to apply a local immediate commit so just revert the commit number.
			_nextLocalCommitNumber -= 1;
			commitNumber = 0L;
		}
		
		return commitNumber;
	}

	/**
	 * Allows millisToPass to pass within the system, simply so things like physics can be applied to the existing
	 * entity.
	 * 
	 * @param millisToPass The number of milliseconds to allow to pass.
	 * @param currentTickTimeMillis The current time, in milliseconds.
	 */
	public void fakePassTime(long millisToPass, long currentTickTimeMillis)
	{
		// We will just fake the end of tick with no other changes.
		// We will ignore any follow-up mutations or changes.
		CommonMutationSink newMutationSink = new CommonMutationSink();
		CommonChangeSink newChangeSink = new CommonChangeSink();
		TickProcessingContext context = _createContext(0L
				, newMutationSink
				, newChangeSink
				, true
				, millisToPass
				, currentTickTimeMillis
		);
		
		CrowdProcessor.ProcessedGroup innerGroup = CrowdProcessor.processCrowdGroupParallel(_singleThreadElement
				, Map.of(_localEntityId, _projectedState.projectedLocalEntity)
				, context
				, Map.of()
		);
		Entity updated = innerGroup.updatedEntities().get(_localEntityId);
		if (null != updated)
		{
			_projectedState.projectedLocalEntity = updated;
			_notifyEntityChanges(_shadowState.getThisEntity(), _projectedState.projectedLocalEntity, Set.of());
		}
	}

	private void _notifyEntityChanges(Entity authoritativeLocalEntity
			, Entity updatedLocalEntity
			, Set<Integer> otherEntityIds
	)
	{
		if (null != updatedLocalEntity)
		{
			_listener.thisEntityDidChange(authoritativeLocalEntity, updatedLocalEntity);
		}
		for (Integer entityId : otherEntityIds)
		{
			_listener.otherEntityDidChange(_shadowState.getEntity(entityId));
		}
	}

	private _SpeculativeOutput _forwardApplySpeculative(IMutationEntity<IMutablePlayerEntity> change
			, boolean shouldSendEvents
			, long commitNumber
			, Map<CuboidAddress, OctreeInflatedByte> lightChangesIfExisting
			, long currentTickTimeMillis
	)
	{
		// We will apply this change to the projected state using the common logic mechanism, looping on any produced updates until complete.
		
		// Only the server can apply ticks so just provide 0.
		long gameTick = 0L;
		long millisecondsBeforeChange = change.getTimeCostMillis();
		
		CommonMutationSink newMutationSink = new CommonMutationSink();
		CommonChangeSink newChangeSink = new CommonChangeSink();
		TickProcessingContext context = _createContext(gameTick
				, newMutationSink
				, newChangeSink
				, shouldSendEvents
				, millisecondsBeforeChange
				, currentTickTimeMillis
		);
		
		Entity[] changedProjectedEntity = _runChangesOnEntity(_singleThreadElement, context, _localEntityId, _projectedState.projectedLocalEntity, List.of(change));
		boolean changeDidPass = (null != changedProjectedEntity);
		if ((null != changedProjectedEntity) && (null != changedProjectedEntity[0]))
		{
			_projectedState.projectedLocalEntity = changedProjectedEntity[0];
		}
		
		// We only bother capturing the changes for this entity since those are the only ones we speculatively apply.
		List<IMutationEntity<IMutablePlayerEntity>> exportedChanges = new ArrayList<>();
		List<ScheduledChange> thisEntityChanges = newChangeSink.takeExportedChanges().get(_localEntityId);
		if (null != thisEntityChanges)
		{
			exportedChanges.addAll(_onlyImmediateChanges(thisEntityChanges));
		}
		List<IMutationBlock> exportedMutations = _onlyImmediateMutations(newMutationSink.takeExportedMutations());
		// The first action is always an entity action so it will have no lighting changes.
		Map<CuboidAddress, List<AbsoluteLocation>> potentialLightChangesByCuboid = Map.of();
		// If we weren't already given lighting changes to pass through, we want to capture the changes here.
		Set<CuboidAddress> lightingChangeLocations = (null == lightChangesIfExisting) ? new HashSet<>() : null;
		
		// Now, loop on applying changes (we will batch the consequences of each step together - we aren't scheduling like the server would, either way).
		List<_SpeculativeConsequences> followUpTicks = new ArrayList<>();
		Map<CuboidAddress, List<AbsoluteLocation>> modifiedBlocksByCuboid = Map.of();
		for (int i = 0; (i < MAX_FOLLOW_UP_TICKS) && (!exportedChanges.isEmpty() || !exportedMutations.isEmpty() || !potentialLightChangesByCuboid.isEmpty()); ++i)
		{
			_SpeculativeConsequences consequences = new _SpeculativeConsequences(exportedChanges, exportedMutations);
			followUpTicks.add(consequences);
			
			CommonMutationSink innerNewMutationSink = new CommonMutationSink();
			CommonChangeSink innerNewChangeSink = new CommonChangeSink();
			TickProcessingContext innerContext = _createContext(gameTick
					, innerNewMutationSink
					, innerNewChangeSink
					, shouldSendEvents
					, _serverMillisPerTick
					, currentTickTimeMillis
			);
			
			// Run these changes and mutations, collecting the resultant output from them.
			_ApplicationResult result = _applyFollowUpBlockMutations(innerContext, exportedMutations, potentialLightChangesByCuboid);
			modifiedBlocksByCuboid = _mergeChanges(modifiedBlocksByCuboid, result.modifiedBlocksByCuboid);
			_applyFollowUpEntityMutations(innerContext, exportedChanges);
			
			// Coalesce the results of these (again, only for this entity).
			exportedChanges = new ArrayList<>();
			thisEntityChanges = innerNewChangeSink.takeExportedChanges().get(_localEntityId);
			if (null != thisEntityChanges)
			{
				exportedChanges.addAll(_onlyImmediateChanges(thisEntityChanges));
			}
			exportedMutations = new ArrayList<>(_onlyImmediateMutations(innerNewMutationSink.takeExportedMutations()));
			if (null != lightingChangeLocations)
			{
				potentialLightChangesByCuboid = result.potentialLightChangesByCuboid;
				lightingChangeLocations.addAll(result.potentialLightChangesByCuboid.keySet());
			}
		}
		
		// If we weren't given changes to re-apply capture them here.
		Map<CuboidAddress, OctreeInflatedByte> lightChanges;
		if (null != lightingChangeLocations)
		{
			// Capture the lighting from this set.
			lightChanges = new HashMap<>();
			for(CuboidAddress address : lightingChangeLocations)
			{
				OctreeInflatedByte unsafeLighting = CuboidUnsafe.getAspectUnsafe(_projectedState.projectedWorld.get(address), AspectRegistry.LIGHT);
				lightChanges.put(address, unsafeLighting);
			}
		}
		else
		{
			// We must just be using the ones we were already given.
			Assert.assertTrue(null != lightChangesIfExisting);
			_applyLightingCapture(lightChangesIfExisting);
			lightChanges = lightChangesIfExisting;
		}
		
		// So long as the original entity change applied, we consider this a pass.
		return changeDidPass
				? new _SpeculativeOutput(new _SpeculativeWrapper(commitNumber, change, followUpTicks, lightChanges, currentTickTimeMillis), modifiedBlocksByCuboid)
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

	// We return the modified blocks by cuboid.
	private Map<CuboidAddress, List<AbsoluteLocation>> _applyFollowUp(_SpeculativeConsequences followUp)
	{
		long gameTick = 0L;
		CommonMutationSink innerNewMutationSink = new CommonMutationSink();
		CommonChangeSink innerNewChangeSink = new CommonChangeSink();
		// The follow-up doesn't worry about current time since it is being synthetically run in a "future tick".
		long currentTickTimeMillis = 0L;
		TickProcessingContext innerContext = _createContext(gameTick
				, innerNewMutationSink
				, innerNewChangeSink
				, false
				, _serverMillisPerTick
				, currentTickTimeMillis
		);
		
		// We just want to apply the follow-ups, not adjusting how the interact if there were any conflicts.  Precisely
		// rationalizing those conflicts woulds be very complicated and would irrelevant after 1-2 ticks, anyway.
		_ApplicationResult result = _applyFollowUpBlockMutations(innerContext
				, followUp.exportedMutations
				, Map.of()
		);
		_applyFollowUpEntityMutations(innerContext, followUp.exportedChanges);
		return result.modifiedBlocksByCuboid;
	}

	private _ApplicationResult _applyFollowUpBlockMutations(TickProcessingContext context
			, List<IMutationBlock> blockMutations
			, Map<CuboidAddress, List<AbsoluteLocation>> potentialLightChangesByCuboid
	)
	{
		Map<CuboidAddress, List<ScheduledMutation>> innerMutations = _createMutationMap(blockMutations, _projectedState.projectedWorld.keySet());
		// We ignore normal "block update" events and logic changes in the speculative projection.
		// We ignore the block updates in the speculative projection (although this could be used in more precisely notifying the listener).
		Map<CuboidAddress, List<AbsoluteLocation>> modifiedBlocksByCuboidAddress = Map.of();
		Map<CuboidAddress, List<AbsoluteLocation>> potentialLogicChangesByCuboid = Map.of();
		Set<CuboidAddress> cuboidsLoadedThisTick = Set.of();
		WorldProcessor.ProcessedFragment innerFragment = WorldProcessor.processWorldFragmentParallel(_singleThreadElement
				, _projectedState.projectedWorld
				, _projectedState.projectedHeightMap
				, context
				, innerMutations
				, Map.of()
				, modifiedBlocksByCuboidAddress
				, potentialLightChangesByCuboid
				, potentialLogicChangesByCuboid
				, cuboidsLoadedThisTick
		);
		_projectedState.projectedWorld.putAll(innerFragment.stateFragment());
		_projectedState.projectedHeightMap.putAll(innerFragment.heightFragment());
		Map<CuboidAddress, List<AbsoluteLocation>> outputModifiedBlocksByCuboidAddress = new HashMap<>();
		Map<CuboidAddress, List<AbsoluteLocation>> outputPotentialLightChangesByCuboidAddress = new HashMap<>();
		for (Map.Entry<CuboidAddress, List<BlockChangeDescription>> elt : innerFragment.blockChangesByCuboid().entrySet())
		{
			CuboidAddress key = elt.getKey();
			List<BlockChangeDescription> value = elt.getValue();
			
			// This must have something in it if it was returned.
			Assert.assertTrue(!value.isEmpty());
			
			outputModifiedBlocksByCuboidAddress.put(key, value.stream().map(
					(BlockChangeDescription description) -> description.serializedForm().getAbsoluteLocation()
			).toList());
			
			List<AbsoluteLocation> lightChanges = value.stream()
					.filter((BlockChangeDescription description) -> description.requiresLightingCheck())
					.map(
						(BlockChangeDescription update) -> update.serializedForm().getAbsoluteLocation()
					).toList();
			if (!lightChanges.isEmpty())
			{
				outputPotentialLightChangesByCuboidAddress.put(key, lightChanges);
			}
		}
		return new _ApplicationResult(outputModifiedBlocksByCuboidAddress, outputPotentialLightChangesByCuboidAddress);
	}

	private void _applyFollowUpEntityMutations(TickProcessingContext context, List<IMutationEntity<IMutablePlayerEntity>> thisEntityMutations)
	{
		if (null != thisEntityMutations)
		{
			Entity[] result = _runChangesOnEntity(_singleThreadElement, context, _localEntityId, _projectedState.projectedLocalEntity, thisEntityMutations);
			if ((null != result) && (null != result[0]))
			{
				_projectedState.projectedLocalEntity = result[0];
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
		List<ScheduledChange> scheduled = _scheduledChangeList(entityMutations);
		CrowdProcessor.ProcessedGroup innerGroup = CrowdProcessor.processCrowdGroupParallel(processor
				, (null != entity) ? Map.of(entityId, entity) : Map.of()
				, context
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
			, boolean shouldSendEvents
			, long millisPerTick
			, long currentTickTimeMillis 
	)
	{
		LazyLocationCache<BlockProxy> cachingLoader = new LazyLocationCache<>(this.projectionBlockLoader);
		TickProcessingContext.IEventSink eventSink = (EventRecord event) -> {
			if (shouldSendEvents)
			{
				// We will just send these events directly.
				_listener.handleEvent(event);
			}
		};
		TickProcessingContext context = new TickProcessingContext(gameTick
				, cachingLoader
				, (Integer entityId) -> (_localEntityId == entityId)
					? MinimalEntity.fromEntity(_shadowState.getThisEntity())
					: MinimalEntity.fromPartialEntity(_shadowState.getEntity(entityId))
				, null
				, newMutationSink
				, newChangeSink
				// We never spawn creatures on the client so no ID assigner.
				, null
				// We need a random number generator for a few cases (like attack) but the server will send us the authoritative result.
				, (int bound) -> 0
				, eventSink
				// By default, we run in hostile mode.
				, new WorldConfig()
				, millisPerTick
				, currentTickTimeMillis
		);
		return context;
	}

	private void _applyLightingCapture(Map<CuboidAddress, OctreeInflatedByte> followUpLightChanges)
	{
		for (Map.Entry<CuboidAddress, OctreeInflatedByte> elt : followUpLightChanges.entrySet())
		{
			CuboidAddress key = elt.getKey();
			IReadOnlyCuboidData old = _projectedState.projectedWorld.get(key);
			if (null != old)
			{
				IReadOnlyCuboidData update = CuboidUnsafe.cloneWithReplacement(old, AspectRegistry.LIGHT, elt.getValue());
				_projectedState.projectedWorld.put(key, update);
			}
		}
	}

	private Map<CuboidAddress, List<AbsoluteLocation>> _mergeChanges(Map<CuboidAddress, List<AbsoluteLocation>> one, Map<CuboidAddress, List<AbsoluteLocation>> two)
	{
		Map<CuboidAddress, List<AbsoluteLocation>> container = new HashMap<>(one);
		for (Map.Entry<CuboidAddress, List<AbsoluteLocation>> elt : two.entrySet())
		{
			CuboidAddress key = elt.getKey();
			if (!container.containsKey(key))
			{
				container.put(key, new ArrayList<>());
			}
			container.get(key).addAll(elt.getValue());
		}
		return container;
	}


	private static record _SpeculativeWrapper(long commitLevel
			, IMutationEntity<IMutablePlayerEntity> change
			, List<_SpeculativeConsequences> followUpTicks
			, Map<CuboidAddress, OctreeInflatedByte> lightChanges
			, long currentTickTimeMillis
	) {}

	private static record _SpeculativeConsequences(List<IMutationEntity<IMutablePlayerEntity>> exportedChanges
			, List<IMutationBlock> exportedMutations
	)
	{
		public void absorb(_SpeculativeConsequences followUp)
		{
			this.exportedChanges.addAll(followUp.exportedChanges);
			this.exportedMutations.addAll(followUp.exportedMutations);
		}
	}

	private static record _ApplicationResult(Map<CuboidAddress, List<AbsoluteLocation>> modifiedBlocksByCuboid
			, Map<CuboidAddress, List<AbsoluteLocation>> potentialLightChangesByCuboid
	) {}

	private static record _SpeculativeOutput(_SpeculativeWrapper wrapper
			, Map<CuboidAddress, List<AbsoluteLocation>> modifiedBlocksByCuboid
	) {}
}
