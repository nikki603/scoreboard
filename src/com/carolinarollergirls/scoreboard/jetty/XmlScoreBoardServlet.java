package com.carolinarollergirls.scoreboard.jetty;
/**
 * Copyright (C) 2008-2012 Mr Temper <MrTemper@CarolinaRollergirls.com>
 *
 * This file is part of the Carolina Rollergirls (CRG) ScoreBoard.
 * The CRG ScoreBoard is licensed under either the GNU General Public
 * License version 3 (or later), or the Apache License 2.0, at your option.
 * See the file COPYING for details.
 */

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.awt.*;
import java.awt.image.*;

import javax.imageio.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.jdom.*;
import org.jdom.output.*;
import org.jdom.xpath.*;

import com.carolinarollergirls.scoreboard.*;
import com.carolinarollergirls.scoreboard.xml.*;
import com.carolinarollergirls.scoreboard.event.*;
import com.carolinarollergirls.scoreboard.model.*;
import com.carolinarollergirls.scoreboard.policy.*;
import com.carolinarollergirls.scoreboard.defaults.*;

import org.apache.commons.fileupload.*;
import org.apache.commons.fileupload.servlet.*;

public class XmlScoreBoardServlet extends AbstractXmlServlet
{
	public String getPath() { return "/XmlScoreBoard"; }

	protected void getAll(HttpServletRequest request, HttpServletResponse response) throws IOException,JDOMException {
		response.setContentType("text/xml");
		prettyXmlOutputter.output(scoreBoardModel.getXmlScoreBoard().getDocument(), response.getOutputStream());
		response.setStatus(HttpServletResponse.SC_OK);
	}

	protected void get(HttpServletRequest request, HttpServletResponse response) throws IOException,JDOMException {
		XmlListener listener = getXmlListenerForRequest(request);
		if (null == listener) {
			registrationKeyNotFound(request, response);
			return;
		}

		if (null == listener.getDocumentFilter()) {
			listener.setDocumentFilter(normalizedTimeDocumentFilter);
		}
		Document d = listener.getDocument(LONGPOLL_TIMEOUT);
		if (null == d) {
			response.sendError(HttpServletResponse.SC_NOT_MODIFIED);
		} else {
			if (debugGet)
				ScoreBoardManager.printMessage("GET to "+listener.getKey()+"\n"+prettyXmlOutputter.outputString(d));
			response.setContentType("text/xml");
			rawXmlOutputter.output(d, response.getOutputStream());
			response.setStatus(HttpServletResponse.SC_OK);
		}
	}

	protected void set(HttpServletRequest request, HttpServletResponse response) throws IOException,JDOMException {
		XmlListener listener = getXmlListenerForRequest(request);
		if (null == listener) {
			registrationKeyNotFound(request, response);
			return;
		}

		Document requestDocument = editor.toDocument(request.getInputStream());
		if (null == requestDocument) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST);
			return;
		}

		if (debugSet)
			ScoreBoardManager.printMessage("SET from "+listener.getKey()+"\n"+rawXmlOutputter.outputString(requestDocument));

		scoreBoardModel.getXmlScoreBoard().mergeDocument(requestDocument);

		response.setContentType("text/plain");
		response.setStatus(HttpServletResponse.SC_OK);
	}

	protected void setNormalizedTime(HttpServletRequest request, HttpServletResponse response, Boolean nt) throws ServletException,IOException {
		XmlListener listener = getXmlListenerForRequest(request);
		if (nt)
			listener.setDocumentFilter(normalizedTimeDocumentFilter);
		else
			listener.setDocumentFilter(millisecondTimeDocumentFilter);
		response.setContentType("text/plain");
		response.setStatus(HttpServletResponse.SC_OK);
	}
 
	protected void setDebug(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {
		String get = request.getParameter("get");
		String set = request.getParameter("set");

		if (null != get) {
			if (get.equals("1") || get.equalsIgnoreCase("true"))
				debugGet = true;
			else if (get.equals("0") || get.equalsIgnoreCase("false"))
				debugGet = false;
		}
		if (null != set) {
			if (set.equals("1") || set.equalsIgnoreCase("true"))
				debugSet = true;
			else if (set.equals("0") || set.equalsIgnoreCase("false"))
				debugSet = false;
		}

		response.setContentType("text/plain");
		response.getWriter().println("Debug /get : "+debugGet);
		response.getWriter().println("Debug /set : "+debugSet);
		response.setStatus(HttpServletResponse.SC_OK);
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {
		super.doPost(request, response);

		try {
			if ("/set".equals(request.getPathInfo()))
				set(request, response);
			else if (!response.isCommitted())
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
		} catch ( JDOMException jE ) {
			ScoreBoardManager.printMessage("XmlScoreBoardServlet ERROR: "+jE.getMessage());
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {
		super.doGet(request, response);

		try {
			if ("/get".equals(request.getPathInfo()))
				get(request, response);
			else if ("/debug".equals(request.getPathInfo()))
				setDebug(request, response);
			else if ("/normalizedTime".equals(request.getPathInfo()))
				setNormalizedTime(request, response, true);
			else if ("/millisecondTime".equals(request.getPathInfo()))
				setNormalizedTime(request, response, false);
			else if (request.getPathInfo().endsWith(".xml"))
				getAll(request, response);
			else if (!response.isCommitted())
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
		} catch ( JDOMException jE ) {
			ScoreBoardManager.printMessage("XmlScoreBoardServlet ERROR: "+jE.getMessage());
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	protected boolean debugGet = false;
	protected boolean debugSet = false;
	private int LONGPOLL_TIMEOUT = 10000;
	private TimeDocumentFilter normalizedTimeDocumentFilter = new TimeDocumentFilter(true);
	private TimeDocumentFilter millisecondTimeDocumentFilter = new TimeDocumentFilter(true);

	protected class TimeDocumentFilter implements SleepingQueueXmlScoreBoardListener.DocumentFilter {
		public TimeDocumentFilter(Boolean Normalized) {
			normalized = Normalized;

			try {
				millisecondExpression = XPath.newInstance("//ScoreBoard/Clock/Time");
				normalizedExpression = XPath.newInstance("//ScoreBoard/Clock/TimeNormalized");
			} catch (Exception e) {
				System.out.printf("ERROR!! Filter: %s\n", e.toString());
			}
		}

		// Return true if empty
		private Boolean removeElement(Element e) {
			Parent p = e.getParent();
			p.removeContent(e);
			p = p.getParent();
			while (null != p) {
				if (p.getContentSize() == 1)
					p = p.getParent();
				else
					return false;
			}
			return true;
		}

		public Document Filter(Document d) {
			java.util.List ms = null, ns = null;
			Document d2 = (Document)d.clone();
			try {
				ms = millisecondExpression.selectNodes(d2.getRootElement());
				ns = normalizedExpression.selectNodes(d2.getRootElement());
			} catch (Exception e) {
				// process entire document if error
				return d;
			}
			if (ms.size() == 0 && ns.size() == 0)
				return d;
			if (normalized) {
				// Strip out Millisecond Time
				for (Object o : ms) {
					Element e = (Element)o;
					if (removeElement(e))
						return null;
				}
				// Rename NormalizedTime to Time
				for (Object o : ns) {
					Element e = (Element)o;
					e.setName("Time");
				}
			} else {
				// Strip out NormalizedTime
				for (Object o : ns) {
					Element e = (Element)o;
					if (removeElement(e))
						return null;
				}
			}
			return d2;
		}

		private XPath millisecondExpression;
		private XPath normalizedExpression;
		private Boolean normalized;
	}
}        
