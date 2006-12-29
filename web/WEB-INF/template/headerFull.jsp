<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
   "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">

<%@ page import="org.openmrs.web.WebConstants" %>
<%
	pageContext.setAttribute("msg", session.getAttribute(WebConstants.OPENMRS_MSG_ATTR)); 
	pageContext.setAttribute("err", session.getAttribute(WebConstants.OPENMRS_ERROR_ATTR));
	session.removeAttribute(WebConstants.OPENMRS_MSG_ATTR);
	session.removeAttribute(WebConstants.OPENMRS_ERROR_ATTR);
%>

<html>
	<head>
		<openmrs:htmlInclude file="/openmrs.js" />
		<openmrs:htmlInclude file="/openmrs.css" />
		<openmrs:htmlInclude file="/style.css" />		
		<openmrs:htmlInclude file="/dwr/engine.js" />
		<openmrs:htmlInclude file="/dwr/interface/DWRAlertService.js" />

		<title><spring:message code="openmrs.title"/></title>
	</head>

<body leftmargin="0" topmargin="0" marginwidth="0" marginheight="0">
	<div id="pageBody">
		<div id="userBar">
			<openmrs:authentication>
				<c:if test="${authenticatedUser != null}">
					<span id="userLoggedInAs" class="firstChild">
						<spring:message code="header.logged.in"/> ${authenticatedUser.firstName} ${authenticatedUser.lastName}
					</span>
					<span id="userLogout">
						<a href='<%= request.getContextPath() %>/logout'><spring:message code="header.logout" /></a>
					</span>
				</c:if>
				<c:if test="${authenticatedUser == null}">
					<span id="userLoggedOut" class="firstChild">
						<spring:message code="header.logged.out"/>
					</span>
					<span id="userLogIn">
						<a href='<%= request.getContextPath() %>/login.htm'><spring:message code="header.login"/></a>
					</span>
				</c:if>
			</openmrs:authentication>
			
			<span id="userHelp">
				<a href='<%= request.getContextPath() %>/help.htm'><spring:message code="header.help"/></a>
			</span>
		</div>

		<div id="banner">
			<%@ include file="/WEB-INF/template/banner.jsp" %>
		</div>
		
		<div id="popupTray">
			&nbsp;
			<c:if test="${empty OPENMRS_DO_NOT_SHOW_PATIENT_SET}">
				<openmrs:portlet url="patientSet" id="patientSetPortlet" size="compact" parameters="selectedPatientId=|linkUrl=patientDashboard.form|allowRemove=true|allowClear=true|mutable=true|droppable=true|allowBatchEntry=true|allowActions=true"/>
			</c:if>
		</div>
		
		<openmrs:hasPrivilege privilege="View Navigation Menu">
			<div id="gutter">
				<%@ include file="/WEB-INF/template/gutter.jsp" %>
			</div>
		</openmrs:hasPrivilege>
		
		<div id="content">

			<script type="text/javascript">
				//// prevents users getting popup alerts when viewing pages
				var handler = function(ex) {
					if (typeof ex == "string")
						window.status = "DWR warning/error: " + ex;
				};
				DWREngine.setErrorHandler(handler);
				DWREngine.setWarningHandler(handler);
			</script>
								
			<openmrs:forEachAlert>
				<c:if test="${varStatus.first}"><div id="alertOuterBox"><div id="alertInnerBox"></c:if>
					<div class="alert">
						<a href="#markRead" onClick="return markAlertRead(this, '${alert.alertId}')" HIDEFOCUS class="markAlertRead">
							<img src="<%= request.getContextPath() %>/images/markRead.gif" alt='<spring:message code="Alert.mark"/>' title='<spring:message code="Alert.mark"/>'/>
						</a>
						${alert.text} ${alert.dateToExpire} <c:if test="${alert.satisfiedByAny}"><i class="smallMessage">(<spring:message code="Alert.mark.satisfiedByAny"/>)</i></c:if>
					</div>
				<c:if test="${varStatus.last}">
					</div>
					<div id="alertBar">
						<img src="<%= request.getContextPath() %>/images/alert.gif" align="center"/>
						<c:if test="${varStatus.count == 1}"><spring:message code="Alert.unreadAlert"/></c:if>
						<c:if test="${varStatus.count != 1}"><spring:message code="Alert.unreadAlerts" arguments="${varStatus.count}" /></c:if>
					</div>
					</div>
				</c:if>
			</openmrs:forEachAlert>

			<c:if test="${msg != null}">
				<div id="openmrs_msg"><spring:message code="${msg}" text="${msg}"/></div>
			</c:if>
			<c:if test="${err != null}">
				<div id="openmrs_error"><spring:message code="${err}" text="${err}"/></div>
			</c:if>