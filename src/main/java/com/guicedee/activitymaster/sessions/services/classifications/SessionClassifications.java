package com.guicedee.activitymaster.sessions.services.classifications;

import com.guicedee.activitymaster.core.services.classifications.involvedparty.IInvolvedPartyClassification;
import com.guicedee.activitymaster.core.services.classifications.resourceitems.IResourceItemClassification;
import com.guicedee.activitymaster.core.services.enumtypes.IClassificationDataConceptValue;
import com.guicedee.activitymaster.core.services.enumtypes.IClassificationValue;

import static com.guicedee.activitymaster.core.services.concepts.EnterpriseClassificationDataConcepts.*;

public enum SessionClassifications
		implements IClassificationValue<SessionClassifications>
				           , IInvolvedPartyClassification<SessionClassifications>,
				           IResourceItemClassification<SessionClassifications>
{
	SessionLastUpdateTime("The last time the Involved Party was logged in", InvolvedPartyXResourceItem),
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
