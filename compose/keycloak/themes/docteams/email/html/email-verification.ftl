<#import "template.ftl" as layout>
<@layout.emailLayout>
<h1>Verify your email address</h1>
<p>Someone has created a ${realmName} account with this email address. If this was you, click the button below to verify your email.</p>
<div class="btn-wrapper" style="text-align:center;padding:8px 0 16px 0;">
  <a href="${link}" class="btn-primary" style="display:inline-block;background-color:#0d9488;color:#ffffff;font-size:14px;font-weight:500;text-decoration:none;padding:10px 24px;border-radius:9999px;">Verify Email Address</a>
</div>
<p class="muted" style="font-size:13px;color:#64748b;">This link will expire in ${linkExpirationFormatter(linkExpiration)}.</p>
<p class="muted" style="font-size:13px;color:#64748b;">If you didn't create this account, you can safely ignore this email.</p>
</@layout.emailLayout>
