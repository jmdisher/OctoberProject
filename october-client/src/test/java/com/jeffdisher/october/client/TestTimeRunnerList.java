package com.jeffdisher.october.client;

import org.junit.Assert;
import org.junit.Test;


public class TestTimeRunnerList
{
	@Test
	public void runNothing() throws Throwable
	{
		TimeRunnerList list = new TimeRunnerList();
		long currentTimeMillis = 1000L;
		int count = list.runFullQueue(currentTimeMillis);
		Assert.assertEquals(0, count);
	}

	@Test
	public void runTwo() throws Throwable
	{
		TimeRunnerList list = new TimeRunnerList();
		
		long[] out = new long[2];
		list.enqueue((long time) -> {
			out[0] = time;
		});
		list.enqueue((long time) -> {
			out[1] = time + 1L;
		});
		long currentTimeMillis = 1000L;
		int count = list.runFullQueue(currentTimeMillis);
		Assert.assertEquals(2, count);
		Assert.assertEquals(currentTimeMillis, out[0]);
		Assert.assertEquals(currentTimeMillis + 1L, out[1]);
	}

	@Test
	public void runOneThenAnother() throws Throwable
	{
		TimeRunnerList list = new TimeRunnerList();
		
		long[] out = new long[2];
		list.enqueue((long time) -> {
			out[0] = time;
		});
		long currentTimeMillis = 1000L;
		int count = list.runFullQueue(currentTimeMillis);
		Assert.assertEquals(1, count);
		
		list.enqueue((long time) -> {
			out[1] = time;
		});
		currentTimeMillis = 2000L;
		count = list.runFullQueue(currentTimeMillis);
		Assert.assertEquals(1, count);
		
		currentTimeMillis = 3000L;
		count = list.runFullQueue(currentTimeMillis);
		Assert.assertEquals(0, count);
		
		Assert.assertEquals(1000L, out[0]);
		Assert.assertEquals(2000L, out[1]);
	}
}
