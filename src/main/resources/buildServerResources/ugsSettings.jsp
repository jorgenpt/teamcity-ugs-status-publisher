<%@ page import="jetbrains.buildServer.web.openapi.PlaceId" %>
<%@ include file="/include.jsp" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="util" uri="/WEB-INF/functions/util" %>

<jsp:useBean id="keys" class="no.tjer.teamcity.ugsStatusPublisher.UgsConstants"/>

<tr>
  <th><label for="${keys.serverUrl}">Server URL:<l:star/></label></th>
  <td>
    <props:textProperty name="${keys.serverUrl}" className="longField"/>
    <span class="error" id="error_${keys.serverUrl}"></span>
  </td>
</tr>

<tr>
  <th><label for="${keys.authUser}">Username:</label></th>
  <td>
    <props:textProperty name="${keys.authUser}" className="mediumField"/>
    <span class="error" id="error_${keys.authUser}"></span>
  </td>
</tr>

<tr>
  <th><label for="${keys.authPassword}">Password:</label></th>
  <td>
    <props:passwordProperty name="${keys.authPassword}" className="mediumField"/>
    <span class="error" id="error_${keys.authPassword}"></span>
  </td>
</tr>

<tr>
  <th><label for="${keys.project}">Project:<l:star/></label></th>
  <td>
    <props:textProperty name="${keys.project}" className="mediumField"/>
    <span class="error" id="error_${keys.project}"></span>
  </td>
</tr>

<tr>
  <th><label for="${keys.badgeName}">Badge name:<l:star/></label></th>
  <td>
    <props:textProperty name="${keys.badgeName}" className="mediumField"/>
    <span class="error" id="error_${keys.badgeName}"></span>
  </td>
</tr>

<c:if test="${testConnectionSupported}">
  <script>
    $j(document).ready(function () {
      PublisherFeature.showTestConnection();
    });
  </script>
</c:if>

<style type="text/css">
  .tc-icon_ugs {
    cursor: pointer;
  }

  a > .tc-icon_ugs_disabled {
    text-decoration: none;
  }
</style>
