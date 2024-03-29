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
		TabListReader.IParseCallbacks callbacks = new _FailingCallbacks();
		_readFile(callbacks, "\n");
	}

	@Test(expected=TabListException.class)
	public void malformed() throws Throwable
	{
		TabListReader.IParseCallbacks callbacks = new _FailingCallbacks();
		_readFile(callbacks, " this should fail \n");
	}

	@Test
	public void nameList() throws Throwable
	{
		List<String> names = new ArrayList<>();
		TabListReader.IParseCallbacks callbacks = new _FailingCallbacks() {
			@Override
			public void startNewRecord(String name, String[] parameters)
			{
				names.add(name);
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
		TabListReader.IParseCallbacks callbacks = new _FailingCallbacks() {
			@Override
			public void startNewRecord(String name, String[] parameters)
			{
				names.add(name);
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
		TabListReader.IParseCallbacks callbacks = new _FailingCallbacks() {
			@Override
			public void startNewRecord(String name, String[] parameters)
			{
				names.add(name);
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
		TabListReader.IParseCallbacks callbacks = new _FailingCallbacks() {
			@Override
			public void startNewRecord(String name, String[] parameters)
			{
				Assert.assertFalse(names.containsKey(name));
				Assert.assertEquals(1, parameters.length);
				names.put(name, parameters[0]);
			}
			@Override
			public void endRecord()
			{
			}
		};
		_readFile(callbacks, "one\tnumber one\ntwo\tanother number\n\nthree\tfinal number\n");
		Assert.assertEquals(3, names.size());
		Assert.assertEquals("number one", names.get("one"));
		Assert.assertEquals("another number", names.get("two"));
		Assert.assertEquals("final number", names.get("three"));
	}

	@Test
	public void subRecord() throws Throwable
	{
		Map<String, Map<String, List<String>>> fields = new HashMap<>();
		TabListReader.IParseCallbacks callbacks = new _FailingCallbacks() {
			private String _id;
			@Override
			public void startNewRecord(String name, String[] parameters)
			{
				Assert.assertNull(_id);
				Assert.assertFalse(fields.containsKey(_id));
				_id = name;
				fields.put(_id, new HashMap<>());
			}
			@Override
			public void endRecord()
			{
				Assert.assertNotNull(_id);
				_id = null;
			}
			@Override
			public void processSubRecord(String name, String[] parameters) throws TabListException
			{
				Assert.assertNotNull(_id);
				Assert.assertTrue(fields.containsKey(_id));
				fields.get(_id).put(name, List.of(parameters));
			}
		};
		_readFile(callbacks, "# Testing multi-level record\n"
				+ "one\n"
				+ "\tfieldOne\tvalue1\tvalue2\n"
				+ "\tfieldTwo\tvalue3\tvalue4\n"
				+ "two\n"
				+ "\tfieldThree\tvalue5\n"
		);
		Assert.assertEquals(2, fields.size());
		Assert.assertEquals(2, fields.get("one").size());
		Assert.assertEquals(2, fields.get("one").get("fieldOne").size());
		Assert.assertEquals(2, fields.get("one").get("fieldTwo").size());
		Assert.assertEquals(1, fields.get("two").get("fieldThree").size());
	}


	private static void _readFile(TabListReader.IParseCallbacks callbacks, String content) throws IOException, TabListException
	{
		TabListReader.readEntireFile(callbacks, new ByteArrayInputStream(content.getBytes()));
	}


	private static class _FailingCallbacks implements TabListReader.IParseCallbacks
	{
		// We just use this as a default implementation so we can fail on unexpected calls.
		@Override
		public void startNewRecord(String name, String[] parameters)
		{
			Assert.fail();
		}
		@Override
		public void endRecord()
		{
			Assert.fail();
		}
		@Override
		public void processSubRecord(String name, String[] parameters) throws TabListException
		{
			Assert.fail();
		}
	};
}
