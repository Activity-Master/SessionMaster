module com.armineasy.activitymaster.sessions {

	exports com.armineasy.activitymaster.sessions.services;


	requires com.armineasy.activitymaster.profiles;

	requires lombok;
	requires org.mapstruct;
	requires net.sf.uadetector.core;
	requires org.json;
	requires com.jwebmp.guicedpersistence;
	requires com.jwebmp.guicedservlets;

	requires cache.annotations.ri.common;
	requires cache.annotations.ri.guice;
	requires cache.api;

	requires java.sql;

	requires com.armineasy.activitymaster.activitymaster;
	requires com.google.guice;

	requires com.jwebmp.guicedinjection;
	requires com.google.common;
	requires javax.servlet.api;
	requires com.fasterxml.jackson.annotation;
	requires com.fasterxml.jackson.databind;


	provides com.armineasy.activitymaster.activitymaster.services.IActivityMasterSystem with com.armineasy.activitymaster.sessions.SessionMasterSystem;
	provides com.jwebmp.guicedinjection.interfaces.IGuiceModule with com.armineasy.activitymaster.sessions.implementations.SessionMasterBinder;


}
