package com.jeffdisher.october.types;

import java.util.function.Function;

import org.junit.Assert;
import org.junit.Test;


public class TestLazyLocationCache
{
	@Test
	public void basicUse() throws Throwable
	{
		int[] counter = new int[1];
		Function<AbsoluteLocation, String> elementFactory = (AbsoluteLocation location) -> {
			counter[0] += 1;
			return (location.equals(new AbsoluteLocation(0, 1, 2)))
				? "FOO"
				: null
			;
		};
		LazyLocationCache<String> cache = new LazyLocationCache<>(elementFactory);
		
		String found = cache.apply(new AbsoluteLocation(0, 1, -2));
		Assert.assertNull(found);
		found = cache.apply(new AbsoluteLocation(0, 1, -2));
		Assert.assertNull(found);
		found = cache.apply(new AbsoluteLocation(0, 1, 2));
		Assert.assertEquals("FOO", found);
		Assert.assertEquals(2, counter[0]);
		found = cache.apply(new AbsoluteLocation(0, 1, 2));
		Assert.assertEquals("FOO", found);
		Assert.assertEquals(2, counter[0]);
	}

	@Test
	public void internalAccess() throws Throwable
	{
		int[] counter = new int[1];
		Function<AbsoluteLocation, String> elementFactory = (AbsoluteLocation location) -> {
			counter[0] += 1;
			return (location.equals(new AbsoluteLocation(0, 1, 2)))
				? "FOO"
				: null
			;
		};
		LazyLocationCache<String> cache = new LazyLocationCache<>(elementFactory);
		
		// Make a basic request.
		String found = cache.apply(new AbsoluteLocation(0, 1, 2));
		Assert.assertEquals("FOO", found);
		Assert.assertEquals(1, counter[0]);
		
		// Now, access internals and show that the cache is now not usable.
		Assert.assertEquals(1, cache.extractCache().size());
		
		try
		{
			cache.apply(new AbsoluteLocation(0, 1, 2));
			Assert.fail();
		}
		catch (AssertionError e)
		{
			// Expected.
		}
		try
		{
			cache.extractCache();
			Assert.fail();
		}
		catch (AssertionError e)
		{
			// Expected.
		}
	}

	@Test
	public void explicitAccess() throws Throwable
	{
		Function<AbsoluteLocation, String> elementFactory = (AbsoluteLocation location) -> {
			return null;
		};
		LazyLocationCache<String> cache = new LazyLocationCache<>(elementFactory);
		AbsoluteLocation hit = new AbsoluteLocation(0, 1, 2);
		AbsoluteLocation miss = new AbsoluteLocation(0, 1, -2);
		
		Assert.assertFalse(cache.contains(hit));
		Assert.assertFalse(cache.contains(miss));
		
		String found = cache.apply(miss);
		Assert.assertNull(found);
		Assert.assertTrue(cache.contains(miss));
		
		cache.add(hit, "FOUND");
		Assert.assertTrue(cache.contains(hit));
		found = cache.apply(hit);
		Assert.assertNotNull(found);
	}
}
