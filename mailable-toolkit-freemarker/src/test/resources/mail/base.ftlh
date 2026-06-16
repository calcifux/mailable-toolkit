<#-- Reusable layout. FreeMarker has no {% extends %}; the shell is exposed as a macro and the
     child template supplies the body via <#nested>. -->
<#macro layout>
<html>
<body>
<div id="content"><#nested></div>
</body>
</html>
</#macro>
