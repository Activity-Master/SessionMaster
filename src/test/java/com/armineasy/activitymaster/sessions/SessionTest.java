package com.armineasy.activitymaster.sessions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SessionTest
{
	@Test
	public void testTheOutput()
	{
		Session s = new Session();
		s.addValue("Value1", "New Object");
		s.addValue("Value2", new Object[]{1,2,"3"});
		System.out.println(s.toString());

	}
}