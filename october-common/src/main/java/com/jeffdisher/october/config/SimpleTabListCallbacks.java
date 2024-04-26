package com.jeffdisher.october.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.october.utils.Assert;


/**
 * An implementation of the callbacks interface to handle the relatively common case of a mostly key-value list but with
 * some optional or required sub-records.  The only common restriction on use-case is that all of the parameter lists
 * for new records or sub-records are expected to be precisely 1 element long.
 * 
 * @param <K> The key type.
 * @param <V> The top-level record value type.
 */
public class SimpleTabListCallbacks<K, V> implements TabListReader.IParseCallbacks
{
	// Overall internal state.
	private final IValueTransformer<K> _keyTransformer;
	private final IValueTransformer<V> _valueTransformer;
	private final Map<String, SubRecordCapture<K, ?>> _requiredSubRecords;
	private final Map<String, SubRecordCapture<K, ?>> _optionalSubRecords;

	// Ephemeral internal state.
	private K _currentRecordKey;
	private Set<String> _pendingSubRecords;

	// Public data which can be directly read from the outside once the file is fully read.
	public final List<K> keyOrder;
	public final Map<K, V> topLevel;

	public SimpleTabListCallbacks(IValueTransformer<K> keyTransformer, IValueTransformer<V> valueTransformer)
	{
		_keyTransformer = keyTransformer;
		_valueTransformer = valueTransformer;
		_requiredSubRecords = new HashMap<>();
		_optionalSubRecords = new HashMap<>();
		this.keyOrder = new ArrayList<>();
		this.topLevel = new HashMap<>();
	}

	/**
	 * Install a handler for a sub-record of a given name.  Note that the returned capture object will contain the
	 * corresponding data after the processing is complete.
	 * 
	 * @param <W> The type of values to process.
	 * @param name The sub-record name.
	 * @param transformer The value transformer for this sub-record's values.
	 * @param required True if this sub-record MUST exist in the data.
	 * @return An object which will capture the parsed values.
	 */
	public <W> SubRecordCapture<K, W> captureSubRecord(String name, IValueTransformer<W> transformer, boolean required)
	{
		Assert.assertTrue(!_requiredSubRecords.containsKey(name));
		Assert.assertTrue(!_optionalSubRecords.containsKey(name));
		
		SubRecordCapture<K, W> capture = new SubRecordCapture<>(transformer);
		if (required)
		{
			_requiredSubRecords.put(name, capture);
		}
		else
		{
			_optionalSubRecords.put(name, capture);
		}
		return capture;
	}

	@Override
	public void startNewRecord(String name, String[] parameters) throws TabListReader.TabListException
	{
		K key = _keyTransformer.transform(name);
		if (1 != parameters.length)
		{
			throw new TabListReader.TabListException("Exactly 1 parameter expected");
		}
		V value = _valueTransformer.transform(parameters[0]);
		if (this.topLevel.containsKey(key))
		{
			throw new TabListReader.TabListException("Duplicate data element: \"" + name + "\"");
		}
		this.keyOrder.add(key);
		this.topLevel.put(key, value);
		
		// Prepare internal state.
		_currentRecordKey = key;
		_pendingSubRecords = new HashSet<>(_requiredSubRecords.keySet());
	}

	@Override
	public void endRecord() throws TabListReader.TabListException
	{
		// Make sure we have everything required.
		if (!_pendingSubRecords.isEmpty())
		{
			String list = "";
			for (String elt : _pendingSubRecords)
			{
				list += " " + elt;
			}
			throw new TabListReader.TabListException("Missing sub-records:" + list);
		}
	}

	@Override
	public void processSubRecord(String name, String[] parameters) throws TabListReader.TabListException
	{
		SubRecordCapture<K, ?> capture = _requiredSubRecords.get(name);
		if (null == capture)
		{
			capture = _optionalSubRecords.get(name);
		}
		if (null == capture)
		{
			throw new TabListReader.TabListException("Unexpected sub-record \"" + name + "\" in: " + _currentRecordKey);
		}
		if (1 != parameters.length)
		{
			throw new TabListReader.TabListException("Exactly 1 parameter expected for \"" + name + "\" in: " + _currentRecordKey);
		}
		capture.transformAndStore(_currentRecordKey, parameters[0]);
		_pendingSubRecords.remove(name);
	}


	/**
	 * The object which will capture the per-record data associated with a specific sub-record.  The recordData can be
	 * read directly once the file has been fully processed.
	 */
	public static class SubRecordCapture<K, W>
	{
		private final IValueTransformer<W> _tranformer;
		public final Map<K, W> recordData;
		
		public SubRecordCapture(IValueTransformer<W> transformer)
		{
			_tranformer = transformer;
			this.recordData = new HashMap<>();
		}
		
		public void transformAndStore(K currentRecordKey, String string) throws TabListReader.TabListException
		{
			W value = _tranformer.transform(string);
			this.recordData.put(currentRecordKey, value);
		}
	}
}
