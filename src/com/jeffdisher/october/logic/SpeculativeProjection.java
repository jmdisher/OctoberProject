package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.function.Consumer;
import java.util.function.Function;

import com.jeffdisher.october.changes.IEntityChange;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.mutations.IMutation;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Either;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Instances of this class are used by clients to manage their local interpretation of the world.  It has their loaded
 * cuboids and whatever mutations they have made, which haven't yet been committed by the server.
 * When the server commits the mutations from a given game tick, it sends any which it accepted and applied to the
 * clients, along with the last mutation number it applied, from that user.  The user can then reverse any of its local
 * mutations, apply the mutations from the server, remove any of its local mutations which the mutation number implies
 * have been applied (or dropped), then re-apply its remaining mutations.
 * This means that any mutations it had which failed to be applied will have been "undone" in its local projection and
 * any new mutations which are inconsistent with this new baseline state (due to dependencies on these rejected
 * mutations or collisions with changes made concurrently by other clients) can be rejected locally.
 * A consequence of this design is that, despite the server-side cuboids being generally immutable and evolved into new
 * instances concurrently, the client-side cuboids are highly mutable and applied on a single thread.
 * An interesting consequence of this local invalidation is that even if a client sends a mutation, then locally rejects
 * it as a conflict, the server may still apply it as a result of races from other clients, so this one will end up
 * applying it, either way, even though it previously "disowned" it.  This doesn't cause a problem.
 * A simple way to think of how this works is much like an "undo stack" where applying an "undo" mutation generates a
 * "redo" mutation.  The only difference is that the "redo" stack is then pruned and re-applied to recreate the state
 * and "undo" stack, after applying changes from the server.
 */
public class SpeculativeProjection
{
	private final IProjectionListener _listener;
	private final Map<CuboidAddress, CuboidData> _loadedCuboids;
	private final Map<Integer, MutableEntity> _entitiesById;
	private final Stack<MutationWrapper> _reverseMutations;
	private long _nextLocalCommitNumber;
	private boolean _shouldTryMerge;

	public SpeculativeProjection(IProjectionListener listener)
	{
		Assert.assertTrue(null != listener);
		_listener = listener;
		_loadedCuboids = new HashMap<>();
		_entitiesById = new HashMap<>();
		_reverseMutations = new Stack<>();
		_nextLocalCommitNumber = 1L;
		_shouldTryMerge = false;
	}

	/**
	 * Called when a new cuboid has been fully-loaded (that is, all of its aspects are loaded and it is internally
	 * consistent).
	 * 
	 * @param address The cuboid address.
	 * @param cuboid The initial state of the cuboid.
	 */
	public void loadedCuboid(CuboidAddress address, CuboidData cuboid)
	{
		CuboidData old = _loadedCuboids.put(address, cuboid);
		// We should never replace in this path.
		Assert.assertTrue(null == old);
		_listener.cuboidDidLoad(address, cuboid);
	}

	/**
	 * Called when a new entity has been created in the world.
	 * 
	 * @param entity The entity.
	 */
	public void loadedEntity(Entity entity)
	{
		// We only ever operate on mutable data so get a mutatle version of the entity.
		MutableEntity old = _entitiesById.put(entity.id(), new MutableEntity(entity));
		// We should never replace in this path.
		Assert.assertTrue(null == old);
		_listener.entityDidLoad(entity);
	}

	/**
	 * Called with all the mutations the server committed within a given tick and has described what it committed from
	 * this client.
	 * This path applies the "reverse-apply-forward" logic from the description.
	 * The set of unloaded cuboids is included here so that any local mutations referencing them can be cleanly purged
	 * after reverse.
	 * 
	 * @param cuboidsToUnload The set of cuboid addresses to unload before applying these mutations.
	 * @param updates The list of either changes or mutations to apply, authoritative, in-order.
	 * @param lastLocalCommitIncluded The last commit number from this client which is represented in this list
	 * (although some of the local mutations in that interval may have been dropped).
	 * @return The number of reverse mutations remaining in the speculative projection after applying.
	 */
	public int applyCommittedMutations(Set<CuboidAddress> cuboidsToUnload, List<Either<IMutation, IEntityChange>> updates, long lastLocalCommitIncluded)
	{
		Function<AbsoluteLocation, BlockProxy> oldWorldLoader = _buildOldWorldLoader();
		TickProcessingContext nullProcessingContext = _createNullProcessingContext(oldWorldLoader);
		
		Map<CuboidAddress, CuboidData> blockCache = new HashMap<>();
		Stack<MutationWrapper> forwardSpeculative = new Stack<>();
		
		// 1)  Revert any of our local mutations (backward), recreating the forward mutations for any which aren't yet committed.
		// Note that we apply all reverse mutations but we need to be careful of the mutations created when reversing
		// those for replay, since we don't want to re-run those secondary mutations since they would be created again
		// when we change direction to apply forward.
		// In order to do this, we will only apply the first (or last, since this is reversed) mutation of a given
		// commit number.  This is why we have an additional look-ahead in this iteration:  To see if they are the same
		// commit.
		MutationWrapper reverseWrapper = _popReverseMutation();
		while (null != reverseWrapper)
		{
			// Note that all client-side changes start with IEntityChange so we can assume that any IMutation instances should not be regenerated.
			// Note that we will still consider the look-ahead since that will matter for IEntityChange, in the future (when we add support for IEntityChange instances to be created by either of these).
			MutationWrapper nextReverseWrapper = _popReverseMutation();
			long commitNumber = reverseWrapper.commitLevel;
			IEntityChange change = reverseWrapper.update.second;
			
			// If these are the same commit number, only run without generating reverse.
			boolean regenerateReverse = (null != change) && ((null == nextReverseWrapper) || (nextReverseWrapper.commitLevel != commitNumber));
			if (regenerateReverse)
			{
				// In the reverse case, we can only ever be using IEntityChange.
				MutableEntity entity = _entitiesById.get(change.getTargetId());
				// This must be present.
				Assert.assertTrue(null != entity);
				IEntityChange forward = change.applyChangeReversible(nullProcessingContext, entity);
				// We cannot fail to apply the mutation in reverse (a non-apply should be a NullMutation).
				Assert.assertTrue(null != forward);
				// If this is more recent than what we were told was committed, we need to re-apply it later.
				if (commitNumber > lastLocalCommitIncluded)
				{
					MutationWrapper forwardWrapper = new MutationWrapper(commitNumber, Either.second(forward));
					forwardSpeculative.push(forwardWrapper);
				}
			}
			else
			{
				// When reversing something we don't want to push back onto the stack, this can be either IMutation or IEntityChange.
				// In either case, we don't want to capture the new mutations since this is reverse.
				boolean didApply;
				if (null != change)
				{
					MutableEntity entity = _entitiesById.get(change.getTargetId());
					// This must be present.
					Assert.assertTrue(null != entity);
					didApply = change.applyChange(nullProcessingContext, entity);
				}
				else
				{
					IMutation mutation = reverseWrapper.update.first;
					MutableBlockProxy thisBlock = _loadMutableBlock(blockCache, mutation);
					didApply = mutation.applyMutation(nullProcessingContext, thisBlock);
				}
				// We can't fail to reverse this.
				Assert.assertTrue(didApply);
			}
			reverseWrapper = nextReverseWrapper;
		}
		// At this point, we have reverted the state to one which does NOT include our speculative data.
		
		// 2)  Unload any given cuboids.
		for (CuboidAddress address : cuboidsToUnload)
		{
			Assert.assertTrue(_loadedCuboids.containsKey(address));
			// Since we removed any mutations to this cuboid, above, we can just it them from our map.
			_listener.cuboidDidUnload(address, _loadedCuboids.remove(address));
			// Since we notified about unload, we DON'T want to notify about changes.
			blockCache.remove(address);
		}
		// Now that we have no speculative data, and nothing the server doesn't want us to have, write-back the block cache to the "old world" state for the next stage.
		Set<CuboidAddress> changedCuboidAddresses = new HashSet<>();
		_writeBackBlockCache(changedCuboidAddresses, blockCache);
		
		// 3)  Apply the incoming updates.
		for (Either<IMutation, IEntityChange> update : updates)
		{
			// Note that we will also ignore secondary mutations generated by applying these authoritative mutations as
			// the server will need to tell us how to apply them (since they schedule them later/differently, they will
			// conflict with other mutations in non-deterministic ways).
			IEntityChange change = update.second;
			boolean didApply;
			if (null != change)
			{
				// In the reverse case, we can only ever be using IEntityChange.
				MutableEntity entity = _entitiesById.get(change.getTargetId());
				// This must be present.
				Assert.assertTrue(null != entity);
				didApply = change.applyChange(nullProcessingContext, entity);
			}
			else
			{
				IMutation mutation = update.first;
				MutableBlockProxy thisBlock = _loadMutableBlock(blockCache, mutation);
				didApply = mutation.applyMutation(nullProcessingContext, thisBlock);
			}
			// If this fails to apply, we are somehow out of sync with the server, which is fatal and shouldn't be possible.
			Assert.assertTrue(didApply);
		}
		_writeBackBlockCache(changedCuboidAddresses, blockCache);
		
		// 4)  Walk the remaining (not yet committed) local mutations, applying them and dropping them if they reject on the new state.
		Set<Integer> modifiedEntityIds = new HashSet<>();
		while (!forwardSpeculative.isEmpty())
		{
			MutationWrapper forwardWrapper = forwardSpeculative.pop();
			long commitLevel = forwardWrapper.commitLevel;
			
			// These should only ever be changes.
			IEntityChange change = forwardWrapper.update.second;
			Assert.assertTrue(null != change);
			_forwardApplySpeculative(oldWorldLoader, blockCache, modifiedEntityIds, change, commitLevel);
		}
		_writeBackBlockCache(changedCuboidAddresses, blockCache);
		
		// We need to notify the listener of anything which changed in this call - we do this at the end to avoid redundant updates.
		_notifyChanges(changedCuboidAddresses, modifiedEntityIds);
		
		// We return the number of reverse mutations associated with the speculation (this is only useful for testing).
		return _reverseMutations.size();
	}

	/**
	 * Applies the given change to the local state as speculative and returns the local commit number associated.  Note
	 * that the returned number may be the same as that returned by the previous call if the implementation decided that
	 * they could be coalesced.  In this case, the caller should replace the change it has buffered to send to the
	 * server with this one.
	 * 
	 * @param change The entity change to apply.
	 * @return The local commit number for this change, 0L if it failed to applied and should be rejected.
	 */
	public long applyLocalChange(IEntityChange change)
	{
		long commitNumber = _nextLocalCommitNumber;
		_nextLocalCommitNumber += 1;
		Map<CuboidAddress, CuboidData> blockCache = new HashMap<>();
		Set<Integer> modifiedEntityIds = new HashSet<>();
		boolean didApply = _forwardApplySpeculative(_buildOldWorldLoader(), blockCache, modifiedEntityIds, change, commitNumber);
		if (didApply)
		{
			// Since this applied, see if it can be merged with the previous (we will reverse the commit number, if possible).
			if (_shouldTryMerge)
			{
				if (_reverseMutations.size() >= 2)
				{
					// We will pull top off to see if it can be fully replaced by the one under it.
					// Due to the logic in reversibility, this means that the change we were given here is all that is
					// needed as it will be equivalent to the reverse of under.
					MutationWrapper topWrap = _reverseMutations.pop();
					MutationWrapper underWrap = _reverseMutations.peek();
					IEntityChange top = topWrap.update.second;
					IEntityChange under = underWrap.update.second;
					boolean canReplace = false;
					if ((null != top) && (null != under) && (top.getTargetId() == under.getTargetId()))
					{
						long previousCommit = underWrap.commitLevel;
						Assert.assertTrue((previousCommit + 1) == commitNumber);
						// This is a little odd, since the mutations are reversed, but this will still do the right
						// thing if we merge in reverse order:  top would be applied BEFORE under so ask under.
						canReplace = under.canReplacePrevious(top);
						if (canReplace)
						{
							// The under can replace top don't re-push it.
							_nextLocalCommitNumber -= 1;
							commitNumber = previousCommit;
						}
					}
					if (!canReplace)
					{
						// We couldn't merge so restore the stack.
						_reverseMutations.push(topWrap);
					}
				}
			}
			// Whether we merged or not, we now have something to try merging with, on the next call.
			_shouldTryMerge = true;
			
			// Write-back the changes and notify that we updated.
			Set<CuboidAddress> changedCuboidAddresses = new HashSet<>();
			_writeBackBlockCache(changedCuboidAddresses, blockCache);
			_notifyChanges(changedCuboidAddresses, modifiedEntityIds);
		}
		else
		{
			// Revert this commit.
			_nextLocalCommitNumber -= 1;
			commitNumber = 0L;
		}
		return commitNumber;
	}

	/**
	 * We normally collect local entity movement updates so that we don't report all the between-frames, but we need to
	 * stop doing that if we see a non-movement change or if the last collected move change has been sent to server.
	 * This function is called to notify the internal logic when that change has been sent.
	 */
	public void sealLastLocalChange()
	{
		// We just disable the test to merge on the next call (it will be re-enabled when something new is added but
		// this will be sufficient to keep what was previously added from ever being merged).
		_shouldTryMerge = false;
	}


	private void _notifyChanges(Set<CuboidAddress> changedCuboidAddresses, Set<Integer> entityIds)
	{
		for (CuboidAddress address : changedCuboidAddresses)
		{
			_listener.cuboidDidChange(address, _loadedCuboids.get(address));
		}
		for (Integer id : entityIds)
		{
			_listener.entityDidChange(_entitiesById.get(id).freeze());
		}
	}

	private Function<AbsoluteLocation, BlockProxy> _buildOldWorldLoader()
	{
		Function<AbsoluteLocation, BlockProxy> oldWorldLoader = (AbsoluteLocation blockLocation) -> {
			CuboidData cuboid = _loadedCuboids.get(blockLocation.getCuboidAddress());
			return (null != cuboid)
					? new BlockProxy(blockLocation.getBlockAddress(), cuboid)
					: null
			;
		};
		return oldWorldLoader;
	}

	private boolean _forwardApplySpeculative(Function<AbsoluteLocation, BlockProxy> oldWorldLoader, Map<CuboidAddress, CuboidData> blockCache, Set<Integer> modifiedEntityIds, IEntityChange change, long commitLevel)
	{
		List<Either<IMutation, IEntityChange>> extraLocalUpdates = new ArrayList<>();
		Consumer<IMutation> localImmediateNewMutationsSink = (IMutation newMutation) -> {
			// When applying speculative mutations, we want to enqueue any mutations for immediate execution, just as a queue, since the speculative handler has no tick scheduler.
			extraLocalUpdates.add(Either.first(newMutation));
		};
		Consumer<IEntityChange> localImmediateNewChangeSink = (IEntityChange newChange) -> {
			// Same logic as for mutations.
			extraLocalUpdates.add(Either.second(newChange));
		};
		TickProcessingContext immediateProcessingContext = new TickProcessingContext(0, oldWorldLoader, localImmediateNewMutationsSink, localImmediateNewChangeSink);
		
		// In the reverse case, we can only ever be using IEntityChange.
		MutableEntity entity = _entitiesById.get(change.getTargetId());
		// This must be present.
		Assert.assertTrue(null != entity);
		IEntityChange reverseChange = change.applyChangeReversible(immediateProcessingContext, entity);
		if (null != reverseChange)
		{
			modifiedEntityIds.add(entity.original.id());
			_reverseMutations.push(new MutationWrapper(commitLevel, Either.second(reverseChange)));
			// Run everything else here, too.  WARNING:  We build this queue while processing it so it could process a LOT.
			while (!extraLocalUpdates.isEmpty())
			{
				Either<IMutation, IEntityChange> extra = extraLocalUpdates.remove(0);
				IMutation extraMutation = extra.first;
				if (null != extraMutation)
				{
					MutableBlockProxy extraBlock = _loadMutableBlock(blockCache, extraMutation);
					IMutation reverse2 = extraMutation.applyMutationReversible(immediateProcessingContext, extraBlock);
					// We can fail to apply one of these later mutations and that is an acceptable type of failure.
					if (null != reverse2)
					{
						// We commit these at the same level.
						_reverseMutations.push(new MutationWrapper(commitLevel, Either.first(reverse2)));
					}
				}
				else
				{
					IEntityChange extraChange = extra.second;
					// In the reverse case, we can only ever be using IEntityChange.
					MutableEntity extraEntity = _entitiesById.get(extraChange.getTargetId());
					// This must be present.
					Assert.assertTrue(null != extraEntity);
					IEntityChange reverse2 = extraChange.applyChangeReversible(immediateProcessingContext, extraEntity);
					// We can fail to apply one of these later changes and that is an acceptable type of failure.
					if (null != reverse2)
					{
						modifiedEntityIds.add(extraEntity.original.id());
						// We commit these at the same level.
						_reverseMutations.push(new MutationWrapper(commitLevel, Either.second(reverse2)));
					}
				}
			}
		}
		// Return true if we applied the initial change.
		return (null != reverseChange);
	}

	private MutableBlockProxy _loadMutableBlock(Map<CuboidAddress, CuboidData> blockCache, IMutation mutation)
	{
		AbsoluteLocation blockLocation = mutation.getAbsoluteLocation();
		CuboidAddress cuboidLocation = blockLocation.getCuboidAddress();
		CuboidData thisCuboid = null;
		if (blockCache.containsKey(cuboidLocation))
		{
			// Just get the cached value.
			thisCuboid = blockCache.get(cuboidLocation);
		}
		else
		{
			// Look-up the value and cache it.
			// Note that we create a clone to do this since other mutations should still see the old data when reading other cuboids.
			thisCuboid = CuboidData.mutableClone(_loadedCuboids.get(cuboidLocation));
			blockCache.put(cuboidLocation, thisCuboid);
		}
		// We should never be applying a mutation to a block we don't have loaded.
		Assert.assertTrue(null != thisCuboid);
		return new MutableBlockProxy(blockLocation.getBlockAddress(), thisCuboid);
	}

	private void _writeBackBlockCache(Set<CuboidAddress> changedCuboidAddresses, Map<CuboidAddress, CuboidData> blockCache)
	{
		for (Map.Entry<CuboidAddress, CuboidData> elt : blockCache.entrySet())
		{
			CuboidAddress address = elt.getKey();
			changedCuboidAddresses.add(address);
			_loadedCuboids.put(address, elt.getValue());
		}
		blockCache.clear();
	}

	private MutationWrapper _popReverseMutation()
	{
		return !_reverseMutations.isEmpty()
				? _reverseMutations.pop()
				: null
		;
	}

	private TickProcessingContext _createNullProcessingContext(Function<AbsoluteLocation, BlockProxy> oldWorldLoader)
	{
		Consumer<IMutation> nullNewMutationsSink = (IMutation mutation) -> {
			// While reversing a mutation, we just drop any new ones it enqueues since it can't be requesting things
			// which aren't already in the stack.
			// Additionally, we never apply secondary mutations generated by the authoritative commits as the server
			// needs to schedule and resolve them so it will send them to us in a future call.
		};
		Consumer<IEntityChange> nullNewChangeSink = (IEntityChange change) -> {
			// Like above, do nothing.
		};
		// Local projection context uses tick 0.
		return new TickProcessingContext(0, oldWorldLoader, nullNewMutationsSink, nullNewChangeSink);
	}


	public static interface IProjectionListener
	{
		void cuboidDidLoad(CuboidAddress address, CuboidData cuboid);
		void cuboidDidChange(CuboidAddress address, CuboidData cuboid);
		void cuboidDidUnload(CuboidAddress address, CuboidData cuboid);
		
		void entityDidLoad(Entity entity);
		void entityDidChange(Entity entity);
	}

	// Only one of mutation or change will be set.
	// Note that only IEntityChange will have an associated commitLevel since they are the only things which can happen in response to local user actions.
	// Client-side:  All IMutation, and some IEntityChange, are created in response to a user action (IEntityChange).
	private static record MutationWrapper(long commitLevel, Either<IMutation, IEntityChange> update) {}
}
