package com.jeffdisher.october.utils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;


public class TestLayeredInputStream
{
	@Test
	public void noLayers() throws Throwable
	{
		String topString = "This is the top\n";
		byte[] top = topString.getBytes();
		InputStream stream = new LayeredInputStream(new ByteArrayInputStream(top), new InputStream[0]);
		Assert.assertEquals(topString.length(), stream.available());
		Assert.assertEquals(topString.getBytes()[0], (byte)stream.read());
		Assert.assertEquals(topString.length() - 1, stream.available());
		byte[] remainder = stream.readAllBytes();
		Assert.assertEquals(topString.length() - 1, remainder.length);
		Assert.assertEquals(0, stream.available());
		Assert.assertEquals(-1, stream.read());
		Assert.assertEquals("his is the top\n", new String(remainder, StandardCharsets.UTF_8));
	}

	@Test
	public void multiLayer() throws Throwable
	{
		String topString = "This is the top\n";
		byte[] top = topString.getBytes();
		String nextString = "This is the next\n";
		byte[] next = nextString.getBytes();
		String bottomString = "This is bottom\n";
		byte[] bottom = bottomString.getBytes();
		InputStream stream = new LayeredInputStream(new ByteArrayInputStream(top), new InputStream[] {
			new ByteArrayInputStream(next),
			new ByteArrayInputStream(bottom),
		});
		int totalLength = topString.length() + nextString.length() + bottomString.length();
		Assert.assertEquals(totalLength, stream.available());
		Assert.assertEquals(topString.getBytes()[0], (byte)stream.read());
		Assert.assertEquals(totalLength - 1, stream.available());
		byte[] remainder = stream.readAllBytes();
		Assert.assertEquals(totalLength - 1, remainder.length);
		Assert.assertEquals(0, stream.available());
		Assert.assertEquals(-1, stream.read());
		Assert.assertEquals("his is the top\nThis is the next\nThis is bottom\n", new String(remainder, StandardCharsets.UTF_8));
	}
}
