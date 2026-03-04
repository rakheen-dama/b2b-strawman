<#outputformat "plainText">
<#assign requiredActionsText><#if requiredActions??><#list requiredActions><#items as reqActionItem>${msg("requiredAction.${reqActionItem}")}<#sep>, </#sep></#items></#list></#if></#assign>
</#outputformat>

<#import "template.ftl" as layout>
<@layout.emailLayout>
<h1>Update Your Account</h1>
<p>Your administrator has requested that you update your ${realmName} account by performing the following action(s): <strong>${requiredActionsText}</strong>.</p>
<p>Click the button below to get started.</p>
<div class="btn-wrapper">
  <a href="${link}" class="btn-primary">Update Account</a>
</div>
<p class="muted">This link will expire in ${linkExpirationFormatter(linkExpiration)}.</p>
<p class="muted">If you are unaware of this request, you can safely ignore this email.</p>
</@layout.emailLayout>
