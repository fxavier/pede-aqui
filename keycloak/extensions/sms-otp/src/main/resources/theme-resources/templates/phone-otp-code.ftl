<#-- LAC-18: step 2 of the customer phone login (the SMS code). The number is only shown masked. -->
<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=true; section>
    <#if section = "header">
        ${msg("phoneOtpCodeTitle")}
    <#elseif section = "form">
        <form id="kc-phone-otp-code-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <p id="kc-phone-otp-masked">${msg("phoneOtpCodeIntro", (maskedPhone!''))}</p>
            <div class="${properties.kcFormGroupClass!}">
                <label for="code" class="${properties.kcLabelClass!}">${msg("phoneOtpCodeLabel")}</label>
                <input id="code" name="code" type="text" inputmode="numeric" pattern="[0-9]*" maxlength="${(codeLength!6)?c}"
                       autocomplete="one-time-code" autofocus class="${properties.kcInputClass!}" />
            </div>
            <div class="${properties.kcFormGroupClass!}">
                <button id="kc-phone-otp-verify" type="submit" name="action" value="verify"
                        class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}">
                    ${msg("phoneOtpVerify")}
                </button>
            </div>
            <div class="${properties.kcFormGroupClass!}">
                <button id="kc-phone-otp-resend" type="submit" name="action" value="resend" formnovalidate
                        class="${properties.kcButtonClass!} ${properties.kcButtonDefaultClass!} ${properties.kcButtonSecondaryClass!} ${properties.kcButtonBlockClass!}">
                    ${msg("phoneOtpResend")}
                </button>
                <button id="kc-phone-otp-change" type="submit" name="action" value="change" formnovalidate
                        class="${properties.kcButtonClass!} ${properties.kcButtonDefaultClass!} ${properties.kcButtonSecondaryClass!} ${properties.kcButtonBlockClass!}">
                    ${msg("phoneOtpChangeNumber")}
                </button>
            </div>
        </form>
    </#if>
</@layout.registrationLayout>
