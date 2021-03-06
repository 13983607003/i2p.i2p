<%@page contentType="text/html" %>
<%@page pageEncoding="UTF-8"%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">

<html><head>
<%@include file="css.jsi" %>
<%=intl.title("config networking")%>
<script src="/js/ajax.js" type="text/javascript"></script>
<%@include file="summaryajax.jsi" %>
</head><body onload="initAjax()">

<%@include file="summary.jsi" %>

<jsp:useBean class="net.i2p.router.web.helpers.ConfigNetHelper" id="nethelper" scope="request" />
<jsp:setProperty name="nethelper" property="contextId" value="<%=(String)session.getAttribute(\"i2p.contextId\")%>" />
<h1><%=intl._t("I2P Network Configuration")%></h1>
<div class="main" id="config_network">
 <%@include file="confignav.jsi" %>

 <jsp:useBean class="net.i2p.router.web.helpers.ConfigNetHandler" id="formhandler" scope="request" />
<%@include file="formhandler.jsi" %>
 <form action="" method="POST">
 <input type="hidden" name="nonce" value="<%=pageNonce%>" >
 <input type="hidden" name="action" value="blah" >
 <h3 id="iptransport" class="tabletitle"><%=intl._t("IP and Transport Configuration")%>&nbsp;<a title="<%=intl._t("Help with router configuration")%>" href="/help#configurationhelp">[<%=intl._t("Configuration Help")%>]</a></h3>
 <table id="netconfig" class="configtable">
 <tr>
  <td class="infohelp">
 <b><%=intl._t("The default settings will work for most people.")%></b> 
 <%=intl._t("Changing these settings will restart your router.")%>
  </td>
 </tr>
 <tr>
  <th id="upnpconfig"><%=intl._t("UPnP Configuration")%>&nbsp;<a href="peers#upnp">[<%=intl._t("UPnP Status")%>]</a></th>
 </tr>
 <tr>
  <td>
    <label><input type="checkbox" class="optbox" name="upnp" value="true" <jsp:getProperty name="nethelper" property="upnpChecked" /> >
    <%=intl._t("Enable UPnP to open firewall ports")%></label>
  </td>
 </tr>
 <tr>
  <th id="ipconfig"><%=intl._t("IP Configuration")%></th>
 </tr>
 <tr>
  <td>
 <b class="suboption"><%=intl._t("Externally reachable hostname or IP address")%>:</b><br>
    <label><input type="radio" class="optbox" name="udpAutoIP" value="local,upnp,ssu" <%=nethelper.getUdpAutoIPChecked(3) %> >
    <%=intl._t("Use all auto-detect methods")%></label><br>
    <label><input type="radio" class="optbox" name="udpAutoIP" value="local,ssu" <%=nethelper.getUdpAutoIPChecked(4) %> >
    <%=intl._t("Disable UPnP IP address detection")%></label><br>
    <label><input type="radio" class="optbox" name="udpAutoIP" value="upnp,ssu" <%=nethelper.getUdpAutoIPChecked(5) %> >
    <%=intl._t("Ignore local interface IP address")%></label><br>
    <label><input type="radio" class="optbox" name="udpAutoIP" value="ssu" <%=nethelper.getUdpAutoIPChecked(0) %> >
    <%=intl._t("Use SSU IP address detection only")%></label><br>
    <label><input type="radio" class="optbox" name="udpAutoIP" value="hidden" <%=nethelper.getUdpAutoIPChecked(2) %> >
    <%=intl._t("Hidden mode - do not publish IP")%> <i><%=intl._t("(prevents participating traffic)")%></i></label><br>
    <label><input type="radio" class="optbox" name="udpAutoIP" value="fixed" <%=nethelper.getUdpAutoIPChecked(1) %> >
    <%=intl._t("Specify hostname or IP")%>:</label>
    <%=nethelper.getAddressSelector() %>
  </td>
 </tr>
 <tr>
  <th id="ipchange"><%=intl._t("Action when IP changes")%></th>
 </tr>
 <tr>
  <td>
    <label><input type="checkbox" class="optbox" name="laptop" value="true" <jsp:getProperty name="nethelper" property="laptopChecked" /> >
    <%=intl._t("Laptop mode - Change router identity and UDP port when IP changes for enhanced anonymity")%>
    (<i><%=intl._t("Experimental")%></i>)</label>
  </td>
 </tr>
 <tr>
  <th id="ipv4config"><%=intl._t("IPv4 Configuration")%></th>
 </tr>
 <tr>
  <td>
    <label><input type="checkbox" class="optbox" name="IPv4Firewalled" value="true" <jsp:getProperty name="nethelper" property="IPv4FirewalledChecked" /> >
    <%=intl._t("Disable inbound (Firewalled by Carrier-grade NAT or DS-Lite)")%></label>
  </td>
 </tr>
 <tr>
  <th id="ipv6config"><%=intl._t("IPv6 Configuration")%></th>
 </tr>
 <tr>
  <td>
    <label><input type="checkbox" class="optbox" name="IPv6Firewalled" value="true" <jsp:getProperty name="nethelper" property="IPv6FirewalledChecked" /> >
    <%=intl._t("Disable inbound (Firewalled by Carrier-grade NAT or DS-Lite)")%></label><br>
    <label><input type="radio" class="optbox" name="ipv6" value="false" <%=nethelper.getIPv6Checked("false") %> >
    <%=intl._t("Disable IPv6")%></label><br>
    <label><input type="radio" class="optbox" name="ipv6" value="enable" <%=nethelper.getIPv6Checked("enable") %> >
    <%=intl._t("Enable IPv6")%></label><br>
    <label><input type="radio" class="optbox" name="ipv6" value="preferIPv4" <%=nethelper.getIPv6Checked("preferIPv4") %> >
    <%=intl._t("Prefer IPv4 over IPv6")%></label><br>
    <label><input type="radio" class="optbox" name="ipv6" value="preferIPv6" <%=nethelper.getIPv6Checked("preferIPv6") %> >
    <%=intl._t("Prefer IPv6 over IPv4")%></label><br>
    <label><input type="radio" class="optbox" name="ipv6" value="only" <%=nethelper.getIPv6Checked("only") %> >
    <%=intl._t("Use IPv6 only (disable IPv4)")%>
    (<i><%=intl._t("Experimental")%></i>)</label>
  </td>
 </tr>
 <tr>
  <th id="udpconfig"><%=intl._t("UDP Configuration")%></th>
 </tr>
 <tr>
  <td class="infowarn">
   <b><%=intl._t("Do not reveal your port numbers to anyone, as they can be used to discover your IP address.")%></b>
  </td>
 </tr>
 <tr>
  <td>
 <b><%=intl._t("UDP port:")%></b>
 <input name ="udpPort" type="text" size="5" maxlength="5" value="<jsp:getProperty name="nethelper" property="configuredUdpPort" />" ><br>
 <label><input type="checkbox" class="optbox" name="disableUDP" value="disabled" <%=nethelper.getUdpDisabledChecked() %> >
 <%=intl._t("Completely disable")%> <i><%=intl._t("(select only if behind a firewall that blocks outbound UDP)")%></i></label>
  </td>
 </tr>
<% /********
<!-- let's keep this simple...
<input type="checkbox" class="optbox" name="requireIntroductions" value="true" <jsp:getProperty name="nethelper" property="requireIntroductionsChecked" /> />
 Require SSU introductions
 <i>(Enable if you cannot open your firewall)</i>
 </p><p>
 Current External UDP address: <i><jsp:getProperty name="nethelper" property="udpAddress" /></i><br>
-->
*********/ %>
 <tr>
  <th id="tcpconfig"><%=intl._t("TCP Configuration")%></th>
 </tr>
 <tr>
  <td>
 <b class="suboption"><%=intl._t("Externally reachable hostname or IP address")%>:</b><br>
    <label><input type="radio" class="optbox" name="ntcpAutoIP" value="true" <%=nethelper.getTcpAutoIPChecked(2) %> >
    <%=intl._t("Use auto-detected IP address")%>
    <i>(<%=intl._t("currently")%> <jsp:getProperty name="nethelper" property="udpIP" />)</i>
    <%=intl._t("if we are not firewalled")%></label><br>
    <label><input type="radio" class="optbox" name="ntcpAutoIP" value="always" <%=nethelper.getTcpAutoIPChecked(3) %> >
    <%=intl._t("Always use auto-detected IP address (Not firewalled)")%></label><br>
    <label><input type="radio" class="optbox" name="ntcpAutoIP" value="false" <%=nethelper.getTcpAutoIPChecked(1) %> >
    <%=intl._t("Specify hostname or IP")%>:</label>
    <input name ="ntcphost" type="text" size="16" value="<jsp:getProperty name="nethelper" property="ntcphostname" />" ><br>
    <label><input type="radio" class="optbox" name="ntcpAutoIP" value="false" <%=nethelper.getTcpAutoIPChecked(0) %> >
    <%=intl._t("Disable inbound (Firewalled)")%></label><br>
    <label><input type="radio" class="optbox" name="ntcpAutoIP" value="disabled" <%=nethelper.getTcpAutoIPChecked(4) %> >
    <%=intl._t("Completely disable")%> <i><%=intl._t("(select only if behind a firewall that throttles or blocks outbound TCP)")%></i></label>
  </td>
 </tr>
 <tr>
  <th id="externaltcp"><%=intl._t("Externally reachable TCP port")%></th>
 </tr>
 <tr>
  <td>
    <label><input type="radio" class="optbox" name="ntcpAutoPort" value="2" <%=nethelper.getTcpAutoPortChecked(2) %> >
    <%=intl._t("Use the same port configured for UDP")%>
    <i>(<%=intl._t("currently")%> <jsp:getProperty name="nethelper" property="udpPort" />)</i></label><br>
    <label><input type="radio" class="optbox" name="ntcpAutoPort" value="1" <%=nethelper.getTcpAutoPortChecked(1) %> >
    <%=intl._t("Specify Port")%>:</label>
    <input name ="ntcpport" type="text" size="5" maxlength="5" value="<jsp:getProperty name="nethelper" property="ntcpport" />" >
  </td>
 </tr>
 <tr>
  <td class="optionsave">
<input type="reset" class="cancel" value="<%=intl._t("Cancel")%>" >
<input type="submit" class="accept" name="save" value="<%=intl._t("Save changes")%>" >
  </td>
 </tr>
</table>

<% /********
<!-- let's keep this simple...
<input type="submit" name="recheckReachability" value="Check network reachability..." />
</p>
-->
*********/ %>
<% /********
      <!--
 <b>Dynamic Router Keys: </b>
 <input type="checkbox" class="optbox" name="dynamicKeys" value="true" <jsp:getProperty name="nethelper" property="dynamicKeysChecked" /> /><br>
 <p>
 This setting causes your router identity to be regenerated every time your IP address
 changes. If you have a dynamic IP this option can speed up your reintegration into
 the network (since people will have banned your old router identity), and, for
 very weak adversaries, help frustrate trivial
 <a href="http://www.i2p.net/how_threatmodel#intersection">intersection
 attacks</a> against the NetDB.  Your different router identities would only be
 'hidden' among other I2P users at your ISP, and further analysis would link
 the router identities further.</p>
 <p>Note that when I2P detects an IP address change, it will automatically
 initiate a restart in order to rekey and to disconnect from peers before they
 update their profiles - any long lasting client connections will be disconnected,
 though such would likely already be the case anyway, since the IP address changed.
 </p>
 <br>
-->
*********/ %>
</form></div></body></html>
