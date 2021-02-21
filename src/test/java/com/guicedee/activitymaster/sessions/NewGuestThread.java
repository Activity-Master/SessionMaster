package com.guicedee.activitymaster.sessions;

import com.google.inject.Inject;
import com.guicedee.activitymaster.core.services.dto.IEnterprise;
import com.guicedee.activitymaster.core.services.dto.ISystems;
import com.guicedee.activitymaster.core.threads.TransactionalIdentifiedThread;
import com.guicedee.activitymaster.profiles.ProfileSystem;
import com.guicedee.activitymaster.profiles.dto.ProfileServiceDTO;
import com.guicedee.activitymaster.sessions.services.ISessionLoginService;
import com.guicedee.activitymaster.sessions.services.dto.UserLoginDTO;
import lombok.extern.java.Log;

import java.util.UUID;

import static com.guicedee.guicedinjection.GuiceContext.*;

@Log
public class NewGuestThread
		extends TransactionalIdentifiedThread
{
	@Inject
	private ISessionLoginService<?> ps;
	@Inject
	private IEnterprise<?> enterprise;
	
	@Override
	public void perform()
	{
		UserLoginDTO<?> newGuest = new UserLoginDTO<>().setWebClientUUID(UUID.randomUUID());
		ISystems<?> profileSystem = get(ProfileSystem.class)
				                            .getSystem(enterprise);
		UUID profileSystemUUID = get(ProfileSystem.class)
				                         .getSystemToken(enterprise);
		ProfileServiceDTO dto = ps.loginVisitor(newGuest, profileSystem, profileSystemUUID);
		log.info("Created Guest : " + newGuest.getWebClientUUID());
	}
}
