<#-- Child page that "inherits" the layout. The import path keeps the literal ".ftlh" because
     render() only appends the configured suffix to the top-level ref, never to imports. With the
     HTML output format forced on the Configuration, ${name} auto-escapes. -->
<#import "base.ftlh" as base>
<@base.layout>Hi ${name}</@base.layout>
