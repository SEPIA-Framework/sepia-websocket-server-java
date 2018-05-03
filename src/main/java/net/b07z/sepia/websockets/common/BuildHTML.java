package net.b07z.sepia.websockets.common;
import static j2html.TagCreator.article;
import static j2html.TagCreator.b;
import static j2html.TagCreator.p;
import static j2html.TagCreator.span;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Class to help building HTML code.
 * 
 * @author Florian Quirin
 *
 */
public class BuildHTML {
	
    //Builds a HTML element with a sender-name, a message, and a timestamp,
    public static String getMessageFromSender(String sender, String message) {
        return article().with(
                b(sender + " says:"),
                p(message),
                span().withClass("timestamp").withText(new SimpleDateFormat("HH:mm:ss").format(new Date()))
        ).render();
    }
}
