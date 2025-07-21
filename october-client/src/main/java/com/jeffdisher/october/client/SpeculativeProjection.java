package com.jeffdisher.october.client;

import java.util.ArrayList;
import java.util.Collections;
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

import com.jeffdisher.october.actions.EntityChangeTopLevelMovement;
import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.data.OctreeInflatedByte;
import com.jeffdisher.october.logic.BlockChangeDescription;
import com.jeffdisher.october.logic.CommonChangeSink;
import com.jeffdisher.october.logic.CommonMutationSink;
import com.jeffdisher.october.logic.CrowdProcessor;
import com.jeffdisher.october.logic.HeightMapHelpers;
import com.jeffdisher.october.logic.ProcessorElement;
import com.jeffdisher.october.logic.ScheduledChange;
import com.jeffdisher.october.logic.ScheduledMutation;
import com.jeffdisher.october.logic.SyncPoint;
import com.jeffdisher.october.logic.WorldProcessor;
import com.jeffdisher.october.mutations.IEntityUpdate;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.IPartialEntityUpdate;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.mutations.MutationEntitySetEntity;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.CuboidColumnAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.LazyLocationCache;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.types.MutableEntity;
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
	
	private final List<_LocalActionWrapper> _speculativeChanges;
	private final List<_LocalCallConsequences> _followUpTicks;
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
		Map<AbsoluteLocation, MutationBlockSetBlock> previousProjectedChanges;
		Set<CuboidAddress> previousProjectedUnsafeLight;
		Entity previousLocalEntity;
		if (null != _projectedState)
		{
			previousProjectedChanges = _projectedState.projectedBlockChanges;
			previousProjectedUnsafeLight = _projectedState.projectedUnsafeLight;
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
			previousProjectedUnsafeLight = Set.of();
			previousLocalEntity = null;
		}
		_projectedState = _shadowState.buildProjectedState(previousProjectedChanges, previousProjectedUnsafeLight);
		
		// Step forward the follow-ups before we add to them when processing speculative changes.
		if (_followUpTicks.size() > 0)
		{
			_followUpTicks.remove(0);
		}
		
		// Merge the follow-up ticks.
		IEntityUpdate modifiedEntity = null;
		Map<AbsoluteLocation, MutationBlockSetBlock> modifiedBlocks = Map.of();
		Map<CuboidAddress, OctreeInflatedByte> modifiedLights = new HashMap<>();
		for (int i = 0; i < _followUpTicks.size(); ++i)
		{
			_LocalCallConsequences followUp = _followUpTicks.get(i);
			if (null != followUp.entityUpdate)
			{
				modifiedEntity = followUp.entityUpdate;
			}
			modifiedBlocks = _mergeChanges(modifiedBlocks, followUp.blockUpdates);
			modifiedLights.putAll(followUp.lightingChanges);
		}
		
		// Merge the speculative changes which are still not committed.
		Iterator<_LocalActionWrapper> previous = _speculativeChanges.iterator();
		while (previous.hasNext())
		{
			_LocalActionWrapper wrapper = previous.next();
			
			// Only consider this if it is more recent than the level we are applying.
			if (wrapper.commitNumber > latestLocalCommitIncluded)
			{
				if (null != wrapper.inlineEntityUpdate)
				{
					modifiedEntity = wrapper.inlineEntityUpdate;
				}
				for (_LocalCallConsequences consequence : wrapper.consequences)
				{
					if (null != consequence.entityUpdate)
					{
						modifiedEntity = consequence.entityUpdate;
					}
					modifiedBlocks = _mergeChanges(modifiedBlocks, consequence.blockUpdates);
					modifiedLights.putAll(consequence.lightingChanges);
				}
			}
			else
			{
				// This has been committed (or dropped) so remove it from speculative as the shadow has the data if it was accepted.
				previous.remove();
				
				// Merge this into follow-ups after applying it to the current change set.
				Iterator<_LocalCallConsequences> oldFollowUpdate = new ArrayList<>(_followUpTicks).iterator();
				_followUpTicks.clear();
				_LocalCallConsequences empty = new _LocalCallConsequences(null, Map.of(), Map.of());
				// NOTE:  We skip the inline change, here, since it is the action covered by the commit.
				for (_LocalCallConsequences consequence : wrapper.consequences)
				{
					// Apply these changes since they are still in play for this tick.
					if (null != consequence.entityUpdate)
					{
						modifiedEntity = consequence.entityUpdate;
					}
					modifiedBlocks = _mergeChanges(modifiedBlocks, consequence.blockUpdates);
					modifiedLights.putAll(consequence.lightingChanges);
					
					// Merge them in to the corresponding follow-up.
					_LocalCallConsequences followUp = oldFollowUpdate.hasNext() ? oldFollowUpdate.next() : empty;
					_LocalCallConsequences merged = _LocalCallConsequences.merge(followUp, consequence);
					_followUpTicks.add(merged);
				}
			}
		}
		
		// We want to strip out any updates to cuboids which have been removed (in this or a previous tick - just check what is here).
		modifiedBlocks = new HashMap<>(modifiedBlocks);
		Iterator<AbsoluteLocation> iter = modifiedBlocks.keySet().iterator();
		while (iter.hasNext())
		{
			if (!_projectedState.projectedWorld.containsKey(iter.next().getCuboidAddress()))
			{
				iter.remove();
			}
		}
		
		// Now that we have collected all the changes which must be applied to the projection, apply them.
		_applyLightingCapture(modifiedLights);
		_applyChangesToProjection(modifiedEntity, modifiedBlocks);
		
		
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
			CuboidAddress address = cuboid.getCuboidAddress();
			_listener.cuboidDidLoad(cuboid, _projectedState.projectedHeightMap.get(address), columnHeightMaps.get(address.getColumn()));
		}
		
		// Use the common path to describe what was changed.
		Entity changedLocalEntity = ((null != previousLocalEntity) && (previousLocalEntity != _projectedState.projectedLocalEntity)) ? _projectedState.projectedLocalEntity : null;
		Assert.assertTrue(!summary.partialEntitiesChanged().contains(_localEntityId));
		ClientChangeNotifier.notifyCuboidChangesFromServer(_listener
				, _projectedState
				, summary.changedBlocks()
				, modifiedBlocks
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
	public long applyLocalChange(EntityChangeTopLevelMovement<IMutablePlayerEntity> change, long currentTickTimeMillis)
	{
		// Create the new commit number although we will reverse this if we can merge.
		long commitNumber = _nextLocalCommitNumber;
		_nextLocalCommitNumber += 1;
		
		// Create the tracking for modifications.
		Entity previousLocalEntity = _projectedState.projectedLocalEntity;
		
		// Attempt to apply the change.
		_LocalActionWrapper output = _applyChangeToProjected(change, commitNumber, currentTickTimeMillis);
		if (null != output)
		{
			_speculativeChanges.add(output);
			Map<AbsoluteLocation, MutationBlockSetBlock> modifiedBlocks = Map.of();
			Set<CuboidAddress> lightOpt = new HashSet<>();
			for (_LocalCallConsequences consequence : output.consequences)
			{
				modifiedBlocks = _mergeChanges(modifiedBlocks, consequence.blockUpdates);
				lightOpt.addAll(consequence.lightingChanges.keySet());
			}
			
			// Notify the listener of what changed.
			Entity changedLocalEntity = ((null != previousLocalEntity) && (previousLocalEntity != _projectedState.projectedLocalEntity)) ? _projectedState.projectedLocalEntity : null;
			ClientChangeNotifier.notifyCuboidChangesFromLocal(_listener
					, _projectedState
					, modifiedBlocks
					, lightOpt
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

	private _LocalActionWrapper _applyChangeToProjected(EntityChangeTopLevelMovement<IMutablePlayerEntity> change
			, long commitNumber
			, long currentTickTimeMillis
	)
	{
		// We will run the change and some follow-up ticks so we can show follow-up consequences.
		
		// Only the server can apply ticks so just provide 0.
		long gameTick = 0L;
		
		// We will collect our results from this sequence of iterations.
		List<_LocalCallConsequences> consequences = new ArrayList<>();
		
		// We will handle the initial call inline with the follow-ups since they have the same shape, just some different parameters.
		List<IEntityAction<IMutablePlayerEntity>> entityChangesToRun = List.of(change);
		List<IMutationBlock> blockMutationstoRun = List.of();
		Map<CuboidAddress, List<AbsoluteLocation>> potentialLightChangesByCuboid = Map.of();
		Set<CuboidAddress> accumulatedLightingChangeCuboids = new HashSet<>();
		boolean didFirstPass = true;
		IEntityUpdate inlineUpdate = null;
		for (int i = 0; (i <= MAX_FOLLOW_UP_TICKS) && didFirstPass && (!entityChangesToRun.isEmpty() || !blockMutationstoRun.isEmpty() || !potentialLightChangesByCuboid.isEmpty()); ++i)
		{
			// Create the context and mutation sinks for this invocation.
			CommonMutationSink newMutationSink = new CommonMutationSink();
			CommonChangeSink newChangeSink = new CommonChangeSink();
			TickProcessingContext context = _createContext(gameTick
					, newMutationSink
					, newChangeSink
					, true
					, _serverMillisPerTick
					, currentTickTimeMillis
			);
			
			// Run these changes and mutations, collecting the resultant output from them.
			Entity[] entityResult = _runChangesOnEntity(_singleThreadElement, context, _localEntityId, _projectedState.projectedLocalEntity, entityChangesToRun);
			// This returns non-null on success but the container may be empty if the entity didn't change.
			IEntityUpdate update = null;
			if ((null != entityResult) && (null != entityResult[0]))
			{
				_projectedState.projectedLocalEntity = entityResult[0];
				update = new MutationEntitySetEntity(_projectedState.projectedLocalEntity);
			}
			// On the first invocation, we MUST pass and we will capture the inline update for immediate reporting.
			if (0 == i)
			{
				didFirstPass = (null != entityResult);
				inlineUpdate = update;
				update = null;
			}
			
			if (didFirstPass)
			{
				_ApplicationResult result = _applyBlockMutationsToProjected(context, blockMutationstoRun, potentialLightChangesByCuboid);
				
				// Collect the results and package them.
				// Note that we will drop any updates which are lighting-only and use the lighting opt with unsafe octree access.
				Map<AbsoluteLocation, MutationBlockSetBlock> nonLightModifiedBlocks = new HashMap<>();
				Map<CuboidAddress, OctreeInflatedByte> lightingOpt = new HashMap<>();
				for (Map.Entry<AbsoluteLocation, MutationBlockSetBlock> modified : result.modifiedBlocks.entrySet())
				{
					AbsoluteLocation key = modified.getKey();
					MutationBlockSetBlock value = modified.getValue();
					CuboidAddress address = key.getCuboidAddress();
					if (value.isSingleAspect(AspectRegistry.LIGHT))
					{
						// See if we need to extract the lighting.
						if (!lightingOpt.containsKey(address))
						{
							OctreeInflatedByte unsafeLighting = CuboidUnsafe.getAspectUnsafe(_projectedState.projectedWorld.get(address), AspectRegistry.LIGHT);
							lightingOpt.put(address, unsafeLighting);
						}
					}
					else
					{
						// Just pass this one.
						nonLightModifiedBlocks.put(key, value);
					}
				}
				
				_LocalCallConsequences consequence = new _LocalCallConsequences(update, nonLightModifiedBlocks, lightingOpt);
				consequences.add(consequence);
				
				// Take the results and prepare to run them in the next iteration.
				List<ScheduledChange> thisEntityChanges = newChangeSink.takeExportedChanges().get(_localEntityId);
				entityChangesToRun = (null != thisEntityChanges)
						? _onlyImmediateChanges(thisEntityChanges)
						: List.of()
				;
				List<ScheduledMutation> theseBlockChanges = newMutationSink.takeExportedMutations();
				blockMutationstoRun = (null != theseBlockChanges)
						? _onlyImmediateMutations(theseBlockChanges)
						: List.of()
				;
				potentialLightChangesByCuboid = result.potentialLightChangesByCuboid;
				accumulatedLightingChangeCuboids.addAll(result.potentialLightChangesByCuboid.keySet());
			}
		}
		
		return didFirstPass
				? new _LocalActionWrapper(commitNumber, inlineUpdate, Collections.unmodifiableList(consequences))
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

	private _ApplicationResult _applyBlockMutationsToProjected(TickProcessingContext context
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
		Map<AbsoluteLocation, MutationBlockSetBlock> outputModifiedBlocks = new HashMap<>();
		Map<CuboidAddress, List<AbsoluteLocation>> outputPotentialLightChangesByCuboid = new HashMap<>();
		for (List<BlockChangeDescription> elt : innerFragment.blockChangesByCuboid().values())
		{
			for (BlockChangeDescription desc : elt)
			{
				MutationBlockSetBlock mutation = desc.serializedForm();
				AbsoluteLocation location = mutation.getAbsoluteLocation();
				MutationBlockSetBlock old = outputModifiedBlocks.put(location, mutation);
				Assert.assertTrue(null == old);
				if (desc.requiresLightingCheck())
				{
					CuboidAddress address = location.getCuboidAddress();
					if (!outputPotentialLightChangesByCuboid.containsKey(address))
					{
						outputPotentialLightChangesByCuboid.put(address, new ArrayList<>());
					}
					outputPotentialLightChangesByCuboid.get(address).add(location);
				}
			}
		}
		return new _ApplicationResult(outputModifiedBlocks, outputPotentialLightChangesByCuboid);
	}

	private List<IEntityAction<IMutablePlayerEntity>> _onlyImmediateChanges(List<ScheduledChange> thisChanges)
	{
		List<IEntityAction<IMutablePlayerEntity>> list = thisChanges.stream().filter(
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

	private static Entity[] _runChangesOnEntity(ProcessorElement processor, TickProcessingContext context, int entityId, Entity entity, List<IEntityAction<IMutablePlayerEntity>> entityMutations)
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

	private static List<ScheduledChange> _scheduledChangeList(List<IEntityAction<IMutablePlayerEntity>> changes)
	{
		return changes.stream().map(
				(IEntityAction<IMutablePlayerEntity> change) -> new ScheduledChange(change, 0L)
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

	private Map<AbsoluteLocation, MutationBlockSetBlock> _mergeChanges(Map<AbsoluteLocation, MutationBlockSetBlock> one, Map<AbsoluteLocation, MutationBlockSetBlock> two)
	{
		Map<AbsoluteLocation, MutationBlockSetBlock> container = new HashMap<>(one);
		for (Map.Entry<AbsoluteLocation, MutationBlockSetBlock> elt : two.entrySet())
		{
			AbsoluteLocation location = elt.getKey();
			MutationBlockSetBlock bottom = container.get(location);
			MutationBlockSetBlock top = elt.getValue();
			if (null == bottom)
			{
				container.put(location, top);
			}
			else
			{
				MutationBlockSetBlock merged = MutationBlockSetBlock.merge(bottom, top);
				container.put(location, merged);
			}
		}
		return container;
	}

	private void _applyChangesToProjection(IEntityUpdate modifiedEntity, Map<AbsoluteLocation, MutationBlockSetBlock> modifiedBlocks)
	{
		if (null != modifiedEntity)
		{
			MutableEntity mutable = MutableEntity.existing(_projectedState.projectedLocalEntity);
			modifiedEntity.applyToEntity(mutable);
			_projectedState.projectedLocalEntity = mutable.freeze();
		}
		Map<CuboidAddress, CuboidData> scratchMap = new HashMap<>();
		for (Map.Entry<AbsoluteLocation, MutationBlockSetBlock> modifiedBlock : modifiedBlocks.entrySet())
		{
			AbsoluteLocation location = modifiedBlock.getKey();
			CuboidAddress address = location.getCuboidAddress();
			if (!scratchMap.containsKey(address))
			{
				IReadOnlyCuboidData readOnly = _projectedState.projectedWorld.get(address);
				// Note that any removed cuboids should have stripped their references from modifiedBlocks.
				Assert.assertTrue(null != readOnly);
				CuboidData cuboid = CuboidData.mutableClone(readOnly);
				scratchMap.put(address, cuboid);
			}
			CuboidData cuboid = scratchMap.get(address);
			MutationBlockSetBlock mutation = modifiedBlock.getValue();
			mutation.applyState(cuboid);
		}
		
		// Update the projected world with these changes (including height maps).
		for (Map.Entry<CuboidAddress, CuboidData> modified : scratchMap.entrySet())
		{
			CuboidAddress address = modified.getKey();
			CuboidData updated = modified.getValue();
			_projectedState.projectedWorld.put(address, updated);
			_projectedState.projectedHeightMap.put(address, HeightMapHelpers.buildHeightMap(updated));
		}
	}


	private static record _ApplicationResult(Map<AbsoluteLocation, MutationBlockSetBlock> modifiedBlocks
			, Map<CuboidAddress, List<AbsoluteLocation>> potentialLightChangesByCuboid
	) {}

	// entityUpdate can be null if there were no entity changes.
	private static record _LocalCallConsequences(IEntityUpdate entityUpdate
			, Map<AbsoluteLocation, MutationBlockSetBlock> blockUpdates
			, Map<CuboidAddress, OctreeInflatedByte> lightingChanges
	) {
		public static _LocalCallConsequences merge(_LocalCallConsequences bottom, _LocalCallConsequences top)
		{
			IEntityUpdate entityUpdate = (null != top.entityUpdate)
					? top.entityUpdate
					: bottom.entityUpdate
			;
			Map<AbsoluteLocation, MutationBlockSetBlock> blockUpdates = new HashMap<>();
			blockUpdates.putAll(bottom.blockUpdates);
			blockUpdates.putAll(top.blockUpdates);
			Map<CuboidAddress, OctreeInflatedByte> lightingChanges = new HashMap<>();
			lightingChanges.putAll(bottom.lightingChanges);
			lightingChanges.putAll(top.lightingChanges);
			return new _LocalCallConsequences(entityUpdate, Collections.unmodifiableMap(blockUpdates), Collections.unmodifiableMap(lightingChanges));
		}
	}

	private static record _LocalActionWrapper(long commitNumber
			, IEntityUpdate inlineEntityUpdate
			, List<_LocalCallConsequences> consequences
	) {}
}
