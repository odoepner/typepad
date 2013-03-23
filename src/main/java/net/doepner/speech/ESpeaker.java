package net.doepner.speech;

import java.io.IOException;

import net.doepner.lang.Language;
import net.doepner.lang.LanguageContext;


public class ESpeaker implements Speaker {

    private final LanguageContext ctx;

    public ESpeaker(LanguageContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void speak(String text) {
        try {
            Runtime.getRuntime().exec(new String[]{
                    "espeak", "-v", getVoice(text), text
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String getVoice(String text) {
        if (isZed(text)) {
            return "en";
        }
        return ctx.getLanguage().getCode();
    }

    private boolean isZed(String text) {
        return Language.ENGLISH == ctx.getLanguage()
                && "z".equalsIgnoreCase(text);
    }

}
