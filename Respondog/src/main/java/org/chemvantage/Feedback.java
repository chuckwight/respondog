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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;


@WebServlet("/Feedback")
public class Feedback extends HttpServlet {

	private static final long serialVersionUID = 137L;
	
	public String getServletInfo() {
		return "This servlet is used by contributors to suggest new and revised Quiz and Homework questions.";
	}

	public void doGet(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		try {
			User user = User.getUser(request.getParameter("sig"));
			if (user == null) user = new User();
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest == null) userRequest = "";
			
			switch (userRequest) {
			default:
				out.println(Subject.header("ResponDog Feedback Form") + feedbackForm(user) + Subject.footer);
			}
		} catch (Exception e) {
			response.sendRedirect("/Logout?sig=" + request.getParameter("sig"));
		}
	}

	public void doPost(HttpServletRequest request,HttpServletResponse response)
	throws ServletException, IOException {
		try {
			User user = User.getUser(request.getParameter("sig"));
			if (user==null) throw new Exception();
			
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			
			String userRequest = request.getParameter("UserRequest");
			if (userRequest == null) userRequest = "";

			out.println(Subject.header("ResponDog Feedback"));
			switch (userRequest) {
			case "SubmitFeedback":
				out.println(submitFeedback(user,request));
				break;
			default:
				out.println(feedbackForm(user));
			}
			out.println(Subject.footer);
		} catch (Exception e) {
			response.sendRedirect("/Logout?sig=" + request.getParameter("sig"));
		}
	}

	public String recordAjaxRating(HttpServletRequest request) {
		int stars = 0;
		try {
			stars = Integer.parseInt(request.getParameter("NStars"));
			Subject.addStarReport(stars);
		}
		catch (Exception e) {
			return e.toString();
		}
		return "Your rating was " + stars + " stars. The average user rating is " + Subject.getAvgStars() + " stars.";
	}

	String feedbackForm(User user) {
		StringBuffer buf = new StringBuffer();
		buf.append(Subject.banner);
		buf.append("<h2>Feedback Page</h2>");

		buf.append("Your comments and opinions are important to us.  We use this<br>"
				+ "information to improve the functionality of the site for our users.<p>");
				
		buf.append("<script type='text/javascript'>\n"
				+ "<!--\n"
				+ "var star1 = new Image(); star1.src='images/star1.gif';\n"
				+ "var star2 = new Image(); star2.src='images/star2.gif';\n"
				+ "var set = false;\n"
				+ "function showStars(n) {"
				+ "  if (!set) {"
				+ "    document.getElementById('vote').innerHTML=(n==0?'(click a star)':''+n+(n>1?' stars':' star'));"
				+ "    for (i=1;i<6;i++) document.getElementById(i).src=(i<=n?star2.src:star1.src);"
				+ "  }"
				+ "}\n"
				+ "function setStars(n) {"
				+ "  set = (n>0?true:false);"
				+ "  document.FeedbackForm.Stars.value = n;"
				+ "}\n"
				+ "// -->\n"
				+ "</script>\n");

		buf.append("Please rate your overall experience with ResponDog:\n");

		buf.append("<div id='vote' style='color:red;'>(click a star):</div>\n");
		for (int istar=1;istar<6;istar++) {
			buf.append("<img src='images/star1.gif' id='" + istar + "' style='width:30px; height:30px;' "        // properties
					+ "onmouseover=showStars(this.id) onmouseout=showStars('0') onClick='set=false;showStars(this.id);setStars(this.id)' />" ); // mouse actions
		}
		buf.append("&nbsp;&nbsp;&nbsp;&nbsp;<input type=range min=1 max=5 style='opacity:0' onfocus=this.style='opacity:1' oninput='set=false;showStars(this.value);setStars(this.value)'>");
		buf.append("<br clear='all'>");
		buf.append("<FONT SIZE=-1>(" + Subject.getNStarReports() + " user ratings; avg = " + Subject.getAvgStars() + " stars)</FONT><p>\n");

		if (user.isAnonymous()) buf.append("<script type='text/javascript' src='https://www.google.com/recaptcha/api.js'> </script>");				
		
		buf.append("<FORM NAME=FeedbackForm ACTION=Feedback METHOD=POST>\n"
				+ "Comments or kudos: <FONT SIZE=-1>(160 characters max.)</FONT><br>"
				+ "<INPUT TYPE=HIDDEN NAME=UserRequest VALUE=SubmitFeedback>"
				+ "<INPUT TYPE=HIDDEN NAME=Stars>"
				+ "<INPUT TYPE=HIDDEN NAME=sig VALUE='" + user.getTokenSignature() + "'>"
				+ "<TEXTAREA NAME=Comments ROWS=5 COLS=60 WRAP=SOFT "				
				+ "onKeyUp=javascript:{document.FeedbackForm.Comments.value=document.FeedbackForm.Comments.value.substring(0,160);document.getElementById('cbox').style.visibility='visible';}>"
				+ "</TEXTAREA><br>");

		buf.append("<label id=cbox style='visibility:hidden'>Email: <input type=text size=50 placeholder=' optional, if you want a response to your comment' name=Email></label><p>");
		
		// If the user is anonymous, insert the Google reCaptcha tool (version 2) on the page
		if (user.isAnonymous()) buf.append("<div class='g-recaptcha' data-sitekey=" + Subject.getReCaptchaSiteKey() + " aria-label='Google Recaptcha'></div><br/><br/>");

		buf.append("<INPUT TYPE=SUBMIT NAME=UserRequest VALUE='Submit Feedback'>"
				+ "<INPUT TYPE=RESET VALUE='Clear Form' "
				+ "onClick=\"javascript: document.FeedbackForm.Stars.value='';"
				+ "setStars(0);"
				+ "for (i=1;i<6;i++) document.getElementById(i).src=star1.src;"
				+ "document.getElementById('vote').innerHTML='(click a star):';"
				+ "document.getElementById('cbox').style.visibility='hidden';"
				+ "\">"
				+ "</FORM><br/><br/>");
		
		return buf.toString(); 
	}

	String submitFeedback(User user,HttpServletRequest request) {
		StringBuffer buf = new StringBuffer();
		
		try { 
			if (user.isAnonymous() && !reCaptchaOK(request)) throw new Exception();
		} catch (Exception e) {
			return Subject.banner + "<h3>The ReCAPTCHA failed, sorry. Please click the BACK button on your browser and try again.</h3>";
		}
		
		int stars = 0;
		try {
			stars = Integer.parseInt(request.getParameter("Stars"));
			if (stars>0) Subject.addStarReport(stars);
		} catch (Exception e) {
		}
		String comments = request.getParameter("Comments");
		if (stars == 0 && (comments == null || comments.isEmpty())) return feedbackForm(user);   //(user==null?anonymousFeedbackForm():feedbackForm(user));
		
		String email = request.getParameter("Email");
		try {
			String regex = "^[\\w-_\\.+]*[\\w-_\\.]\\@([\\w]+\\.)+[\\w]+[\\w]$";
			if (!email.matches(regex)) email = null;
		} catch (Exception e) {
			email = null;
		}
	
		if (comments.length() > 0) {
			String message = "";
			if (stars>0) message += "Rating: " + stars + " stars.<br/>";
			else message += "No star rating provided.<br/>";
			message += "Email: " + (email==null?"not provided":email) + "<br/>";
			message += ("Comments:<br/>" + comments);
			if (email!=null && !email.isEmpty()) sendEmailToAdmin(message);
		}

		buf.append(Subject.banner);
		buf.append("<h2>Feedback Page</h2>");
		buf.append(new Date().toString() + "<p>");
		buf.append("Thank you for your feedback" + (stars>0?" (" + stars + " stars" + (stars==5?"!":"") + ").":"."));
		
		if (stars > 0) buf.append("<br>The average user rating for ResponDog is " + Subject.getAvgStars() + " stars (" + Subject.getNStarReports() + " user ratings).<p>");
		
		if (comments.length() > 0) {
			buf.append("Your comment: <font color=red>" + comments + "</font><p>");
		
			if (email==null) buf.append("We will review your comment, but we're unable to provide a response because you did not provide an email address.<p>");
			else buf.append("We will review your comment. Any response will be sent to " + email + ".<p>");
			buf.append("Feel free to email any additional comments to <a href=mailto:admin@chemvantage.org>admin@chemvantage.org</a><p>");
		}
		
		if (user.isAnonymous()) buf.append("<p><a href=Home>Return to the Home page</a><br>");
		return buf.toString();
	}

	boolean reCaptchaOK(HttpServletRequest request) throws Exception {
		OutputStreamWriter writer = null;
		BufferedReader reader = null;
		JsonObject captchaResponse = null;
		try {
			String queryString = "secret=" + Subject.getReCaptchaSecret() + "&response=" 
					+ request.getParameter("g-recaptcha-response") + "&remoteip=" + request.getRemoteAddr();
			URL u = new URL("https://www.google.com/recaptcha/api/siteverify");
			HttpURLConnection uc = (HttpURLConnection) u.openConnection();
			uc.setDoOutput(true);
			uc.setRequestMethod("POST");
			uc.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
			uc.setRequestProperty("Content-Length", String.valueOf(queryString.length()));

			writer = new OutputStreamWriter(uc.getOutputStream());
			writer.write(queryString);
			writer.flush();
			writer.close();

			// read & interpret the JSON response from Google
			reader = new BufferedReader(new InputStreamReader(uc.getInputStream()));
			captchaResponse = JsonParser.parseReader(reader).getAsJsonObject();
			reader.close();
		} catch (Exception e) {
		} finally {
			writer.close();
			reader.close();
		}
		return captchaResponse.get("success").getAsBoolean();
	}
	
	private void sendEmailToAdmin(String message) {
		Properties props = new Properties();
		Session session = Session.getDefaultInstance(props, null);

		try {
			Message msg = new MimeMessage(session);
			msg.setFrom(new InternetAddress("admin@chemvantage.org", "ResponDog"));
			msg.addRecipient(Message.RecipientType.TO,
					new InternetAddress("admin@chemvantage.org", "ResponDog"));
			msg.setSubject("ResponDog Feedback Report");
			msg.setContent(message,"text/html");
			Transport.send(msg);
		} catch (Exception e) {
		}
	}
}

