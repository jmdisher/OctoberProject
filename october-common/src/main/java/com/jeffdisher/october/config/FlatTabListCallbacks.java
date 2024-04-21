package com.jeffdisher.october.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * An implementation of the callbacks interface to handle the common case of a flat key-value list.
 * 
 * @param <K> The key type.
 * @param <V> The value type.
 */
public class FlatTabListCallbacks<K, V> implements TabListReader.IParseCallbacks
{
	private final IValueTransformer<K> _keyTransformer;
	private final IValueTransformer<V> _valueTransformer;
	public final List<K> keyOrder;
	public final Map<K, V> data;

	public FlatTabListCallbacks(IValueTransformer<K> keyTransformer, IValueTransformer<V> valueTransformer)
	{
		_keyTransformer = keyTransformer;
		_valueTransformer = valueTransformer;
		this.keyOrder = new ArrayList<>();
		this.data = new HashMap<>();
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
		if (data.containsKey(key))
		{
			throw new TabListReader.TabListException("Duplicate data element: \"" + name + "\"");
		}
		this.keyOrder.add(key);
		this.data.put(key, value);
	}

	@Override
	public void endRecord() throws TabListReader.TabListException
	{
	}

	@Override
	public void processSubRecord(String name, String[] parameters) throws TabListReader.TabListException
	{
		throw new TabListReader.TabListException("Sub-records are not expected");
	}
}
