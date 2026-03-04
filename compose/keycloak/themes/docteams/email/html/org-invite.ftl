<#import "template.ftl" as layout>
<@layout.emailLayout>
<h1>You've been invited to join ${organization.name}</h1>
<#if firstName?? && lastName??>
<p>Hi ${firstName},</p>
</#if>
<p>You've been invited to join the <strong>${organization.name}</strong> organization on ${realmName}. Click the button below to accept the invitation and get started.</p>
<div class="btn-wrapper" style="text-align:center;padding:8px 0 16px 0;">
  <a href="${link}" class="btn-primary" style="display:inline-block;background-color:#0d9488;color:#ffffff;font-size:14px;font-weight:500;text-decoration:none;padding:10px 24px;border-radius:9999px;">Accept Invitation</a>
</div>
<p class="muted" style="font-size:13px;color:#64748b;">This link will expire in ${linkExpirationFormatter(linkExpiration)}.</p>
<p class="muted" style="font-size:13px;color:#64748b;">If you don't want to join this organization, you can safely ignore this email.</p>
</@layout.emailLayout>
