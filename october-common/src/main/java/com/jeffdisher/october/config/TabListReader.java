package com.jeffdisher.october.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import com.jeffdisher.october.utils.Assert;


/**
 * A utility class for reading the "tab list" data files used to describe the aspects and registries.
 * This data format is designed to capture the bare minimum, while being human-editable and trivial to parse:
 * -each line is treated as a record where each field is delimited by tabs (since tabs don't appear in the middle of
 * textual statements, meaning no "quote" state machine is required)
 */
public class TabListReader
{
	/**
	 * Parses a full tab list data file from the given stream, sending all parse events to the given callbacks object.
	 * Closes the stream on completion.
	 * 
	 * @param callbacks Will receive the parser events as the parse runs.
	 * @param stream The stream containing the data (will be closed when done).
	 * @throws IOException There was a problem reading the stream.
	 * @throws TabListException The data wasn't well-formed.
	 */
	public static void readEntireFile(IParseCallbacks callbacks, InputStream stream) throws IOException, TabListException
	{
		BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
		TabListReader parser = new TabListReader(callbacks);
		while (reader.ready())
		{
			String line = reader.readLine();
			parser.handleLine(line);
		}
		reader.close();
	}


	private final IParseCallbacks _callbacks;

	private TabListReader(IParseCallbacks callbacks)
	{
		// We only want these instances to exist internally.
		_callbacks = callbacks;
	}

	public void handleLine(String line) throws TabListException
	{
		// Skip empty lines.
		if (line.length() > 0)
		{
			String[] parts = line.split("\t");
			// There must be at least one part to this since it is non-empty.
			Assert.assertTrue(parts.length > 0);
			// Note that the first part is not allowed to begin/end in whitespace (just for sanity reasons).
			String identifier = parts[0];
			if (identifier.trim().length() < identifier.length())
			{
				throw new TabListException("Identifier edges cannot be whitespace");
			}
			_callbacks.startNewRecord(identifier);
			for (int i = 1; i < parts.length; ++i)
			{
				_callbacks.handleParameter(parts[i]);
			}
			_callbacks.endRecord();
		}
	}


	public interface IParseCallbacks
	{
		void startNewRecord(String name);
		void handleParameter(String value);
		void endRecord();
	}

	public static class TabListException extends Exception
	{
		private static final long serialVersionUID = 1L;
		public TabListException(String string)
		{
			super(string);
		}
	}
}
