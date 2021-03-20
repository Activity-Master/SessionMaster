package com.guicedee.activitymaster.sessions.services.classifications;

import static com.guicedee.activitymaster.client.services.classifications.EnterpriseClassificationDataConcepts.*;

public enum SessionClassifications
{
	SessionLastUpdateTime("The last time the Involved Party was logged in", InvolvedPartyXResourceItem),
	SessionObject("The object representing the session data information", InvolvedPartyXResourceItem),
	;

	private String description;
	private com.guicedee.activitymaster.client.services.classifications.EnterpriseClassificationDataConcepts concept;

	SessionClassifications(String description, com.guicedee.activitymaster.client.services.classifications.EnterpriseClassificationDataConcepts concept)
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

	public com.guicedee.activitymaster.client.services.classifications.EnterpriseClassificationDataConcepts concept()
	{
		return concept;
	}
}
