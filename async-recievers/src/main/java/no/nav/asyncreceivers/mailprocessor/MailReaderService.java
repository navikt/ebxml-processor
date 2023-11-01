package no.nav.asyncreceivers.mailprocessor;

import jakarta.mail.*;
import jakarta.mail.internet.MimeMultipart;
import lombok.extern.slf4j.Slf4j;
import no.nav.asyncreceivers.kafka.KafkaProducer;

import java.util.ArrayList;
import java.util.Properties;
@Slf4j
public class MailReaderService {
    Properties props = new Properties();
    String username;
    String password;

    public MailReaderService(String username, String password){
        //props.put("mail.pop3.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        props.put("mail.pop3.socketFactory.fallback", "false");
        //props.put("mail.pop3.socketFactory.port", "3995");
        props.put("mail.pop3.socketFactory.port", "3110");
        //props.put("mail.pop3.port", "3995");
        props.put("mail.pop3.port", "3110");
        props.put("mail.pop3.host", "localhost");
        props.put("mail.store.protocol", "pop3");
        //props.put("mail.pop3.ssl.protocols", "TLSv1.2");

        this.username = username;
        this.password = password;
    }

    public ArrayList<String> readMail(String username, String password) throws Exception {
        Authenticator auth = new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        };


        Session session = Session.getDefaultInstance(props, auth);
        Store store = session.getStore("pop3");
        store.connect("localhost", username, password);
        Folder inbox = store.getFolder("INBOX");
        inbox.open(Folder.READ_ONLY);

        Message[] messages = inbox.getMessages();
        log.info("Number of messages in inbox: " + messages.length);
        ArrayList<String> messageSubjects = new ArrayList<>();
        for (Message message : messages) {
            log.info("Message found in inbox");
            log.info("Subject: " + message.getSubject());
            log.info("From: " + message.getFrom()[0]);
            MimeMultipart multipart = (MimeMultipart) message.getContent();
            String bodypart = multipart.getBodyPart(0).getContent().toString();
            log.info("Text: " + bodypart);
            messageSubjects.add(message.getSubject());

            KafkaProducer kafkaProducer = new KafkaProducer(KafkaProducer.getKafkaTemplate());
            kafkaProducer.sendDeenvelopedMessage(bodypart);
        }

        inbox.close(false);
        store.close();

        return messageSubjects;
    }
}
