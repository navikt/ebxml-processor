package no.nav.asyncreceivers.mailprocessor;

import jakarta.mail.Authenticator;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.net.URI;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Properties;

@Slf4j
public class MailSenderService {

    Properties props = new Properties();
    String username;
    String password;

    public MailSenderService(String username, String password) {
        props.put("mail.smtp.auth", true);
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "localhost");
        props.put("mail.smtp.port", "3025");
        props.put("mail.smtp.ssl.trust", "localhost");
        this.username = username;
        this.password = password;
    }
    public String sendMail() throws Exception {
        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress("peder@epost.com"));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse("thomas@epost.com"));
        Timestamp ts
                = new Timestamp(System.currentTimeMillis());
        Date date = new Date(ts.getTime());
        message.setSubject("Mail Subject " + date);

        String msg = "This message was sent " + date ;

        MimeBodyPart mimeBodyPart = new MimeBodyPart();
        mimeBodyPart.setContent(msg, "text/html; charset=utf-8");

        String msgStyled = "This is my <b style='color:red;'>bold-red email</b> using JavaMailer";
        MimeBodyPart mimeBodyPartWithStyledText = new MimeBodyPart();
        mimeBodyPartWithStyledText.setContent(msgStyled, "text/html; charset=utf-8");

        MimeBodyPart attachmentBodyPart = new MimeBodyPart();

        attachmentBodyPart.attachFile(getFile());

        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(mimeBodyPart);
        multipart.addBodyPart(mimeBodyPartWithStyledText);
        multipart.addBodyPart(attachmentBodyPart);

        message.setContent(multipart);

        Transport.send(message);

        log.info("Mail sent successfully", message.getSubject());
        return message.getSubject();
    }
    private File getFile() throws Exception {
        URI uri = this.getClass()
                .getClassLoader()
                .getResource("attachment.txt")
                .toURI();
        return new File(uri);
    }


}
