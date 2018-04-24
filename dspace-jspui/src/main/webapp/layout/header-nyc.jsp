<%--

--%>
<%@ page contentType="text/html;charset=UTF-8"%>

<%@ page import="org.dspace.core.ConfigurationManager" %>
<%@ page import="org.springframework.security.core.context.SecurityContextHolder"%>
<%@ page import="org.springframework.security.core.Authentication"%>

<%
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    String user = (String) authentication.getPrincipal();
    String logoutURL = ConfigurationManager.getProperty("logout.url");
%>

<link rel="stylesheet" href="<%= request.getContextPath() %>/static/css/nyc-gov.css" type="text/css" />

<div class="nycidm-header">
    <div class="upper-header-black">
        <div class="container-nycidm">
            <span class="upper-header-left">
            <!-- The logo should link to nyc-dev-web.csc.nycnet for development environments and nyc-stg-web.csc.nycnet for staging environments. -->
    		<a href="http://www1.nyc.gov/"><img class="small-nyc-logo" alt="" src="<%= request.getContextPath() %>/static/img/nyc_white@x2.png"></a>
    		<img class="vert-divide" alt="" src="<%= request.getContextPath() %>/static/img/upper-header-divider.gif">
                <span class="upper-header-black-title">
                    Government Publications Portal
                </span>
            </span>

            <% if (user.equals("anonymousUser")) { %>
                <span class="upper-header-right">
                    <span class="upper-header-a">
                        <a href="<%= request.getContextPath() %>/saml/login">Log In</a>
                    </span>
                </span>
            <% } else { %>
                <span class="upper-header-right">
                    <span class="upper-header-a">
                        <a id="logout" href="<%= request.getContextPath() %>/saml-logout">Log Out</a>
                    </span>
                </span>
            <% } %>
        </div>
    </div>
</div>

<script type="text/javascript">
    "use strict";

    function idpLogout(afterTimeout) {
        var logoutTab = window.open("<%= logoutURL %>");
        logoutTab.opener =- null;
        setTimeout(function() {
            logoutTab.close();
            if (typeof afterTimeout === "function") {
                afterTimeout();
            }
        }, 1000);
    }

    $("#logout").click(function (e) {
        e.preventDefault();
        var logoutPage = this.href;
        idpLogout(function() {
            window.location =logoutPage;
        });
    });
</script>