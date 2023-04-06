/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2023 ChemVantage LLC
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

import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withUrl;
import static com.googlecode.objectify.ObjectifyService.ofy;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.taskqueue.QueueFactory;
import com.googlecode.objectify.Key;

@WebServlet(urlPatterns={"/Poll","/poll"})
public class Poll extends HttpServlet {
	private static final long serialVersionUID = 137L;
	private static final String[] colors = {"#ffffcc","#ffccff","#ccffff","#ccccff","#ccffcc","#ffcccc"};
	
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		response.setContentType("text/html");
		response.setCharacterEncoding("UTF-8");
		PrintWriter out = response.getWriter();			
		try {
			User user = User.getUser(request.getParameter("sig"));
			if (user==null) throw new Exception("User is not authorized.");

			String userRequest = request.getParameter("UserRequest");
			if (userRequest==null) userRequest = "";
			
			Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).now();
			
			switch (userRequest) {
			case "Quit":
			case "EditPoll":
			case "Return to the edit page":
				out.println(Subject.header() + editPage(user,a,request) + Subject.footer);
				break;
			case "NewQuestion":
			case "Create a new question":
				out.println(Subject.header() + newQuestionForm(user,request) + Subject.footer);
				break;
			case "Add questions that I previously authored":
				out.println(Subject.header() + selectAuthoredQuestions(user,a,request) + Subject.footer);
				break;
			case "Done editing":
				out.println(Subject.header() + instructorPage(user,a,request) + Subject.footer);
				break;
			case "Preview":
				out.println(Subject.header() + previewQuestion(user,request) + Subject.footer);
				break;
			case "PrintPoll":
				out.println(Subject.header() + showPollQuestions(user,a) + Subject.footer);
				break;
			case "ViewResults":
				if (user.isInstructor()) out.println(Subject.header() + allResultsPage(user,a) + Subject.footer);
				break;
			case "ShowSummary":
				out.println(Subject.header("Your Group ResponDog Scores") + showSummary(user,request) + Subject.footer);
				break;
			case "Review":
				String forUserHashedId = request.getParameter("ForUserHashedId");
				String forUserName = request.getParameter("ForUserName");
				out.println(Subject.header("Poll Submission Review") + userResultsPage(user,forUserHashedId,forUserName,a));
				break;
			case "Synch":
				out.println(new Date().getTime());
				break;
			case "Edit":
				out.println(Subject.header() + editQuestion(user,request) + Subject.footer);
				break;
			case "Finish":
				out.println(Subject.header() + allResultsPage(user,a) + Subject.footer);
				break;
			default:
				if (user.isInstructor()) out.println(Subject.header() + instructorPage(user,a,request) + Subject.footer);
				else {
					if (a.pollIsClosed) out.println(Subject.header() + waitForPoll(user,a) + Subject.footer);
					else out.println(Subject.header() + showPollQuestions(user,a) + Subject.footer);
				}
			}
			} catch (Exception e) {
				out.println(Subject.header() + e.getMessage()==null?e.toString():e.getMessage());
				//response.sendRedirect("/Logout?sig=" + request.getParameter("sig"));
			}
		}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		response.setContentType("text/html");
		response.setCharacterEncoding("UTF-8");
		PrintWriter out = response.getWriter();			
		StringBuffer debug = new StringBuffer("Debug: ");
		
		try {
			User user = User.getUser(request.getParameter("sig"));
			if (user==null) throw new Exception();

			String userRequest = request.getParameter("UserRequest");
			if (userRequest==null) userRequest = "";
			
			Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).now();
			
			switch (userRequest) {
			case "SetNickname":
				PollTransaction pt = getPollTransaction(user,a);
				String nickname = request.getParameter("Nickname");
				if (nickname==null) nickname = "anonymous";
				pt.nickname = nickname;
				ofy().save().entity(pt).now();
				doGet(request,response);
				return;
			case "ShowResults":
				if (user.isInstructor()) {
					a.pollIsClosed = true;
					ofy().save().entity(a).now();
				}
				out.println(Subject.header() + resultPage(user,a) + Subject.footer);
				break;
			case "NextQuestion":
				if (!user.isInstructor()) break;
				a.questionNumber = 0;
				try {
					a.questionNumber = Integer.parseInt(request.getParameter("QuestionNumber"));
					int timeAllowed = a.timeAllowed.get(a.questionNumber); // seconds
					a.pollClosesAt = timeAllowed==0?null:new Date(new Date().getTime() + timeAllowed*1000L);
				} catch (Exception e) { 
					a.pollClosesAt = null; 
				}
				a.pollIsClosed = false;
				ofy().save().entity(a).now();
				out.println(Subject.header() + showPollQuestions(user,a) + Subject.footer);
				break;
			case "ClosePoll":
				if (!user.isInstructor()) break;
				a.pollIsClosed = true;
				a.questionNumber = 0;
				out.println(Subject.header() + instructorPage(user,a,request) + Subject.footer);
				break;
			case "Reset":
				if (!user.isInstructor()) break;
				try { a.pollClosesAt = new Date(new Date().getTime() + Long.parseLong(request.getParameter("TimeLimit"))*60000L); } catch (Exception e) { a.pollClosesAt = null; }
				a.pollIsClosed = false;
				ofy().save().entity(a).now();
				out.println(Subject.header() + instructorPage(user,a,request) + Subject.footer);
				break;
			case "Save New Question":
				if (!user.isInstructor()) break;
				long qid = createQuestion(user,request);
				if (qid > 0) {
					a.questionKeys.add(Key.create(Question.class,qid));
					a.timeAllowed.add(60);
					ofy().save().entity(a).now();
				}
				out.println(Subject.header() + editPage(user,a,request) + Subject.footer);
				break;
			case "Update Question":
				if (!user.isInstructor()) break;
				Question q = assembleQuestion(request);
				q.id = Long.parseLong(request.getParameter("QuestionId"));
				ofy().save().entity(q).now();
				out.println(Subject.header() + editPage(user,a,request) + Subject.footer);
				break;
			case "Delete Question":
				try {
					Key<Question> k = Key.create(Question.class,Long.parseLong(request.getParameter("QuestionId")));
					ofy().delete().key(k).now();
					if (a.questionKeys.remove(k)) ofy().save().entity(a).now();
				} catch (Exception e) {}
				out.println(Subject.header() + editPage(user,a,request) + Subject.footer);
				break;
			case "SubmitResponses":
				submitResponses(user,a,request);
				out.println(Subject.header() + resultPage(user,a) + Subject.footer);
				break;
			case "AddQuestions":
				if (!user.isInstructor()) break;
				addQuestions(user,a,request);
				out.println(Subject.header() + editPage(user,a,request) + Subject.footer);
				break;
			case "Remove":
				if (!user.isInstructor()) break;
				removeQuestion(user,a,request);
				out.println(Subject.header() + editPage(user,a,request) + Subject.footer);
				break;
			case "Save New Title":
				a.title = request.getParameter("AssignmentTitle");
				ofy().save().entity(a).now();
				out.println(Subject.header("ResponDog Presenter Page") + instructorPage(user,a,request) + Subject.footer);
			break;
			case "View the Poll Results":
				out.println(Subject.header() + resultPage(user,a) + Subject.footer);
				break;
			case "MvUp":
				moveUp(user,a,request);
				out.println(Subject.header() + editPage(user,a,request) + Subject.footer);
				break;
			case "MvDn":
				moveDn(user,a,request);
				out.println(Subject.header() + editPage(user,a,request) + Subject.footer);
				break;
			case "Set":
				setAllowedTime(user,a,request);
				out.println(Subject.header() + editPage(user,a,request) + Subject.footer);
				break;
			case "Preview":
			case "Quit":
			default:
				doGet(request,response);
				break;
			}
		} catch (Exception e) {
			out.println(Subject.header() + e.getMessage()==null?e.toString():e.getMessage() + "<br/>" + debug.toString());
		}
	}

	static String instructorPage(User user,Assignment a,HttpServletRequest request) throws Exception {
		StringBuffer buf = new StringBuffer(Subject.banner);
		
		buf.append("<h2>Presenter Page</h2>");
		if (!user.isInstructor()) return "You must be an instructor to view this page.";
		if (a.questionKeys.size()==0) return editPage(user,a,request);
		String guestCode = Long.toHexString(a.id);
		while (guestCode.lastIndexOf('0')==guestCode.length()-1) guestCode = guestCode.substring(0,guestCode.length()-1); // trims trailing zeros from hex string
		
		buf.append("This Poll assignment allows you to pose questions to your class and get real-time responses. Participants will need a laptop, "
				+ "tablet or smartphone. Participants who are logged into your LMS can get scores returned to the LMS grade book. If you have guests "
				+ "without an LMS login, please instruct them to go to https://respondog.com and enter "
				+ "<a href=# onclick=document.getElementById('guestcode').style.display='inline' >the guest code for this poll</a>.<br/>"
				+ "<span id=guestcode style='display:none' ><h2>The guest code for this poll is " + guestCode + "</h2></span>");
		
		buf.append("<br/><form method=post action=/Poll onsubmit=document.getElementById('sbmt1').disabled=true; >"
				+ "Title: <input type=text name=AssignmentTitle placeholder='" + a.title + "' /> "
				+ "<input type=hidden name=sig value=" + user.getTokenSignature() + " />"
				+ "<input type=hidden name=UserRequest value='Save New Title' />"
				+ "<input type=submit id=sbmt1 value='Save New Title' />"
				+ "</form><br/>");
		
		buf.append("You may <a href=/Poll?UserRequest=EditPoll&sig=" + user.getTokenSignature() + ">review and edit the questions for this poll</a>.<br/><br/>");
		
		int nSubmissions = ofy().load().type(PollTransaction.class).filter("assignmentId",a.id).filter("completed >",0).count();
		boolean supportsMembership = a.lti_nrps_context_memberships_url != null;
		
		if (nSubmissions > 0) {
			buf.append("There " + (nSubmissions==1?"is 1":"are " + nSubmissions) + " completed submission" + (nSubmissions>1?"s":"") + " for this poll. ");
			if (a.pollIsClosed) {
				buf.append("<a href=/Poll?UserRequest=ViewResults&sig=" + user.getTokenSignature() + ">View the poll results</a> "
						+ (supportsMembership?"or <a href='/Poll?UserRequest=ShowSummary&sig=" + user.getTokenSignature() + "'>review your participants' scores</a>":"") 
						+ "<br/><br/>");
			} else {
				buf.append("<a href=/Poll?sig=" + user.getTokenSignature() + ">Update</a><br/><br/>");
			}
		}
		PollTransaction pt = getPollTransaction(user,a);
		buf.append("<form method=post action=/Poll onsubmit=document.getElementById('sbmt2').disabled=true; >"
				+ "Choose a nickname: " 
				+ "<input type=text size=15 name=Nickname placeholder='" + Question.quot2html(pt.nickname) + "' /> "
				+ "<input type=hidden name=sig value='" + user.getTokenSignature() + "' />"
				+ "<input type=hidden name=UserRequest value=SetNickname />"
				+ "<input id=sbmt2 type=submit />"
				+ "</form><br/>");
		
		if (a.pollIsClosed) { 
			buf.append("<form method=post action=/Poll onsubmit=document.getElementById('sbmt3').disabled=true; >"
				+ "<input type=hidden name=sig value='" + user.getTokenSignature() + "' />"
				+ "<input type=hidden name=UserRequest value=NextQuestion />"
				+ "<input type=hidden name=QuestionNumber value=0 />"
				+ "<input type=submit id=sbmt3 value='Start the Poll' />"
				+ "</form><br/><br/>");
		} else {
			buf.append("<form method=post action=/Poll onsubmit=document.getElementById('sbmt4').disabled=true; >"
					+ "<b>The poll is open.</b>"
					+ "<input type=hidden name=sig value=" + user.getTokenSignature() + " />"
					+ "<input type=hidden name=UserRequest value=ClosePoll />"
					+ "<input id=sbmt4 type=submit value='Close the Poll' />"
					+ "</form><br/><br/>");
		}
		return buf.toString();
	}
	
	private static String waitForPoll(User user,Assignment a) throws Exception {
		StringBuffer buf = new StringBuffer();
		buf.append(Subject.banner);
		PollTransaction pt = getPollTransaction(user,a);
		
		buf.append("<h2>The poll is closed. Please wait.</h2>");
		
		if ("anonymous".equals(pt.nickname)) {
			buf.append("<form method=post action=/Poll onsubmit=document.getElementById('sbmt1').disabled=true; >"
					+ "Choose a nickname: " 
					+ "<input type=text size=15 name=Nickname placeholder='" + Question.quot2html(pt.nickname) + "' /> "
					+ "<input type=hidden name=sig value='" + user.getTokenSignature() + "' />"
					+ "<input type=hidden name=UserRequest value=SetNickname />"
					+ "<input id=sbmt1 type=submit />"
					+ "</form><br/>");
		}
		
		if (a.questionNumber==null || a.questionNumber==0) {  // Poll is just starting
			buf.append("Welcome, " + pt.nickname + ".<br/><br/>"
				+ "The presenter should inform you when the poll is open.<br/>"
				+ "At that time you can click the button below to view the first question.<br/><br/>");
		} else {
			buf.append("Thanks for your patience, " + pt.nickname + ".<br/><br/>"
				+ "The presenter should inform you when the poll is open.<br/>"
				+ "At that time you can click the button below to view the next question.<br/><br/>");
		}
		
		buf.append("<form method=get action=/Poll onsubmit=document.getElementById('sbmt2').disabled=true; />"
				+ "<input type=hidden name=sig value='" + user.getTokenSignature() + "' />"
				+ "<input id=sbmt2 type=submit value='View the Poll' /> "
				+ "</form><br/><br/>");
		return buf.toString();
	}
	
	static String showPollQuestions(User user, Assignment a) throws Exception {
		/*
		 * This method is reached by Learners or Instructors who launch the correct LTI Resource Link in the LMS, thereby binding
		 * the assignmentId to their User entity.
		 */
		try {
			if (a.pollIsClosed) return waitForPoll(user,a);  // nobody gets in without the instructor opening the poll first

			StringBuffer buf = new StringBuffer();

			// see if this user already has a submission for this assignment; else get a new PollTransaction
			PollTransaction pt = getPollTransaction(user,a);
			Map<Key<Question>,Question> pollQuestions = ofy().load().keys(a.questionKeys);

			Key<Question> k = a.questionKeys.get(a.questionNumber);
			
			if (user.isInstructor()) {
				buf.append("<form method=post action='/Poll' style='display:inline' onsubmit=document.getElementById('sbmt1').disabled=true; >"
						+ "<b>Please tell your audience that the poll is now open so they can view the poll questions.</b><br/>"
						+ "<span id='timer0' style='color: #EE0000'></span>" 
						+ (a.timeAllowed.get(a.questionKeys.indexOf(k))>0?"&nbsp;or&nbsp;":"")
						+ "<input type=hidden name=sig value='" + user.getTokenSignature() + "' />"
						+ "<input type=hidden name=UserRequest value='ShowResults' />"
						+ "<input id=sbmt1 type=submit value='Stop Now and Show Results' />"
						+ "</form><br/><br/>");
			} else buf.append("<span id='timer0' style='color: #EE0000'></span>");
			
			buf.append("<form id=pollForm method=post action='/Poll' onsubmit=document.getElementById('sbmt2').disabled=true; >"
					+ "<input type=hidden name=sig value='" + user.getTokenSignature() + "' />");

			Question q = pollQuestions.get(k); // this should nearly always work
			q.setParameters(a.id % Integer.MAX_VALUE);
			String studentResponse = pt.responses.get(k);
			if (studentResponse==null) studentResponse = "";
			
			buf.append("<div style='font-size:1.5em; background-color:" + colors[a.questionNumber%6] + "; padding:15px;'>" + q.print(null,studentResponse));
			
			int possibleScore = q.pointValue*1000;

			buf.append(timer(user));
			
			buf.append("<input type=hidden name=PossibleScore value='" + possibleScore + "' />");
			buf.append("<input type=hidden name=UserRequest value='SubmitResponses' />");
			buf.append("<input type=submit style='font-size:1em' id=sbmt2 />");
			buf.append("</form><br/><br/></div>");

			if (a.pollClosesAt != null && a.pollClosesAt.after(new Date())) buf.append("<script>startTimer(" + (a.pollClosesAt.getTime()-(user.isInstructor()?0L:3000L)) + ");</script>");

			return buf.toString();
		} catch (Exception e) {
			throw new Exception("Poll.showPollQuestions failed:" + e.getMessage()==null?e.toString():e.getMessage());
		}
	}

	private static PollTransaction submitResponses(User user,Assignment a,HttpServletRequest request) throws Exception {
		if (a.pollIsClosed) return null;
		
		PollTransaction pt = getPollTransaction(user,a);
		pt.completed = new Date();
		pt.nSubmissions++;
		
		Map<Key<Question>,Question> pollQuestions = ofy().load().keys(a.questionKeys);
		for (Key<Question> k : a.questionKeys) {
			Question q = pollQuestions.get(k);
			pt.possibleScores.put(k,1000*q.pointValue);
		}
		
		Key<Question> k = a.questionKeys.get(a.questionNumber);
		try {
			Question q = pollQuestions.get(k);
			String studentAnswer = orderResponses(request.getParameterValues(Long.toString(k.getId())));
			int score = 0;
			if (!studentAnswer.isEmpty()) {
				pt.responses.put(k, studentAnswer);
				q.setParameters(a.id % Integer.MAX_VALUE);
				int timeDependent = a.timeAllowed.get(a.questionNumber)>0?(int)(q.pointValue * (a.pollClosesAt.getTime() - pt.completed.getTime())/(10*a.timeAllowed.get(a.questionNumber))):100;
				score = q.isCorrect(studentAnswer) || !q.hasACorrectAnswer()?q.pointValue * (900 + timeDependent):0;
			}
			pt.scores.put(k,Integer.valueOf(score));
			ofy().save().entity(pt).now();
		} catch (Exception e) {}

		try {
			if (user.isAnonymous()) throw new Exception();  // don't save Scores for anonymous users
			if (a.lti_ags_lineitem_url != null) {
				QueueFactory.getDefaultQueue().add(withUrl("/ReportScore").param("AssignmentId",String.valueOf(a.id)).param("UserId",URLEncoder.encode(user.getId(),"UTF-8")));  // put report into the Task Queue
			}
		} catch (Exception e) {}
		return pt;
	}
	
	private static PollTransaction getPollTransaction(User user,Assignment a) throws Exception {
		PollTransaction pt = null;
		try {
			long assignmentId = a.id;
			String userId = user.getHashedId();
			pt = ofy().load().type(PollTransaction.class).filter("assignmentId",assignmentId).filter("userId",userId).first().now();
			if (pt == null) pt = new PollTransaction(user.getId(),new Date(),user.getAssignmentId());
		} catch (Exception e) {
			throw new Exception("Poll.getPollTransaction: " + e.getMessage()==null?e.toString():e.getMessage());
		}
		return pt;
	}
	
	private String waitForResults(User user, Assignment a) {
		
		if (a.pollIsClosed) return resultPage(user,a);
		if (user.isInstructor() && a.pollClosesAt!=null && a.pollClosesAt.before(new Date())) {
			a.pollIsClosed = true;
			ofy().save().entity(a).now();
			return resultPage(user,a);
		}
		
		StringBuffer buf = new StringBuffer(Subject.banner);
		
		buf.append("<h3>Please wait for the poll to close.</h3>"
				+ (a.pollClosesAt != null?"<div id='timer0' style='color: #EE0000'></div><br/>":""));
		
		buf.append("<form id=pollForm method=post action='/Poll' onsubmit=document.getElementById('sbmt1').disabled=true; >"
				+ (user.isInstructor()?"Whenever submissions are complete, you can ":"")
				+ "<input type=hidden name=sig value='" + user.getTokenSignature() + "' />"
				+ "<input type=hidden name=UserRequest value='ShowResults' />"
				+ "<input type=submit id=sbmt1 value='" + (user.isInstructor()?"Close and Show the Results":"View the Poll Results") + "' /> ");
		buf.append("</form><br/><br/>");

		if (!a.pollIsClosed && a.pollClosesAt != null) {
			buf.append(timer(user));
			buf.append("<script>startTimer(" + (a.pollClosesAt.getTime()+(user.isInstructor()?0L:3000L)) + ");</script>");
		}
		
		return buf.toString();	
	}
	
	private static String timer(User u) {
		return "\n<SCRIPT>"
				+ "var seconds;"
				+ "var minutes;"
				+ "var oddSeconds;"
				+ "var endMillis;"
				+ "var clock;"
				+ "var timer0 = document.getElementById('timer0');"
				+ "var timer1 = document.getElementById('timer1');"
				+ "var form = document.getElementById('pollForm');"
				+ "function countdown() {"
				+ "	var seconds=Math.round((endMillis-Date.now())/1000);"
				+ "	var minutes = seconds<0?Math.ceil(seconds/60.):Math.floor(seconds/60.);"
				+ "	var oddSeconds = seconds%60;"
				+ " if (oddSeconds<10) oddSeconds = '0'+ oddSeconds;"
				+ " clock = seconds<=0?'0:00':minutes + ':' + oddSeconds;"
				+ " if (timer0!=null) timer0.innerHTML = 'Time remaining: ' + clock;"
				+ " if (timer1!=null) timer1.innerHTML = 'Time remaining: ' + clock;"
				+ "	if (seconds <= 0) form.submit();"
				+ " else setTimeout(() => countdown(), 1000);"
				+ "}\n"
				+ "function startTimer(m) {"
				+ " endMillis = m;"
				+ " countdown();"
				+ " setTimeout(() => synchTimer(), Math.floor(Math.random()*10000)+10000);"  // schedule synch 10-20 s from now
				+ "}\n"
				+ "function synchTimer() {"
				+ "  var xmlhttp=new XMLHttpRequest();"
				+ "  if (xmlhttp==null) {"
				+ "    alert ('Sorry, your browser does not support AJAX!');"
				+ "    return false;"
				+ "  }"
				+ "  xmlhttp.onreadystatechange=function() {"
				+ "    if (xmlhttp.readyState==4) {"
				+ "     const serverNowMillis = xmlhttp.responseText.trim();"  // server returned new Date().getTime()
				+ "     endMillis += Date.now() - serverNowMillis;"          // corrects for fast or slow browser clock
				+ "    }"
				+ "  }\n"
				+ "  var url = 'Poll?UserRequest=Synch&sig=" +u.getTokenSignature() + "';"
				+ "  timer0.innerHTML = 'synchronizing clocks...';"
				+ "  xmlhttp.open('GET',url,true);"
				+ "  xmlhttp.send(null);"
				+ "  return false;"
				+ "}\n"
				+ "</SCRIPT>";
	}
	
	private String resultPage(User user,Assignment a) {
		// This method shows the participant a narrow stack of
		// - the current scored question
		// - current status of the leader board
		// - histogram of group results for the current question
		StringBuffer buf = new StringBuffer();
		StringBuffer debug = new StringBuffer("Debug:");

		try {
			if (!a.pollIsClosed) return waitForResults(user,a);

			if (user.isInstructor()) {
				if (a.timeAllowed.isEmpty() || a.timeAllowed.size()<a.questionNumber-1 || a.timeAllowed.get(a.questionNumber)==null || a.timeAllowed.get(a.questionNumber)==0) {
					buf.append("<b>Please tell your audience that they can view the poll results.</b>");
				}
				buf.append("<form method=post action=/Poll onsubmit=document.getElementById('sbmt2').disabled=true; >"
						+ "<input type=hidden name=sig value='" + user.getTokenSignature() + "' />"
						+ "<input type=hidden name=QuestionNumber value=" + (a.questionNumber+1) + " />"
						+ "<input type=hidden name=UserRequest value='" + (a.questionNumber<a.questionKeys.size()-1?"NextQuestion":"Finish") + "' />"
						+ "When you are ready &rarr; "
						+ "<input type=submit id=sbmt2 value='" + (a.questionNumber<a.questionKeys.size()-1?"Show the Next Question":"Finish") + "' />"
						+ "</form>");
			} else { // participant button to continue
				buf.append("<form method=get action=/Poll onsubmit=document.getElementById('sbmt3').disabled=true; >"
						+ "<input type=hidden name=sig value='" + user.getTokenSignature() + "' />"
						+ "<input type=hidden name=UserRequest value='" + (a.questionNumber<a.questionKeys.size()-1?"Show the Next Question":"Finish") + "' />"
						+ "<input type=submit id=sbmt3 value='" + (a.questionNumber<a.questionKeys.size()-1?"Show the Next Question":"Finish") + "' />"
						+ "</form>");
			}
			debug.append("b.");
			
			buf.append("<h3>Your Result</h3>");
			
			PollTransaction pt = getPollTransaction(user,a);
			List<PollTransaction> pts = ofy().load().type(PollTransaction.class).filter("assignmentId",a.id).list();
			Map<Key<Question>,Question> pollQuestions = ofy().load().keys(a.questionKeys);
			Key<Question> k = a.questionKeys.get(a.questionNumber);			
			Question q = pollQuestions.get(k);
			q.setParameters(a.id % Integer.MAX_VALUE);
			if (q.correctAnswer==null) q.correctAnswer = "";
			
			String userResponse = pt.responses==null?"":(pt.responses.get(k)==null?"":pt.responses.get(k));

			buf.append("<div style='background-color:" + colors[a.questionNumber%6] + "; padding:15px;'>" + q.printAllToStudents(userResponse,user.isInstructor()) + "</div>");

			// This is where we will construct a histogram showing the distribution of responses
			buf.append("<h3>Summary of Group Results</h3>");		
			buf.append("<div style='background-color:" + colors[(a.questionNumber+1)%6] + "; padding:15px;'>" + getHistogram(q,pts) + "</div>");
		
			// Print a summary of the top 3 scores in the group
			buf.append("<h3>Leader Board</h3>");
			buf.append("<div style='background-color:" + colors[(a.questionNumber+2)%6] + "; padding:15px;'>" + getLeaderBoard(a,pt,pts) + "</div>");
						
		} catch (Exception e) {
			buf.append(e.getMessage()==null?e.toString():e.getMessage() + "<br/>" + debug.toString());
		}
		return buf.toString();
	}
	
	private String allResultsPage(User user,Assignment a) throws Exception {
		StringBuffer buf = new StringBuffer();
		buf.append("<h2>" + a.title + "</h2>");
		
		buf.append("<h3>Congratulations to the Top Scorers!</h3>");
		PollTransaction pt = getPollTransaction(user,a);
		List<PollTransaction> pts = ofy().load().type(PollTransaction.class).filter("assignmentId",a.id).list();		
		buf.append("<div style='background-color:" + colors[5] + "; padding:15px;'>" + getLeaderBoard(a,pt,pts) + "</div>");
		
		buf.append("<h3>Summary of Group Results</h3>");
		Map<Key<Question>,Question> pollQuestions = ofy().load().keys(a.questionKeys);
		buf.append("<OL>");
		for (Key<Question> k : a.questionKeys) {
			Question q = pollQuestions.get(k);
			q.setParameters(a.id % Integer.MAX_VALUE);
			if (q.correctAnswer==null) q.correctAnswer = "";
			String userResponse = pt.responses==null?"":(pt.responses.get(k)==null?"":pt.responses.get(k));
			buf.append("<LI><div style='background-color:" + colors[a.questionKeys.indexOf(k)%6] + "; padding:15px;'>");
			buf.append(q.printAllToStudents(userResponse,user.isInstructor()));
			buf.append(getHistogram(q,pts));
			buf.append("</div><br/></LI>");
		}
		buf.append("</OL>");
		return buf.toString();
	}
	
	private String userResultsPage(User user, String forUserHashedId, String forUserName, Assignment a) throws Exception {
		if (!user.isInstructor()) throw new Exception("You must be the instructor to view this page.");
		StringBuffer buf = new StringBuffer();
		PollTransaction pt = null;

		pt = ofy().load().type(PollTransaction.class).filter("assignmentId",a.id).filter("userId",forUserHashedId).first().now();
		if (pt==null) {
			buf.append("<br/>There was no poll submission for this user.");
			return buf.toString();
		} else {
			buf.append("Name: " + (forUserName==null?"(withheld)":forUserName) + "<br/>"
					+ "Assignment ID: " + a.id + "<br/>"
					+ "Transaction ID: " + pt.id + "<br/>"
					+ "Submissions: " + pt.nSubmissions + "<br/>"
					+ (pt.nSubmissions>1?"First Submitted: " + pt.downloaded + "<br/>Last ":"")
					+ "Submitted: " + pt.completed + "<br/><br/>");	
		}

		Map<Key<Question>,Question> pollQuestions = ofy().load().keys(a.questionKeys);

		for (Key<Question> k : a.questionKeys) {
			Question q = pollQuestions.get(k);
			q.setParameters(a.id % Integer.MAX_VALUE);
			if (q.correctAnswer==null) q.correctAnswer = "";
			String userResponse = pt.responses==null?"":(pt.responses.get(k)==null?"":pt.responses.get(k));
			buf.append("<div style='background-color:" + colors[a.questionKeys.indexOf(k)%6] + "; padding:15px;'>" + q.printAllToStudents(userResponse,user.isInstructor()) + "</div>");
		}
		return buf.toString();
	}
	
	private String getLeaderBoard(Assignment a,PollTransaction pt,List<PollTransaction> pts) {
		StringBuffer buf = new StringBuffer();
		List<PollTransaction> leaderboard = new ArrayList<PollTransaction>(pts);  // clone the PollTransaction List
		Collections.sort(leaderboard,new SortByScore());
		int boardsize = pts.size()<4?1:(pts.size()==4?2:3);
		
		leaderboard.subList(boardsize,leaderboard.size()).clear();  // eliminates all but the top scorers
		
		buf.append("After " + (a.questionNumber+1) + " question" + (a.questionNumber+1>1?"s":"") + ", the leaders are:<br/>");
		buf.append("<OL>");
		for (PollTransaction t : leaderboard) {
			buf.append("<LI>" + t.nickname + "&nbsp;&rarr;&nbsp;" + t.compileScore(a.questionKeys) + " pts</LI>");
		}
		buf.append("</OL>");
	
		buf.append("Your score is " + pt.compileScore(a.questionKeys) + " out of a possible " + pt.compilePossibleScore(a.questionKeys) + " pts." );
		
		return buf.toString();
	}
	
	private static String getHistogram(Question q,List<PollTransaction> pts) throws Exception {
		StringBuffer buf = new StringBuffer();
		StringBuffer debug = new StringBuffer("Debug:");
		
		Map<String,Integer> histogram = new HashMap<String,Integer>();
		//String correctResponse = q.getCorrectAnswer();
		String otherResponses = null;
		char choice = 'a';
		Key<Question> k = Key.create(q);
		
		switch (q.getQuestionType()) {
		case Question.MULTIPLE_CHOICE:
			for (int j = 0; j < q.nChoices; j++) {
				histogram.put(String.valueOf(choice),0);
				choice++;
			}
			debug.append("2a.");
			for (PollTransaction t : pts) {
				if (t.completed==null || t.responses==null || t.responses.get(k)==null) continue;
				histogram.put(t.responses.get(k),histogram.get(t.responses.get(k))+1);
			}
			break;
		case Question.TRUE_FALSE:
			histogram.put("true", 0);
			histogram.put("false", 0);
			//chart_height = 100;
			debug.append("2b.");
			for (PollTransaction t : pts) {
				if (t.completed==null || t.responses==null || t.responses.get(k)==null) continue;
				histogram.put(t.responses.get(k),histogram.get(t.responses.get(k))+1);
			}
			break;
		case Question.SELECT_MULTIPLE:
			for (int j = 0; j < q.nChoices; j++) {
				histogram.put(String.valueOf(choice),0);
				choice++;
			}
			debug.append("2c.");
			for (PollTransaction t : pts) {
				if (t.completed==null || t.responses==null || t.responses.get(k)==null) continue;
				String response = t.responses.get(k);
				debug.append(response + ".");
				for (int m=0; m<response.length();m++) {
					debug.append("4.");
					histogram.put(String.valueOf(response.charAt(m)),histogram.get(String.valueOf(response.charAt(m)))+1);
				}
			}
			break;
		case Question.FILL_IN_WORD:
			histogram.put("correct", 0);
			histogram.put("incorrect", 0);
			//chart_height = 100;
			debug.append("2d.");
			for (PollTransaction t : pts) {
				if (t.completed==null || t.responses==null || t.responses.get(k)==null) continue;
				if (q.isCorrect(t.responses.get(k))) histogram.put("correct",histogram.get("correct")+1);
				else {
					histogram.put("incorrect", histogram.get("incorrect") + 1);
					if (otherResponses==null) otherResponses = t.responses.get(k);
					else if (otherResponses.length()<500 && t.responses.get(k) != null && !otherResponses.toLowerCase().contains(t.responses.get(k).toLowerCase())) otherResponses += "; " + t.responses.get(k);
				}
			}
			break;
		case Question.NUMERIC:
			histogram.put("correct", 0);
			histogram.put("incorrect", 0);
			//chart_height = 100;
			debug.append("2e.");
			for (PollTransaction t : pts) {
				if (t.completed==null || t.responses==null || t.responses.get(k)==null) continue;
				if (q.isCorrect(t.responses.get(k))) histogram.put("correct",histogram.get("correct")+1);
				else {
					histogram.put("incorrect", histogram.get("incorrect") + 1);
					if (otherResponses==null) otherResponses = t.responses.get(k);
					else if (otherResponses.length()<500 && t.responses.get(k) != null && !otherResponses.toLowerCase().contains(t.responses.get(k).toLowerCase())) otherResponses += "; " + t.responses.get(k);
				}
			}
			break;
		case Question.FIVE_STAR:
			for (int iStars=1;iStars<6;iStars++) histogram.put(String.valueOf(iStars) + (iStars==1?" star":" stars"), 0);
			for (PollTransaction t : pts) {
				if (t.completed==null || t.responses==null || t.responses.get(k)==null) continue;
				try {
					String nStars = t.responses.get(k);
					Integer.parseInt(nStars);
					histogram.put(nStars + (nStars.equals("1")?" star":" stars"),histogram.get(nStars + (nStars.equals("1")?" star":" stars"))+1);
				} catch (Exception e) {}
			}
			break;
		case Question.ESSAY:
			histogram.put("Number of responses", 0);
			for (PollTransaction t : pts) {
				if (t.completed==null || t.responses==null || t.responses.get(k)==null) continue;
				if (t.responses.get(k).length()>0) histogram.put("Number of responses", histogram.get("Number of responses")+1);
			}
		default:
		}
		debug.append("histogram initialized.");

		// Calculate a scale factor for the maximum width of the graph bars based on the max % response

		int maxValue = 0;
		int totalValues = 0;
		for (Entry<String,Integer> e : histogram.entrySet()) {
			totalValues += e.getValue();
			if (e.getValue() > maxValue) maxValue = e.getValue();
		}
		debug.append("maxValue="+maxValue+".totalValues="+totalValues+".");

		if (totalValues>0) {
			// Print a histogram as a table containing a horizontal bar graph:
			switch (q.getQuestionType()) {
			case Question.MULTIPLE_CHOICE:
			case Question.TRUE_FALSE:
			case Question.SELECT_MULTIPLE:
				buf.append("Summary of responses received for this question:<p></p>");
				buf.append("<table>");
				for (Entry<String,Integer> e : histogram.entrySet()) {
					buf.append("<tr><td>");
					buf.append(e.getKey() + "&nbsp;");
					buf.append("</td><td>");
					buf.append("<div style='background-color: blue;display: inline-block; width: " + 150*e.getValue()/(totalValues+1) + "px;'>&nbsp;</div>");
					buf.append("&nbsp;" + e.getValue() + "</td></tr>");
				}
				buf.append("</table>");
				break;
			case Question.FILL_IN_WORD:
			case Question.NUMERIC:
				buf.append("Summary of responses received for this question:<p></p>");
				if (q.hasACorrectAnswer()) {
					buf.append("<table>");
					buf.append("<tr><td>");
					buf.append("correct" + "&nbsp;");
					buf.append("</td><td>");
					buf.append("<div style='background-color: blue;display: inline-block; width: " + 150*histogram.get("correct")/(totalValues+1) + "px;'>&nbsp;</div>");
					buf.append("&nbsp;" + histogram.get("correct") + "</td></tr>");
					buf.append("<tr><td>");
					buf.append("incorrect" + "&nbsp;");
					buf.append("</td><td>");
					buf.append("<div style='background-color: blue;display: inline-block; width: " + 150*histogram.get("incorrect")/(totalValues+1) + "px;'>&nbsp;</div>");
					buf.append("&nbsp;" + histogram.get("incorrect") + "</td></tr>");
					if (otherResponses != null) buf.append("<tr><td colspan=2><br />Incorrect Responses: " + otherResponses + "</td></tr>");							
					buf.append("</table><br/>");
				} else buf.append(otherResponses + "<br/>");
				break;
			case Question.FIVE_STAR:
				buf.append("Summary of responses received for this question:<p></p>");
				buf.append("<table>");
				for (int iStars=5;iStars>0;iStars--) {
					buf.append("<tr><td>");
					buf.append(String.valueOf(iStars) + (iStars==1?" star":" stars"));
					buf.append("</td><td>");
					buf.append("<div style='background-color: blue;display: inline-block; width: " + 150*histogram.get(String.valueOf(iStars) + (iStars==1?" star":" stars"))/(totalValues+1) + "px;'>&nbsp;</div>");
					buf.append("&nbsp;" + histogram.get(String.valueOf(iStars) + (iStars==1?" star":" stars")) + "</td></tr>");
				}
				buf.append("</table><br/>");
				break;
			case Question.ESSAY:
				int nEssays = histogram.get("Number of responses");
				buf.append("A total of " + nEssays + (nEssays==1?" essay was ":" essays were ") + "submitted for this question.");
				break;
			}
		} else buf.append("No responses were submitted for this question.<br/>");
		//buf.append("<br/>");
		
		return buf.toString();
	}

	private static String editPage(User user,Assignment a,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer(Subject.banner);
		if (!user.isInstructor()) return "<h1>Sorry, you are not authorized to view this page.</h1>";

		int nAuthoredQuestions = ofy().load().type(Question.class).filter("authorId",user.getId()).count();
		
		if (a.getQuestionKeys().size() == 0) {
			buf.append("<h2>Welcome to ResponDog. Let's create your group poll.</h2>");
			if (nAuthoredQuestions==0)	{
				List<Question> starterQuestions = ofy().load().type(Question.class).filter("authorId","https://respondog.com/starter").list();
				String userId = user.getId();
				for (Question q : starterQuestions) {
					q.id = null;
					q.authorId = userId;
				}
				if (starterQuestions.size()>0) {
					ofy().save().entities(starterQuestions).now();
					for (Question q : starterQuestions) {
						a.questionKeys.add(Key.create(q));
						a.timeAllowed.add(60);
					}
					ofy().save().entity(a).now();
					nAuthoredQuestions = starterQuestions.size();
					buf.append("We've included a short list of question items below to get you started. Use the controls "
							+ "to change the order, remove them, edit or delete them entirely. They are yours.<br/><br/>");
				}
			}
		} else buf.append("<br/>");

		// Display a selector to create a new question or display candidate questions authored by this user:
		buf.append("<form method=get action='/Poll'>"
				+ "<input type=hidden name=sig value='" + user.getTokenSignature() + "' />"
				+ "<input type=submit name=UserRequest value='Create a new question' /> or "
				+ "<input type=submit name=UserRequest value='Add questions that I previously authored'" + (nAuthoredQuestions==a.questionKeys.size()?"disabled":"") + " /> or "
				+ "<input type=submit name=UserRequest value='Done editing' />"
				+ "</form>");

		if (a.questionKeys.size()>0) {
			buf.append("<h3>Current Questions For This Poll</h3>");
			int possibleScore = 0;
			Map<Key<Question>,Question> currentQuestions = ofy().load().keys(a.questionKeys);
			for (Key<Question> k : a.questionKeys) {  // main loop to present questions
				Question q = currentQuestions.get(k);
				if (q==null) { // somehow the question has been deleted from the database
					a.questionKeys.remove(k);
					ofy().save().entity(a).now();
					continue;
				}
				q.setParameters();
				int i = a.questionKeys.indexOf(k);
				int minutes = a.timeAllowed.get(i)/60;
				int seconds = a.timeAllowed.get(i)%60;
				String time = String.valueOf(minutes) + ":" + (seconds<10?"0":"") + String.valueOf(seconds);
				buf.append("<div style='display: table-row'>");
				buf.append("<div style='display: table-cell;width: 128px;padding-right:20px;align: center'>" 
						+ "<div style='text-align: right';>" + (i+1) + ".</div>"
						+ "<form action=/Poll method=post>"
						+ "<input type=hidden name=QuestionId value='" + q.id + "' />"
						+ "<input type=hidden name=sig value='" + user.getTokenSignature() + "' />"
						+ (a.questionKeys.indexOf(k)>0?"<input type=submit name=UserRequest value='MvUp' />":"")
						+ (a.questionKeys.indexOf(k)<a.questionKeys.size()-1?"<input type=submit name=UserRequest value='MvDn' />":"")
						+ "<br/><input type=submit name=UserRequest value='Remove' /><br/>"
						+ "<label>Time:<input type=text size=3 name=AllowedTime value=" + time + " /></label>"
						+ "<input type=submit name=UserRequest value=Set />"
						+ "</form>");
				if (user.getId().equals(q.authorId)) {  // give a chance to edit the question
					buf.append("<a href=/Poll?sig=" + user.getTokenSignature() + "&UserRequest=Edit&QuestionId=" + q.id + ">Edit</a>");
				}
				buf.append("</div>");
				buf.append("<div style='display: table-cell'>" + q.printAll() + "</div>");
				buf.append("</div><br/>"); // end of row
				possibleScore += q.pointValue;
			}
			if (a.questionKeys.size()>0) buf.append("<hr/>This poll is worth a possible " + possibleScore + " points.");
		} 
		buf.append("<br/><br/>");		
		return buf.toString();
	}
	
	private static String selectAuthoredQuestions(User user, Assignment a, HttpServletRequest request) {
		StringBuffer buf = new StringBuffer(Subject.banner);
		buf.append("<h2>My Poll Question Item Bank</h2>");
		buf.append("These are questions that you have created or edited. Select any of them to include<br/>"
				+ "in the current poll. If you don't see any you like, you can "
				+ "<a href=/Poll?UserRequest=NewQuestion&sig=" + user.getTokenSignature() + ">create a new one</a>.<br/><br/>");
		
		List<Key<Question>> authoredQuestionKeys = ofy().load().type(Question.class).filter("authorId",user.getId()).keys().list();
		if (a.questionKeys.containsAll(authoredQuestionKeys)) {
			buf.append("It looks like all of the questions that you have authored, including any starter questions, are already "
					+ "included in the current poll. <a href=/Poll?UserRequest=EditPoll&sig=" + user.getTokenSignature() + ">"
					+ "Return to the Edit Page</a>");
			return buf.toString();
		}
		
		List<Question> authoredQuestions = ofy().load().type(Question.class).filter("authorId",user.getId()).list();
		 
		buf.append("<form method=post action='/Poll'><input type=hidden name=sig value='" + user.getTokenSignature() + "' />");
		buf.append("<input type=hidden name=UserRequest value='AddQuestions' />");
		buf.append("<input type=submit value='Include the selected items below in the poll' /> "
				+ "or <a href=/Poll?UserRequest=EditPoll&sig=" + user.getTokenSignature() + ">Cancel</a><br/><br/>");
		int i=0;
		buf.append("<div style='display:table'>");
		for (Question q : authoredQuestions) {
			if (a.questionKeys.contains(Key.create(q))) continue; // don't allow duplicate questions in a Poll
			i++;
			q.setParameters(a.id % Integer.MAX_VALUE);
			buf.append("<div style='display: table-row'>");
			buf.append("<div style='display: table-cell;width: 55px;'><input type=checkbox name=QuestionId value='" + q.id + "' />&nbsp;" + i + ".</div>");
			buf.append("<div style='display: table-cell'>" + q.printAll() + "</div>");
			buf.append("</div>");  // end of row
		}
		buf.append("</div>");  // end of table
		buf.append("<input type=submit value='Include the selected items above in the poll' /> "
				+ "or <a href=/Poll?UserRequest=EditPoll&sig=" + user.getTokenSignature() + ">Cancel</a><br/><br/>");
		buf.append("</form>");

		return buf.toString();
	}
	
	private static void addQuestions(User user,Assignment a,HttpServletRequest request) {
		String[] qids = request.getParameterValues("QuestionId");
		List<Key<Question>> questionKeys = new ArrayList<Key<Question>>();
		List<Integer> timeAllowed = new ArrayList<Integer>();
		for (String qid : qids) {
			try {
				questionKeys.add(Key.create(Question.class,Long.parseLong(qid)));
				timeAllowed.add(60);
			} catch (Exception e) {}
		}
		if (questionKeys.size()>0) {
			if (a.questionKeys == null) {
				a.questionKeys = questionKeys;
				a.timeAllowed = timeAllowed;
			}
			else {
				a.questionKeys.addAll(questionKeys);
				a.timeAllowed.addAll(timeAllowed);
			}
			ofy().save().entity(a).now();
		}
	}
	
	private static void removeQuestion(User user,Assignment a,HttpServletRequest request) {
		try {
			Long questionId = Long.parseLong(request.getParameter("QuestionId"));
			Key<Question> k = Key.create(Question.class,questionId);
			if (a.questionKeys.remove(k)) ofy().save().entity(a).now();
		} catch (Exception e) {}
	}
	
	private static String newQuestionForm(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer(Subject.banner);
		String assignmentType = "Poll";
		int questionType = 0;
		try {
			questionType = Integer.parseInt(request.getParameter("QuestionType"));
			switch (questionType) {
			case (1): 
				buf.append("<h3>New Multiple-Choice Question</h3>");
				buf.append("Enter the question text and up to 5 choices. If there is a correct answer, "
					+ "select it, but if you are asking for an opinion, you may leave all choices unselected."); 
				break;
			case (2): 
				buf.append("<h3>New True-False Question</h3>");
				buf.append("Write the question as an affirmative statement. Then select the true statement, "
						+ "or leave both unselected if you are asking for an opinion.");
				break;
			case (3): 
				buf.append("<h3>New Select-Multiple Question</h3>");
				buf.append("Enter the question text and up to 5 choices. If there are correct answers, "
					+ "select them, but if you are asking for an opinion you may leave them all unselected.");
				break;
			case (4): 
				buf.append("<h3>New Fill-in-Word Question</h3>");
				buf.append("Start the question text in the upper textarea box. Indicate "
					+ "the correct answer (and optionally, an alternative correct answer) in "
					+ "the middle box, or leave it blank to ask for an opinion. Finish the sentence in "
					+ "the box below that. Scoring is not case-sensitive or punctuation-sensitive, and spelling "
					+ "is somewhat lenient.");
				break;
			case (5): 
				buf.append("<h3>New Numeric Question</h3>");
				buf.append("Fill in the question text in the upper textarea box and "
					+ "the correct numeric answer below, or leave blank to ask an opinion. Indicate the "
					+ "required precision of the response in percent (default = 1%). Use the bottom "
					+ "textarea box to finish the question text and/or to indicate the "
					+ "expected dimensions or units of the answer."); 
				break;
			case (6):
				buf.append("<h3>New 5-Star Rating Question</h3>");
			buf.append("Ask the participants to rate something from 1 to 5 stars.");			
				break;
			case (7):
				buf.append("<h3>New Short Essay Question</h3>");
				buf.append("Ask the participants to briefly explain something in a few sentences. Note that this item "
						+ "cannot be scored automatically, so participants will be awarded full credit for providing any response. "
						+ "The instructor has the ability to review the participant responses and score them, if desired.");			
				break;
			default: buf.append("An unexpected error occurred. Please try again.");
			}
			Question question = new Question(questionType);
			if (questionType==5) question.requiredPrecision = 1.0;
			buf.append("<p><FORM METHOD=GET ACTION='/Poll'>"
					+ "<INPUT TYPE=HIDDEN NAME=sig VALUE='" + user.getTokenSignature() + "' />"
					+ "<INPUT TYPE=HIDDEN NAME=AssignmentType VALUE='" + assignmentType + "' />"
					+ "<INPUT TYPE=HIDDEN NAME=AuthorId VALUE='" + user.getId() + "' />");
			buf.append("<INPUT TYPE=HIDDEN NAME=QuestionType VALUE=" + questionType + " />");
			
			//buf.append("Assignment Type: Poll<br />");
			buf.append("Point Value: <input type=text size=2 name=PointValue value=1 /><br />");
			buf.append(question.edit());
			buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Preview' />"
					+ "</FORM><br/><br/>");
		} catch (Exception e) {
			buf.append("<h2>Create A New Question</h2>");
			buf.append("Select one of the following question types:<br />");
			buf.append("<a href=/Poll?UserRequest=NewQuestion&QuestionType=1&sig=" + user.getTokenSignature() + "><img height=128 width=128 src=/images/multiple_choice.png alt='multiple_choice'></a> "
					+ "<a href=/Poll?UserRequest=NewQuestion&QuestionType=3&sig=" + user.getTokenSignature() + "><img height=128 width=128 src=/images/checkbox.png alt='checkbox'></a> "
					+ "<a href=/Poll?UserRequest=NewQuestion&QuestionType=2&sig=" + user.getTokenSignature() + "><img height=128 width=128 src=/images/true_false.png alt='true_false'></a> "
					+ "<a href=/Poll?UserRequest=NewQuestion&QuestionType=5&sig=" + user.getTokenSignature() + "><img height=128 width=128 src=/images/numeric.png alt='numeric'></a> "
					+ "<a href=/Poll?UserRequest=NewQuestion&QuestionType=6&sig=" + user.getTokenSignature() + "><img height=128 width=128 src=/images/five_stars.png alt='five_stars'></a> "
					+ "<a href=/Poll?UserRequest=NewQuestion&QuestionType=4&sig=" + user.getTokenSignature() + "><img height=128 width=128 src=/images/fill_in_blank.png alt='fill_in_blank'></a> "
					+ "<a href=/Poll?UserRequest=NewQuestion&QuestionType=7&sig=" + user.getTokenSignature() + "><img height=128 width=128 src=/images/short_essay.png alt='short_essay'></a>"
					+ "<br/><br/>");
/*			
					+ "<INPUT TYPE=HIDDEN NAME=sig VALUE='" + user.getTokenSignature() + "' />"
					+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=NewQuestion />"
					+ "<INPUT TYPE=HIDDEN NAME=QuestionType />"
					+ "<INPUT TYPE=BUTTON onCLick=\"document.NewQuestion.QuestionType.value=1;submit()\" VALUE='Multiple Choice' /> "
					+ "<INPUT TYPE=BUTTON onCLick=\"document.NewQuestion.QuestionType.value=2;submit()\" VALUE='True/False' /> "
					+ "<INPUT TYPE=BUTTON onCLick=\"document.NewQuestion.QuestionType.value=3;submit()\" VALUE='Select Multiple' /> "
					+ "<INPUT TYPE=BUTTON onCLick=\"document.NewQuestion.QuestionType.value=4;submit()\" VALUE='Fill in Word' /> "
					+ "<INPUT TYPE=BUTTON onCLick=\"document.NewQuestion.QuestionType.value=5;submit()\" VALUE='Numeric' /> "
					+ "<INPUT TYPE=BUTTON onCLick=\"document.NewQuestion.QuestionType.value=6;submit()\" VALUE='5-Star Rating' /> "
					+ "<INPUT TYPE=BUTTON onCLick=\"document.NewQuestion.QuestionType.value=7;submit()\" VALUE='Short Essay' /> ");
			buf.append("</FORM><br/><br/>");
*/
		}
		return buf.toString();
	}

	private static String editQuestion (User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer(Subject.banner);
		try {
			long questionId = Long.parseLong(request.getParameter("QuestionId"));
			Question q = ofy().load().type(Question.class).id(questionId).safe();
			
			if (!user.isInstructor() || !user.getId().equals(q.authorId)) throw new Exception("Access denied.");
			
			if (q.requiresParser()) q.setParameters();
			buf.append("<h3>Current Question</h3>");
			//buf.append("Assignment Type: Poll<br>");
			//buf.append("Author: " + q.authorId + "<br>");
			//buf.append("Editor: " + q.editorId + "<br><br/>");
			
			buf.append("<FORM Action=/Poll METHOD=POST>"
					+ "<input type=hidden name=sig value='" + user.getTokenSignature() + "' />");
			
			buf.append(q.printAll());
			
			if (q.authorId==null) q.authorId="";
			if (q.editorId==null) q.editorId="";
			buf.append("<INPUT TYPE=HIDDEN NAME=AuthorId VALUE='" + q.authorId + "' />");
			buf.append("<INPUT TYPE=HIDDEN NAME=EditorId VALUE='" + q.editorId + "' />");
			buf.append("<INPUT TYPE=HIDDEN NAME=QuestionId VALUE='" + questionId + "' />");
			buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Delete Question' onclick='return confirm(\"Delete this item permanently?\");' />");
			buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Quit' />");
			
			buf.append("<hr><h3>Edit This Question</h3>");
			
			//buf.append("Assignment Type: Poll<br>");
			
			buf.append("Question Type:" + questionTypeDropDownBox(q.getQuestionType()));
			buf.append(" Point Value: 1<br>");
			
			buf.append(q.edit());
			
			buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE=Preview />");
			buf.append("</FORM><br/><br/>");
		} catch (Exception e) {
			buf.append(e.toString());
		}
		return buf.toString();
	}
	
	private static String previewQuestion(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer(Subject.banner);
		StringBuffer debug = new StringBuffer("Debug:");
		try {
			long questionId = 0;
			boolean current = false;
			boolean proposed = false;
			try {
				questionId = Long.parseLong(request.getParameter("QuestionId"));
				current = true;
			} catch (Exception e2) {
				debug.append("a");
			}
			long proposedQuestionId = 0;
			try {
				proposedQuestionId = Long.parseLong(request.getParameter("ProposedQuestionId"));
				proposed = true;
				current = false;
			} catch (Exception e2) {
				debug.append("b");
			}
			
			Question q = assembleQuestion(request);
			debug.append("c");
			if (q.requiresParser()) q.setParameters();
			debug.append("d");
			
			buf.append("<h3>Preview Poll Question</h3>");
			
			q.assignmentType = "Poll";
				
			//buf.append("Author: " + q.authorId + "<br />");
			//buf.append("Editor: " + user.getId() + "<p>");
			
			buf.append("<FORM Action='/Poll' METHOD=POST>"
					+ "<INPUT TYPE=HIDDEN NAME=sig VALUE='" + user.getTokenSignature() + "' />");
			debug.append("e");
			
			buf.append(q.printAll());
			debug.append("f");
			
			if (q.authorId==null) q.authorId="";
			buf.append("<INPUT TYPE=HIDDEN NAME=AuthorId VALUE='" + q.authorId + "' />");
			buf.append("<INPUT TYPE=HIDDEN NAME=EditorId VALUE='" + user.getId() + "' />");
			
			
			if (current) {
				buf.append("<INPUT TYPE=HIDDEN NAME=QuestionId VALUE=" + questionId + " />");
				buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Update Question' />");
			}
			if (proposed) {
				buf.append("<INPUT TYPE=HIDDEN NAME=ProposedQuestionId VALUE=" + proposedQuestionId + " />");
				buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Activate This Question' />");
			} else buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Save New Question' />");
			
			buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Quit' />");
			
			buf.append("<hr><h3>Continue Editing</h3>");
			buf.append("Assignment Type: Poll<br />");
			
			buf.append("<br />");
			buf.append("Question Type:" + questionTypeDropDownBox(q.getQuestionType()));
			
			buf.append(" Point Value: <input type=text size=2 name=PointValue value='" + q.pointValue + "' /><br />");
			
			buf.append(q.edit());
			
			buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE=Preview />");
			buf.append("</FORM><br/><br/>");
		} catch (Exception e) {
			buf.append("<br/>Error: " + e.getMessage()==null?e.toString():e.getMessage() + "<br/>" + debug.toString());
		}
		return buf.toString();
	}

	private static Question assembleQuestion(HttpServletRequest request) {
		try {
			int questionType = Integer.parseInt(request.getParameter("QuestionType"));
			return assembleQuestion(request,new Question(questionType)); 
		} catch (Exception e) {
			return null;
		}
	}
	
	private static Question assembleQuestion(HttpServletRequest request,Question q) {
		String assignmentType = "Poll";
		int type = q.getQuestionType();
		try {
			type = Integer.parseInt(request.getParameter("QuestionType"));
		}catch (Exception e) {}
		String questionText = request.getParameter("QuestionText");
		ArrayList<String> choices = new ArrayList<String>();
		int nChoices = 0;
		char choice = 'A';
		for (int i=0;i<5;i++) {
			String choiceText = request.getParameter("Choice"+ choice +"Text");
			if (choiceText==null) choiceText = "";
			if (choiceText.length() > 0) {
				choices.add(choiceText);
				nChoices++;
			}
			choice++;
		}
		double requiredPrecision = 1.; // percent
		int significantFigures = 0;
		boolean scrambleChoices = false;
		boolean strictSpelling = false;
		int pointValue = 1;
		try {
			pointValue = Integer.parseInt(request.getParameter("PointValue"));
		} catch (Exception e) {
		}
		try {
			requiredPrecision = Double.parseDouble(request.getParameter("RequiredPrecision"));
		} catch (Exception e) {
		}
		try {
			significantFigures = Integer.parseInt(request.getParameter("SignificantFigures"));
		} catch (Exception e) {
		}
		try {
			scrambleChoices = Boolean.parseBoolean(request.getParameter("ScrambleChoices"));
		} catch (Exception e) {
		}
		try {
			strictSpelling = Boolean.parseBoolean(request.getParameter("StrictSpelling"));
		} catch (Exception e) {
		}
		String correctAnswer = "";
		try {
			String[] allAnswers = request.getParameterValues("CorrectAnswer");
			for (int i = 0; i < allAnswers.length; i++) correctAnswer += allAnswers[i];
		} catch (Exception e) {
			correctAnswer = request.getParameter("CorrectAnswer");
		}
		String parameterString = request.getParameter("ParameterString");
		if (parameterString == null) parameterString = "";
		
		q.assignmentType = assignmentType;
		q.setQuestionType(type);
		q.text = questionText;
		q.nChoices = nChoices;
		q.choices = choices;
		q.requiredPrecision = requiredPrecision;
		q.significantFigures = significantFigures;
		q.correctAnswer = correctAnswer;
		q.tag = request.getParameter("QuestionTag");
		q.pointValue = pointValue;
		q.parameterString = parameterString;
		q.hint = request.getParameter("Hint");
		q.solution = request.getParameter("Solution");
		q.notes = "";
		q.scrambleChoices = scrambleChoices;
		q.strictSpelling = strictSpelling;
		q.authorId = request.getParameter("AuthorId");
		q.editorId = request.getParameter("EditorId");
		q.validateFields();
		return q;
	}
	
	private static String questionTypeDropDownBox(int questionType) {
		StringBuffer buf = new StringBuffer();
		buf.append("\n<SELECT NAME=QuestionType>"
				+ "<OPTION VALUE=1" + (questionType==1?" SELECTED>":">") + "Multiple Choice</OPTION>"
				+ "<OPTION VALUE=2" + (questionType==2?" SELECTED>":">") + "True/False</OPTION>"
				+ "<OPTION VALUE=3" + (questionType==3?" SELECTED>":">") + "Select Multiple</OPTION>"
				+ "<OPTION VALUE=4" + (questionType==4?" SELECTED>":">") + "Fill in word/phrase</OPTION>"
				+ "<OPTION VALUE=5" + (questionType==5?" SELECTED>":">") + "Numeric</OPTION>"
				+ "<OPTION VALUE=6" + (questionType==6?" SELECTED>":">") + "5-Star Rating</OPTION>"
				+ "<OPTION VALUE=7" + (questionType==7?" SELECTED>":">") + "Short Essay</OPTION>"
				+ "</SELECT>");
		return buf.toString();
	}
	
	private static long createQuestion(User user,HttpServletRequest request) { //previously type long
		try {
			Question q = assembleQuestion(request);
			q.isActive = true;
			ofy().save().entity(q).now();
			return q.id;
		} catch (Exception e) {
			return 0;
		}
	}
	
	private static String orderResponses(String[] answers) {
		Arrays.sort(answers);
		String studentAnswer = "";
		for (String a : answers) studentAnswer = studentAnswer + a;
		return studentAnswer;
	}

	private static String showSummary(User user, HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		Assignment a = ofy().load().type(Assignment.class).id(user.getAssignmentId()).now();
		if (a==null) return "No assignment was specified for this request.";
		if (!user.isInstructor()) return "You must be logged in as the instructor to view this page.";
		if (a.lti_nrps_context_memberships_url==null) return "Sorry, your LMS does not support the Names and Roles Provisioning Service.";

		try {
			buf.append("<h3>" + a.assignmentType + "</h3>");
			buf.append("Valid: " + new Date() + "<p>");
			buf.append("The roster below is obtained using the Names and Role Provisioning service offered by your learning management system, "
					+ "and may or may not include user's names or emails, depending on the settings of your LMS.<br/><br/>");

			Map<String,String> scores = LTIMessage.readMembershipScores(a);
			if (scores==null) scores = new HashMap<String,String>();  // in case service call fails

			Map<String,String[]> membership = LTIMessage.getMembership(a);
			if (membership==null) membership = new HashMap<String,String[]>(); // in case service call fails

			Map<String,Key<Score>> keys = new HashMap<String,Key<Score>>();
			Deployment d = ofy().load().type(Deployment.class).id(a.domain).safe();
			String platform_id = d.getPlatformId() + "/";
			for (String id : membership.keySet()) {
				keys.put(id,Key.create(Key.create(User.class,Subject.hashId(platform_id+id)),Score.class,a.id));
			}
			Map<Key<Score>,Score> cvScores = ofy().load().keys(keys.values());
			
			// Make a map of all PollTransactions using the users' hashedId values as a key;
			List<PollTransaction> ptsList = ofy().load().type(PollTransaction.class).filter("assignmentId",a.id).list();
			Map<String,PollTransaction> ptsMap = new HashMap<String,PollTransaction>();
			for (PollTransaction pt : ptsList) ptsMap.put(pt.userId,pt);
			
			buf.append("<table><tr><th>&nbsp;</th><th>Name</th><th>Email</th><th>Role</th><th>LMS Score</th><th>Poll Score</th><th>Scores Detail</th></tr>");
			int i=0;
			boolean synched = true;
			for (Map.Entry<String,String[]> entry : membership.entrySet()) {
				if (entry == null) continue;
				String s = scores.get(entry.getKey());
				Score cvScore = cvScores.get(keys.get(entry.getKey()));
				String forUserHashedId = Subject.hashId(platform_id + entry.getKey());  // only send hashed values through links
				ptsMap.remove(forUserHashedId);
				i++;
				buf.append("<tr><td>" + i + ".&nbsp;</td>"
						+ "<td>" + entry.getValue()[1] + "</td>"
						+ "<td>" + entry.getValue()[2] + "</td>"
						+ "<td>" + entry.getValue()[0] + "</td>"
						+ "<td align=center>" + (s == null?" - ":s + "%") + "</td>"
						+ "<td align=center>" + (cvScore == null?" - ":String.valueOf(cvScore.getPctScore()) + "%") + "</td>"
						+ "<td align=center>" + (cvScore == null?" - ":"<a href=/Poll?UserRequest=Review&sig=" + user.getTokenSignature() + "&ForUserHashedId=" + forUserHashedId + "&ForUserName=" + entry.getValue()[1].replaceAll(" ","+") + ">show</a>") + "</td>"
						+ "</tr>");
				// Flag this score set as unsynchronized only if there is one or more non-null ResponDog Learner score that is not equal to the LMS score
				// Ignore Instructor scores because the LMS often does not report them, and ignore null cvScore entities because they cannot be reported.
				synched = synched && (!"Learner".equals(entry.getValue()[0]) || (cvScore!=null?String.valueOf(cvScore.getPctScore()).equals(s):true));
			}
			// Continue to the table by finding and including any guest participants
			for (Map.Entry<String,PollTransaction> entry : ptsMap.entrySet()) {
				i++;
				PollTransaction pt = entry.getValue();
				int scorePct = pt.compileScore(a.questionKeys)*100/pt.compilePossibleScore(a.questionKeys);
				buf.append("<tr><td>" + i + ".&nbsp;</td>"
						+ "<td>" + entry.getValue().nickname + "</td>"
						+ "<td align=center> - </td>"
						+ "<td>Guest</td>"
						+ "<td align=center> - </td>"
						+ "<td align=center>" + scorePct + "%</td>"
						+ "<td align=center><a href=/Poll?UserRequest=Review&sig=" + user.getTokenSignature() + "&ForUserHashedId=" + pt.userId + "&ForUserName=" + pt.nickname + ">show</a></td>"
						+ "</tr>");
			}
			buf.append("</table><br/>");
			if (!synched) {
				buf.append("If any of the Learner scores above are not synchronized, you may use the button below to launch a background task " 
						+ "where ResponDog will resubmit them to your LMS. This can take several seconds to minutes depending on the "
						+ "number of scores to process. Please note that you may have to adjust the settings in your LMS to accept the "
						+ "revised scores. For example, in Canvas you may need to change the assignment settings to Unlimited Submissions. "
						+ "This may also cause the submission to be counted as late if the LMS assignment deadline has passed.<br/>"
						+ "<form method=post action=/Poll >"
						+ "<input type=hidden name=sig value=" + user.getTokenSignature() + " />"
						+ "<input type=hidden name=UserRequest value='Synchronize Scores' />"
						+ "<input type=submit value='Synchronize Scores' />"
						+ "</form>");
			}
			return buf.toString();
		} catch (Exception e) {
			buf.append(e.toString());
		}

		return buf.toString();
	}
	
	private static void moveUp(User user,Assignment a,HttpServletRequest request) {
		// this method moves a Poll question earlier in the Poll
		if (!user.isInstructor()) return;
		try {
			Long questionId = Long.parseLong(request.getParameter("QuestionId"));
			Key<Question> k = Key.create(Question.class,questionId);
			int i = a.questionKeys.indexOf(k);
			if(i>0) {
				Collections.swap(a.questionKeys, i, i-1);
				Collections.swap(a.timeAllowed, i, i-1);
				ofy().save().entity(a).now();
			}
		} catch (Exception e) {			
		}
	}
	
	private static void moveDn(User user,Assignment a,HttpServletRequest request) {
		// this method moves a Poll question later in the Poll
		if (!user.isInstructor()) return;
		try {
			Long questionId = Long.parseLong(request.getParameter("QuestionId"));
			Key<Question> k = Key.create(Question.class,questionId);
			int i = a.questionKeys.indexOf(k);
			if(i<a.questionKeys.size()-1) {
				Collections.swap(a.questionKeys, i, i+1);
				Collections.swap(a.timeAllowed, i, i+1);
				ofy().save().entity(a).now();
			}
		} catch (Exception e) {			
		}
	}
	
	private static void setAllowedTime(User user, Assignment a, HttpServletRequest request) {
		// this method set the allowed time for a question displayed on the Edit page
		if (!user.isInstructor()) return;
		try {
			int seconds = 0;
			String[] time = request.getParameter("AllowedTime").split(":");  // accepts format min:sec
			if (time.length==2) seconds = 60*Integer.parseInt(time[0]);
			seconds += Integer.parseInt(time[time.length-1]);
			Long questionId = Long.parseLong(request.getParameter("QuestionId"));
			Key<Question> k = Key.create(Question.class,questionId);
			int i = a.questionKeys.indexOf(k);
			a.timeAllowed.set(i,seconds);
			ofy().save().entity(a).now();
		} catch (Exception e) {			
		}
	}
	
	class SortByScore implements Comparator<PollTransaction> {
		
		SortByScore() {}
		
		Map<Long,Assignment> assignments = new HashMap<Long,Assignment>();
		
		public int compare(PollTransaction t1, PollTransaction t2) {
			Assignment a = getAssignment(t1.assignmentId);
			int score1 = t1.compileScore(a.questionKeys);
			int score2 = t2.compileScore(a.questionKeys);
			return score2-score1;
		}
		
		private Assignment getAssignment(Long id) {
			Assignment a = assignments.get(id);
			if (a==null) {
				a = ofy().load().type(Assignment.class).id(id).now();
				assignments.put(id, a);
			}
			return a;
		}
	}
}
