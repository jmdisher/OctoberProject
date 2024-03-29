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
			public void startNewRecord(String name)
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
			public void startNewRecord(String name)
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
			public void startNewRecord(String name)
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

	@Test
	public void subRecord() throws Throwable
	{
		Map<String, Map<String, List<String>>> fields = new HashMap<>();
		TabListReader.IParseCallbacks callbacks = new _FailingCallbacks() {
			private String _id;
			private String _field;
			private List<String> _subRecord;
			@Override
			public void startNewRecord(String name)
			{
				Assert.assertNull(_id);
				Assert.assertNull(_field);
				Assert.assertNull(_subRecord);
				Assert.assertFalse(fields.containsKey(_id));
				_id = name;
				fields.put(_id, new HashMap<>());
			}
			@Override
			public void handleParameter(String value)
			{
				// We will assume that this is only for our sub-record.
				Assert.assertNotNull(_id);
				Assert.assertNotNull(_field);
				Assert.assertNotNull(_subRecord);
				_subRecord.add(value);
			}
			@Override
			public void endRecord()
			{
				Assert.assertNotNull(_id);
				Assert.assertNull(_field);
				Assert.assertNull(_subRecord);
				_id = null;
			}
			@Override
			public void startSubRecord(String name) throws TabListException
			{
				Assert.assertNotNull(_id);
				Assert.assertNull(_field);
				Assert.assertNull(_subRecord);
				Assert.assertTrue(fields.containsKey(_id));
				_field = name;
				_subRecord = new ArrayList<>();
				fields.get(_id).put(_field, _subRecord);
			}
			@Override
			public void endSubRecord() throws TabListException
			{
				Assert.assertNotNull(_id);
				Assert.assertNotNull(_field);
				Assert.assertNotNull(_subRecord);
				_field = null;
				_subRecord = null;
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
		@Override
		public void startSubRecord(String name) throws TabListException
		{
			Assert.fail();
		}
		@Override
		public void endSubRecord() throws TabListException
		{
			Assert.fail();
		}
	};
}
