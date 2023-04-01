/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2011 ChemVantage LLC
*   
*    This program is free software: you can redistribute it and/or modify
*   it under the terms of the GNU General Public License as published by
*   the Free Software Foundation, either version 3 of the License, or
*   (at your option) any later version.
*
*   This program is distributed in the hope that it will be useful,
*   but WITHOUT ANY WARRANTY; without even the implied warranty of
*   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*   GNU General Public License for more details.
*
*   You should have received a copy of the GNU General Public License
*   along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.chemvantage;

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

@Entity
public class Assignment implements java.lang.Cloneable {
	@Id 	Long id;
	@Index	public String domain;
	@Index	String assignmentType = "Poll";
	@Index	String resourceLinkId;
	@Index 	Date created;
	@Index	public 	String lti_ags_lineitems_url;
	@Index	public	String lti_ags_lineitem_url;
	@Index	Date valid;
			String lti_nrps_context_memberships_url;
			String title = "Group Poll";
			boolean pollIsClosed=true;
			Date pollClosesAt = null;
			Integer questionNumber;
			List<Key<Question>> questionKeys = new ArrayList<Key<Question>>();
			List<Integer> timeAllowed = new ArrayList<Integer>();  // time for each question in seconds

	Assignment() {}

	Assignment(String platformDeploymentId,String resourceLinkId,String lti_nrps_context_memberships_url) {
		this.domain = platformDeploymentId;
		this.resourceLinkId = resourceLinkId;
		this.lti_nrps_context_memberships_url = lti_nrps_context_memberships_url;
		this.created = new Date();
	}

	Assignment(String assignmentType, String platform_deployment_id) {
		this.assignmentType = assignmentType;
		this.domain = platform_deployment_id;
		this.created = new Date();
		this.valid = new Date();
		this.pollIsClosed = true;
	}
	
	public List<Key<Question>> getQuestionKeys() {
		return questionKeys==null?new ArrayList<Key<Question>>():this.questionKeys;
	}

	void updateQuestions(HttpServletRequest request) {
		try {
			String[] questionIds = request.getParameterValues("QuestionId");
			this.questionKeys.clear();
			if (questionIds != null) for (String id : questionIds) this.questionKeys.add(Key.create(Question.class,Long.parseLong(id)));
			ofy().save().entity(this).now();	
		} catch (Exception e) {}
	}

	boolean equivalentTo(Assignment a) {
		// This method compares another Assignment to this one and ensures that all fields are either the same or both null
		// except for primitive (long) fields, which may not be null and must be equal (possibly 0L).
		if (a==null) return false;
		
		return	((this.id != null && this.id.equals(a.id)) 																								|| (a.id == null && this.id == null)) &&
				((this.domain != null && this.domain.equals(a.domain)) 																					|| (a.domain == null && this.domain == null)) &&
				((this.assignmentType != null && this.assignmentType.equals(a.assignmentType)) 															|| (a.assignmentType == null && this.assignmentType == null)) &&
				((this.resourceLinkId != null && this.resourceLinkId.equals(a.resourceLinkId)) 															|| (a.resourceLinkId == null && this.resourceLinkId == null)) &&
				((this.lti_ags_lineitems_url != null && this.lti_ags_lineitems_url.equals(a.lti_ags_lineitems_url)) 									|| (a.lti_ags_lineitems_url == null && this.lti_ags_lineitems_url == null)) &&
				((this.lti_ags_lineitem_url != null && this.lti_ags_lineitem_url.equals(a.lti_ags_lineitem_url)) 										|| (a.lti_ags_lineitem_url == null && this.lti_ags_lineitem_url == null)) &&
				((this.lti_nrps_context_memberships_url != null && this.lti_nrps_context_memberships_url.equals(a.lti_nrps_context_memberships_url)) 	|| (a.lti_nrps_context_memberships_url == null && this.lti_nrps_context_memberships_url == null)) &&
				((this.questionKeys != null && this.questionKeys.equals(a.questionKeys)) 																|| (a.questionKeys == null && this.questionKeys == null)) &&
				(this.valid != null && a.valid != null && this.valid.getTime()-a.valid.getTime()<2635200000L);	// less than 1 month since last validation		
	}
		
	protected Assignment clone() throws CloneNotSupportedException {
		return (Assignment) super.clone();
	}

}