package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.List;

import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.mutations.IMutation;


/**
 * A snapshot of the state of a cuboid and its associated updates.  If there are pending mutations, this instance will
 * be included in the game tick and will be replaced by a new copy.
 */
public class CuboidState
{
	public final IReadOnlyCuboidData data;
	private List<IMutation> _pendingMutations;

	public CuboidState(IReadOnlyCuboidData data)
	{
		this.data = data;
	}

	// These can be called by anyone at any time, except when being processed.
	public synchronized void enqueueMutation(IMutation mutation)
	{
		if (null == _pendingMutations)
		{
			_pendingMutations = new ArrayList<>();
		}
		_pendingMutations.add(mutation);
	}

	// Called on one thread before update cycle.
	public List<IMutation> drainPendingMutations()
	{
		try
		{
			return _pendingMutations;
		}
		finally
		{
			_pendingMutations = null;
		}
	}
}
