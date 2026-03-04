<#outputformat "plainText">
<#assign requiredActionsText><#if requiredActions??><#list requiredActions><#items as reqActionItem>${msg("requiredAction.${reqActionItem}")}<#sep>, </#sep></#items></#list></#if></#assign>
</#outputformat>

<#import "template.ftl" as layout>
<@layout.emailLayout>
<h1>Update Your Account</h1>
<p>Your administrator has requested that you update your ${realmName} account by performing the following action(s): <strong>${requiredActionsText}</strong>.</p>
<p>Click the button below to get started.</p>
<div class="btn-wrapper" style="text-align:center;padding:8px 0 16px 0;">
  <a href="${link}" class="btn-primary" style="display:inline-block;background-color:#0d9488;color:#ffffff;font-size:14px;font-weight:500;text-decoration:none;padding:10px 24px;border-radius:9999px;">Update Account</a>
</div>
<p class="muted" style="font-size:13px;color:#64748b;">This link will expire in ${linkExpirationFormatter(linkExpiration)}.</p>
<p class="muted" style="font-size:13px;color:#64748b;">If you are unaware of this request, you can safely ignore this email.</p>
</@layout.emailLayout>
