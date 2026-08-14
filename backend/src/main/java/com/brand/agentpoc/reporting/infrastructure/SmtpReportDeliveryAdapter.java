package com.brand.agentpoc.reporting.infrastructure;

import com.brand.agentpoc.config.AppProperties;
import com.brand.agentpoc.reporting.application.ReportDeliveryPort;
import com.brand.agentpoc.reporting.application.ReportDeliveryPort.DeliveryRequest;
import com.brand.agentpoc.reporting.application.ReportDeliveryPort.DeliveryResult;
import com.brand.agentpoc.reporting.application.TenantSmtpConfigRegistry;
import com.brand.agentpoc.reporting.application.TenantSmtpConfigRegistry.ResolvedSmtpConfig;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import javax.net.ssl.SSLException;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
public class SmtpReportDeliveryAdapter implements ReportDeliveryPort {

    private static final Pattern CONTROL_PATTERN = Pattern.compile("[\\r\\n\\u0000-\\u001F\\u007F]");

    private final TenantSmtpConfigRegistry configRegistry;
    private final AppProperties appProperties;

    public SmtpReportDeliveryAdapter(
            TenantSmtpConfigRegistry configRegistry,
            AppProperties appProperties
    ) {
        this.configRegistry = configRegistry;
        this.appProperties = appProperties;
    }

    @Override
    public DeliveryResult deliver(DeliveryRequest request) {
        if (request == null || request.tenantId() == null) {
            return DeliveryResult.permanent("DELIVERY_REQUEST_INVALID");
        }
        if (invalidHeader(request.subject()) || invalidHeader(request.deliveryKey())
                || request.recipientEmail() == null || request.recipientEmail().isBlank()
                || request.body() == null || request.body().isBlank()) {
            return DeliveryResult.permanent("DELIVERY_MESSAGE_INVALID");
        }
        ResolvedSmtpConfig config;
        try {
            config = configRegistry.resolve(request.tenantId()).orElse(null);
        } catch (RuntimeException exception) {
            return DeliveryResult.permanent("SMTP_CONFIG_INVALID");
        }
        if (config == null || !config.enabled()) {
            return DeliveryResult.permanent("SMTP_NOT_CONFIGURED");
        }
        if (request.body().getBytes(StandardCharsets.UTF_8).length > appProperties.getNotification().getMaxMessageBytes()) {
            return DeliveryResult.permanent("MESSAGE_TOO_LARGE");
        }

        try {
            JavaMailSenderImpl sender = sender(config);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            if (config.fromDisplayName() == null || config.fromDisplayName().isBlank()) {
                helper.setFrom(config.fromAddress());
            } else {
                helper.setFrom(config.fromAddress(), config.fromDisplayName());
            }
            helper.setTo(request.recipientEmail());
            helper.setSubject(request.subject());
            helper.setText(request.body(), false);
            message.setHeader("X-Report-Delivery-Key", request.deliveryKey());
            if (serializedSize(message) > appProperties.getNotification().getMaxMessageBytes()) {
                return DeliveryResult.permanent("MESSAGE_TOO_LARGE");
            }
            sender.send(message);
            return DeliveryResult.succeeded(null);
        } catch (MailAuthenticationException | MailParseException | MailPreparationException exception) {
            return DeliveryResult.permanent("SMTP_CONFIGURATION_REJECTED");
        } catch (MailSendException exception) {
            return classifySendFailure(exception);
        } catch (MailException exception) {
            return classifyCause(exception);
        } catch (MessagingException | java.io.IOException exception) {
            return DeliveryResult.permanent("DELIVERY_MESSAGE_INVALID");
        } catch (RuntimeException exception) {
            return DeliveryResult.unknown("SMTP_OUTCOME_UNKNOWN");
        }
    }

    private JavaMailSenderImpl sender(ResolvedSmtpConfig config) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.host());
        sender.setPort(config.port());
        sender.setUsername(config.username());
        sender.setPassword(config.password());
        boolean startTls = config.securityMode().name().equals("STARTTLS");
        String protocol = startTls ? "smtp" : "smtps";
        sender.setProtocol(protocol);
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.transport.protocol", protocol);
        properties.put("mail." + protocol + ".auth", "true");
        properties.put("mail." + protocol + ".connectiontimeout", timeoutMillis(
                appProperties.getNotification().getSmtpConnectionTimeout()));
        properties.put("mail." + protocol + ".timeout",
                timeoutMillis(appProperties.getNotification().getSmtpReadTimeout()));
        properties.put("mail." + protocol + ".writetimeout",
                timeoutMillis(appProperties.getNotification().getSmtpWriteTimeout()));
        if (startTls) {
            properties.put("mail.smtp.starttls.enable", "true");
            properties.put("mail.smtp.starttls.required", "true");
            properties.put("mail.smtp.ssl.checkserveridentity", "true");
        } else {
            properties.put("mail.smtps.ssl.enable", "true");
            properties.put("mail.smtps.ssl.checkserveridentity", "true");
        }
        return sender;
    }

    DeliveryResult classifySendFailure(MailSendException exception) {
        Integer code = smtpReplyCode(exception);
        if (code != null && code >= 400 && code < 500) {
            return DeliveryResult.retryable("SMTP_TEMPORARY_REJECTION", null);
        }
        if (code != null && code >= 500) {
            return DeliveryResult.permanent("SMTP_PERMANENT_REJECTION");
        }
        return classifyCause(exception);
    }

    private DeliveryResult classifyCause(Throwable throwable) {
        if (contains(throwable, MailAuthenticationException.class)) {
            return DeliveryResult.permanent("SMTP_AUTHENTICATION_FAILED");
        }
        if (contains(throwable, ConnectException.class)
                || contains(throwable, UnknownHostException.class)
                || containsSimpleName(throwable, "MailConnectException")) {
            return DeliveryResult.retryable("SMTP_CONNECTION_FAILED", null);
        }
        if (contains(throwable, SSLException.class)
                || contains(throwable, CertificateException.class)) {
            return DeliveryResult.permanent("SMTP_TLS_FAILED");
        }
        if (contains(throwable, SocketTimeoutException.class)) {
            return DeliveryResult.unknown("SMTP_TIMEOUT_UNKNOWN");
        }
        if (containsInvalidAddresses(throwable)
                || containsSimpleName(throwable, "AddressException")) {
            return DeliveryResult.permanent("SMTP_RECIPIENT_REJECTED");
        }
        return DeliveryResult.unknown("SMTP_OUTCOME_UNKNOWN");
    }

    private boolean containsInvalidAddresses(Throwable throwable) {
        for (Throwable current : relatedThrowables(throwable)) {
            if (current instanceof SendFailedException sendFailedException
                    && sendFailedException.getInvalidAddresses() != null
                    && sendFailedException.getInvalidAddresses().length > 0) {
                return true;
            }
        }
        return false;
    }

    private Integer smtpReplyCode(Throwable throwable) {
        for (Throwable current : relatedThrowables(throwable)) {
            try {
                Object value = current.getClass().getMethod("getReturnCode").invoke(current);
                if (value instanceof Integer code) {
                    return code;
                }
            } catch (ReflectiveOperationException ignored) {
                // The Jakarta Mail exception type does not expose a reply code.
            }
        }
        return null;
    }

    private boolean contains(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable current : relatedThrowables(throwable)) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsSimpleName(Throwable throwable, String simpleName) {
        for (Throwable current : relatedThrowables(throwable)) {
            if (current.getClass().getSimpleName().equals(simpleName)) {
                return true;
            }
        }
        return false;
    }

    private Iterable<Throwable> relatedThrowables(Throwable root) {
        if (root == null) {
            return java.util.List.of();
        }
        java.util.List<Throwable> result = new java.util.ArrayList<>();
        ArrayDeque<Throwable> queue = new ArrayDeque<>();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        queue.add(root);
        while (!queue.isEmpty()) {
            Throwable current = queue.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            result.add(current);
            if (current.getCause() != null) {
                queue.addLast(current.getCause());
            }
            if (current instanceof MessagingException messagingException
                    && messagingException.getNextException() != null) {
                queue.addLast(messagingException.getNextException());
            }
            if (current instanceof MailSendException mailSendException) {
                for (Map.Entry<Object, Exception> failed : mailSendException.getFailedMessages().entrySet()) {
                    if (failed.getValue() != null) {
                        queue.addLast(failed.getValue());
                    }
                }
            }
        }
        return result;
    }

    private int serializedSize(MimeMessage message) throws MessagingException, java.io.IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        message.writeTo(output);
        return output.size();
    }

    private boolean invalidHeader(String value) {
        return value == null || value.isBlank() || CONTROL_PATTERN.matcher(value).find();
    }

    private int timeoutMillis(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return 1;
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1, duration.toMillis()));
    }
}
