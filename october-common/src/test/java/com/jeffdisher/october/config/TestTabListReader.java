package com.jeffdisher.october.config;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.config.TabListReader.TabListException;


public class TestTabListReader
{
	@Test
	public void empty() throws Throwable
	{
		TabListReader.IParseCallbacks callbacks = new TabListReader.IParseCallbacks() {
			@Override
			public void startNewRecord(String name)
			{
				Assert.fail();
			}
			@Override
			public void handleParameter(String value)
			{
				Assert.fail();
			}
			@Override
			public void endRecord()
			{
				Assert.fail();
			}
		};
		_readFile(callbacks, "\n");
	}

	@Test(expected=TabListException.class)
	public void malformed() throws Throwable
	{
		TabListReader.IParseCallbacks callbacks = new TabListReader.IParseCallbacks() {
			@Override
			public void startNewRecord(String name)
			{
				Assert.fail();
			}
			@Override
			public void handleParameter(String value)
			{
				Assert.fail();
			}
			@Override
			public void endRecord()
			{
				Assert.fail();
			}
		};
		_readFile(callbacks, " this should fail \n");
	}

	@Test
	public void nameList() throws Throwable
	{
		List<String> names = new ArrayList<>();
		TabListReader.IParseCallbacks callbacks = new TabListReader.IParseCallbacks() {
			@Override
			public void startNewRecord(String name)
			{
				names.add(name);
			}
			@Override
			public void handleParameter(String value)
			{
				Assert.fail();
			}
			@Override
			public void endRecord()
			{
			}
		};
		_readFile(callbacks, "one\ntwo\n\nthree\n");
		Assert.assertEquals(3, names.size());
		Assert.assertEquals("one", names.get(0));
		Assert.assertEquals("two", names.get(1));
		Assert.assertEquals("three", names.get(2));
	}

	@Test
	public void nameListWindows() throws Throwable
	{
		List<String> names = new ArrayList<>();
		TabListReader.IParseCallbacks callbacks = new TabListReader.IParseCallbacks() {
			@Override
			public void startNewRecord(String name)
			{
				names.add(name);
			}
			@Override
			public void handleParameter(String value)
			{
				Assert.fail();
			}
			@Override
			public void endRecord()
			{
			}
		};
		_readFile(callbacks, "one\r\ntwo\r\n\r\nthree\r\n");
		Assert.assertEquals(3, names.size());
		Assert.assertEquals("one", names.get(0));
		Assert.assertEquals("two", names.get(1));
		Assert.assertEquals("three", names.get(2));
	}

	@Test
	public void nameListMissingEnd() throws Throwable
	{
		List<String> names = new ArrayList<>();
		TabListReader.IParseCallbacks callbacks = new TabListReader.IParseCallbacks() {
			@Override
			public void startNewRecord(String name)
			{
				names.add(name);
			}
			@Override
			public void handleParameter(String value)
			{
				Assert.fail();
			}
			@Override
			public void endRecord()
			{
			}
		};
		_readFile(callbacks, "one\ntwo\n\nthree");
		Assert.assertEquals(3, names.size());
		Assert.assertEquals("one", names.get(0));
		Assert.assertEquals("two", names.get(1));
		Assert.assertEquals("three", names.get(2));
	}

	@Test
	public void idsAndNames() throws Throwable
	{
		Map<String, String> names = new HashMap<>();
		TabListReader.IParseCallbacks callbacks = new TabListReader.IParseCallbacks() {
			private String _id;
			@Override
			public void startNewRecord(String name)
			{
				Assert.assertNull(_id);
				_id = name;
			}
			@Override
			public void handleParameter(String value)
			{
				Assert.assertNotNull(_id);
				Assert.assertFalse(names.containsKey(_id));
				names.put(_id, value);
			}
			@Override
			public void endRecord()
			{
				Assert.assertNotNull(_id);
				_id = null;
			}
		};
		_readFile(callbacks, "one\tnumber one\ntwo\tanother number\n\nthree\tfinal number\n");
		Assert.assertEquals(3, names.size());
		Assert.assertEquals("number one", names.get("one"));
		Assert.assertEquals("another number", names.get("two"));
		Assert.assertEquals("final number", names.get("three"));
	}


	private static void _readFile(TabListReader.IParseCallbacks callbacks, String content) throws IOException, TabListException
	{
		TabListReader.readEntireFile(callbacks, new ByteArrayInputStream(content.getBytes()));
	}
}
