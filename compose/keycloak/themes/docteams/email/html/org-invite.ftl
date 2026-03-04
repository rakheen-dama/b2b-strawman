<#import "template.ftl" as layout>
<@layout.emailLayout>
<h1>You've been invited to join ${organization.name}</h1>
<#if firstName?? && lastName??>
<p>Hi ${firstName},</p>
</#if>
<p>You've been invited to join the <strong>${organization.name}</strong> organization on ${realmName}. Click the button below to accept the invitation and get started.</p>
<div class="btn-wrapper">
  <a href="${link}" class="btn-primary">Accept Invitation</a>
</div>
<p class="muted">This link will expire in ${linkExpirationFormatter(linkExpiration)}.</p>
<p class="muted">If you don't want to join this organization, you can safely ignore this email.</p>
</@layout.emailLayout>
