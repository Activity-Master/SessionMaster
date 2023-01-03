package com.guicedee.activitymaster.sessions.services.classifications;

import com.guicedee.activitymaster.fsdm.client.types.classifications.EnterpriseClassificationDataConcepts;

import static com.guicedee.activitymaster.fsdm.client.types.classifications.EnterpriseClassificationDataConcepts.*;

public enum SessionClassifications
{
	SessionInformation("Describes information related to a session", Classification),
	
	SessionLastUpdateTime("The last time the Involved Party was logged in", InvolvedPartyXResourceItem),
	SessionObject("The object representing the session data information", InvolvedPartyXResourceItem),
	
	DeviceUsedBy("The device is used by ", InvolvedPartyXInvolvedParty),
	UserLoggedIn("The logged in Involved Party ", EventXInvolvedParty),
	UserLoggedOut("The Involved Party logged out", EventXInvolvedParty),
	UserSessionExpired("The Involved Party logged out by expiration", EventXInvolvedParty),
	SystemPerformed("The event was performed by the system Involved Party", EventXInvolvedParty),
	;

	private String description;
	private EnterpriseClassificationDataConcepts concept;

	SessionClassifications(String description, EnterpriseClassificationDataConcepts concept)
	{
		this.description = description;
		this.concept = concept;
	}

	SessionClassifications(String description)
	{
		this.description = description;
	}

	public String classificationDescription()
	{
		return this.description;
	}

	public EnterpriseClassificationDataConcepts concept()
	{
		return concept;
	}
}
