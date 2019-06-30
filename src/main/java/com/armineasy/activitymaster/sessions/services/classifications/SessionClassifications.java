package com.armineasy.activitymaster.sessions.services.classifications;

import com.armineasy.activitymaster.activitymaster.services.classifications.involvedparty.IInvolvedPartyClassification;
import com.armineasy.activitymaster.activitymaster.services.enumtypes.IClassificationDataConceptValue;
import com.armineasy.activitymaster.activitymaster.services.enumtypes.IClassificationValue;

import static com.armineasy.activitymaster.activitymaster.services.concepts.EnterpriseClassificationDataConcepts.*;

public enum SessionClassifications
		implements IClassificationValue<SessionClassifications>
				           , IInvolvedPartyClassification<SessionClassifications>
{
	SessionClassifications("The last time the Involved Party was logged in", InvolvedPartyXResourceItem),

	SessionObject("The object representing the session data information", InvolvedPartyXResourceItem),
	;

	private String description;
	private IClassificationDataConceptValue<?> concept;

	SessionClassifications(String description, IClassificationDataConceptValue<?> concept)
	{
		this.description = description;
		this.concept = concept;
	}

	SessionClassifications(String description)
	{
		this.description = description;
	}

	@Override
	public String classificationDescription()
	{
		return this.description;
	}

	@Override
	public IClassificationDataConceptValue<?> concept()
	{
		return concept;
	}
}
