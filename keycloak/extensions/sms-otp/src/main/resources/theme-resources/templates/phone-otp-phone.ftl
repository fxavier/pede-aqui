<#-- LAC-18: step 1 of the customer phone login. Theme-agnostic (only template.ftl + theme CSS classes). -->
<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=true; section>
    <#if section = "header">
        ${msg("phoneOtpTitle")}
    <#elseif section = "form">
        <form id="kc-phone-otp-phone-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <div class="${properties.kcFormGroupClass!}">
                <label for="phone" class="${properties.kcLabelClass!}">${msg("phoneOtpPhoneLabel")}</label>
                <input id="phone" name="phone" type="tel" inputmode="tel" autocomplete="tel" autofocus
                       class="${properties.kcInputClass!}" value="${(phone!'')}" placeholder="+258 84 123 4567" />
                <p class="${properties.kcInputHelperTextBeforeClass!}">${msg("phoneOtpPhoneHelp")}</p>
            </div>
            <div class="${properties.kcFormGroupClass!}">
                <button id="kc-phone-otp-send" type="submit" name="action" value="send"
                        class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}">
                    ${msg("phoneOtpSend")}
                </button>
            </div>
            <div class="${properties.kcFormGroupClass!}">
                <button id="kc-phone-otp-use-password" type="submit" name="action" value="password" formnovalidate
                        class="${properties.kcButtonClass!} ${properties.kcButtonDefaultClass!} ${properties.kcButtonSecondaryClass!} ${properties.kcButtonBlockClass!}">
                    ${msg("phoneOtpUsePassword")}
                </button>
            </div>
        </form>
    </#if>
</@layout.registrationLayout>
