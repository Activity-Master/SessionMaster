package com.guicedee.activitymaster.sessions.implementations;

import com.guicedee.client.services.config.IGuiceScanModuleInclusions;
import jakarta.validation.constraints.NotNull;

import java.util.HashSet;
import java.util.Set;

public class SessionsModuleInclusion implements IGuiceScanModuleInclusions<SessionsModuleInclusion>
{
	@Override
	public @NotNull Set<String> includeModules()
	{
		Set<String> set = new HashSet<>();
		set.add("com.guicedee.activitymaster.sessions");
		return set;
	}
}
