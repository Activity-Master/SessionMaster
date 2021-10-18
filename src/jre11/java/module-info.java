import com.guicedee.activitymaster.fsdm.client.services.systems.IActivityMasterSystem;
import com.guicedee.activitymaster.sessions.SessionMasterSystem;
import com.guicedee.activitymaster.sessions.implementations.*;
import com.guicedee.guicedservlets.services.IOnCallScopeExit;

module com.guicedee.activitymaster.sessions {
	
	requires static lombok;
	
	exports com.guicedee.activitymaster.sessions.services;
	exports com.guicedee.activitymaster.sessions.services.classifications;
	exports com.guicedee.activitymaster.sessions.services.dto;
	
	requires org.json;
	requires com.guicedee.guicedpersistence;
	requires com.guicedee.guicedservlets;

	requires com.guicedee.activitymaster.profiles;
	
	requires cache.annotations.ri.common;
	requires cache.annotations.ri.guice;
	requires cache.api;
	
	requires java.sql;
	
	requires com.jwebmp.core;

	requires com.guicedee.activitymaster.fsdm;
	requires com.google.guice;
	
	requires com.guicedee.guicedinjection;
	requires com.google.common;

	requires com.fasterxml.jackson.annotation;
	requires com.fasterxml.jackson.databind;
	requires com.fasterxml.jackson.core;
	requires net.sf.uadetector.core;
	requires com.guicedee.activitymaster.fsdm.client;
	requires com.entityassist;
	
	provides IActivityMasterSystem with SessionMasterSystem;
	provides com.guicedee.guicedinjection.interfaces.IGuiceModule with SessionMasterBinder;
	provides com.guicedee.guicedinjection.interfaces.IGuiceScanModuleInclusions with SessionsModuleInclusion;
	provides IOnCallScopeExit with CallScopeOnExitSessionPersist;
	
	exports com.guicedee.activitymaster.sessions;
	opens com.guicedee.activitymaster.sessions to com.google.guice, com.fasterxml.jackson.databind;
	opens com.guicedee.activitymaster.sessions.services.dto to com.google.guice, com.fasterxml.jackson.databind;
	
	exports com.guicedee.activitymaster.sessions.implementations;
	opens com.guicedee.activitymaster.sessions.implementations to com.google.guice;
	exports com.guicedee.activitymaster.sessions.implementations.providers;
	opens com.guicedee.activitymaster.sessions.implementations.providers to com.google.guice;
	
	exports com.guicedee.activitymaster.sessions.implementations.updates to com.google.guice;
	opens com.guicedee.activitymaster.sessions.implementations.updates to com.google.guice;
	
	opens com.guicedee.activitymaster.sessions.services to com.google.guice;
	
}
