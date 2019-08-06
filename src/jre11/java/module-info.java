module com.armineasy.activitymaster.sessions {

	exports com.armineasy.activitymaster.sessions.services;
	exports com.armineasy.activitymaster.sessions.services.classifications;

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
	requires com.fasterxml.jackson.core;


	provides com.armineasy.activitymaster.activitymaster.services.IActivityMasterSystem with com.armineasy.activitymaster.sessions.SessionMasterSystem;
	provides com.jwebmp.guicedinjection.interfaces.IGuiceModule with com.armineasy.activitymaster.sessions.implementations.SessionMasterBinder;

	opens com.armineasy.activitymaster.sessions to com.google.guice,com.fasterxml.jackson.databind;
	opens com.armineasy.activitymaster.sessions.implementations to com.google.guice;
	opens com.armineasy.activitymaster.sessions.services to com.google.guice;
}
