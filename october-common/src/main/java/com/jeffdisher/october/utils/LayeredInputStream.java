package com.jeffdisher.october.utils;

import java.io.IOException;
import java.io.InputStream;


/**
 * This InputStream implementation allows additional data to be appended to the end of an incoming InputStream,
 * transparently.  This is so that mods can inject data into the Environment's tablist files.
 */
public class LayeredInputStream extends InputStream
{
	private final InputStream[] _layers;
	private int _currentIndex;

	public LayeredInputStream(InputStream head, InputStream[] tails)
	{
		_layers = new InputStream[1 + tails.length];
		_layers[0] = head;
		System.arraycopy(tails, 0, _layers, 1, tails.length);
		_currentIndex = 0;
	}

	@Override
	public int available() throws IOException
	{
		// Those buffered readers seem to need this implementation.
		int ready = 0;
		int index = _currentIndex;
		while (index < _layers.length)
		{
			ready += _layers[index].available();
			index += 1;
		}
		return ready;
	}

	@Override
	public int read() throws IOException
	{
		// Default to EOF.
		int val = -1;
		while ((-1 == val) && (_currentIndex < _layers.length))
		{
			val = _layers[_currentIndex].read();
			if (-1 == val)
			{
				_currentIndex += 1;
			}
		}
		return val;
	}

	@Override
	public void close() throws IOException
	{
		for (InputStream stream : _layers)
		{
			stream.close();
		}
		_currentIndex = _layers.length;
	}
}
