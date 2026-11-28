package io.github.wlk.jlibrus.server.it;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Mailer whose SMTP server refuses chosen recipients; enabled only by {@link PartialFailureIT.Profile}. */
@Alternative
@ApplicationScoped
public class FlakyMailer implements Mailer {

    private final Set<String> refused = ConcurrentHashMap.newKeySet();
    private final List<Mail> sent = new CopyOnWriteArrayList<>();

    void refuse(String address) {
        refused.add(address);
    }

    void accept(String address) {
        refused.remove(address);
    }

    List<Mail> sentTo(String address) {
        return sent.stream().filter(mail -> mail.getTo().contains(address)).toList();
    }

    @Override
    public void send(Mail... mails) {
        for (Mail mail : mails) {
            if (mail.getTo().stream().anyMatch(refused::contains)) {
                throw new IllegalStateException("SMTP server refused " + mail.getTo());
            }
            sent.add(mail);
        }
    }
}
