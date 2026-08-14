package com.brand.agentpoc.reporting.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.brand.agentpoc.config.AppProperties;
import com.brand.agentpoc.reporting.application.ReportDeliveryPort.DeliveryResult;
import com.brand.agentpoc.reporting.application.ReportDeliveryPort.Outcome;
import com.brand.agentpoc.reporting.application.TenantSmtpConfigRegistry;
import jakarta.mail.Address;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.InternetAddress;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import javax.net.ssl.SSLHandshakeException;
import java.util.Map;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;

class SmtpReportDeliveryAdapterTest {

    private final SmtpReportDeliveryAdapter adapter = new SmtpReportDeliveryAdapter(
            org.mockito.Mockito.mock(TenantSmtpConfigRegistry.class), new AppProperties());

    @Test
    void classifiesNestedFourHundredReplyAsRetryable() {
        DeliveryResult result = adapter.classifySendFailure(mailFailure(450));

        assertThat(result.outcome()).isEqualTo(Outcome.RETRYABLE_FAILURE);
        assertThat(result.errorCode()).isEqualTo("SMTP_TEMPORARY_REJECTION");
    }

    @Test
    void classifiesNestedFiveHundredReplyAsPermanent() {
        DeliveryResult result = adapter.classifySendFailure(mailFailure(550));

        assertThat(result.outcome()).isEqualTo(Outcome.PERMANENT_FAILURE);
        assertThat(result.errorCode()).isEqualTo("SMTP_PERMANENT_REJECTION");
    }

    @Test
    void classifiesPreConnectionFailureAsRetryable() {
        DeliveryResult result = adapter.classifySendFailure(mailFailure(new ConnectException("unavailable")));

        assertThat(result.outcome()).isEqualTo(Outcome.RETRYABLE_FAILURE);
        assertThat(result.errorCode()).isEqualTo("SMTP_CONNECTION_FAILED");
    }

    @Test
    void classifiesAuthenticationAndTlsFailuresAsPermanent() {
        DeliveryResult authentication = adapter.classifySendFailure(
                mailFailure(new MailAuthenticationException("rejected")));
        DeliveryResult tls = adapter.classifySendFailure(
                mailFailure(new SSLHandshakeException("certificate rejected")));

        assertThat(authentication.outcome()).isEqualTo(Outcome.PERMANENT_FAILURE);
        assertThat(authentication.errorCode()).isEqualTo("SMTP_AUTHENTICATION_FAILED");
        assertThat(tls.outcome()).isEqualTo(Outcome.PERMANENT_FAILURE);
        assertThat(tls.errorCode()).isEqualTo("SMTP_TLS_FAILED");
    }

    @Test
    void classifiesAmbiguousTimeoutAndGenericSendFailureAsUnknown() {
        DeliveryResult timeout = adapter.classifySendFailure(
                mailFailure(new SocketTimeoutException("final response missing")));
        DeliveryResult generic = adapter.classifySendFailure(
                mailFailure(new SendFailedException("send stage unknown")));

        assertThat(timeout.outcome()).isEqualTo(Outcome.UNKNOWN);
        assertThat(timeout.errorCode()).isEqualTo("SMTP_TIMEOUT_UNKNOWN");
        assertThat(generic.outcome()).isEqualTo(Outcome.UNKNOWN);
        assertThat(generic.errorCode()).isEqualTo("SMTP_OUTCOME_UNKNOWN");
    }

    @Test
    void classifiesExplicitInvalidRecipientAsPermanent() throws Exception {
        Address[] invalid = {new InternetAddress("invalid@example.com")};
        SendFailedException failure = new SendFailedException(
                "invalid recipient", null, null, null, invalid);

        DeliveryResult result = adapter.classifySendFailure(mailFailure(failure));

        assertThat(result.outcome()).isEqualTo(Outcome.PERMANENT_FAILURE);
        assertThat(result.errorCode()).isEqualTo("SMTP_RECIPIENT_REJECTED");
    }

    private MailSendException mailFailure(int replyCode) {
        SMTPSendFailedException nested = new SMTPSendFailedException(
                "DATA", replyCode, "safe test reply", null, null, null, null);
        return mailFailure(nested);
    }

    private MailSendException mailFailure(Exception nested) {
        return new MailSendException(Map.of("message", nested));
    }
}
