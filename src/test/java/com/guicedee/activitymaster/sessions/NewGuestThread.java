package com.guicedee.activitymaster.sessions;

import com.google.inject.Inject;
import com.guicedee.activitymaster.client.services.annotations.ActivityMasterDB;
import com.guicedee.activitymaster.client.services.builders.warehouse.enterprise.IEnterprise;
import com.guicedee.activitymaster.client.services.builders.warehouse.systems.ISystems;
import com.guicedee.activitymaster.profiles.dto.ProfileServiceDTO;
import com.guicedee.activitymaster.sessions.services.ISessionLoginService;
import com.guicedee.activitymaster.sessions.services.dto.UserLoginDTO;
import com.guicedee.guicedpersistence.db.annotations.Transactional;
import lombok.extern.java.Log;

import java.util.UUID;

import static com.guicedee.guicedinjection.GuiceContext.*;

@Log
public class NewGuestThread
		extends Thread
{
	@Inject
	private ISessionLoginService<?> ps;
	@Inject
	private IEnterprise<?,?> enterprise;
	
	@Override
	@Transactional(entityManagerAnnotation = ActivityMasterDB.class)
	public void run()
	{
		UserLoginDTO<?> newGuest = new UserLoginDTO<>().setWebClientUUID(UUID.randomUUID());
		ISystems<?,?> profileSystem = get(SessionMasterSystem.class)
				                            .getSystem(enterprise);
		UUID profileSystemUUID = get(SessionMasterSystem.class)
				                         .getSystemToken(enterprise);
		ProfileServiceDTO<?> dto = ps.loginVisitor(newGuest, profileSystem, profileSystemUUID);
		log.info("Created Guest : " + newGuest.getWebClientUUID());
	}
}
