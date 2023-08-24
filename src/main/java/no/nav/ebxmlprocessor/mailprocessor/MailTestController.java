package no.nav.ebxmlprocessor.mailprocessor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;

@RestController
@Slf4j
@RequestMapping("/mailtest")
public class MailTestController {
    @GetMapping("/send")
    public ResponseEntity<String> get() {
        try {
            return ResponseEntity.ok().body(new MailSenderService("peder@epost.com", "peder")
                    .sendMail());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }
    @GetMapping("/inbox")
    public ResponseEntity<ArrayList<String>> getInbox() {
        try {
            return ResponseEntity.ok().body(new MailReaderService("thomas@epost.com", "thomas")
                    .readMail("thomas@epost.com", "thomas"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(new ArrayList<String>());
        }
    }
}
