package com.jeffdisher.october.aspects;

import java.util.ArrayList;
import java.util.List;


public class AspectRegistry
{
	private final List<Aspect<?>> _aspects = new ArrayList<>();

	public <T> Aspect<T> registerAspect(String name, Class<T> type)
	{
		Aspect<T> aspect = new Aspect<>(name, _aspects.size(), type);
		_aspects.add(aspect);
		return aspect;
	}

	public Aspect<?>[] finalList()
	{
		return _aspects.toArray((int size) -> new Aspect<?>[size]);
	}
}
