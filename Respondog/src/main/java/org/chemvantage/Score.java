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

import java.util.Date;
import java.util.List;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;
import com.googlecode.objectify.annotation.Parent;

@Entity
public class Score {    // this object represents a best score achieved by a user on a quiz or homework
	@Id	Long assignmentId;      // from the datastore.
	@Parent Key<User> owner;
	@Index	boolean lisReportComplete;
	int score;
	int maxPossibleScore;
	int numberOfAttempts;
	@Index	Date mostRecentAttempt;

	Score() {
		lisReportComplete=false;
		score = 0;
		maxPossibleScore = 0;
		numberOfAttempts = 0;
		mostRecentAttempt = null;
	}	

	public static Score getInstance(String userId,Assignment a) {
		String hashedId = Subject.hashId(userId);
		Score s = new Score();
		s.assignmentId = a.id;
		s.owner = Key.create(User.class,hashedId);

		List<PollTransaction> pollTransactions = ofy().load().type(PollTransaction.class).filter("assignmentId",a.id).filter("userId",hashedId).list();
		for (PollTransaction pt : pollTransactions) {
			s.numberOfAttempts++;  // number of pre-deadline quiz attempts
			s.score = (pt.score>s.score?pt.score:s.score);  // keep the best (max) score
			if (s.mostRecentAttempt==null || pt.downloaded.after(s.mostRecentAttempt)) {  // this transaction is the most recent so far
				s.mostRecentAttempt = pt.downloaded;
				s.maxPossibleScore = pt.possibleScore;
			}
		}

		if (s.score > s.maxPossibleScore) s.score = s.maxPossibleScore;  // max really is the limit for LTI reporting
		return s;
	}

	public String getScore() {
		return numberOfAttempts>0?Integer.toString(score):"-";
	}

	public double getPctScore() {
		if (maxPossibleScore>0) return Math.round(1000.*score/maxPossibleScore)/10.;
		else return 0.;
	}

	public boolean needsLisReporting() {
		try {
			Assignment a = ofy().load().type(Assignment.class).id(this.assignmentId).safe();
			if (!lisReportComplete && a.lti_ags_lineitem_url != null) return true;
		} catch (Exception e) {
			ofy().delete().entity(this);
		}
		return false;
	}

	boolean equals(Score s) {
		return s.score == this.score
				&& s.assignmentId == this.assignmentId
				&& s.owner.equals(this.owner)
				&& s.score == this.score
				&& s.maxPossibleScore == this.maxPossibleScore
				&& s.numberOfAttempts == this.numberOfAttempts
				&& s.mostRecentAttempt.equals(s.mostRecentAttempt);
	}
}