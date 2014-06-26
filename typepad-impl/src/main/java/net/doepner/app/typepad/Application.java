package net.doepner.app.typepad;

import net.doepner.app.typepad.action.ActionDescriptions;
import net.doepner.app.typepad.action.EmailAction;
import net.doepner.app.typepad.action.ResizeFont;
import net.doepner.app.typepad.action.SpeakAll;
import net.doepner.app.typepad.action.SpeakWord;
import net.doepner.app.typepad.action.SwitchBuffer;
import net.doepner.app.typepad.action.SwitchLanguage;
import net.doepner.app.typepad.action.SwitchSpeaker;
import net.doepner.app.typepad.ui.SwingFrame;
import net.doepner.file.PathHelper;
import net.doepner.file.PathType;
import net.doepner.file.StdPathHelper;
import net.doepner.file.TextBuffers;
import net.doepner.file.TextFiles;
import net.doepner.lang.CanadianDeutsch;
import net.doepner.lang.LanguageChanger;
import net.doepner.log.Log;
import net.doepner.log.LogProvider;
import net.doepner.log.Slf4jLogProvider;
import net.doepner.mail.Emailer;
import net.doepner.mail.NoEmailer;
import net.doepner.mail.SmtpConfig;
import net.doepner.mail.SmtpEmailer;
import net.doepner.resources.CascadingResourceFinder;
import net.doepner.resources.ClasspathFinder;
import net.doepner.resources.FileFinder;
import net.doepner.resources.GoogleTranslateDownload;
import net.doepner.resources.ImageCollector;
import net.doepner.resources.ResourceFinder;
import net.doepner.resources.StdImageCollector;
import net.doepner.resources.StdResourceCollector;
import net.doepner.speech.AudioFileSpeaker;
import net.doepner.speech.ESpeaker;
import net.doepner.speech.ManagedSpeakers;
import net.doepner.text.TextListener;
import net.doepner.text.TextModel;
import net.doepner.text.WordExtractor;
import net.doepner.text.WordProvider;
import net.doepner.ui.Editor;
import net.doepner.ui.EmailDialog;
import net.doepner.ui.SwingEditor;
import net.doepner.ui.SwingEmailDialog;
import net.doepner.ui.text.AlphaNumStyler;
import net.doepner.ui.text.DocTextModel;
import net.doepner.ui.text.FontChooser;
import net.doepner.ui.text.TextStyler;

import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.StyledDocument;
import java.awt.Dimension;
import java.awt.Font;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static net.doepner.log.Log.Level.error;

/**
 * Wires up the application
 */
public class Application {

    private final Log log;

    private final SwingFrame frame;

    public Application() {

        final LogProvider logProvider = new Slf4jLogProvider();
        log = logProvider.getLog(getClass());

        Thread.setDefaultUncaughtExceptionHandler(
                new Thread.UncaughtExceptionHandler() {
                    public void uncaughtException(Thread t, Throwable e) {
                        log.as(error, e);
                    }
                });

        final String appName = "Typepad";
        final Path homeDir = Paths.get(System.getProperty("user.home"));

        final LanguageChanger languageChanger = new CanadianDeutsch(logProvider);

        final PathHelper pathHelper =
                new StdPathHelper(appName, homeDir, logProvider);

        final ResourceFinder resourceFinder = new CascadingResourceFinder(
                new FileFinder(pathHelper, logProvider),
                new ClasspathFinder(logProvider),
                new GoogleTranslateDownload(logProvider, pathHelper));

        final ManagedSpeakers managedSpeakers = new ManagedSpeakers(
                logProvider,
                new AudioFileSpeaker("google-translate",
                        logProvider, resourceFinder, languageChanger),
                new ESpeaker(languageChanger, "robbi")
        );

        final ImageCollector imageCollector = new StdImageCollector(
                new StdResourceCollector(resourceFinder), 10, logProvider);

        final TextBuffers buffers = new TextFiles(logProvider, pathHelper);

        final Path configFile = pathHelper.findOrCreate("email.properties", PathType.FILE);
        final Emailer emailer = createEmailer(logProvider, configFile);

        final StyledDocument doc = new DefaultStyledDocument();
        doc.addDocumentListener(new TextStyler(new AlphaNumStyler()));

        final JTextPane textPane = new JTextPane(doc);
        textPane.setFont(new Font("serif", Font.PLAIN, 40));

        final Editor editor = new SwingEditor(textPane);
        final TextModel textModel = new DocTextModel(doc);

        final WordProvider wordProvider = new WordExtractor(logProvider, textModel);

        final EmailDialog emailDialog = new SwingEmailDialog(resourceFinder);

        final Dimension frameSize = new Dimension(800, 600);
        final Dimension imageSize = new Dimension(100, 100);

        frame = new SwingFrame(logProvider,
                appName, editor, languageChanger, wordProvider, imageCollector,
                new FontChooser(textPane),
                new JScrollPane(textPane),
                imageSize, frameSize,
                new ActionDescriptions(),
                new SwitchLanguage(languageChanger),
                new SpeakWord(editor, wordProvider, managedSpeakers),
                new ResizeFont(-1, editor),
                new ResizeFont(+1, editor),
                new SwitchBuffer(logProvider, textModel, buffers),
                new SwitchSpeaker(managedSpeakers),
                new EmailAction(emailDialog, textModel, emailer),
                new SpeakAll(textModel, managedSpeakers));

        addListeners(managedSpeakers, buffers, textModel);
    }

    private Emailer createEmailer(LogProvider logProvider, Path configFile) {
        try {
            return new SmtpEmailer(new SmtpConfig(configFile), logProvider);
        } catch (IOException e) {
            log.as(error, e);
            return new NoEmailer(logProvider);
        }
    }

    private void addListeners(final ManagedSpeakers managedSpeakers,
                              final TextBuffers buffers,
                              final TextModel textModel) {

        addShutdownHook(new Runnable() {
            @Override
            public void run() {
                buffers.save(textModel.getText().trim());
            }
        });

        textModel.addTextListener(new TextListener() {
            @Override
            public void handleText(final String text) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        managedSpeakers.speak(text);
                    }
                }).start();
            }
        });
    }

    private static void addShutdownHook(Runnable runnable) {
        Runtime.getRuntime().addShutdownHook(new Thread(runnable));
    }


    public void start() {
        frame.show();
    }
}
