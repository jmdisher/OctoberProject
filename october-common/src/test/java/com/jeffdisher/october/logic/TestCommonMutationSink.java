package com.jeffdisher.october.logic;

import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.mutations.MutationBlockIncrementalBreak;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CuboidAddress;


public class TestCommonMutationSink
{
	@Test
	public void basicCalls()
	{
		AbsoluteLocation location = new AbsoluteLocation(5, 40, -6);
		AbsoluteLocation external = new AbsoluteLocation(100, 40, -6);
		CuboidAddress address = location.getCuboidAddress();
		Set<CuboidAddress> loadedCuboids = Set.of(address);
		CommonMutationSink sink = new CommonMutationSink(loadedCuboids);
		
		MutationBlockIncrementalBreak next = new MutationBlockIncrementalBreak(location, (short)100, 0);
		MutationBlockIncrementalBreak future = new MutationBlockIncrementalBreak(location, (short)200, 0);
		
		sink.future(future, 1000L);
		sink.next(next);
		sink.future(new MutationBlockIncrementalBreak(external, (short)100, 0), 1000L);
		sink.next(new MutationBlockIncrementalBreak(external, (short)100, 0));
		
		List<ScheduledMutation> mutations = sink.takeExportedMutations();
		Assert.assertEquals(2, mutations.size());
		Assert.assertEquals(1000L, mutations.get(0).millisUntilReady());
		Assert.assertEquals(future, mutations.get(0).mutation());
		Assert.assertEquals(0L, mutations.get(1).millisUntilReady());
		Assert.assertEquals(next, mutations.get(1).mutation());
	}
}
