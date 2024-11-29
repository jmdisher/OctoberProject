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

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.data.MutableBlockProxy;
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
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.MutablePartialEntity;
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
	
	private Entity _thisShadowEntity;
	private final Map<CuboidAddress, IReadOnlyCuboidData> _shadowWorld;
	private final Map<CuboidAddress, CuboidHeightMap> _shadowHeightMap;
	private final Map<Integer, PartialEntity> _shadowCrowd;
	
	// Note that we don't keep a projection of the crowd since we never apply speculative operations to them.
	// So, we keep a projected version of the local entity and just fall-back to the shadow data for any read-only operations.
	private Entity _projectedLocalEntity;
	private Map<CuboidAddress, IReadOnlyCuboidData> _projectedWorld;
	private Map<CuboidAddress, CuboidHeightMap> _projectedHeightMap;
	private Map<AbsoluteLocation, BlockProxy> _projectedBlockChanges;
	/**
	 * The block loader is exposed publicly since it is often used by clients to view the world.  Note that this always
	 * points to current _projectedWorld, symbolically.
	 */
	public final Function<AbsoluteLocation, BlockProxy> projectionBlockLoader;
	
	private final List<_SpeculativeWrapper> _speculativeChanges;
	private final List<_SpeculativeConsequences> _followUpTicks;
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
		
		// The initial states start as empty and are populated by the server.
		_shadowWorld = new HashMap<>();
		_shadowHeightMap = new HashMap<>();
		_shadowCrowd = new HashMap<>();
		
		_projectedWorld = new HashMap<>();
		_projectedHeightMap = new HashMap<>();
		_projectedBlockChanges = new HashMap<>();
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
	 * @param events The list of events which were generated in this tick.
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
			
			, List<EventRecord> events
			
			, long latestLocalCommitIncluded
			, long currentTimeMillis
	)
	{
		// We assume that we must have been told about ourselves before this first tick.
		Assert.assertTrue(null != _thisShadowEntity);
		
		// We will take a snapshot of the previous shadowWorld so we can use it to describe what is changing in the authoritative set.
		Map<CuboidAddress, IReadOnlyCuboidData> staleShadowWorld = new HashMap<>(_shadowWorld);
		
		// Before applying the updates, add the new data.
		_shadowCrowd.putAll(addedEntities.stream().collect(Collectors.toMap((PartialEntity entity) -> entity.id(), (PartialEntity entity) -> entity)));
		_shadowWorld.putAll(addedCuboids.stream().collect(Collectors.toMap((IReadOnlyCuboidData cuboid) -> cuboid.getCuboidAddress(), (IReadOnlyCuboidData cuboid) -> cuboid)));
		_shadowHeightMap.putAll(addedCuboids.stream().collect(Collectors.toMap((IReadOnlyCuboidData cuboid) -> cuboid.getCuboidAddress(), (IReadOnlyCuboidData cuboid) -> HeightMapHelpers.buildHeightMap(cuboid))));
		
		// Apply all of these to the shadow state, much like TickRunner.  We ONLY change the shadow state in response to these authoritative changes.
		Map<CuboidAddress, List<MutationBlockSetBlock>> updatesToApply = _createUpdateMap(cuboidUpdates, _shadowWorld.keySet());
		_UpdateTuple shadowUpdates = _applyUpdatesToShadowState(entityUpdates, partialEntityUpdates, updatesToApply);
		
		// Apply these to the shadow collections.
		// (we ignore exported changes or mutations since we will wait for the server to send those to us, once it commits them)
		if (null != shadowUpdates.updatedShadowEntity)
		{
			_thisShadowEntity = shadowUpdates.updatedShadowEntity;
		}
		_shadowCrowd.putAll(shadowUpdates.entitiesChangedInTick);
		_shadowWorld.putAll(shadowUpdates.stateFragment());
		_shadowHeightMap.putAll(shadowUpdates.heightFragment());
		
		// Remove before moving on to our projection.
		_shadowCrowd.keySet().removeAll(removedEntities);
		_shadowWorld.keySet().removeAll(removedCuboids);
		_shadowHeightMap.keySet().removeAll(removedCuboids);
		
		// Verify that all state changes to the shadow data actually did something (since this would be a needless change we should prune, otherwise).
		// In the future, we may want to remove this since it is a non-trivial check.
		for (MutationBlockSetBlock update : cuboidUpdates)
		{
			AbsoluteLocation location = update.getAbsoluteLocation();
			CuboidAddress address = location.getCuboidAddress();
			BlockAddress block = location.getBlockAddress();
			IReadOnlyCuboidData staleCuboid = staleShadowWorld.get(address);
			IReadOnlyCuboidData shadowCuboid = _shadowWorld.get(address);
			BlockProxy stale = new BlockProxy(block, staleCuboid);
			BlockProxy shadow = new BlockProxy(block, shadowCuboid);
			Assert.assertTrue(!stale.doAspectsMatch(shadow));
		}
		
		// ***** By this point, the shadow state has been updated so we can rebuild the projected state.
		
		// Rebuild our projection from these collections.
		boolean isFirstRun = (null == _projectedLocalEntity);
		_projectedWorld = new HashMap<>(_shadowWorld);
		_projectedHeightMap = new HashMap<>(_shadowHeightMap);
		Entity previousLocalEntity = _projectedLocalEntity;
		_projectedLocalEntity = _thisShadowEntity;
		
		// Step forward the follow-ups before we add to them when processing speculative changes.
		if (_followUpTicks.size() > 0)
		{
			_followUpTicks.remove(0);
		}
		
		Map<CuboidAddress, List<AbsoluteLocation>> modifiedBlocksByCuboid = new HashMap<>();
		List<_SpeculativeWrapper> previous = new ArrayList<>(_speculativeChanges);
		_speculativeChanges.clear();
		for (_SpeculativeWrapper wrapper : previous)
		{
			// Only consider this if it is more recent than the level we are applying.
			if (wrapper.commitLevel > latestLocalCommitIncluded)
			{
				_SpeculativeWrapper appliedWrapper = _forwardApplySpeculative(modifiedBlocksByCuboid, wrapper.change, wrapper.commitLevel, wrapper.currentTickTimeMillis);
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
			}
		}
		
		// Apply any remaining follow-up changes.
		for (int i = 0; i < _followUpTicks.size(); ++i)
		{
			_SpeculativeConsequences followUp = _followUpTicks.get(i);
			_applyFollowUp(modifiedBlocksByCuboid, followUp);
		}
		
		// ***** By this point, the projected state has been replaced so we need to determine what to send to the listener
		
		// We don't keep the height maps, only generating them for the cuboids we need to notify.
		Set<CuboidColumnAddress> columnsToGenerate = addedCuboids.stream().map((IReadOnlyCuboidData cuboid) -> cuboid.getCuboidAddress().getColumn()).collect(Collectors.toUnmodifiableSet());
		Map<CuboidColumnAddress, ColumnHeightMap> columnHeightMaps = _buildProjectedColumnMaps(columnsToGenerate);
		
		// Notify the listener of what was added.
		if (isFirstRun)
		{
			// This is the first load so nothing should have changed.
			Assert.assertTrue(_thisShadowEntity == _projectedLocalEntity);
			_listener.thisEntityDidLoad(_thisShadowEntity);
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
		Entity changedLocalEntity = ((null != previousLocalEntity) && (previousLocalEntity != _projectedLocalEntity)) ? _projectedLocalEntity : null;
		Set<Integer> otherEntitiesChanges = new HashSet<>(shadowUpdates.entitiesChangedInTick.keySet());
		otherEntitiesChanges.remove(_localEntityId);
		Map<AbsoluteLocation, BlockProxy> previousProjectedChanges = _projectedBlockChanges;
		_projectedBlockChanges = new HashMap<>();
		_notifyCuboidChanges(previousProjectedChanges, staleShadowWorld, updatesToApply, modifiedBlocksByCuboid, columnHeightMaps);
		_notifyEntityChanges(_thisShadowEntity, changedLocalEntity, otherEntitiesChanges);
		
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
		Entity previousLocalEntity = _projectedLocalEntity;
		Map<CuboidAddress, List<AbsoluteLocation>> modifiedBlocksByCuboid = new HashMap<>();
		
		// Attempt to apply the change.
		_SpeculativeWrapper appliedWrapper = _forwardApplySpeculative(modifiedBlocksByCuboid, change, commitNumber, currentTickTimeMillis);
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
		_notifyCuboidChanges(Map.of(), Map.of(), Map.of(), modifiedBlocksByCuboid, Map.of());
		_notifyEntityChanges(_thisShadowEntity, changedLocalEntity, Set.of());
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
				, millisToPass
				, currentTickTimeMillis
		);
		
		CrowdProcessor.ProcessedGroup innerGroup = CrowdProcessor.processCrowdGroupParallel(_singleThreadElement
				, Map.of(_localEntityId, _projectedLocalEntity)
				, context
				, Map.of()
		);
		Entity updated = innerGroup.updatedEntities().get(_localEntityId);
		if (null != updated)
		{
			_projectedLocalEntity = updated;
			_notifyEntityChanges(_thisShadowEntity, _projectedLocalEntity, Set.of());
		}
	}



	private _UpdateTuple _applyUpdatesToShadowState(List<IEntityUpdate> entityUpdates
			, Map<Integer, List<IPartialEntityUpdate>> partialEntityUpdates
			, Map<CuboidAddress, List<MutationBlockSetBlock>> updatesToApply
	)
	{
		Entity updatedShadowEntity = _applyLocalEntityUpdatesToShadowState(entityUpdates);
		Map<Integer, PartialEntity> entitiesChangedInTick = _applyPartialEntityUpdatesToShadowState(partialEntityUpdates);
		
		Map<CuboidAddress, IReadOnlyCuboidData> updatedCuboids = new HashMap<>();
		Map<CuboidAddress, CuboidHeightMap> updatedMaps = new HashMap<>();
		_applyCuboidUpdatesToShadowState(updatedCuboids
				, updatedMaps
				, updatesToApply
		);
		_UpdateTuple shadowUpdates = new _UpdateTuple(updatedShadowEntity
				, entitiesChangedInTick
				, updatedCuboids
				, updatedMaps
		);
		return shadowUpdates;
	}

	private Entity _applyLocalEntityUpdatesToShadowState(List<IEntityUpdate> entityUpdates)
	{
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
				update.applyToEntity(mutable);
			}
			Entity frozen = mutable.freeze();
			if (entityToChange != frozen)
			{
				updatedShadowEntity = frozen;
			}
		}
		return updatedShadowEntity;
	}

	private Map<Integer, PartialEntity> _applyPartialEntityUpdatesToShadowState(Map<Integer, List<IPartialEntityUpdate>> partialEntityUpdates)
	{
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
				update.applyToEntity(mutable);
			}
			PartialEntity frozen = mutable.freeze();
			if (partialEntityToChange != frozen)
			{
				entitiesChangedInTick.put(entityId, frozen);
			}
		}
		return entitiesChangedInTick;
	}

	private void _applyCuboidUpdatesToShadowState(Map<CuboidAddress, IReadOnlyCuboidData> out_updatedCuboids
			, Map<CuboidAddress, CuboidHeightMap> out_updatedMaps
			, Map<CuboidAddress, List<MutationBlockSetBlock>> updatesToApply
	)
	{
		// NOTE:  This logic is similar to WorldProcessor but partially-duplicated here to avoid all the other requirements of the WorldProcessor or redundant operations it would perform.
		for (Map.Entry<CuboidAddress, List<MutationBlockSetBlock>> entry : updatesToApply.entrySet())
		{
			Set<AbsoluteLocation> existingUpdates = new HashSet<>();
			CuboidAddress address = entry.getKey();
			IReadOnlyCuboidData readOnly = _shadowWorld.get(address);
			
			// We will lazily create the mutable version, only if something needs to be written-back (since some updates, at least in tests, are meaningless).
			CuboidData mutableCuboid = null;
			for (MutationBlockSetBlock update : entry.getValue())
			{
				AbsoluteLocation location = update.getAbsoluteLocation();
				// We expect only one update per location - if this fails, we need to update this algorithm (although the current plan is just to make a single update parameterized).
				boolean didAdd = existingUpdates.add(location);
				Assert.assertTrue(didAdd);
				
				MutableBlockProxy proxy = new MutableBlockProxy(location, readOnly);
				update.applyState(proxy);
				if (proxy.didChange())
				{
					if (null == mutableCuboid)
					{
						mutableCuboid = CuboidData.mutableClone(readOnly);
					}
					proxy.writeBack(mutableCuboid);
				}
			}
			if (null != mutableCuboid)
			{
				out_updatedCuboids.put(address, mutableCuboid);
				out_updatedMaps.put(address, HeightMapHelpers.buildHeightMap(mutableCuboid));
			}
		}
	}

	private void _notifyCuboidChanges(Map<AbsoluteLocation, BlockProxy> previousProjectedChanges
			, Map<CuboidAddress, IReadOnlyCuboidData> staleShadowWorld
			, Map<CuboidAddress, List<MutationBlockSetBlock>> authoritativeChangesByCuboid
			, Map<CuboidAddress, List<AbsoluteLocation>> locallyChangedBlocksByCuboid
			, Map<CuboidColumnAddress, ColumnHeightMap> knownHeightMaps
	)
	{
		Map<CuboidAddress, IReadOnlyCuboidData> cuboidsToReport = new HashMap<>();
		Map<CuboidAddress, Set<BlockAddress>> changedBlocks = new HashMap<>();
		Set<Aspect<?, ?>> changedAspects = new HashSet<>();
		
		// We want to add all changes which can in from the server.
		for (Map.Entry<CuboidAddress, List<MutationBlockSetBlock>> changed : authoritativeChangesByCuboid.entrySet())
		{
			CuboidAddress address = changed.getKey();
			Set<BlockAddress> set = changedBlocks.get(address);
			IReadOnlyCuboidData activeVersion = _projectedWorld.get(address);
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
					BlockProxy previousReport = _projectedBlockChanges.get(location);
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
						Assert.assertTrue(!mismatches.isEmpty());
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
			IReadOnlyCuboidData activeVersion = _projectedWorld.get(address);
			IReadOnlyCuboidData shadowVersion = _shadowWorld.get(address);
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
						BlockProxy previousReport = _projectedBlockChanges.get(location);
						if (null == previousReport)
						{
							// We will claim that we are comparing against the shadow version so we don't report non-changes.
							previousReport = new BlockProxy(block, shadowVersion);
						}
						
						if (!previousReport.doAspectsMatch(projected))
						{
							_projectedBlockChanges.put(location, projected);
							
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
			IReadOnlyCuboidData activeVersion = _projectedWorld.get(cuboidAddress);
			if (null != activeVersion)
			{
				IReadOnlyCuboidData shadowVersion = _shadowWorld.get(cuboidAddress);
				BlockAddress block = blockLocation.getBlockAddress();
				BlockProxy projected = new BlockProxy(block, activeVersion);
				BlockProxy shadow = new BlockProxy(block, shadowVersion);
				
				if (!projected.doAspectsMatch(shadow))
				{
					// This still differs so it still counts as a change.
					_projectedBlockChanges.put(blockLocation, projected);
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
		}
		
		// Generate any of the missing columns.
		Set<CuboidColumnAddress> missingColumns = cuboidsToReport.keySet().stream()
				.map((CuboidAddress address) -> address.getColumn())
				.filter((CuboidColumnAddress column) -> !knownHeightMaps.containsKey(column))
				.collect(Collectors.toUnmodifiableSet())
		;
		Map<CuboidColumnAddress, ColumnHeightMap> addedHeightMaps = _buildProjectedColumnMaps(missingColumns);
		Map<CuboidColumnAddress, ColumnHeightMap> allHeightMaps = new HashMap<>();
		allHeightMaps.putAll(knownHeightMaps);
		allHeightMaps.putAll(addedHeightMaps);
		
		for (Map.Entry<CuboidAddress, IReadOnlyCuboidData> elt : cuboidsToReport.entrySet())
		{
			CuboidAddress address = elt.getKey();
			IReadOnlyCuboidData data = elt.getValue();
			Set<BlockAddress> blocksChanged = changedBlocks.get(address);
			_listener.cuboidDidChange(data
					, allHeightMaps.get(address.getColumn())
					, blocksChanged
					, changedAspects
			);
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
			_listener.otherEntityDidChange(_shadowCrowd.get(entityId));
		}
	}

	// Note that we populate modifiedCuboids with cuboids changed by this change but only the local entity could change (others are all read-only).
	private _SpeculativeWrapper _forwardApplySpeculative(Map<CuboidAddress, List<AbsoluteLocation>> modifiedBlocksByCuboid, IMutationEntity<IMutablePlayerEntity> change, long commitNumber, long currentTickTimeMillis)
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
				, millisecondsBeforeChange
				, currentTickTimeMillis
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
					, _serverMillisPerTick
					, currentTickTimeMillis
			);
			
			// Run these changes and mutations, collecting the resultant output from them.
			_applyFollowUpBlockMutations(innerContext, modifiedBlocksByCuboid, exportedMutations);
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
				? new _SpeculativeWrapper(commitNumber, change, followUpTicks, currentTickTimeMillis)
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

	private Map<CuboidAddress, List<MutationBlockSetBlock>> _createUpdateMap(List<MutationBlockSetBlock> updates, Set<CuboidAddress> loadedCuboids)
	{
		Map<CuboidAddress, List<MutationBlockSetBlock>> updatesByCuboid = new HashMap<>();
		for (MutationBlockSetBlock update : updates)
		{
			CuboidAddress address = update.getAbsoluteLocation().getCuboidAddress();
			// If the server sent us an update, we MUST have it loaded.
			Assert.assertTrue(loadedCuboids.contains(address));
			
			List<MutationBlockSetBlock> queue = updatesByCuboid.get(address);
			if (null == queue)
			{
				queue = new LinkedList<>();
				updatesByCuboid.put(address, queue);
			}
			queue.add(update);
		}
		return updatesByCuboid;
	}

	private void _applyFollowUp(Map<CuboidAddress, List<AbsoluteLocation>> modifiedBlocksByCuboid, _SpeculativeConsequences followUp)
	{
		long gameTick = 0L;
		CommonMutationSink innerNewMutationSink = new CommonMutationSink();
		CommonChangeSink innerNewChangeSink = new CommonChangeSink();
		// The follow-up doesn't worry about current time since it is being synthetically run in a "future tick".
		long currentTickTimeMillis = 0L;
		TickProcessingContext innerContext = _createContext(gameTick
				, innerNewMutationSink
				, innerNewChangeSink
				, _serverMillisPerTick
				, currentTickTimeMillis
		);
		
		// We ignore the results of these.
		_applyFollowUpBlockMutations(innerContext, modifiedBlocksByCuboid, followUp.exportedMutations);
		_applyFollowUpEntityMutations(innerContext, followUp.exportedChanges);
	}

	private void _applyFollowUpBlockMutations(TickProcessingContext context, Map<CuboidAddress, List<AbsoluteLocation>> modifiedBlocksByCuboid, List<IMutationBlock> blockMutations)
	{
		Map<CuboidAddress, List<ScheduledMutation>> innerMutations = _createMutationMap(blockMutations, _projectedWorld.keySet());
		// We ignore the block updates in the speculative projection (although this could be used in more precisely notifying the listener).
		Map<CuboidAddress, List<AbsoluteLocation>> modifiedBlocksByCuboidAddress = Map.of();
		Map<CuboidAddress, List<AbsoluteLocation>> potentialLightChangesByCuboid = Map.of();
		Map<CuboidAddress, List<AbsoluteLocation>> potentialLogicChangesByCuboid = Map.of();
		Set<CuboidAddress> cuboidsLoadedThisTick = Set.of();
		WorldProcessor.ProcessedFragment innerFragment = WorldProcessor.processWorldFragmentParallel(_singleThreadElement
				, _projectedWorld
				, _projectedHeightMap
				, context
				, innerMutations
				, modifiedBlocksByCuboidAddress
				, potentialLightChangesByCuboid
				, potentialLogicChangesByCuboid
				, cuboidsLoadedThisTick
		);
		_projectedWorld.putAll(innerFragment.stateFragment());
		_projectedHeightMap.putAll(innerFragment.heightFragment());
		for (Map.Entry<CuboidAddress, List<BlockChangeDescription>> elt : innerFragment.blockChangesByCuboid().entrySet())
		{
			CuboidAddress key = elt.getKey();
			List<BlockChangeDescription> value = elt.getValue();
			
			// This must have something in it if it was returned.
			Assert.assertTrue(!value.isEmpty());
			
			List<AbsoluteLocation> list = modifiedBlocksByCuboid.get(key);
			if (null == list)
			{
				list = new ArrayList<>();
				modifiedBlocksByCuboid.put(key, list);
			}
			list.addAll(value.stream().map(
					(BlockChangeDescription description) -> description.serializedForm().getAbsoluteLocation()
			).toList());
		}
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
			, long millisPerTick
			, long currentTickTimeMillis 
	)
	{
		LazyLocationCache<BlockProxy> cachingLoader = new LazyLocationCache<>(this.projectionBlockLoader);
		TickProcessingContext context = new TickProcessingContext(gameTick
				, cachingLoader
				, (Integer entityId) -> (_localEntityId == entityId)
					? MinimalEntity.fromEntity(_thisShadowEntity)
					: MinimalEntity.fromPartialEntity(_shadowCrowd.get(entityId))
				, null
				, newMutationSink
				, newChangeSink
				// We never spawn creatures on the client so no ID assigner.
				, null
				// We need a random number generator for a few cases (like attack) but the server will send us the authoritative result.
				, (int bound) -> 0
				// TODO:  Replace this with a real event handler.
				, (EventRecord event) -> {}
				// By default, we run in hostile mode.
				, new WorldConfig()
				, millisPerTick
				, currentTickTimeMillis
		);
		return context;
	}

	private Map<CuboidColumnAddress, ColumnHeightMap> _buildProjectedColumnMaps(Set<CuboidColumnAddress> columnsToGenerate)
	{
		Map<CuboidAddress, CuboidHeightMap> mapsToCoalesce = _projectedHeightMap.entrySet().stream()
				.filter((Map.Entry<CuboidAddress, CuboidHeightMap> entry) -> columnsToGenerate.contains(entry.getKey().getColumn()))
				.collect(Collectors.toMap((Map.Entry<CuboidAddress, CuboidHeightMap> entry) -> entry.getKey(), (Map.Entry<CuboidAddress, CuboidHeightMap> entry) -> entry.getValue()))
		;
		Map<CuboidColumnAddress, ColumnHeightMap> columnHeightMaps = HeightMapHelpers.buildColumnMaps(mapsToCoalesce);
		return columnHeightMaps;
	}


	public static interface IProjectionListener
	{
		/**
		 * Called when a new cuboid is loaded (may have been previously unloaded but not currently loaded).
		 * 
		 * @param cuboid The read-only cuboid data.
		 * @param heightMap The height map for this cuboid's column.
		 */
		void cuboidDidLoad(IReadOnlyCuboidData cuboid, ColumnHeightMap heightMap);
		/**
		 * Called when a new cuboid is replaced due to changes (must have been previously loaded).
		 * 
		 * @param cuboid The read-only cuboid data.
		 * @param heightMap The height map for this cuboid's column.
		 * @param changedBlocks The set of blocks which have some kind of change.
		 * @param changedAspects The set of aspects changed by any of the changedBlocks.
		 */
		void cuboidDidChange(IReadOnlyCuboidData cuboid
				, ColumnHeightMap heightMap
				, Set<BlockAddress> changedBlocks
				, Set<Aspect<?, ?>> changedAspects
		);
		/**
		 * Called when a new cuboid should be unloaded as the server is no longer telling the client about it.
		 * 
		 * @param address The address of the cuboid.
		 */
		void cuboidDidUnload(CuboidAddress address);
		
		/**
		 * Called when the client's entity has loaded for the first time.
		 * Only called once per instance.
		 * 
		 * @param authoritativeEntity The entity state from the server.
		 */
		void thisEntityDidLoad(Entity authoritativeEntity);
		/**
		 * Called when the client's entity has changed (either due to server-originating changes or local changes).
		 * Called very frequently.
		 * 
		 * @param authoritativeEntity The entity state from the server.
		 * @param projectedEntity The client's local state (local changes applied to server data).
		 */
		void thisEntityDidChange(Entity authoritativeEntity, Entity projectedEntity);
		
		/**
		 * Called when another entity is loaded for the first time.
		 * 
		 * @param entity The server's entity data.
		 */
		void otherEntityDidLoad(PartialEntity entity);
		/**
		 * Called when a previously-loaded entity's state changes.
		 * 
		 * @param entity The server's entity data.
		 */
		void otherEntityDidChange(PartialEntity entity);
		/**
		 * Called when another entity should be unloaded as the server is no longer sending us updates.
		 * 
		 * @param id The ID of the entity to unload.
		 */
		void otherEntityDidUnload(int id);
		
		/**
		 * Called when a game tick from the server has been fully processed.
		 * 
		 * @param gameTick The tick number (this is monotonic).
		 */
		void tickDidComplete(long gameTick);
	}

	private static record _SpeculativeWrapper(long commitLevel
			, IMutationEntity<IMutablePlayerEntity> change
			, List<_SpeculativeConsequences> followUpTicks
			, long currentTickTimeMillis
	) {}

	private static record _SpeculativeConsequences(List<IMutationEntity<IMutablePlayerEntity>> exportedChanges, List<IMutationBlock> exportedMutations)
	{
		public void absorb(_SpeculativeConsequences followUp)
		{
			this.exportedChanges.addAll(followUp.exportedChanges);
			this.exportedMutations.addAll(followUp.exportedMutations);
		}
	}

	private static record _UpdateTuple(Entity updatedShadowEntity
			, Map<Integer, PartialEntity> entitiesChangedInTick
			, Map<CuboidAddress, IReadOnlyCuboidData> stateFragment
			, Map<CuboidAddress, CuboidHeightMap> heightFragment
	) {}
}
