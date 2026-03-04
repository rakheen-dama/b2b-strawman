<#import "template.ftl" as layout>
<@layout.emailLayout>
<h1>Verify your email address</h1>
<p>Someone has created a ${realmName} account with this email address. If this was you, click the button below to verify your email.</p>
<div class="btn-wrapper">
  <a href="${link}" class="btn-primary">Verify Email Address</a>
</div>
<p class="muted">This link will expire in ${linkExpirationFormatter(linkExpiration)}.</p>
<p class="muted">If you didn't create this account, you can safely ignore this email.</p>
</@layout.emailLayout>
