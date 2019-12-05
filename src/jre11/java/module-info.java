import com.guicedee.activitymaster.sessions.SessionMasterSystem;
import com.guicedee.activitymaster.sessions.implementations.SessionMasterBinder;

module com.guicedee.activitymaster.sessions {

	exports com.guicedee.activitymaster.sessions.services;
	exports com.guicedee.activitymaster.sessions.services.classifications;

	requires net.sf.uadetector.core;
	requires org.json;
	requires com.guicedee.guicedpersistence;
	requires com.guicedee.guicedservlets;

	requires cache.annotations.ri.common;
	requires cache.annotations.ri.guice;
	requires cache.api;

	requires java.sql;

	requires com.guicedee.activitymaster.core;
	requires com.google.guice;

	requires com.guicedee.guicedinjection;
	requires com.google.common;
	requires javax.servlet.api;
	requires com.fasterxml.jackson.annotation;
	requires com.fasterxml.jackson.databind;
	requires com.fasterxml.jackson.core;


	provides com.guicedee.activitymaster.core.services.IActivityMasterSystem with SessionMasterSystem;
	provides com.guicedee.guicedinjection.interfaces.IGuiceModule with SessionMasterBinder;

	opens com.guicedee.activitymaster.sessions to com.google.guice,com.fasterxml.jackson.databind;
	opens com.guicedee.activitymaster.sessions.implementations to com.google.guice;
	opens com.guicedee.activitymaster.sessions.services to com.google.guice;
}
